# 007. Replace Service Bus queue ingestion with a direct Event Grid webhook

**Status:** Accepted, 29 Jul 2026
**Jira:** AMP-892 — replace Service Bus queue with webhook ingestion

## Context

Implementing,
[`2026-07-29-pcr-eventgrid-webhook-ingestion-design.md`](../designs/2026-07-29-pcr-eventgrid-webhook-ingestion-design.md)
replaces this service's first event-driven ingestion path — Azure Event Grid → self-provisioned
Service Bus queue → `HearingResultedProcessorService` (adopted in
[ADR-002](002-AMP-889-event-driven-hearing-ingestion-servicebus-redis.md)) — with Azure Event
Grid delivering directly to a new internal webhook endpoint on this service
(`POST /internal/pcr/hearingResults`), removing the intermediate queue entirely.

This is both a new integration pattern (nobody in this org has an *external* system push an
inbound webhook before — the existing "internal" endpoints in this org are internal by
path/tag/media-type convention only, always behind the same `bearerAuth`+`subscriptionKey` as
public endpoints) and a breaking change to the `api-cp-crime-results-pcr` contract (new
operation). Per `hmcts-standards.md`, both require an ADR before proceeding.

Event Grid subscription already configured on the platform side:

| Field | Value |
|---|---|
| Subscription Name | `pcr-hearing-results` |
| Subscription Type | Webhook |
| Endpoint Name | `eg-ste-ccp0121-hearingres` |
| Webhook URL (dev) | `https://devamp01.ingress01.dev.nl.cjscp.org.uk/internal/pcr/hearingResults` |

## Decision

Adopt Event Grid's native `WebHook` destination type in place of the Service Bus queue:

- **New endpoint, not a new service** — `HearingResultedWebhookController`/`HearingResultedWebhookService`
  replace `HearingResultedProcessorService` 1:1; `ResultsIngestionService`'s Redis-first/
  REST-fallback/completeness logic is unchanged, only the trigger mechanism and the
  retry-escalation tier change (design doc §4).
- **`security: []` on this operation only** — the endpoint is secured by network isolation
  (only reachable via the platform's internal ingress that Event Grid's egress is routed
  through, not exposed on the public APIM gateway host), not by `bearerAuth`/`subscriptionKey`.
  Event Grid's webhook delivery does not carry an APIM subscription key, and adding
  Azure AD-token-based auth was considered and explicitly deferred (see Alternatives).
- **Event Grid's own retry policy replaces the custom scheduled-redelivery tier** — a `503`
  response from this service on "still incomplete after in-process retries" causes Event Grid to
  redeliver per its own exponential-backoff schedule (up to 24 hours). The bespoke
  `RetryServiceConfig`/`ServiceBusSenderClient` scheduled-redelivery mechanism (ADR-002) is
  deleted, not reused.
- **The in-process completeness retry (2s/4s/8s), originally specified in the Service Bus design
  doc but never implemented, is built now** — Event Grid's minimum redelivery interval is
  coarser than the ~14s this loop covers, so skipping it would make every short viewstore-lag
  case wait on Event Grid's schedule instead of resolving quickly in-process.
- **Idempotency remains deferred downstream**, unchanged from ADR-002's position — Event Grid's
  at-least-once delivery (now more likely to redeliver, since a `503` is a deliberate signal to
  retry) still hands off deduping to whichever component eventually dedupes `cp_version` writes.
- The Event Grid subscription's validation handshake
  (`Microsoft.EventGrid.SubscriptionValidationEvent` → echo `validationCode`) is implemented in
  `HearingResultedWebhookService` — no prior art in this org to reuse; built from Event Grid's
  documented webhook contract.

## Consequences

- Removes an entire dependency and its operational surface: `com.azure:azure-messaging-servicebus`,
  `com.azure:azure-identity`, the self-provisioned `pcr.hearing-resulted` queue, and
  `HearingResultedQueueProvisioner`. No Service Bus emulator needed for local dev/CI for this
  path any more.
- New operational surface instead: an internet-facing (to Event Grid) HTTP endpoint whose only
  protection is network isolation. If that isolation is ever misconfigured, this endpoint would
  accept unauthenticated POSTs — worth a platform/infra review of the ingress routing before
  this goes further than dev, tracked as a follow-up, not blocking this ADR.
- A `503` from this service is now a meaningful retry signal to an external system (Event Grid),
  not just an internal log line — the `IncompleteHearingDetailsException` → 503 mapping in
  `GlobalExceptionHandler` is a public contract behaviour now, not an implementation detail.
- Cutover requires a manual ops step (design doc §6) — deleting the old Service Bus queue and any
  Service-Bus-destination Event Grid subscription pointing to it — after the webhook path is
  confirmed live. Not automated by this change.

## Alternatives considered

- **Keep Service Bus, add the webhook alongside it (parallel run)** — rejected; adds operational
  complexity (two ingestion paths to reconcile/monitor) for a transitional period with no
  planned long-term dual-path use. AMP-892 calls for a replacement, not an addition.
- **Azure AD token-based auth on the webhook** (Event Grid subscription configured with
  `azureActiveDirectoryTenantId`/`ApplicationIdOrUri`, reusing this service's existing
  `bearerAuth` JWT validation) — considered as the more defense-in-depth option, since it would
  let this endpoint reuse the same auth scheme as everything else. Deferred in favour of network
  isolation only, per this decision; revisit if the network-isolation assumption doesn't hold up
  to platform/infra review.
- **Build our own async re-queue instead of relying on Event Grid's redelivery** — rejected;
  would reintroduce the same custom retry-state tracking this ADR removes Service Bus to avoid,
  for no benefit over Event Grid's built-in policy.