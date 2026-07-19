# ADR-0002: At-least-once delivery + idempotent consumers

**Status:** accepted (2026-07-18)

## Decision
All Kafka consumption is at-least-once. Every consumer is idempotent: each event's
`eventId` is recorded in a per-service `processed_events` collection (unique index,
TTL ~7 days) and already-seen events are skipped. Natural keys (unique `orderId`
index on reservations and payments) act as a second idempotency layer.

## Rationale
Kafka exactly-once semantics do not extend to external databases (the Mongo write is
outside the Kafka transaction), so idempotency is required regardless. At-least-once
+ idempotent handlers is the mainstream production pattern.

## Alternatives
Kafka transactions + read_committed: heavier operationally, still doesn't cover the
DB write, rejected.
