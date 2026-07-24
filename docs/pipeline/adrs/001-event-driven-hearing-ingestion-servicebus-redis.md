# 001. Event-driven hearing ingestion via Azure Service Bus and Redis

**Status:** Accepted, 23 Jul 2026

## Context

Phase 1 of this service is a stateless, synchronous proxy — no event consumption, no cache,
no message broker. Implementing
[`2026-07-22-pcr-hearing-event-ingestion-design.md`](../../2026-07-22-pcr-hearing-event-ingestion-design.md)
introduces this service's first event-driven ingestion path, triggered by Azure Event Grid's
`Hearing_Resulted` event, and its first read of a cache another service owns. Both require new
external dependencies not previously used anywhere in this repo:

- **Azure Service Bus SDK** (`com.azure:azure-messaging-servicebus`, `com.azure:azure-identity`)
  — this service consumes the `Hearing_Resulted` pointer event off a queue it self-provisions in
  the shared per-environment Service Bus namespace, and owns its own scheduled-retry escalation
  (design doc §3.1/§3.4).
- **Redis client** (`spring-boot-starter-data-redis`) — read-only access to the hearing-result
  cache `cpp-context-results` already writes to on every `Hearing_Resulted` event (design doc
  §3.2). This service never writes to this cache.

Per `hmcts-standards.md`, any new external dependency and any integration pattern not previously
used in a repo requires an ADR before proceeding.

## Decision

Adopt both dependencies, using the same client-construction pattern already proven in
`service-cp-crime-hearing-results-document-subscription` ("HRDS"), a live sibling `service-cp-*`
service:

- Raw Azure SDK (`ServiceBusAdministrationClient`, `ServiceBusClientBuilder`,
  `ServiceBusProcessorClient`), not `spring-cloud-azure-stream-binder-servicebus` — matches how
  this org already talks to Service Bus.
- Passwordless, managed-identity auth in Azure (`DefaultAzureCredentialBuilder`); connection
  string only for the local emulator. No connection-string-with-embedded-key anywhere in Azure.
- Retry policy is **not** copied from HRDS — HRDS's `ServiceBusRetryService` is tuned for
  external subscriber-callback availability (hours-long schedule); this service's problem is
  eventual-consistency wait on the Results viewstore (seconds-long). See design doc §3.4 for the
  purpose-built two-tier schedule (~14s in-process, ~4min scheduled redelivery) used instead.
- `StringRedisTemplate` via Spring Data Redis, matching the exact cache-key format
  `cpp-context-results`/the legacy Function App already use — no new key scheme invented.

## Consequences

- This service gains its first genuinely async, at-least-once-delivery code path — idempotency
  on redelivery is a real concern, explicitly handed off to whichever downstream component
  eventually writes `pcr_version` rows (design doc §4), not solved here.
- Local development and CI need a Service Bus emulator and a Redis instance available — not yet
  wired into `docker-compose`/`apitest.gradle` as of this ADR; tracked as a follow-up once the
  emulator-based integration tests land (design doc §5).
- The one genuine cross-team dependency this introduces is the Event Grid subscription itself
  (routing `Hearing_Resulted` into this service's self-provisioned queue) — tracked separately as
  a Jira ticket, not part of this ADR.

## Alternatives considered

- **Spring Cloud Stream's Service Bus binder** — rejected; not the pattern this org's other
  `service-cp-*` services use for Service Bus, and would introduce a second, inconsistent way of
  talking to the same shared namespace.
- **Native Service Bus abandon/redeliver instead of scheduled redelivery** — rejected; no
  built-in backoff, would hammer the Results API in a tight loop on sustained viewstore lag (see
  design doc §3.4).