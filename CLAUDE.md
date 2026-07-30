# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repo: service-cp-crime-results-pcr

Spring Boot service exposing Prison Court Register (PCR) source data — the same content
currently distributed as a PDF via the legacy Function App/Progression/Docmosis pipeline —
as a new pull-based read channel for API Marketplace subscribers. The contract is not scoped
to any single consumer's stated needs — decisions here apply platform-wide, not to one
subscriber.

**Pattern**: Hybrid — synchronous stateless proxy (`GET /pcr`) implemented; Azure Event Grid
webhook ingestion (`POST /internal/hearing-results`) wired end-to-end into the DB-backed version
store (Postgres/Flyway, `cp_version` rows), gated by the generation-gate check.
**Spring Boot version**: 4.1.0
**Implements**: `api-cp-crime-results-pcr` v1.1.0 (`PcrApi`/`InternalApi` — see `build.gradle`)

Replaces the earlier self-provisioned Service Bus queue ingestion path (ADR-002/AMP-889) with a
direct Event Grid webhook per ADR-007/AMP-892 — see
`docs/plans/2026-07-29-pcr-eventgrid-webhook-ingestion.md`.

**Status**: `GET /pcr` now reads from the data store; the webhook ingestion path drives the
generation gate and the data store as one connected pipeline:
- `GET /pcrs/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}`
  (`PcrResultsController` → `PcrResultsService`) reads `cp_version` (plus its offences, court
  applications, judicial results, and prompts) via the repository layer and returns the full
  recorded history as an array, ordered oldest-to-newest by `created_at` — no `version` query
  param, no live call to `ResultsClient` or the Results Query API at all. See
  `docs/designs/2026-07-28-pcr-read-path-data-store-design.md`.
- `POST /internal/hearing-results` (`HearingResultedWebhookController` → `HearingResultedWebhookService`
  → `ResultsIngestionService`) receives Azure Event Grid's `Hearing_Resulted` pointer event (and its
  subscription-validation handshake) directly, checks Redis-then-REST for complete hearing data with
  an in-process 2s/4s/8s retry, and — via `ResultsIngestionService.ingestAndPersist` — **does persist**:
  for each defendant it invokes the generation gate, then delegates to `CPEntityPersistenceService`
  to find-or-create a `cp_case_hearing` row and write a `cp_version` row (plus its offences, court
  applications, judicial results, and prompts) through the repository layer.
- The generation-gate package (`CPVocabularyService`, `CPResultsPcrFilter`,
  `CPNowSubscriptionMatcher`, `ReferenceDataClient`) is now called — `ResultsIngestionService`
  computes a `CPVocabulary` per defendant and invokes `isPrisonCourtRegisterRequired` to decide
  whether a `cp_version` row gets written at all. It is still not called from
  `PcrResultsController` or `PcrResultsService` — `GET /pcr` is unaffected.

Read `docs/designs/2026-07-16-pcr-api-marketplace-design-v2.md` (authoritative architecture) plus the
five dated follow-on design docs in `docs/designs/` before adding any component, not just this file —
each one is a deeper design pass on one layer (stateless-proxy phase 1, data store phase 2,
hearing-event ingestion, generation gate, persistence wiring) and states its own scope/status at the top.
`docs/pipeline/adrs/` records the decisions behind each layer, tagged with their Jira ticket
(AMP-888 parent epic through AMP-943) — read the relevant ADR before revisiting a decision
that looks arbitrary; it likely isn't.

## Infrastructure

