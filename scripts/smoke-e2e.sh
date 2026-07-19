#!/usr/bin/env bash
# End-to-end saga smoke test against a running Docker Compose stack.
# Exercises: happy path (PAID), deterministic payment failure (.99 rule) with
# stock compensation, insufficient-stock failure, and the authenticated
# gateway flow (register/login, JWT-forced customerId, public catalog,
# 401 without a token, 404-not-403 for a non-owner's order).
set -euo pipefail

ORDER=http://localhost:8081
INVENTORY=http://localhost:8082
PAYMENT=http://localhost:8083
NOTIFICATION=http://localhost:8084
GATEWAY=${GATEWAY_URL:-http://localhost:8080}

PRODUCT="smoke-p$RANDOM"
CORR="smoke-$(date +%s)"

say()  { printf '\n\033[1m== %s\033[0m\n' "$*"; }
fail() { printf '\033[31mFAIL: %s\033[0m\n' "$*"; exit 1; }
ok()   { printf '\033[32mok\033[0m %s\n' "$*"; }

wait_status() { # orderId expected_status
  local id=$1 expected=$2 status=""
  for _ in $(seq 1 30); do
    status=$(curl -sf "$ORDER/api/v1/orders/$id" | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])')
    [ "$status" = "$expected" ] && return 0
    case "$status" in PAID|FAILED|CANCELLED) break;; esac
    sleep 1
  done
  [ "$status" = "$expected" ] || fail "order $id ended as $status, expected $expected"
}

create_order() { # unitPrice quantity -> orderId
  curl -sf -X POST "$ORDER/api/v1/orders" \
    -H 'Content-Type: application/json' -H "X-Correlation-Id: $CORR" \
    -d "{\"customerId\":\"smoke-cust\",\"currency\":\"USD\",\"items\":[
          {\"productId\":\"$PRODUCT\",\"productName\":\"Smoke Widget\",
           \"quantity\":$2,\"unitPrice\":$1}]}" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])'
}

available() {
  curl -sf "$INVENTORY/api/v1/products/$PRODUCT" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["availableQty"])'
}

say "seed product with 10 units"
curl -sf -X POST "$INVENTORY/api/v1/products" -H 'Content-Type: application/json' \
  -d "{\"productId\":\"$PRODUCT\",\"name\":\"Smoke Widget\",\"initialStock\":10}" >/dev/null
ok "product $PRODUCT seeded"

say "happy path: order 2 units at 40.00 -> expect PAID"
# random declines are possible (success-rate 0.9): retry a couple of times
PAID_ID=""
for attempt in 1 2 3 4 5; do
  id=$(create_order 40.00 2)
  for _ in $(seq 1 30); do
    s=$(curl -sf "$ORDER/api/v1/orders/$id" | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])')
    case "$s" in PAID|FAILED|CANCELLED) break;; esac
    sleep 1
  done
  if [ "$s" = "PAID" ]; then PAID_ID=$id; break; fi
  echo "  attempt $attempt: order $id ended $s (random decline) — retrying"
done
[ -n "$PAID_ID" ] || fail "no order reached PAID in 5 attempts"
ok "order $PAID_ID is PAID"

resv=$(curl -sf "$INVENTORY/api/v1/reservations?orderId=$PAID_ID" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])')
[ "$resv" = "CONFIRMED" ] || fail "reservation status $resv, expected CONFIRMED"
ok "reservation CONFIRMED (stock permanently deducted)"

pay=$(curl -sf "$PAYMENT/api/v1/payments?orderId=$PAID_ID" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])')
[ "$pay" = "COMPLETED" ] || fail "payment status $pay"
ok "payment COMPLETED"

notif=$(curl -sf "$NOTIFICATION/api/v1/notifications?orderId=$PAID_ID" \
  | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))')
[ "$notif" -ge 4 ] || fail "expected >=4 notifications (received+receipt x email+sms), got $notif"
ok "$notif notifications recorded"

