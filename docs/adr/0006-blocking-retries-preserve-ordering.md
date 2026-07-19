# ADR-0006: Blocking consumer retries to preserve per-key ordering

**Status:** accepted (2026-07-18)

## Decision
Consumer failures are retried in place with exponential backoff (Spring Kafka
DefaultErrorHandler, ~3 attempts), then dead-lettered to `<topic>.DLT` with error
metadata in headers. All topics are keyed by `orderId` (3 partitions locally).

## Rationale
The saga depends on per-order event ordering. Non-blocking retry topics
(`@RetryableTopic`) break per-key ordering — a retrying reservation can be overtaken
by its own release. Blocking retries trade a little throughput under failure for
correctness.

## Alternatives
Non-blocking retry topics: appropriate for order-independent workloads (e.g.
notification sending); may be adopted there later and noted as such.