| Component | Technology | Purpose |
|---|---|---|
| Ingestion trigger | Azure Event Grid `Hearing_Resulted` → `POST /internal/hearing-results` (`HearingResultedWebhookController` → `HearingResultedWebhookService`, ADR-007/AMP-892) | Delivered as a JSON array of the generated `HearingResultedWebhookEvent` model; also receives Event Grid's subscription-validation handshake (`Microsoft.EventGrid.SubscriptionValidationEvent`), echoed back via `WebhookAck.validationResponse`. Malformed/unrecognized payloads return `400`, not silently dropped |
| Results Query Client | `HearingResultedCacheClient` (Redis, read-only `StringRedisTemplate`) first, `ResultsClient` (`RestClient`) REST fallback against `results-query-api/.../hearingDetails/internal/{hearingId}` | Two-step retrieval per design §4a/4b — **ingestion path only**; `GET /pcr` calls neither of these, it reads the data store |
| Completeness retry | `ResultsIngestionService.ingestHearingResults` in-process retry (`sleepUninterruptibly`) | On an incomplete hearing, retries up to 3 attempts with 2s/4s exponential backoff before throwing `IncompleteHearingDetailsException`, mapped to `503` by `GlobalExceptionHandler` — Event Grid redelivers per its own retry policy on `503` |
| Reference Data — `ResultDefinition` | Lookups, offence metadata (e.g. `startDate`) | Not yet built — "to be analysed" per design §8 |
| Reference Data — `now-subscriptions` | `ReferenceDataClient` (`RestClient`) → `.../referencedata-query-api/.../now-subscriptions?on=<date>`; `CPNowSubscriptionMatcher` matches the PCR-flagged subset against a `CPVocabulary` | Now called from `ResultsIngestionService.ingestAndPersist`, once per hearing, using the first `JudicialResult.orderedDate` found on the hearing as `on` — a provisional stand-in, not the confirmed date-selection strategy from design §7 |
| Generation gate | `CPVocabularyService` (fact computation) + `CPResultsPcrFilter` (`excludePublishedForNows`, `isPrisonCourtRegisterRequired`) | Design §4 scope, confirmed with Common Platform TA per ADR-005/AMP-943 — generation-gate logic only; recipient resolution and Progression submission are explicitly out of scope. Now called from `ResultsIngestionService.ingestAndPersist`, gating whether a `cp_version` row gets written per defendant |
| Data store | Flyway migrations (`V1.001`-`V1.010`), 7 JPA entities (`entities/`), 7 plain `JpaRepository`s (`repositories/`) | Schema + persistence layer built and integration-tested against a real, manually-started Postgres (`PostgresInitialise`, same pattern as HRDS). Wired on both sides — `ResultsIngestionService.ingestAndPersist` delegates to `CPEntityPersistenceService`, which writes `cp_case_hearing`/`cp_version` (and dependent) rows via the repository layer once the generation gate passes, and `PcrResultsService` reads them back for `GET /pcr`. Encryption (ADR-004) and the version-correlation mechanism (§7) are separate, still-open work |
| Version lookup / retention | Read path implemented — `GET /pcr` returns the full recorded history per `(caseURN, hearingId, defendantId)`, ordered oldest-to-newest. The separate `/versions` metadata-only endpoint and retention policy remain `501`/not implemented, pending the still-undecided version-correlation mechanism (§7) |

## Source Structure

- `controllers/PcrResultsController` — implements generated `PcrApi`; validates `caseURN`
  against `CASE_URN_REGEX` (`^[0-9a-zA-Z]{1,30}$`) before delegating
- `services/PcrResultsService` — reads `cp_case_hearing`/`cp_version` and children via the
  repository layer for a given `(caseURN, hearingId, defendantId)` and maps each recorded version
  to a `PcrHearingResult`; always returns `200` with an empty array when nothing is found — no
  `404` distinction, per the settled design decision
- `controllers/HearingResultedWebhookController` — implements generated `InternalApi`; delegates
  `POST /internal/hearing-results` straight to `HearingResultedWebhookService`, no logic of its own
- `services/HearingResultedWebhookService` — branches on the generated `HearingResultedWebhookEvent`'s
  `eventType`: echoes Event Grid's subscription-validation handshake, or unpacks the strongly-typed
  `HearingResultedWebhookEventData` (`hearingId`/`hearingDay`/`userId`) and calls
  `ResultsIngestionService.ingestAndPersist`; throws `IllegalArgumentException` (→ `400`) on an
  unrecognized `eventType` or empty delivery
- `services/ingestion/ResultsIngestionService` — Redis-then-REST hearing lookup with an in-process
  2s/4s/8s completeness retry (`sleepUninterruptibly`, up to 3 attempts) before throwing
  `IncompleteHearingDetailsException`; `ingestAndPersist` additionally runs the generation gate per
  defendant and delegates to `CPEntityPersistenceService` to write the `cp_version` write-graph