say "payment failure path: unit price 19.99 -> deterministic decline, stock released"
before=$(available)
fid=$(create_order 19.99 1)
wait_status "$fid" FAILED
after=$(available)
[ "$after" = "$before" ] || fail "stock not released: before=$before after=$after"
ok "order $fid FAILED and compensation restored stock ($after units)"

say "insufficient stock path: order 999 units -> FAILED without payment"
sid=$(create_order 40.00 999)
wait_status "$sid" FAILED
pay_code=$(curl -s -o /dev/null -w '%{http_code}' "$PAYMENT/api/v1/payments?orderId=$sid")
[ "$pay_code" = "404" ] || fail "payment should not exist for $sid (got HTTP $pay_code)"
ok "order $sid FAILED at inventory, payment never attempted"

say "authenticated flow: register/login through the gateway, order via JWT"
EMAIL="smoke-$(date +%s)@example.com"
register_and_login() { # email password -> token
  curl -sf -X POST "$GATEWAY/api/auth/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$2\",\"displayName\":\"Smoke User\"}" >/dev/null
  curl -sf -X POST "$GATEWAY/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$2\"}" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])'
}
TOKEN=$(register_and_login "$EMAIL" "Sm0ke-Passw0rd")
ok "registered and logged in as $EMAIL"

catalog_code=$(curl -s -o /dev/null -w '%{http_code}' "$GATEWAY/api/catalog")
[ "$catalog_code" = "200" ] || fail "GET /api/catalog without a token should be public, got HTTP $catalog_code"
ok "catalog browsing needs no account"

unauth_code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$GATEWAY/api/orders" \
  -H 'Content-Type: application/json' -d '{}')
[ "$unauth_code" = "401" ] || fail "POST /api/orders without a token should be 401, got HTTP $unauth_code"
ok "creating an order without a token is rejected"

AUTHED_ID=$(curl -sf -X POST "$GATEWAY/api/orders" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"customerId\":\"ignored-by-gateway\",\"currency\":\"USD\",\"items\":[
        {\"productId\":\"$PRODUCT\",\"productName\":\"Smoke Widget\",
         \"quantity\":1,\"unitPrice\":40.00}]}" \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["id"])')
owner=$(curl -sf "$GATEWAY/api/orders/$AUTHED_ID" -H "Authorization: Bearer $TOKEN" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["customerId"])')
[ "$owner" != "ignored-by-gateway" ] || fail "gateway did not override customerId from the JWT"
ok "order $AUTHED_ID owned by the JWT subject, not the client-supplied customerId"

OTHER_EMAIL="smoke-other-$(date +%s)@example.com"
OTHER_TOKEN=$(register_and_login "$OTHER_EMAIL" "Sm0ke-Passw0rd-2")
other_code=$(curl -s -o /dev/null -w '%{http_code}' "$GATEWAY/api/orders/$AUTHED_ID" \
  -H "Authorization: Bearer $OTHER_TOKEN")
[ "$other_code" = "404" ] || fail "a non-owner should get 404 (not 403) for someone else's order, got HTTP $other_code"
ok "a different account gets 404, not 403, for an order it doesn't own"

say "correlation id '$CORR' appears in every service's logs"
# SMOKE_LOGS selects where logs live: docker (compose, default) or k8s
logs_of() {
  case "${SMOKE_LOGS:-docker}" in
    k8s) kubectl -n ecommerce logs "deploy/$1" --tail=-1 2>&1 ;;
    *)   docker logs "$1" 2>&1 ;;
  esac
}
for svc in order-service inventory-service payment-service notification-service; do
  # no `grep -q`: with pipefail, early grep exit SIGPIPEs the log command and fails the pipeline
  logs_of "$svc" | grep "$CORR" >/dev/null || fail "correlation id missing in $svc logs"
  ok "$svc"
done

printf '\n\033[32mE2E SMOKE PASSED\033[0m\n'
