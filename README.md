# Saga Storefront

An event-driven e-commerce platform: a **choreographed saga** across four
Spring Boot microservices communicating exclusively through Apache Kafka,
fronted by a JWT-secured API gateway and a React storefront. MongoDB
(database per service), full observability (Micrometer → Prometheus →
Grafana). Runs locally on Docker Compose or a kind Kubernetes cluster.

| | |
|---|---|
| Backend | Java 17 · Spring Boot 3 · Spring Kafka · Spring Data MongoDB · Maven |
| Edge | Spring Cloud Gateway (Server MVC) · RS256 JWT / JWKS · React 19 + Vite |
| Messaging | Apache Kafka 3.9 (KRaft) — at-least-once + idempotent consumers |
| Persistence | MongoDB 7 replica set — one database per service, transactional outbox |
| Observability | Micrometer · Prometheus · Grafana (13-panel provisioned dashboard) |
| Testing | JUnit 5 · Mockito · Testcontainers · WireMock |
| Deployment | Docker Compose · Kubernetes (kind) — Kustomize, probes, non-root |

## Services

| Service | Port | Responsibility |
|---|---|---|
| **api-gateway** | 8080 | The only thing the browser talks to — validates JWTs, routes to the internal services |
| order-service | 8081 | Order lifecycle state machine; saga entry point; transactional outbox |
| inventory-service | 8082 | Catalog + two-phase stock reservation (reserve → confirm/release); no oversell |
| payment-service | 8083 | Simulated payment gateway; write-once payment record |
| notification-service | 8084 | Simulated email/SMS; notification history |
| **auth-service** | 8085 | Register/login; issues RS256 JWTs; JWKS endpoint |
| **frontend** | 8090 (5173 dev) | React storefront — catalog, cart, checkout, login, My Orders |

order-service through notification-service are **internal-only** in the
intended topology — the browser never reaches them directly, only through
the gateway. Each Java service exposes Swagger UI at
`http://localhost:<port>/swagger-ui.html` and health/metrics under
`/actuator`. See **[docs/edge-design.md](docs/edge-design.md)** for the full
edge/auth contract (routes, JWT claims, CORS, identity propagation).

## The saga at a glance

```
OrderCreated ─► InventoryReserved ─► PaymentCompleted ─► order PAID, NotificationSent
      │                │                    │
      │                └─ InventoryReservationFailed ──► order FAILED (no payment attempted)
      │                                     └─ PaymentFailed ─► stock released, order FAILED
      └─ OrderCancelled (only before payment) ─────────► stock released
```

Full diagrams, event catalog, and pattern walkthroughs: **[docs/architecture.md](docs/architecture.md)**.

## Quickstart

Requires JDK 17, Maven 3.9+ (or `./mvnw`), Node 20+, and Docker.

```bash
# Docker Compose
cp infra/.env.example infra/.env      # optional overrides; defaults work
make build up                         # build jars + images, start the stack
make smoke                            # end-to-end saga smoke test, incl. the authed gateway flow
make down                             # stop (make clean also deletes volumes)

# Kubernetes (kind)
cp deploy/k8s/.secrets.env.example deploy/k8s/.secrets.env   # edit values
make k8s-cluster k8s-images k8s-apply # create cluster, load images, deploy
make k8s-forward                      # port-forward 8081-8085, 8080, 8090, 9090, 3000
make k8s-smoke                        # same smoke test, through the forwards
```

Open the storefront at `http://localhost:8090` (Compose) or after
`k8s-forward`. For frontend-only development against an already-running
stack: `cd frontend && npm install && npm run dev` (`http://localhost:5173`).

Details, configuration model, and recovery procedures:
**[docs/deployment.md](docs/deployment.md)** · **[docs/runbook.md](docs/runbook.md)**.

## Testing

```bash
./mvnw verify                 # backend: unit + integration tests, all modules (needs Docker for ITs)
cd frontend && npm run build && npm run lint   # frontend: production build + lint
```

- **Unit tests**: domain state machines, use cases, simulated gateway rules,
  JWT signing/validation (including forged/expired/wrong-issuer rejection),
  gateway route rewriting and identity-header spoofing defense.
- **Integration tests** (Testcontainers): outbox atomicity against a real
  Mongo replica set, concurrent no-oversell reservation, payment/notification
  idempotency, full saga flows over a real Kafka broker (poison-pill →
  dead-letter-topic routing), and the full register/login flow.

Jacoco reports land in each module's `target/site/jacoco/`.

## Repository layout

```
common/common-events/     Event contracts (envelope, payloads, topic names) — no logic
common/common-web/        Correlation-ID filter + error handling starter
common/common-kafka/      Idempotent-consumer pipeline, retry/DLQ policy starter
services/                 order/inventory/payment/notification (hexagonal-lite), auth-service, api-gateway
frontend/                 React (Vite) storefront — served by nginx in the built image
infra/                    Docker Compose, Prometheus & Grafana provisioning
deploy/k8s/               Kubernetes manifests (Kustomize)
scripts/                  End-to-end smoke test (saga + authenticated gateway flow)
docs/                     Architecture, edge design, deployment guide, runbook, ADRs
```

## Documentation

- [Architecture](docs/architecture.md) — diagrams, event catalog, patterns
- [Edge design](docs/edge-design.md) — gateway, auth, JWT contract, CORS
- [Deployment guide](docs/deployment.md) — Compose and Kubernetes, config model
- [Runbook](docs/runbook.md) — operating, debugging, and recovering the platform
- [ADRs](docs/adr/) — the architectural decision record, including scope decisions
