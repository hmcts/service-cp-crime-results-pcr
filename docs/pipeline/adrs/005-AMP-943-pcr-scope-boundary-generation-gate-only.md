# 005. PCR API scope boundary — generation-gate logic only, not distribution

**Status:** Accepted, 24 Jul 2026
**Jira:** AMP-943 — PCR Orchestrator scope confirmation with Common Platform TA
**Design docs:** [`2026-07-22-pcr-orchestrator-design.md`](../../designs/2026-07-22-pcr-orchestrator-design.md)
implements the scope this ADR confirms; that doc links back here at its Jira line.

## Context

§4/§7 of the orchestrator design doc raised an open question: how much of the legacy
`PrisonCourtRegisterOrchestrator` Durable Function pipeline does this service need to
reproduce? The pipeline covers more than generation — recipient/email resolution and
Progression PDF submission are also part of it. Building the wrong scope either duplicates
work this service shouldn't own, or leaves a real gap in what determines whether a PCR exists
at all.

This was raised directly with the Common Platform TA (David Edwards) rather than decided
unilaterally, given it affects a cross-team boundary (this service vs. the existing
Function App/Progression pipeline vs.
`service-cp-crime-hearing-results-document-subscription`).

## Decision

Confirmed with the Common Platform TA:

- **In scope for this API: the generation gate only** — `CPVocabularyService` (fact
  computation), `excludePublishedForNows` (content filter), and `CPNowSubscriptionMatcher`
  (subscription matching). Together these determine whether a PCR *would have been
  generated* in the existing Function App flow — the same question this service's
  `isPrisonCourtRegisterRequired` answers.
- **Out of scope for this phase:** recipient/email resolution and Progression PDF
  submission. These stay owned by the existing Function App/Progression pipeline and by
  `service-cp-crime-hearing-results-document-subscription`'s subscriber/callback
  infrastructure — unchanged by this ADR (see also ADR-001/AMP-888's "does not rebuild
  subscriber management" decision, which this reaffirms rather than revisits).
- **`ReferenceDataClient`/`CPNowSubscription` stay generic, not PCR-specific.** The
  `now-subscriptions` configuration this service reads is shared across other
  distribution-channel kinds (NOW, EDT, informant register, court register subscriptions) —
  same underlying Reference Data source, not a PCR-only dataset. This service only ever
  consumes the `isPrisonCourtRegisterSubscription`-flagged subset
  (`CPResultsPcrOrchestrator.isPrisonCourtRegisterRequired` filters to it), but the client and
  domain model themselves make no PCR-specific assumption about the response shape —
  confirms the modelling choice already made, not a change driven by this ADR.
- **Long-term direction (not this phase):** moving the remaining Function App logic and
  Progression-owned PDF generation into this same service is agreed as the right eventual
  direction, once the generation-gate slice above is proven out. Not scheduled or scoped
  here — a future ADR when that work is actually picked up.

## Consequences

- `CPResultsPcrOrchestrator`/`CPVocabularyService`/`CPNowSubscriptionMatcher` are now confirmed as
  the full intended scope of "the orchestrator" for this phase — no further build-out
  (recipient resolution, Progression submission) should be added under this same umbrella
  without a new scope conversation.
- The existing Function App pipeline keeps running unchanged for recipient resolution and
  PDF submission — this service's eligibility gate and the legacy pipeline's own equivalent
  gate are two independent implementations of the same logic until the long-term
  consolidation happens; golden-master drift detection (design doc §9) is the safeguard
  against them silently diverging in the meantime.
- `ReferenceDataClient`/`CPNowSubscription` are safe to reuse as-is if this service's scope
  ever grows to consume other subscription kinds — no rework anticipated on that account.

## Alternatives considered

- **Build the full pipeline (generation + recipient resolution + Progression submission) now**
  — rejected; out of proportion to this phase's confirmed requirement, and duplicates
  functionality Progression/the Function App already provide correctly today.
- **Model `CPNowSubscription` as PCR-only** (e.g. drop the other subscription-kind flags
  entirely, assume every response row is a PCR subscription) — rejected per the TA
  discussion; the same Reference Data source serves other distribution channels, and a
  narrower model would need rework the moment this service (or another) needs a second kind.

## Compliance notes

- No new data-handling or classification implications — this ADR is a scope boundary, not a
  new integration pattern or dependency.