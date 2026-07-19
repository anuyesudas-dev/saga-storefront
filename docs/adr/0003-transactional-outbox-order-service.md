# ADR-0003: Transactional outbox in order-service only

**Status:** accepted (2026-07-18)

## Decision
order-service writes the order and an outbox document in one MongoDB transaction
(Mongo runs as a single-node replica set to enable this); a relay publishes outbox
entries to Kafka. Other services publish directly after their DB write.

## Rationale
The outbox solves the dual-write problem (crash between DB save and Kafka publish
loses the event). It matters most at the saga entry point, where a lost OrderCreated
strands an order silently. Downstream services re-derive their work from upstream
events, and implementing the outbox four times adds boilerplate without insight.

## Alternatives
- Debezium CDC outbox (Kafka Connect): the heavyweight production answer; too much
  infra for a laptop platform. Documented as the scaling path.
- Publish-then-save or save-then-publish everywhere: accepted for non-entry-point
  services, rejected for order creation.
