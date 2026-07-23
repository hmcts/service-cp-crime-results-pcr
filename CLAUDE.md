# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repo: service-cp-crime-results-pcr

Spring Boot service exposing Prison Court Register (PCR) source data — the same content
currently distributed as a PDF via the legacy Function App/Progression/Docmosis pipeline —
as a new pull-based read channel for API Marketplace subscribers (HMPPS/prisons).

**Pattern**: DB-backed (event-driven ingest, immutable version rows, TTL retention)
**Spring Boot version**: 4.1.0
**Implements**: No `api-cp-*` spec published yet — the `apiSpec` dependency coordinate in
`build.gradle` is intentionally left unwired until that spec repo exists (see design doc §13).

**Status**: Design-only. Only `Application.java`, `application.yaml`, and the standard
template tests exist so far — no controllers, services, clients, or Service Bus consumer are
implemented yet. `docs/2026-07-16-pcr-api-marketplace-design-v2.md` is the authoritative
architecture source; read it in full before adding any component, not just this file.

## Infrastructure

| Component | Technology | Purpose |
|---|---|---|
| Trigger | Azure Event Grid `Hearing_Resulted` → Service Bus queue → `spring-cloud-azure-stream-binder-servicebus` consumer | Pointer-only event (`hearingId`/`hearingDay`/`userId`); no PCR content on the wire |
| Results Query Client | Redis (guaranteed populated, 24hr TTL) first, REST fallback with retry against `results-query-api/.../hearingDetails/internal/{hearingId}` | Two-step retrieval — the event only carries a pointer |
| Reference Data | `ResultDefinition` lookups (enrichment), offence metadata lookups (e.g. `startDate`) | Both "to be analysed" per design §8 — not yet built |
| Data store | Immutable version rows, keyed `(hearingId, defendantId)` | One PCR row per defendant per hearing, never merged (design §2) |
| Retention | Automatic 30-day TTL purge | Fixed window; API Marketplace does not retain PCR data beyond it (design §11) |

## Source Structure

- `Application.java` — `@SpringBootApplication` only; no other packages exist yet
- When implementing, follow the layer table in design doc §8 (Service Bus Consumer → Results
  Query Client → Decision Engine → Enrichment/Offence-metadata clients → Transformer →
  Version/Correlation handler → Data store → Query API → Retention) — each layer has a named
  legacy component it ports from (design §5a) or is called out as genuinely new

## Environment Variables

| Variable | Purpose | Default |
|---|---|---|
| `SERVER_PORT` | HTTP port | `8082` |
| `rpe.AppInsightsInstrumentationKey` | Azure Application Insights key | `00000000-0000-0000-0000-000000000000` |

## Repo-Specific Architecture Rules

- **One PCR record per `(hearingId, defendantId)`, never merged across defendants** — this is
  load-bearing throughout the design (decision engine fan-out, data store keying, Query API
  shape). Do not "simplify" to one row per hearing.
- **Redis-first, REST-fallback-with-retry is mandatory, not an optimisation** — Redis is written
  synchronously before `Hearing_Resulted` fires (guaranteed populated); the REST viewstore is
  updated asynchronously and can race. Skipping the Redis check reintroduces a real, confirmed
  race condition (design §4a/§4b), not a theoretical one.
- **Version correlation mechanism is still TBD** (design §7) — three options considered
  (`recorded_date` ruled out, `sharedTime` propagation, `resultEventId` propagation), none
  decided. Don't build against any of them as if settled; check design doc status first.
- **`PcrVersionCorrelationHandler` is the only component allowed to know Progression exists** —
  every other layer only ever reads `versionStatus`/`materialId` once the correlator has set them.
- **Do not port `VocabularyService` or `PrisonCourtRegisterSubscriptions`** — confirmed
  out-of-scope (design §5b/§14); subscriber matching stays owned by
  `service-cp-crime-hearing-results-document-subscription` and `now_subscriptions`.
- **Resolved — dropped.** Confirmed-dead legacy fields (`officerInCase`,
  `parentGuardianName`/`Address1-5`/`PostCode`) are not carried through — HMPPS confirmed no
  parent/guardian concept is needed from this API. Same resolution covers defendant name/DOB/
  address (`title`/`firstName`/`middleName`/`lastName`/`dateOfBirth`/`address`) and
  prosecution/defence counsel/aliases — HMPPS resolves the defendant entirely via
  `defendantId`/`masterDefendantId` against NOMIS, so none of this is modelled anywhere in
  this service or the `Defendant`/`Address` API schemas.
- Field-level mapping detail (base shape, aliasing fixes, enrichment additions) lives in
  `PCR-HMPPS-FIELD-MAPPING.md` in the `api-cp-crime-results-pcr` spec repo, not duplicated here.

## Debugging

| Symptom | Cause / Fix |
|---|---|
| Retry logic assumes REST fallback fails cleanly on a race | Unconfirmed assumption per design §4b/§13 item 2 — verify against the Results team's actual code before relying on it |

## Repo-Specific Notes

- Downstream: `service-cp-crime-hearing-results-document-subscription` — this service's Query
  API URL gets wired into that service's existing subscriber callback payload; this service does
  not own subscriber registration or push notification.
- Golden-master drift detection planned (design §9): pre-launch tests replay real past hearings
  through the new code path and assert output matches Progression's stored
  `prison_court_register.payload`; post-launch, the correlator's `ORPHANED` status is the live
  version of the same check — someone needs to actually watch that list, not just log it.
- MVP scope is Story 3's non-amendment phase-1 slice (mirror the Function App, no amendment
  handling) to get early HMPPS feedback before the full service is built (design §12).