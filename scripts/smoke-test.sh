#!/usr/bin/env bash
# End-to-end check of the order saga through the API gateway.
#   1. happy path:  stock -> order -> SHIPPED, stock reserved, payment + shipment + notifications exist
#   2. out of stock: order -> CANCELLED
#   3. payment over limit: order -> FAILED, reserved stock released
# Usage: scripts/smoke-test.sh [gateway-url]   (default http://localhost:8088)
set -euo pipefail

GATEWAY=${1:-${GATEWAY_URL:-http://localhost:8088}}
CUSTOMER_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
TIMEOUT=60

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
item=$(curl -sf "$GATEWAY/api/inventory/$product")
[ "$(echo "$item" | field availableQuantity)" = 8 ] && [ "$(echo "$item" | field reservedQuantity)" = 2 ] \
  && pass "2 units reserved, 8 available" || fail "unexpected stock: $item"
[ "$(curl -sf "$GATEWAY/api/payments/order/$order" | field amount)" = 99.8 ] \
  && pass "payment captured the order total (99.80)" || fail "payment amount mismatch"
tracking=$(curl -sf "$GATEWAY/api/shipping/order/$order" | field trackingNumber)
[ -n "$tracking" ] && pass "shipment created, tracking $tracking"
sleep 2
types=$(curl -sf "$GATEWAY/api/notifications/order/$order" \
  | python3 -c 'import sys, json; print(" ".join(n["type"] for n in json.load(sys.stdin)))')
for t in ORDER_CREATED PAYMENT_SUCCEEDED ORDER_SHIPPED; do
  [[ "$types" == *"$t"* ]] && pass "notification $t sent" || fail "missing notification $t"
done

echo "2) Out of stock -> order cancelled"
product=$(create_product "Limited edition mug" 1)
order=$(place_order "$product" 5 10)
wait_for_status "$order" CANCELLED && pass "order $order CANCELLED"

echo "3) Payment declined -> order failed, stock released"
product=$(create_product "Gaming laptop" 3)
order=$(place_order "$product" 1 25000)
wait_for_status "$order" FAILED && pass "order $order FAILED (amount over payment limit)"
for _ in $(seq 1 10); do
  available=$(curl -sf "$GATEWAY/api/inventory/$product" | field availableQuantity)
  [ "$available" = 3 ] && break; sleep 1
done
[ "$available" = 3 ] && pass "reservation released, 3 available again" || fail "stock not released ($available)"

printf '\n\033[32mAll saga checks passed.\033[0m\n'
