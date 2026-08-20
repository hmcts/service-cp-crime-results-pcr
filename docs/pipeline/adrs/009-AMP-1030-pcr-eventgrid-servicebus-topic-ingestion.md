# 009. Replace Event Grid webhook ingestion with a shared Event Grid → Service Bus Topic

**Status:** Accepted, 20 Aug 2026
**Jira:** AMP-1030 — migrate PCR hearing-result ingestion to a shared Service Bus topic

## Context

[`2026-08-17-pcr-eventgrid-servicebus-ingestion-design.md`](../designs/2026-08-17-pcr-eventgrid-servicebus-ingestion-design.md)
replaces the current path — Event Grid → `pcr-eventgrid-relay-function` (Function App) →
`POST /internal/hearing-results` ([ADR-007](007-AMP-892-pcr-eventgrid-webhook-ingestion.md)) — with
Event Grid delivering directly to a Service Bus Topic, `hearing-resulted`, consumed via this
service's own Subscription.

Motivated by:

- **Function Apps being retired platform-wide** — `pcr-eventgrid-relay-function` needs replacing
  regardless of the mechanism.
- **A second confirmed consumer.** The NOW service needs the same `Hearing_Resulted` signal for its
  own generation-gate decision. A webhook delivers to one destination only.

## Decision

Adopt a shared Service Bus **Topic**, `hearing-resulted`, with one **Subscription** per consumer:

- **Topic + per-service Subscription, not a Queue** — PCR's own `pcr` subscription; NOW gets its
  own when built.
- **Payload unchanged:** `hearingId`, `hearingDay`, `userId` — the existing
  `HearingResultedWebhookEventData` fields, no new shared key.
- **Native `maxDeliveryCount` + dead-lettering** for outright failures — peek-lock,
  `complete()`/abandon, no application retry code.
- **Completeness-retry schedule relocated, not revised** — `ResultsIngestionService`'s existing
  2s/4s/8s check moves off the consumer thread onto a scheduled Service Bus message
  (`ScheduledEnqueueTimeUtc`). Extending the schedule and alerting/escalation are deferred.
- **Staged cutover behind a switch**, off by default — `pcr-eventgrid-relay-function` stays live
  until the new path is proven in lower environments, then production, at which point the relay's
  routing is disabled. Never both channels active in one environment (`ingestAndPersist` isn't
  idempotent). Both channels log receipt during the coexistence window.
- **Provisioning is split by resource:** the Event Grid event subscription is provisioned via
  **Terraform (IaC)**; the Service Bus topic/subscription via idempotent app-code
  create-if-not-exists at startup.
- **Managed Identity throughout** — no connection strings, SAS tokens, or account keys.

## Consequences

- Reintroduces a Service Bus dependency into PCR (removed in ADR-007), now as a shared Topic model
  driven by the second-consumer requirement.
- Two ingestion paths coexist temporarily — a bounded, switch-gated exception to ADR-007. Relay
  decommissioning is separate follow-up work once the new path is proven.
- Retry-schedule tuning and alerting are out of scope — native dead-lettering ships now; proactive
  alerting doesn't.
- `/internal/hearing-results` stays live for as long as the relay function does.
- Doesn't design NOW's own consumer, generation-gate logic, or data model.

## Alternatives considered

- **Give NOW its own separate relay/webhook** — rejected; duplicates relay infrastructure and its
  known production-readiness gaps.
- **A plain Queue instead of a Topic** — rejected; competing-consumer semantics mean only one of
  PCR/NOW would ever receive a given message.
- **Immediate cutover, no staged switch** — rejected; a consumer is actively testing the existing
  path today.
- **Extend the retry schedule and build alerting as part of this change** — deferred; out of scope
  for a transport-mechanism migration.