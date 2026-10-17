#!/usr/bin/env bash
# End-to-end check of the order saga through the API gateway.
#   1. happy path:  stock -> order -> SHIPPED, stock reserved, payment + shipment + notifications exist
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

create_product() { # name quantity
  curl -sf -X POST "$GATEWAY/api/inventory" -H 'Content-Type: application/json' \
    -d "{\"name\":\"$1\",\"description\":\"smoke test\",\"quantity\":$2}" | field id
}

place_order() { # productId quantity price
  curl -sf -X POST "$GATEWAY/api/orders" -H 'Content-Type: application/json' -d "{
    \"customerId\":\"$CUSTOMER_ID\",
    \"items\":[{\"productId\":\"$1\",\"productName\":\"Smoke product\",\"quantity\":$2,\"price\":$3}]}" \
    | field orderId
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
  for path in inventory orders; do
    [ "$(curl -s -o /dev/null -w '%{http_code}' "$GATEWAY/api/$path")" = 200 ] || ready=false
  done
  $ready && break
  printf '.'; sleep 1
done
$ready && echo " ok" || fail "gateway routes not ready after 90s"

echo "1) Happy path"
product=$(create_product "Mechanical keyboard" 10)
order=$(place_order "$product" 2 49.90)
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

echo "2) Out of stock -> order cancelled"
product=$(create_product "Limited edition mug" 1)
order=$(place_order "$product" 5 10)
wait_for_status "$order" CANCELLED && pass "order $order CANCELLED"

echo "3) Payment declined -> order failed, stock released"
product=$(create_product "Gaming laptop" 3)
order=$(place_order "$product" 1 25000)
wait_for_status "$order" FAILED && pass "order $order FAILED (amount over payment limit)"
wait_for_stock "$product" 3/0 && pass "reservation released, 3 available again"

echo "4) Shipping rejected -> payment refunded, stock released"
product=$(create_product "Grand piano" 2)
order=$(place_order "$product" 1 6000)
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
  -d '{"customerId":null,"items":[{"productId":null,"quantity":0,"price":-1}]}')
[ "$(echo "$response" | tail -1)" = 400 ] && [[ "$response" == *'"errors"'* ]] \
  && pass "invalid order -> 400 with field errors" || fail "unexpected: $response"

if [ "${SKIP_KAFKA_CHECKS:-}" = 1 ]; then
  echo "6) Kafka checks skipped"
else
  echo "6) Redelivery and dead letter topic"
  # Replay the happy-path OrderCreatedEvent exactly as it was published (same headers and event id)
  record=$(kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic order-events \
    --from-beginning --timeout-ms 8000 --property print.headers=true --property print.key=true 2>/dev/null \
    | grep "$happy_order" | head -1)
  [ -n "$record" ] || fail "could not read the original OrderCreatedEvent"
  printf '%s\n' "$record" | kafka kafka-console-producer.sh --bootstrap-server localhost:9092 --topic order-events \
    --property parse.headers=true --property parse.key=true >/dev/null
  sleep 5
  [ "$(stock "$happy_product")" = 8/0 ] && pass "replayed OrderCreatedEvent did not reserve stock again" \
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
