# Architecture Decision Records

Short, immutable records of significant architectural decisions and their trade-offs.
New decisions get a new file; superseded decisions are marked, never deleted.

| ADR | Decision |
|---|---|
| [0001](0001-saga-choreography.md) | Saga via choreography, not orchestration |
| [0002](0002-at-least-once-idempotent-consumers.md) | At-least-once delivery + idempotent consumers |
| [0003](0003-transactional-outbox-order-service.md) | Transactional outbox in order-service only |
| [0004](0004-event-contracts-shared-module.md) | Event contracts in a shared module; event-carried state transfer |
| [0005](0005-envelope-versioning-tolerant-readers.md) | Envelope versioning + tolerant readers |
| [0006](0006-blocking-retries-preserve-ordering.md) | Blocking consumer retries to preserve per-key ordering |
| [0007](0007-scope-stop-at-kubernetes.md) | Stop at Kubernetes — defer OpenShift and load testing |
| [0008](0008-custom-auth-service-over-keycloak.md) | Custom auth-service over Keycloak |
| [0009](0009-edge-validation-trusted-header.md) | Edge JWT validation + trusted X-User-Id header |
| [0010](0010-client-side-cart.md) | Client-side cart, no cart-service |
