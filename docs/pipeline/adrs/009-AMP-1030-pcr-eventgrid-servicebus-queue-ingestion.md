# 009. Replace Event Grid webhook ingestion with a dedicated Event Grid → Service Bus Queue

**Status:** Accepted, 25 Aug 2026
**Jira:** AMP-1030 — migrate PCR hearing-result ingestion to a dedicated Service Bus queue

## Context

[`2026-08-17-pcr-eventgrid-servicebus-ingestion-design.md`](../designs/2026-08-17-pcr-eventgrid-servicebus-ingestion-design.md)
replaces the current path — Event Grid → `pcr-eventgrid-relay-function` (Function App) →
`POST /internal/hearing-results` ([ADR-007](007-AMP-892-pcr-eventgrid-direct-ingestion.md)) — with
Event Grid delivering directly to a Service Bus Queue owned solely by this service, consumed via
peek-lock.

Motivated by:

- **Function Apps being retired platform-wide** — `pcr-eventgrid-relay-function` needs replacing
  regardless of the mechanism.
- **A second confirmed consumer.** The NOW service needs the same `Hearing_Resulted` signal for its
  own generation-gate decision. A webhook delivers to one destination only.

## Decision

Adopt a Service Bus Queue owned solely by this service, `pcr.hearing-resulted`, fed by its own
independent Event Grid event subscription:

- **One dedicated Queue per consuming service** — PCR owns `pcr.hearing-resulted` outright; NOW
  will own its own separate queue, fed by its own separate Event Grid event subscription, when
  built.
- **Payload unchanged:** `hearingId`, `hearingDay`, `userId` — the existing
  `HearingResultedWebhookEventData` fields, no new shared key.
- **Native `maxDeliveryCount` + dead-lettering** for outright failures — peek-lock,
  `complete()`/abandon, no application retry code.
- **Completeness retry moved off the consumer thread onto a scheduled Service Bus message**
  (`ScheduledEnqueueTimeUtc`), with its own configurable schedule and ceiling
  (`service-bus.retry-durations`, `service-bus.max-tries`, default 24 tries topping out at ~10.6
  hours) — separate from `ResultsIngestionService.MAX_COMPLETENESS_RETRIES`, which still only
  bounds the synchronous POST path's retry. Sized for PCR's own failure mode (viewstore
  replication lag — minutes, not days). Each follow-up message sets its own 24h `TimeToLive`,
  since the queue's own 10-minute default would otherwise auto-expire a longer-delayed retry into
  the DLQ before `max-tries` gets a say.
- **Cutover complete — the queue is now the only ingestion path.** The synchronous POST path
  (`HearingResultedEventController`, `HearingResultedEventService`, `POST /internal/hearing-results`)
  and the coexistence switch (`service-bus.ingestion-enabled`) have been removed — there's nothing
  left to switch between. Retiring `pcr-eventgrid-relay-function` itself is a separate, cross-repo
  decision, tracked as AMP-1053 — not something this repo's code can do.
- **Both the Event Grid event subscription and the Service Bus queue are provisioned via
  Terraform (IaC)** — the same shared per-environment Service Bus namespace as before. The app
  never creates the queue itself: at startup it only verifies the queue exists
  (`ServiceBusProvisioningService.queueExists`) and fails startup if it doesn't, naming Terraform
  as the expected owner. This keeps the app off `Manage`-level Service Bus permissions it would
  otherwise only need for a create call it should never actually have to make, and avoids an
  ordering problem for the Event Grid event subscription's `service_bus_queue_endpoint_id`, which
  must reference the queue as a real resource — a queue the app only creates at runtime wouldn't
  exist yet when Terraform first applies. The queue's durability settings (`LockDuration` `PT1M`,
  `MaxDeliveryCount` 10, `DefaultMessageTimeToLive` `PT10M`, `DeadLetteringOnMessageExpiration`
  `true`) are set on the Terraform resource.
- **Managed Identity throughout** — no connection strings, SAS tokens, or account keys.

## Consequences

- Reintroduces a Service Bus dependency into PCR (removed in ADR-007) — PCR owns its queue
  outright, no cross-team governance over shared resource settings or lifecycle.
- Alerting/escalation on DLQ messages is still out of scope — native dead-lettering ships, proactive
  alerting doesn't.
- `pcr-eventgrid-relay-function` itself isn't retired by this change (AMP-1053) — it's just no
  longer called by anything in this repo.
- Doesn't design NOW's own queue, consumer, generation-gate logic, or data model — NOW's queue and
  Event Grid event subscription are entirely its own to provision, on its own timeline.
- The queue's existence at app startup depends on Terraform having already run in that
  environment — first-time environment setup must apply Terraform before the app's first deploy,
  not after. `ServiceBusProvisioningService.isServiceBusReady`/`queueExists` still call the
  administration client for read-only checks, so the exact minimum Service Bus role for the app's
  identity is a DevOps decision, not yet finalised.

## Alternatives considered

- **Give NOW its own separate relay/webhook** — rejected; duplicates relay infrastructure and its
  known production-readiness gaps.
- **A single Queue shared by both consumers** — rejected; competing-consumer semantics mean only
  one of PCR/NOW would ever receive a given message. Each consumer owning its own dedicated queue
  avoids this entirely.
- **Immediate cutover, no staged switch** — rejected at the time; a consumer was actively testing
  the existing path. Cutover happened later once that was no longer a concern (see Decision above).
- **Extend the retry schedule and build alerting as part of this change** — deferred at the time;
  the retry schedule was extended later (see Decision above), alerting is still outstanding.
