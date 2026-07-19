# ADR-0008: Custom auth-service over Keycloak

**Status:** accepted (2026-07-19)

## Decision
Identity is issued by a small, hand-built `auth-service` (register/login,
RS256-signed JWTs, a JWKS endpoint) rather than an off-the-shelf identity
provider like Keycloak.

## Rationale
The point of building this piece was to learn JWT/JWKS mechanics end to end -
key generation, `kid`-based rotation, signature verification against a public
key the issuer never shares - which a managed IdP would abstract away
entirely. A custom service also fits the platform's RAM budget (Keycloak's
JVM + its own database is a meaningfully heavier footprint on a laptop-sized
compose stack) and needs zero extra infrastructure beyond the Mongo replica
set every other service already uses.

## Consequences
- No password reset, MFA, social login, or admin console - `auth-service`
  does exactly two things (register, login) and nothing else.
- The signing key is ephemeral and in-memory (`AppConfig.rsaSigningKey()`),
  regenerated on every restart. Fine for a single instance; a multi-replica
  deployment would need a shared/persisted key (a PEM from a platform Secret)
  so all replicas sign with, and verify against, the same key.
- The migration path to a real IdP is contained: the gateway validates
  against a JWKS URI and an issuer string - swapping `auth-service` for
  Keycloak (or any OIDC provider) means pointing `jwk-set-uri` and
  `gateway.jwt.issuer` at the new provider. No route or service code changes.

## Alternatives considered
**Keycloak / any OIDC provider** - the enterprise-correct choice once
password reset, MFA, or social login are actually needed; deliberately not
built here since the learning goal was the mechanics themselves.