- `services/ingestion/CPEntityPersistenceService` — find-or-creates the shared `cp_case_hearing`
  row and writes a `cp_version` row (plus its offences, court applications, judicial results, and
  prompts) via the repository layer, once the generation gate has already decided a PCR is
  required; the only class in this repo that owns the write-side repositories
- `clients/ResultsClient` — `RestClient` call with `Accept: application/vnd.results.hearing-details-internal+json`
- `clients/HearingResultedCacheClient` — Redis read only; key format
  `INT_{hearingId}_{hearingDay}_result_`, matching the legacy Function App/`cpp-context-results`
  scheme exactly (no new scheme invented)
- `mappers/PcrResultsMapper` — builds a `PcrHearingResult` from `CPCaseHearingEntity`,
  `CPVersionEntity`, and their gathered children (case markers, court applications, offences,
  judicial results, prompts); replaces the deleted `PcrVersionMapper` now that `GET /pcr` reads
  from the data store instead of `HearingDetailsResponse`. Read-side only — the PCR API's own
  controller→service→mapper flow, hence the `Pcr` prefix.
- `mappers/CPHearingResultEntityMapper` — the write-side counterpart; builds `CPCaseHearingEntity`/
  `CPCaseMarkerEntity` and a `CPEntitySet` (version + offences + judicial results + prompts +
  court applications) from a `Defendant`/`HearingDetail` — `CP`-prefixed, not `Pcr`, since it's
  this repo's own entity-construction logic, not the PCR API's read flow
- `mappers/CPEntitySet` — the record `CPHearingResultEntityMapper.toWriteBundle` returns;
  unpacked and saved by `CPEntityPersistenceService`
- `mappers/CPJudicialResultPromptParser` — extracts sentencing detail (`concurrent`, `fineAmount`,
  `imprisonmentPeriod`, etc.) from `judicialResultPrompts` by `promptReference` string lookup
- `config/AppPropertiesBackend` — `@Value`-backed config bean for backend URLs
  (results-query-client, reference-data-client)

### The `pcrcompute` sub-package (`services/pcrcompute/`, `domain/pcrcompute/`)

Every class here is built and unit-tested and is now called from `ResultsIngestionService`
(invoked by the webhook path via `ingestAndPersist`) — but still **not called from
`PcrResultsController` or `PcrResultsService`**; `GET /pcr` is unaffected by this wiring. It's
sub-packaged (not a new top-level layer — every sibling `service-cp-*` repo's
`controllers/services/clients/domain` shape stays intact) specifically so this boundary stays
visible in the directory listing and import statements instead of only living in this file's
prose. Named for what it computes — the generation-gate decision — not "orchestrator"; none of
these classes coordinate other services, they compute facts and match rules.
`clients/ReferenceDataClient` sits in plain `clients/` alongside its siblings
(`HearingResultedCacheClient`, `ResultsClient`), not in its own sub-package — it's a plain HTTP
client, no different in kind from the others:

- `services/pcrcompute/CPVocabularyService` — computes `CPVocabulary` (custody, custodial-result,
  CPS, age-group, court-language facts) from a defendant + hearing; merges custody/CPS scan
  across every `prosecutionCase`/`courtApplication` sharing the defendant's `masterDefendantId`
  on the same hearing (a real merge scenario, not a data-model bug — see repo architecture rules
  below)
- `services/pcrcompute/CPNowSubscriptionMatcher` — matches a `CPNowSubscription`'s vocabulary
  requirements against a computed `CPVocabulary` + eligible `JudicialResult`s; every dimension
  fails closed when unconfigured except `applySubscriptionRules == false`; attendance matching
  is stubbed (any-flag-only) pending a confirmed `hearing.defendantAttendance` source
- `services/pcrcompute/CPResultsPcrFilter` — `excludePublishedForNows` (plain-field content
  filter); `fetchPrisonCourtRegisterSubscriptions` (fetches via `ReferenceDataClient`, filters to
  `isPrisonCourtRegisterSubscription` — called once per hearing, not per defendant, since
  `activeAt` is hearing-wide) and `isPrisonCourtRegisterRequired` (the generation gate: matches
  the already-fetched subscriptions via `CPNowSubscriptionMatcher`)
