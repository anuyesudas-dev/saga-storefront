# frontend

React (Vite) storefront - the only thing the browser talks to. Every
request goes through the gateway (`/api/auth/*`, `/api/catalog/*`,
`/api/orders/*`); it never calls order-service, inventory-service, etc.
directly. See `docs/edge-design.md` at the repo root for the full contract.

## Run locally

```bash
npm install
cp .env.example .env.local   # only needed if the gateway isn't on localhost:8080
npm run dev                  # http://localhost:5173
```

Requires the gateway (and the rest of the stack) running - `make up` from
the repo root, or `make k8s-forward` for the kind cluster.

## Build

```bash
npm run build      # dist/, served by nginx in the Docker image
npm run lint
```

## Notes

- **Cart** is `localStorage`-backed, not a service - browser state until
  checkout (recorded trade-off, `docs/edge-design.md`).
- **Auth token** is stored in `localStorage`, not an httpOnly cookie -
  accepted XSS trade-off for a learning project (the alternative brings
  CSRF instead); see `docs/edge-design.md`.
- **Order status** on the order-detail page polls the gateway every 2s
  until the saga reaches a terminal status (`PAID`, `FAILED`,
  `CANCELLED`) - there's no push channel, so this is how "watch the saga
  resolve" works from the browser.
