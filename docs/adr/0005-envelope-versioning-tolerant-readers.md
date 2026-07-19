# ADR-0005: Envelope versioning + tolerant readers

**Status:** accepted (2026-07-18)

## Decision
Every event uses a standard envelope (`eventId`, `eventType`, `eventVersion`,
`occurredAt`, `correlationId`, `source`, `payload`). Evolution rules:
1. Additive optional fields: same version; consumers ignore unknown fields
   (`FAIL_ON_UNKNOWN_PROPERTIES=false`).
2. Breaking changes bump `eventVersion`; producers may dual-publish during migration.
3. Fields are never reused or repurposed.
4. Topic names never encode versions.

Wire format details: JSON; `BigDecimal` as strings (precision); `Instant` as
ISO-8601 UTC; Spring Kafka type headers disabled (no Java class names on the wire);
deserialization is by topic since each topic carries exactly one event type.

## Alternatives
Versioned topics (`order-created-v2`): cleaner isolation but painful offset/group
migration; rejected for JSON-based contracts.