- `clients/ReferenceDataClient` — `RestClient` call to Reference Data's
  `now-subscriptions` endpoint; deliberately generic (`CPNowSubscription`/`SubscriptionVocabulary`),
  not PCR-specific — the same config backs other distribution-channel kinds (NOW/EDT/informant/
  court register), per ADR-005
- `domain/pcrcompute/CPNowSubscription`/`CPNowSubscriptionsResponse` — wire shape for the
  `now-subscriptions` response; every `SubscriptionVocabulary` field is boxed `Boolean`, not
  primitive — a real subscription omits a dimension's keys entirely rather than sending `false`
  when it doesn't configure that dimension
- `domain/pcrcompute/CPVocabulary` — the eligibility-fact record `CPVocabularyService` computes;
  never surfaces in `PcrVersion`/`cp_version` — it exists only to decide *whether* a PCR is
  generated, not to describe its content (design doc §2)

`HearingDetailsResponse` stays in top-level `domain/` — it's genuinely shared across all three
code paths, unlike the `pcrcompute`-only types above. The generated `HearingResultedWebhookEvent`/
`HearingResultedWebhookEventData` models (from `api-cp-crime-results-pcr`) are consumed directly
by `HearingResultedWebhookService` — no hand-written envelope/pointer domain type exists for the
webhook path.

### Data store (`entities/`, `repositories/`)

Flat entity-per-table mapping, no JPA associations (`@ManyToOne`/`@OneToMany`) — foreign keys
are plain `UUID` fields, matching `service-cp-crime-hearing-results-document-subscription`'s
established convention. `cp_offence`/`cp_judicial_result`'s polymorphic parent (exactly one of
two nullable FKs set, design doc §1/§3) is enforced by the DB `CHECK` constraint only, not
modelled as inheritance in Java. `CPVersionEntity`'s PII columns (`title`/`firstName`/etc.) are
plain values today — `dateOfBirth` a real `LocalDate`, the rest `String` — no `EncryptionService`
is wired yet; encryption at rest (ADR-004) is deferred to a future phase, not this one.
Every repository is a bare `JpaRepository<Entity, UUID>` — `CPCaseHearingRepository` now also has
`findByCaseUrnAndHearingId` (a real custom query method, added to support find-or-create), and
all 7 repositories are now called from `CPEntityPersistenceService` (invoked by
`ResultsIngestionService.ingestAndPersist`). Proven against
a real Postgres, not an in-memory substitute — same pattern as
`service-cp-crime-hearing-results-document-subscription`: full `@SpringBootTest` +
`PostgresInitialise` (`integration/config/PostgresInitialise.java`, a `TestPropertyValues`-based
`ApplicationContextInitializer` asserting `jdbc:postgresql://localhost:5432/pcrdb` is reachable
before the context boots), not `@DataJpaTest`/Testcontainers — that was tried first but abandoned
in favour of matching the established org convention. Needs a real Postgres running locally with
a `pcrdb` database created (`docker compose up -d postgres`, service added to this repo's
`docker-compose.yml`, or a native install) — there's no self-contained fallback.
`spring-boot-starter-flyway` (not just raw `flyway-core`/`flyway-database-postgresql`) is
required for `FlywayAutoConfiguration` to exist at all — without it, migrations silently never
run, in production or in tests; discovered the hard way when repository tests failed with
`relation "cp_case_hearing" does not exist` despite Flyway configuration looking correct.

## Environment Variables

| Variable | Purpose | Default |
|---|---|---|
| `SERVER_PORT` | HTTP port | `8082` |
| `CP_BACKEND_URL` | Results Query API base URL | `http://localhost:8081` |
| `CJSCPPUID` | Client identity header sent to the Results Query API and Reference Data | `00000000-0000-0000-0000-000000000000` |
| `REFERENCE_DATA_URL` | Reference Data (`now-subscriptions`) base URL — real dev/SIT value unconfirmed, see design doc §7 | falls back to `CP_BACKEND_URL` |
| `REDIS_HOST` / `REDIS_PORT` | Hearing-result cache (read-only) | `localhost` / `6379` |
| `rpe.AppInsightsInstrumentationKey` | Azure Application Insights key | `00000000-0000-0000-0000-000000000000` |

