# Edge design — storefront, gateway, and authentication (Phases 20–25)

Adds a user-facing storefront to the platform without touching the event-driven
core: a React SPA talks to a **Spring Cloud Gateway**, which authenticates
requests with **JWTs issued by a new auth-service** and routes them to the
existing services. The saga, Kafka topics, and event contracts are unchanged.

## Components and ports

| Component | Port | Role |
|---|---|---|
| frontend (React + Vite) | 5173 dev / 8090 nginx | catalog, cart (localStorage), checkout, my orders |
| api-gateway (Spring Cloud Gateway) | 8080 | JWT validation, routing, CORS, identity injection |
| auth-service (Spring Boot) | 8085 | register/login, issues RS256 JWTs, JWKS endpoint |
| existing services | 8081–8084 | unchanged; become *internal* — the browser never calls them |

## Route table

The gateway exposes **only what the storefront needs**. Admin operations
(product create/replenish) and internal queries (payments, notifications,
reservations) are deliberately *not* routed — they stay reachable only on the
internal network / via port-forward, which sidesteps per-resource ownership
checks the storefront doesn't need.

| Gateway route | Auth | Target | Notes |
|---|---|---|---|
| `POST /api/auth/register` | public | auth-service `/api/v1/auth/register` | |
| `POST /api/auth/login` | public | auth-service `/api/v1/auth/login` | returns `{ token, expiresAt, user }` |
| `GET /api/catalog/**` | public | inventory `/api/v1/products/**` | **GET only** — browsing needs no account |
| `POST /api/orders` | JWT | order `/api/v1/orders` | gateway forces `customerId` = JWT sub |
| `GET /api/orders` | JWT | order `/api/v1/orders` | filtered to the caller's orders |
| `GET /api/orders/{id}` | JWT | order `/api/v1/orders/{id}` | 404 unless owned by caller |
| `POST /api/orders/{id}/cancel` | JWT | order `.../cancel` | ownership enforced the same way |

Identity flows via one header: the gateway **strips any client-supplied
`X-User-Id`**, then sets it from the validated token's `sub` claim. Services
trust the header because they are only reachable through the gateway
(trade-off recorded below).

## JWT contract

- **Algorithm:** RS256. The auth-service holds the private key; verifiers fetch
  the public key from `GET /.well-known/jwks.json` (with `kid` rotation
  support). No shared secrets between services.
- **Claims:** `iss=ecommerce-auth`, `sub=<userId (UUID)>`, `email`, `iat`,
  `exp` (30 minutes).
- **No refresh tokens** — expiry means re-login. Right-sized for this project;
  refresh-token rotation is the documented evolution, not the start.
- **Transport:** `Authorization: Bearer <token>`. The SPA keeps the token in
  `localStorage` — accepted XSS trade-off for a learning project, noted in the
  frontend README (the alternative, httpOnly cookies, brings CSRF instead).

## API additions (Phase 21 & 23)

auth-service:

```
POST /api/v1/auth/register  { email, password, displayName }        → 201 { userId }
POST /api/v1/auth/login     { email, password }                     → 200 { token, expiresAt, user: { userId, email, displayName } }
GET  /.well-known/jwks.json                                         → public keys
```

Duplicate email → 409; bad credentials → 401 (same body for unknown email and
wrong password — no user enumeration). Passwords bcrypt-hashed, users in the
auth-service's own Mongo database (`auth_db`) — DB-per-service holds.

inventory-service (the catalog): `Product` gains `name` and `price`
(display/checkout data lives with stock — a separate catalog-service would be
overkill at this size; recorded as the split point if catalog data grows).
`GET /api/v1/products` becomes the public catalog listing.

order-service: **no schema change.** `customerId` (already on the order) *is*
the user identity: when `X-User-Id` is present it overrides the request body's
`customerId`, the list endpoint filters by it, and `GET /{id}`/cancel return
404 for non-owners. Existing direct/Swagger usage without the header keeps
working unchanged.

Checkout prices: the SPA builds the order from catalog data, but the authority
question ("client sends 19.99 for a 99.99 item") already exists today and
payment simulation is amount-driven by design (`.99` = decline). Server-side
price resolution at checkout is noted as the production evolution, not built.

## CORS

Handled **only at the gateway**: allowed origin `http://localhost:5173` (dev)
and the nginx origin (compose/k8s), methods `GET POST OPTIONS`, headers
`Authorization, Content-Type`, no credentials (Bearer only, no cookies).
Services need no CORS config since the browser never reaches them.

## Trade-offs recorded (→ ADRs in Phase 25)

1. **Custom auth-service over Keycloak** — teaches JWT/JWKS mechanics
   end-to-end and fits the RAM budget; Keycloak/OIDC is the enterprise path
   and the migration is contained (same Bearer contract at the gateway).
2. **Edge validation + trusted header** over per-service resource servers —
   one enforcement point, zero change to four services; depends on services
   not being directly exposed. Evolution: token relay + per-service validation
   (or mTLS) when the network can't be trusted.
3. **Client-side cart** — cart is browser state until checkout; no sixth
   service, no cross-device carts. Evolution: cart-service keyed by userId.

## Phase plan

| Phase | Deliverable | Verification |
|---|---|---|
| 21 | auth-service | unit + Testcontainers ITs (register/login/JWKS, dupe email, bad creds) |
| 22 | api-gateway | route tests with mocked JWKS: public passes, missing/expired/forged JWT → 401, header injection & stripping |
| 23 | catalog fields + order ownership | updated service tests, full `./mvnw verify` green |
| 24 | React SPA | manual flow + saga status visibly progressing on My Orders |
| 25 | compose + k8s wiring, authed smoke test, docs + ADRs | `make smoke` / `k8s-smoke` green including gateway flow |
