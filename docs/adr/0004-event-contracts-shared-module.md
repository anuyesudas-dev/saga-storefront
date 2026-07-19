# ADR-0004: Event contracts in a shared module; event-carried state transfer

**Status:** accepted (2026-07-18)

## Decision
Event contracts (envelope + payload records + topic names) live in `common-events`,
a module containing contracts ONLY — no Spring, no entities, no logic. Events carry
the data downstream consumers need (e.g. `InventoryReserved` carries the order
amount for payment-service) rather than consumers calling back over REST.

## Rationale
In a monorepo, a contracts module gives compile-time safety at the same coupling
cost a schema registry would impose. Event enrichment keeps services autonomous —
no synchronous inter-service calls.

## Alternatives
- Confluent Schema Registry + Avro/JSON-Schema: right for independent teams and
  cross-org contracts; extra infrastructure here. The envelope maps mechanically to
  it (and to CloudEvents) if needed later.
- Callback REST queries or local read models for cross-service data: rejected
  (coupling / complexity respectively).
