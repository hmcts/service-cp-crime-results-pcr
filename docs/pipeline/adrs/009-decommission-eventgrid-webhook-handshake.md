# 009. Decommission this service's own Event Grid webhook handshake

**Status:** Accepted, 14 Aug 2026

## Context

ADR-007/AMP-892 had Azure Event Grid delivering `Hearing_Resulted` events straight to this
service's own webhook (`HearingResultedWebhookController`/`HearingResultedWebhookService`
answering Event Grid's subscription-validation handshake itself). `pcr-eventgrid-relay-function`
was later built as a standalone Function App that owns the real Event Grid subscription and
answers the handshake itself via its own `@EventGridTrigger` binding, then relays each event
verbatim to this same `/internal/hearing-results` endpoint as a plain internal HTTP call.

An earlier attempt to remove the resulting dead handshake code was reverted to defer the
decommission, not because the reasoning was wrong. A fresh impact analysis (14 Aug 2026) confirms
`pcr-eventgrid-relay-function` is the real, live path:

- It is an active, deployed repo, already verified in STE delivering real Event Grid events
  end-to-end, including working TLS trust to this service.
- The `pcr-hearing-results` subscription ADR-007 describes as "already provisioned" does not
  exist — of the real subscriptions on that Event Grid topic, none targets this service's webhook
  directly. Nothing has ever delivered to it.
- `pcr-eventgrid-relay-function` has no dependency on this service's or `api-cp-crime-results-pcr`'s
  code — it parses events as raw JSON and posts plain HTTP.

## Decision

- `HearingResultedWebhookController`/`HearingResultedWebhookService` are renamed to
  `HearingResultedEventController`/`HearingResultedEventService`.
- The subscription-validation handshake branch (echoing `Microsoft.EventGrid.SubscriptionValidationEvent`
  via `WebhookAck.validationResponse`) is deleted — this service will never receive that event type.
- `/internal/hearing-results` now returns a plain `200` with no response body (matching
  `api-cp-crime-results-pcr` v1.1.14's `WebhookAck` removal).
- All "webhook" naming is removed from code, tests, and fixtures (`HearingResultedEvent*`,
  `events/hearing-resulted-event.json`) — this operation is a plain internal ingestion endpoint,
  not a webhook, since it no longer talks to Event Grid directly.

## Consequences

- ADR-007 is marked superseded rather than rewritten — its historical record of the original
  webhook decision stays intact for the CP-source trail.
- Ingress-isolation review (ADR-007's "worth a platform/infra review" follow-up) is now
  `pcr-eventgrid-relay-function`'s concern, not this service's — this endpoint is only ever called
  internally by the relay function, not by Event Grid directly.
- No change to `ResultsIngestionService`'s Redis-first/REST-fallback/completeness-retry logic, the
  generation gate, or the data store — only the trigger-side naming and handshake handling change.