## Repo-Specific Architecture Rules

- **One PCR record per `(hearingId, defendantId)`, never merged across defendants** — this is
  load-bearing throughout the design (decision engine fan-out, data store keying, Query API
  shape). Do not "simplify" to one row per hearing.
- **`CPVocabularyService` computes facts across every case/application sharing a
  `masterDefendantId`, not scoped to one `defendantId`'s own case — this is a real, confirmed
  scenario, not a data-model bug.** One physical defendant can have multiple `defendantId`s
  (one per prosecution case) and appear on multiple court applications within the same hearing,
  all sharing one `masterDefendantId`. Custody location and CPS-prosecuted scan the whole
  hearing by `masterDefendantId` for this reason (generation-gate design doc §2/§7). This is
  orthogonal to the point above: computing *eligibility* against the merged view does not mean
  the *persisted* `cp_version` row merges — it stays keyed per `(hearingId, defendantId)`.
- **Redis-first, REST-fallback-with-retry is mandatory, not an optimisation** — Redis is written
  synchronously before `Hearing_Resulted` fires (guaranteed populated); the REST viewstore is
  updated asynchronously and can race. Skipping the Redis check reintroduces a real, confirmed
  race condition (design §4a/§4b), not a theoretical one. The in-process 2s/4s/8s retry in
  `ResultsIngestionService.ingestHearingResults` is now actually implemented (previously only
  designed) — three attempts before throwing `IncompleteHearingDetailsException` (→ `503`,
  Event Grid redelivers). **This rule currently applies to the webhook ingestion path
  only** — `GET /pcr` (`PcrResultsService`) does not check Redis and has no completeness gate;
  don't assume the synchronous endpoint is race-safe just because the ingestion path is.
- **The webhook ingestion path now persists, via `ResultsIngestionService.ingestAndPersist`** — see
  `docs/designs/2026-07-28-pcr-persistence-wiring-design.md`. A successful
  `ingestHearingResults` call only proves the hearing data is complete and retryable-safe;
  `ingestAndPersist` is the method that additionally runs the generation gate per defendant and
  delegates to `CPEntityPersistenceService` to write `cp_case_hearing`/`cp_version` (and
  dependent) rows when it passes. `GET /pcr` now reads from the same data store this write path
  populates, via the repository layer — not from "whatever the listener last saw" in memory, and
  not from `ResultsClient` any more.
- **Version correlation mechanism is still TBD** (design §7) — three options considered
  (`recorded_date` ruled out, `sharedTime` propagation, `resultEventId` propagation), none
  decided. Don't build against any of them as if settled; check design doc status first.
- **`PcrVersionCorrelationHandler` is the only component allowed to know Progression exists** —
  every other layer only ever reads `versionStatus`/`materialId` once the correlator has set them.
  (Not yet implemented — this rule takes effect whenever it is.)
- **`CPVocabularyService`/`CPNowSubscriptionMatcher` are in scope; `PrisonCourtRegisterSubscriptions`
  (recipient/email resolution) and Progression PDF submission are not — confirmed with the
  Common Platform TA, see ADR-005/AMP-943.** This service's generation gate
  (`CPResultsPcrFilter.isPrisonCourtRegisterRequired`) only answers "would a PCR have been
  generated" — it never resolves *who* receives it. Subscriber registration and push
  notification stay owned by `service-cp-crime-hearing-results-document-subscription` and
  `now_subscriptions`. Long-term direction (agreed, not scheduled) is folding the remaining
  Function App/Progression logic into this same service — don't build against that as if
  already decided-and-scoped.
- **Confirmed dead, dropped.** `officerInCase` and `parentGuardianName`/`Address1-5`/`PostCode`
  are hardcoded blank in the legacy generator and have no real source data — not carried
  through. This is a "the data doesn't exist" exclusion, unrelated to the point below.
