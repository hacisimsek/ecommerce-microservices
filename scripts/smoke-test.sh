#!/usr/bin/env bash
# End-to-end check through the API gateway.
#   0. demo catalog is seeded and inventory has a stock record for every product
#   1. happy path: catalog product -> stock -> cart -> checkout -> SHIPPED, stock committed,
#      payment + shipment + notifications exist; cart follows catalog price changes
#   1b. checkout rules: client-sent prices are ignored, Idempotency-Key replays the same order,
#       inactive products cannot be added to a cart
#   2. out of stock: order -> CANCELLED
#   3. payment over limit: order -> FAILED, reserved stock released
#   4. shipping rejected: order -> FAILED, payment refunded, reserved stock released
#   5. API errors are RFC 9457 problem details
#   6. (needs docker compose) a redelivered event is ignored; a poison message lands on the DLT
# Usage: scripts/smoke-test.sh [gateway-url]   (default http://localhost:8088)
#        SKIP_KAFKA_CHECKS=1 skips step 6
set -euo pipefail

GATEWAY=${1:-${GATEWAY_URL:-http://localhost:8088}}
CUSTOMER_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
TIMEOUT=60
COMPOSE="docker compose -f $(dirname "$0")/../docker-compose.yml"

field() { python3 -c 'import sys, json; print(json.load(sys.stdin)[sys.argv[1]])' "$1"; }
pass() { printf '  \033[32m✔\033[0m %s\n' "$1"; }
fail() { printf '  \033[31m✘\033[0m %s\n' "$1"; exit 1; }

CATEGORY_ID=""

# Like curl -sf, but retries while the answer is 503: right after startup a service may not yet
# see its dependencies in Eureka and answers with a retryable problem detail.
call() { # curl args... -> body on stdout
  local out code
  out=$(mktemp)
  for _ in $(seq 1 30); do
    code=$(curl -s -o "$out" -w '%{http_code}' "$@")
    [ "$code" != 503 ] && break
    sleep 1
  done
  cat "$out"; rm -f "$out"
  [[ "$code" == 2* ]]
}

create_product() { # name price stock -> productId (catalog product whose inventory record has <stock> units)
  local sku id
  sku="SMOKE-$(uuidgen | cut -c1-8)"
  id=$(curl -sf -X POST "$GATEWAY/api/catalog/products" -H 'Content-Type: application/json' -d "{
    \"sku\":\"$sku\",\"name\":\"$1\",\"description\":\"smoke test\",\"price\":$2,\"categoryId\":\"$CATEGORY_ID\"}" \
    | field id)
  # Inventory creates the stock record from the ProductCreatedEvent
  for _ in $(seq 1 20); do
    curl -sf -o /dev/null "$GATEWAY/api/inventory/$id" && break
    sleep 0.5
  done
  curl -sf -o /dev/null -X PUT "$GATEWAY/api/inventory/$id/stock" -H 'Content-Type: application/json' \
    -d "{\"availableQuantity\":$3}" || fail "no inventory record for product $id"
  echo "$id"
}

add_to_cart() { # productId quantity
  call -X POST "$GATEWAY/api/carts/$CUSTOMER_ID/items" -H 'Content-Type: application/json' \
    -d "{\"productId\":\"$1\",\"quantity\":$2}" >/dev/null
}

place_order() { # productId quantity -> orderId, through cart + checkout
  add_to_cart "$1" "$2"
  call -X POST "$GATEWAY/api/carts/$CUSTOMER_ID/checkout" | field orderId
}

stock() { # productId -> "available/reserved"
  curl -sf "$GATEWAY/api/inventory/$1" \
    | python3 -c 'import sys, json; d = json.load(sys.stdin); print(d["availableQuantity"], d["reservedQuantity"], sep="/")'
}

wait_for_stock() { # productId expected("available/reserved")
  local current=""
  for _ in $(seq 1 20); do
    current=$(stock "$1")
    [ "$current" = "$2" ] && return 0
    sleep 1
  done
  fail "stock of $1 is $current, expected $2 (available/reserved)"
}

notification_types() { # orderId
  curl -sf "$GATEWAY/api/notifications/order/$1" \
    | python3 -c 'import sys, json; print(" ".join(sorted(n["type"] for n in json.load(sys.stdin))))'
}

kafka() { $COMPOSE exec -T kafka /opt/kafka/bin/"$@"; }

wait_for_status() { # orderId expectedStatus
  local status=""
  for _ in $(seq 1 $TIMEOUT); do
    status=$(curl -sf "$GATEWAY/api/orders/$1" | field status)
    [ "$status" = "$2" ] && return 0
    sleep 1
  done
  fail "order $1 ended in $status, expected $2"
}

echo "Gateway: $GATEWAY"

# Right after startup the gateway answers 503 until its service-discovery cache has every route
printf '  waiting for gateway routes'
for _ in $(seq 1 90); do
  ready=true
  for path in inventory orders catalog/categories carts/$CUSTOMER_ID; do
    [ "$(curl -s -o /dev/null -w '%{http_code}' "$GATEWAY/api/$path")" = 200 ] || ready=false
  done
  $ready && break
  printf '.'; sleep 1
done
$ready && echo " ok" || fail "gateway routes not ready after 90s"

echo "0) Demo catalog"
products=$(curl -sf "$GATEWAY/api/catalog/products?size=100" | field totalElements)
[ "$products" -ge 12 ] && pass "catalog serves $products products" || fail "catalog has $products products"
CATEGORY_ID=$(curl -sf "$GATEWAY/api/catalog/categories" \
  | python3 -c 'import sys, json; print(next(c["id"] for c in json.load(sys.stdin) if c["slug"] == "electronics"))')
items=$(curl -sf "$GATEWAY/api/inventory" | python3 -c 'import sys, json; print(len(json.load(sys.stdin)))')
[ "$items" -ge 12 ] && pass "inventory has stock records for the seeded products ($items)" \
  || fail "inventory has $items records"

echo "1) Happy path: cart -> checkout -> shipped"
product=$(create_product "Mechanical keyboard" 49.90 10)
add_to_cart "$product" 1
add_to_cart "$product" 1
cart=$(call "$GATEWAY/api/carts/$CUSTOMER_ID")
[ "$(echo "$cart" | field itemCount)" = 2 ] && [ "$(echo "$cart" | field subtotal)" = 99.8 ] \
  && pass "cart holds 2 x 49.90 = 99.80" || fail "unexpected cart: $cart"
order=$(call -X POST "$GATEWAY/api/carts/$CUSTOMER_ID/checkout" | field orderId)
[ "$(curl -sf "$GATEWAY/api/carts/$CUSTOMER_ID" | field itemCount)" = 0 ] && pass "checkout emptied the cart"
wait_for_status "$order" SHIPPED && pass "order $order reached SHIPPED"
wait_for_stock "$product" 8/0 && pass "stock committed on shipment: 8 available, 0 reserved"
[ "$(curl -sf "$GATEWAY/api/payments/order/$order" | field amount)" = 99.8 ] \
  && pass "payment captured the order total (99.80)" || fail "payment amount mismatch"
tracking=$(curl -sf "$GATEWAY/api/shipping/order/$order" | field trackingNumber)
[ -n "$tracking" ] && pass "shipment created, tracking $tracking"
sleep 2
types=$(notification_types "$order")
[ "$types" = "ORDER_CREATED ORDER_SHIPPED PAYMENT_SUCCEEDED" ] \
  && pass "notifications sent: $types" || fail "unexpected notifications: $types"
happy_order=$order; happy_product=$product

echo "1b) Checkout rules"
direct=$(call -X POST "$GATEWAY/api/orders" -H 'Content-Type: application/json' -H 'Idempotency-Key: smoke-'"$CUSTOMER_ID" \
  -d "{\"customerId\":\"$CUSTOMER_ID\",\"items\":[{\"productId\":\"$product\",\"quantity\":1,\"price\":0.01}]}")
[ "$(echo "$direct" | field totalAmount)" = 49.9 ] && pass "client-sent price ignored, catalog price 49.90 charged" \
  || fail "unexpected total: $direct"
replay_code=$(curl -s -o /tmp/smoke-replay.json -w '%{http_code}' -X POST "$GATEWAY/api/orders" \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: smoke-'"$CUSTOMER_ID" \
  -d "{\"customerId\":\"$CUSTOMER_ID\",\"items\":[{\"productId\":\"$product\",\"quantity\":1}]}")
[ "$replay_code" = 200 ] && [ "$(field orderId < /tmp/smoke-replay.json)" = "$(echo "$direct" | field orderId)" ] \
  && pass "repeated Idempotency-Key returned the same order (200)" || fail "replay returned $replay_code"
wait_for_status "$(echo "$direct" | field orderId)" SHIPPED

curl -sf -o /dev/null -X PUT "$GATEWAY/api/catalog/products/$product" -H 'Content-Type: application/json' -d "{
  \"sku\":\"$(curl -sf "$GATEWAY/api/catalog/products/$product" | field sku)\",\"name\":\"Mechanical keyboard\",
  \"price\":39.90,\"categoryId\":\"$CATEGORY_ID\"}"
add_to_cart "$product" 1
[ "$(curl -sf "$GATEWAY/api/carts/$CUSTOMER_ID" | field subtotal)" = 39.9 ] \
  && pass "cart shows the new catalog price (39.90)" || fail "cart did not pick up the price change"
curl -sf -o /dev/null -X DELETE "$GATEWAY/api/carts/$CUSTOMER_ID"

inactive=$(create_product "Discontinued gadget" 15 5)
curl -sf -o /dev/null -X PUT "$GATEWAY/api/catalog/products/$inactive" -H 'Content-Type: application/json' -d "{
  \"sku\":\"$(curl -sf "$GATEWAY/api/catalog/products/$inactive" | field sku)\",\"name\":\"Discontinued gadget\",
  \"price\":15,\"categoryId\":\"$CATEGORY_ID\",\"active\":false}"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$GATEWAY/api/carts/$CUSTOMER_ID/items" \
  -H 'Content-Type: application/json' -d "{\"productId\":\"$inactive\",\"quantity\":1}")
[ "$code" = 409 ] && pass "inactive product cannot be added to the cart (409)" || fail "got $code"

echo "2) Out of stock -> order cancelled"
product=$(create_product "Limited edition mug" 10 1)
order=$(place_order "$product" 5)
wait_for_status "$order" CANCELLED && pass "order $order CANCELLED"

echo "3) Payment declined -> order failed, stock released"
product=$(create_product "Gaming laptop" 25000 3)
order=$(place_order "$product" 1)
wait_for_status "$order" FAILED && pass "order $order FAILED (amount over payment limit)"
wait_for_stock "$product" 3/0 && pass "reservation released, 3 available again"

echo "4) Shipping rejected -> payment refunded, stock released"
product=$(create_product "Grand piano" 6000 2)
order=$(place_order "$product" 1)
wait_for_status "$order" FAILED && pass "order $order FAILED (over carrier's insured value)"
for _ in $(seq 1 20); do
  payment_status=$(curl -sf "$GATEWAY/api/payments/order/$order" | field status)
  [ "$payment_status" = REFUNDED ] && break; sleep 1
done
[ "$payment_status" = REFUNDED ] && pass "payment REFUNDED" || fail "payment is $payment_status, expected REFUNDED"
wait_for_stock "$product" 2/0 && pass "reservation released, 2 available again"
sleep 2
[[ "$(notification_types "$order")" == *PAYMENT_REFUNDED* ]] && pass "customer notified about the refund" \
  || fail "missing PAYMENT_REFUNDED notification"

echo "5) API errors are problem details"
response=$(curl -s -w '\n%{http_code} %{content_type}' "$GATEWAY/api/orders/$(uuidgen)")
[[ "$(echo "$response" | tail -1)" == "404 application/problem+json"* ]] \
  && pass "unknown order -> 404 application/problem+json" || fail "unexpected: $response"
response=$(curl -s -w '\n%{http_code}' -X POST "$GATEWAY/api/orders" -H 'Content-Type: application/json' \
  -d '{"customerId":null,"items":[{"productId":null,"quantity":0}]}')
[ "$(echo "$response" | tail -1)" = 400 ] && [[ "$response" == *'"errors"'* ]] \
  && pass "invalid order -> 400 with field errors" || fail "unexpected: $response"

if [ "${SKIP_KAFKA_CHECKS:-}" = 1 ]; then
  echo "6) Kafka checks skipped"
else
  echo "6) Redelivery and dead letter topic"
  # Replay the happy-path OrderCreatedEvent exactly as it was published (same headers and event id)
  stock_before=$(stock "$happy_product")
  record=$(kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic order-events \
    --from-beginning --timeout-ms 8000 --property print.headers=true --property print.key=true 2>/dev/null \
    | grep "$happy_order" | head -1)
  [ -n "$record" ] || fail "could not read the original OrderCreatedEvent"
  printf '%s\n' "$record" | kafka kafka-console-producer.sh --bootstrap-server localhost:9092 --topic order-events \
    --property parse.headers=true --property parse.key=true >/dev/null
  sleep 5
  [ "$(stock "$happy_product")" = "$stock_before" ] && pass "replayed OrderCreatedEvent did not reserve stock again" \
    || fail "duplicate reserved stock: $(stock "$happy_product")"
  [ "$(notification_types "$happy_order")" = "ORDER_CREATED ORDER_SHIPPED PAYMENT_SUCCEEDED" ] \
    && pass "replayed event did not notify the customer twice" || fail "duplicate notification"

  marker="poison-$(uuidgen)"
  printf '%s\tthis is not json\n' "$marker" | kafka kafka-console-producer.sh --bootstrap-server localhost:9092 \
    --topic order-events --property parse.key=true >/dev/null
  dlt=""
  for _ in $(seq 1 10); do
    dlt=$(kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic order-events.DLT \
      --from-beginning --timeout-ms 3000 --property print.key=true 2>/dev/null | grep "$marker" || true)
    [ -n "$dlt" ] && break
  done
  [ -n "$dlt" ] && pass "undeserializable message parked on order-events.DLT" || fail "poison message not on DLT"
fi

printf '\n\033[32mAll saga checks passed.\033[0m\n'
