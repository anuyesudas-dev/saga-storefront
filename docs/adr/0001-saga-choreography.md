# ADR-0001: Saga via choreography, not orchestration

**Status:** accepted (2026-07-18)

## Decision
The order saga (order → inventory → payment → notification) is coordinated by
choreography: each service reacts to events and emits its own. There is no central
saga orchestrator.

## Rationale
Four services and a linear flow fit choreography naturally; it maximizes service
autonomy and demonstrates event-driven design. The known weakness — no single place
answering "where is my saga?" — is mitigated by (a) order-service tracking order
status as a state machine fed by downstream events, and (b) a correlation ID
carried through every event and log line.

## Alternatives
An explicit orchestrator (state-machine table, command topics) is the better choice
once sagas grow past ~6 steps or start branching. That is the documented evolution
path, not the starting point.
