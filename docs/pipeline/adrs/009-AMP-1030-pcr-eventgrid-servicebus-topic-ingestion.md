# 009. Replace Event Grid webhook ingestion with a dedicated Event Grid → Service Bus Queue

**Status:** Accepted, 25 Aug 2026
**Jira:** AMP-1030 — migrate PCR hearing-result ingestion to a dedicated Service Bus queue

## Context

[`2026-08-17-pcr-eventgrid-servicebus-ingestion-design.md`](../designs/2026-08-17-pcr-eventgrid-servicebus-ingestion-design.md)
replaces the current path — Event Grid → `pcr-eventgrid-relay-function` (Function App) →
`POST /internal/hearing-results` ([ADR-007](007-AMP-892-pcr-eventgrid-webhook-ingestion.md)) — with
Event Grid delivering directly to a Service Bus Queue owned solely by this service, consumed via
peek-lock.

Motivated by:

- **Function Apps being retired platform-wide** — `pcr-eventgrid-relay-function` needs replacing
  regardless of the mechanism.
- **A second confirmed consumer.** The NOW service needs the same `Hearing_Resulted` signal for its
  own generation-gate decision. A webhook delivers to one destination only.

A shared Service Bus Topic with a Subscription per consumer was the design originally agreed here
(20 Aug 2026) to serve both consumers without duplicating relay infrastructure. Before
implementation shipped, Common Platform's technical architecture review recommended per-consumer
dedicated Queues instead — each service provisions and owns its own queue outright, with no shared
Service Bus resource (and no cross-team coordination over its settings or lifecycle) between PCR
and NOW. This ADR reflects that revised recommendation.

## Decision

Adopt a Service Bus **Queue** owned solely by this service, `pcr.hearing-resulted`, fed by its own
independent Event Grid event subscription:

- **One dedicated Queue per consuming service, not a shared Topic** — PCR owns
  `pcr.hearing-resulted` outright; NOW will own its own separate queue, fed by its own separate
  Event Grid event subscription, when built. Event Grid's native support for multiple independent
  event subscriptions off one source event provides the fan-out — no Service Bus Topic is needed
  for it.
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
- **Both the Event Grid event subscription and the Service Bus queue are provisioned via
  Terraform (IaC)** — the same shared per-environment Service Bus namespace as before, just no
  shared Topic resource within it. The app never creates the queue itself: at startup it only
  verifies the queue exists (`ServiceBusProvisioningService.queueExists`) and fails startup if it
  doesn't, naming Terraform as the expected owner. This keeps the app off `Manage`-level Service
  Bus permissions it would otherwise only need for a create call it should never actually have to
  make, and avoids an ordering problem for the Event Grid event subscription's
  `service_bus_queue_endpoint_id`, which must reference the queue as a real resource — a queue
  the app only creates at runtime wouldn't exist yet when Terraform first applies. The queue's
  durability settings (`LockDuration` `PT1M`, `MaxDeliveryCount` 10, `DefaultMessageTimeToLive`
  `PT10M`, `DeadLetteringOnMessageExpiration` `true`) are set on the Terraform resource.
- **Managed Identity throughout** — no connection strings, SAS tokens, or account keys.

## Consequences

- Reintroduces a Service Bus dependency into PCR (removed in ADR-007), now as a queue PCR owns
  outright rather than a shared Topic — no cross-team governance over a shared resource's settings
  or lifecycle.
- Two ingestion paths coexist temporarily — a bounded, switch-gated exception to ADR-007. Relay
  decommissioning is separate follow-up work once the new path is proven.
- Retry-schedule tuning and alerting are out of scope — native dead-lettering ships now; proactive
  alerting doesn't.
- `/internal/hearing-results` stays live for as long as the relay function does.
- Doesn't design NOW's own queue, consumer, generation-gate logic, or data model — NOW's queue and
  Event Grid event subscription are entirely its own to provision, on its own timeline.
- The queue's existence at app startup depends on Terraform having already run in that
  environment — first-time environment setup must apply Terraform before the app's first deploy,
  not after. `ServiceBusProvisioningService.isServiceBusReady`/`queueExists` still call the
  administration client for read-only checks, so the exact minimum Service Bus role for the app's
  identity is a DevOps decision, not yet finalised.

## Alternatives considered

- **A shared Service Bus Topic with a Subscription per consumer** — the design originally agreed
  here (20 Aug 2026); superseded by Common Platform TA review before implementation shipped, in
  favour of per-consumer Queues, which avoid a shared resource whose settings and lifecycle would
  otherwise need coordinating between PCR and NOW. Event Grid's own native multi-subscription
  fan-out makes the Topic's fan-out redundant here.
- **Give NOW its own separate relay/webhook** — rejected; duplicates relay infrastructure and its
  known production-readiness gaps.
- **A single Queue shared by both consumers** — rejected; competing-consumer semantics mean only
  one of PCR/NOW would ever receive a given message. Each consumer owning its own dedicated queue
  avoids this entirely.
- **Immediate cutover, no staged switch** — rejected; a consumer is actively testing the existing
  path today.
- **Extend the retry schedule and build alerting as part of this change** — deferred; out of scope
  for a transport-mechanism migration.
