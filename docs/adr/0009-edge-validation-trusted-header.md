# ADR-0009: Edge JWT validation + trusted X-User-Id header, not per-service resource servers

**Status:** accepted (2026-07-19)

## Decision
JWT validation happens once, at `api-gateway`. It strips any client-supplied
`X-User-Id`, validates the bearer token, and re-sets `X-User-Id` from the
token's `sub`. Downstream services (order-service, inventory-service, ...)
never see or validate a JWT - they trust `X-User-Id` because they are only
reachable through the gateway.

## Rationale
One enforcement point instead of four. Adding an OAuth2 resource server to
every service would mean four copies of the same JWKS-fetching, algorithm-
pinning, issuer-checking configuration (see ADR-0008 and the gateway's
`SecurityConfig`) for services that don't otherwise need Spring Security at
all - order-service's ownership check (`OrderQueryService.getById`,
`CancelOrderUseCase.handle`) is a two-line header comparison specifically
*because* it can assume the header is already trustworthy.

## Consequences
- **Depends on network isolation holding.** The trust in `X-User-Id` is only
  as good as "services aren't directly reachable" - true in this compose/k8s
  setup (only the gateway is port-mapped/exposed), but it is a real
  assumption, not a cryptographic guarantee. Anyone who gets a packet onto
  the internal network can forge the header.
- Non-owner access returns 404, not 403, from the affected services
  themselves (order-service) - a second, defense-in-depth layer behind the
  gateway's own routing, in case a request ever reaches a service with a
  stale or absent header.
- Services stay simple: no security starter dependency, easy to unit test
  (a plain header read + `equals`, not a mocked `SecurityContext`).

## Evolution path
If services must one day be reachable on a network that isn't fully trusted
(shared cluster, zero-trust mesh), the documented next step is token relay
(the gateway forwards the original JWT, each service validates it itself) or
mTLS between the gateway and services - not a redesign, an additive change
at the point where the trust assumption stops holding.

## Alternatives considered
**Per-service OAuth2 resource servers** - the standard "don't trust the
network" posture; the right call once services are reachable from anywhere
other than the gateway.
