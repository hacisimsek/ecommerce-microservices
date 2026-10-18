#!/usr/bin/env bash
# Gives every product in the inventory a sellable quantity, so the seeded demo catalog can be
# ordered right away. Products start at zero stock when the catalog creates them.
# Usage: scripts/seed-demo-stock.sh [quantity] [gateway-url]   (defaults: 25, http://localhost:8088)
set -euo pipefail

QUANTITY=${1:-25}
GATEWAY=${2:-${GATEWAY_URL:-http://localhost:8088}}

ids=$(curl -sf "$GATEWAY/api/inventory" | python3 -c 'import sys, json; print(" ".join(i["id"] for i in json.load(sys.stdin)))')
count=0
for id in $ids; do
  curl -sf -o /dev/null -X PUT "$GATEWAY/api/inventory/$id/stock" -H 'Content-Type: application/json' \
    -d "{\"availableQuantity\":$QUANTITY}"
  count=$((count + 1))
done
echo "Set available stock to $QUANTITY for $count products"
