# 006. PCR API security model — bearer JWT + subscription key, no OAuth2 scope

**Status:** Accepted, 27 Jul 2026
**Jira:** AMP-890 — PCR contract redesign

## Context

PCR's contract used `oAuthJwt` (OAuth2 client-credentials + `prosecution-case-results.read` scope).

## Decision

Switch to the `api-cp-crime-hearing-results-document-subscription` (HRDS) pattern:

- `bearerAuth` — `type: http`, `scheme: bearer`, `bearerFormat: JWT`
- `subscriptionKey` — `type: apiKey`, `in: header`, `name: Ocp-Apim-Subscription-Key`

Both required together. No OAuth2 scope. `403` now means "valid subscription key required but not
subscribed to this API."

## Consequences

- No scope-level authorization — any caller with a valid bearer JWT + subscription gets full read access.
- No consumer implemented against this contract yet, so zero migration cost.

## Compliance notes

PCR returns OFFICIAL-SENSITIVE defendant data (encrypted at rest per ADR-004/AMP-891). Access
control now rests on APIM subscription + bearer JWT, not a distinct scope — a deliberate trade-off.