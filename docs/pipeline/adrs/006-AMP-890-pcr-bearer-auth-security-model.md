# 006. PCR API security model — bearer JWT + subscription key, no OAuth2 scope

**Status:** Accepted, 27 Jul 2026
**Jira:** AMP-890 — PCR contract redesign

## Context

Switch to the `api-cp-crime-hearing-results-document-subscription` (HRDS) pattern:

- `bearerAuth` — `type: http`, `scheme: bearer`, `bearerFormat: JWT`
- `subscriptionKey` — `type: apiKey`, `in: header`, `name: Ocp-Apim-Subscription-Key`

Both required together. `403` now means "valid subscription key required but not
subscribed to this API."


## Compliance notes

PCR returns OFFICIAL-SENSITIVE defendant data (encrypted at rest per ADR-004/AMP-891). Access
control now rests on APIM subscription + bearer JWT.