- **Defendant PII is carried, per ADR-004 — this reverses an earlier decision.**
  `title`/`firstName`/`middleName`/`lastName`/`dateOfBirth`/`address` were previously dropped on
  the basis that one consumer resolved defendant identity via `defendantId`/`masterDefendantId`
  against their own systems and didn't need this data from this API. A confirmed new requirement
  now needs it carried through, so it's back in scope — see
  `docs/pipeline/adrs/004-AMP-891-carry-defendant-pii-encrypted-at-rest.md` for the reversal.
  **Encryption at rest is future scope, deferred out of the current phase** — `CPVersionEntity`'s
  PII fields are plain values today (`dateOfBirth` a real `LocalDate`/`date` column, not
  ciphertext-shaped `varchar`). The `api-cp-crime-results-pcr` `Defendant` schema (v1.0.3, already
  pinned in `build.gradle`) already declares these fields; nothing in the spec needs to change.
  **Source confirmed as CP itself, not an external lookup — 28 Jul 2026.** CP's own hearing
  payload already carries this data at `personDefendant.personDetails.{title, firstName,
  middleName, lastName, dateOfBirth, address}` — the same object this service already reads via
  the Redis cache or the `hearingDetails/internal` REST fallback. Confirmed by cross-referencing
  `cpp-context-azure-legalaidagency`'s Redis-seeded integration-test fixtures (which populate this
  exact key format with this exact field shape) against its `DefendantMapper.js`, which reads
  `firstName`/`middleName`/`lastName`/`dateOfBirth`/`address`/`title` directly off the identical
  cache-or-API object with no separate downstream enrichment call in between. What remains is
  wiring, not sourcing: `HearingDetailsResponse.PersonDefendant` carries a `PersonDetails`/
  `Address` shape, and `CPHearingResultEntityMapper.applyPersonDetails`/`applyAddress` populate
  `CPVersionEntity`'s PII columns from it — done as part of the phase-2 persistence-wiring
  work, no longer an unconfirmed/blocked gap. Prosecution/defence counsel and aliases remain
  excluded for now — not reversed by this decision, revisit only if a real requirement surfaces
  for them too.
- Field-level mapping detail (base shape, aliasing fixes, enrichment additions) lives in the
  field-mapping doc in the `api-cp-crime-results-pcr` spec repo, not duplicated here.

## Debugging

| Symptom | Cause / Fix |
|---|---|
| `GET /pcr` returns an empty array for a hearing/defendant known to exist upstream | Expected if the webhook ingestion path hasn't persisted a `cp_version` row for it yet (async, gated by the generation-gate check) — `GET /pcr` only ever reads what's already in the data store, it does not call the Results Query API or wait on ingestion |
| Retry logic assumes REST fallback fails cleanly on a race | Unconfirmed assumption per design §4b/§13 item 2 — verify against the Results team's actual code before relying on it |
| Redis not available locally | Not yet wired into `docker-compose.yml` or an `apitest.gradle` project — `docker-compose.yml` only starts the `app` container today |
| `repositories/*RepositoryTest` fails with `PSQLException`/`does not exist` | Needs a real Postgres reachable at `localhost:5432` with a `pcrdb` database already created — start it via `docker compose up -d postgres` (service defined in this repo's `docker-compose.yml`) before running `./gradlew test`; `PostgresInitialise` fails fast with instructions if it's unreachable |

## Repo-Specific Notes

- Downstream: `service-cp-crime-hearing-results-document-subscription` — this service's Query
  API URL gets wired into that service's existing subscriber callback payload; this service does
  not own subscriber registration or push notification.
- Golden-master drift detection planned (design §9): pre-launch tests replay real past hearings
  through the new code path and assert output matches Progression's stored
  `prison_court_register.payload`; post-launch, the correlator's `ORPHANED` status is the live
  version of the same check — someone needs to actually watch that list, not just log it.
- MVP scope is Story 3's non-amendment phase-1 slice (mirror the Function App, no amendment
  handling) to get early subscriber feedback before the full service is built (design §12).
- No `apiTest`/docker-compose integration coverage for the webhook + Redis path yet — the
  existing `src/test` suite (`HearingResultedWebhookServiceTest`, `ResultsIngestionServiceTest`,
  etc.) is unit-level with mocked Redis clients only; the E2E test under `integration/e2e`
  drives the real path via `mockMvc` POST to `/internal/hearing-results` against a real Postgres/Redis.