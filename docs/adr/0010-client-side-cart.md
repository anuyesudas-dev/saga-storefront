# ADR-0010: Client-side cart, no cart-service

**Status:** accepted (2026-07-19)

## Decision
The shopping cart lives in the browser's `localStorage` (`frontend/src/context/CartContext.jsx`)
until checkout. There is no cart-service and no server-side cart record.

## Rationale
A cart is disposable, single-user, single-device state right up until the
moment it becomes an order - at which point order-service's transactional
outbox already gives it durability and the saga takes over. Standing up a
sixth service (plus its own database, per the platform's DB-per-service
rule) to persist pre-checkout scratch state would be infrastructure for a
problem this project doesn't have.

## Consequences
- **No cross-device carts.** Adding an item on a phone and checking out on a
  laptop doesn't work - the cart doesn't exist anywhere the other device can
  see it.
- **Cart survives a reload but not a "clear site data".** Acceptable for a
  storefront demo; not acceptable if cart abandonment recovery (e.g.
  "you left something in your cart" email) is ever a requirement, since
  nothing server-side knows the cart existed.
- Checkout price authority is a separate, already-recorded gap
  (`docs/edge-design.md`): the client sends `unitPrice` on order creation,
  and nothing currently re-derives it from the catalog at checkout time.
  Whoever builds server-side price resolution should look at this ADR too -
  a cart-service would be a natural place to own that check if the two
  evolve together.

## Evolution path
A `cart-service` keyed by `userId`, written the same way as the other
services (own Mongo database, REST API), is the documented next step if
cross-device carts or abandonment-recovery become real requirements. Nothing
in the current design blocks adding it later - the frontend's `CartContext`
is the one place that would need to swap `localStorage` reads/writes for API
calls.

## Alternatives considered
**A cart-service from the start** - correct once cross-device carts or
abandonment recovery are actual requirements; premature for a project whose
storefront scope is "make the saga demoable from a browser."
