# Event-Driven E-Commerce Platform

An event-driven order processing platform built as a **choreographed saga** across
four Spring Boot microservices communicating exclusively through Apache Kafka,
with MongoDB (database per service) and full observability (Micrometer →
Prometheus → Grafana). Runs locally on Docker Compose or a kind Kubernetes
cluster.

| | |
|---|---|
| Stack | Java 17 · Spring Boot 3 · Spring Kafka · Spring Data MongoDB · Maven |
| Messaging | Apache Kafka 3.9 (KRaft) — at-least-once + idempotent consumers |
| Persistence | MongoDB 7 replica set — one database per service, transactional outbox |
| Observability | Micrometer · Prometheus · Grafana (13-panel provisioned dashboard) |
| Testing | JUnit 5 · Mockito · Testcontainers — 81 unit + 12 integration tests |
| Deployment | Docker Compose · Kubernetes (kind) — Kustomize, probes, non-root |

## Services

| Service | Port | Responsibility |
|---|---|---|
| order-service | 8081 | Order lifecycle state machine; saga entry point; transactional outbox |
| inventory-service | 8082 | Two-phase stock reservation (reserve → confirm/release); no oversell |
| payment-service | 8083 | Simulated payment gateway; write-once payment record |
| notification-service | 8084 | Simulated email/SMS; notification history |

Each service exposes Swagger UI at `http://localhost:<port>/swagger-ui.html` and
health/metrics under `/actuator`.

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

Requires JDK 17, Maven 3.9+ (or `./mvnw`), and Docker.

```bash
# Docker Compose
cp infra/.env.example infra/.env      # optional overrides; defaults work
make build up                         # build jars + images, start the stack
make smoke                            # end-to-end saga smoke test
make down                             # stop (make clean also deletes volumes)

# Kubernetes (kind)
cp deploy/k8s/.secrets.env.example deploy/k8s/.secrets.env   # edit values
make k8s-cluster k8s-images k8s-apply # create cluster, load images, deploy
make k8s-forward                      # port-forward 8081-8084, 9090, 3000
make k8s-smoke                        # same smoke test, through the forwards
```

Details, configuration model, and recovery procedures:
**[docs/deployment.md](docs/deployment.md)** · **[docs/runbook.md](docs/runbook.md)**.

## Testing

```bash
./mvnw verify        # unit + integration tests, all modules (needs Docker for ITs)
```

- **Unit tests** (81): domain state machines, use cases, simulated gateway rules.
- **Integration tests** (12, Testcontainers): outbox atomicity against a real
  Mongo replica set, concurrent no-oversell reservation, payment/notification
  idempotency, and full saga flows over a real Kafka broker — including
  poison-pill → dead-letter-topic routing.

Jacoco reports land in each module's `target/site/jacoco/`.

## Repository layout

```
common/common-events/     Event contracts (envelope, payloads, topic names) — no logic
common/common-web/        Correlation-ID filter + error handling starter
common/common-kafka/      Idempotent-consumer pipeline, retry/DLQ policy starter
services/                 The four microservices (hexagonal-lite structure)
infra/                    Docker Compose, Prometheus & Grafana provisioning
deploy/k8s/               Kubernetes manifests (Kustomize)
scripts/                  End-to-end smoke test
docs/                     Architecture, deployment guide, runbook, ADRs
```

## Documentation

- [Architecture](docs/architecture.md) — diagrams, event catalog, patterns
- [Deployment guide](docs/deployment.md) — Compose and Kubernetes, config model
- [Runbook](docs/runbook.md) — operating, debugging, and recovering the platform
- [ADRs](docs/adr/) — the architectural decision record, including scope decisions
