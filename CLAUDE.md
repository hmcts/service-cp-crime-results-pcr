# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repo: service-cp-crime-results-pcr

Spring Boot service exposing Prison Court Register (PCR) source data — the same content
currently distributed as a PDF via the legacy Function App/Progression/Docmosis pipeline —
as a new pull-based read channel for API Marketplace subscribers. The contract is not scoped
to any single consumer's stated needs — decisions here apply platform-wide, not to one
subscriber.

**Pattern**: Hybrid, mid-build — synchronous stateless proxy (`GET /pcr`) implemented; async
Service Bus ingestion listener implemented but not yet wired to any persistence; DB-backed
version store (Postgres/Flyway, immutable `cp_version` rows) is phase 2 and design-only.
**Spring Boot version**: 4.1.0
**Implements**: `api-cp-crime-results-pcr` v1.0.3 (`PcrApi` — see `build.gradle`)

**Status**: Three independent code paths exist and do not yet talk to each other:
- `GET /pcr/.../{version}` (`ResultsPcrController` → `ResultsPcrService`) calls `ResultsClient`
  synchronously on every request. It never reads Redis, never goes through the ingestion
  listener, and has no completeness gate — it returns whatever the Results Query API has *right
  now*, even mid-race. Only `version=latest` is supported; any other value is `501`.
- The Service Bus listener (`HearingResultedProcessorService` → `ResultsIngestionService`)
  consumes `Hearing_Resulted`, checks Redis-then-REST for complete hearing data, and
  retries/dead-letters on incompleteness — but **does not persist anything**. There is no data
  store yet for it to write a `cp_version` row into.
- The orchestrator (`CPVocabularyService`, `CPResultsPcrOrchestrator`, `CPNowSubscriptionMatcher`,
  `ReferenceDataClient`) is fully implemented and unit-tested but **not called from anywhere**
  — no controller, service, or listener constructs a `CPVocabulary` or invokes
  `isPrisonCourtRegisterRequired` yet. It exists ahead of wiring, same pattern as the phase-2
  Flyway migrations.

Read `docs/designs/2026-07-16-pcr-api-marketplace-design-v2.md` (authoritative architecture) plus the
four dated follow-on design docs in `docs/designs/` before adding any component, not just this file —
each one is a deeper design pass on one layer (stateless-proxy phase 1, data store phase 2,
hearing-event ingestion, orchestrator) and states its own scope/status at the top.
`docs/pipeline/adrs/` records the decisions behind each layer, tagged with their Jira ticket
(AMP-888 parent epic through AMP-943) — read the relevant ADR before revisiting a decision
that looks arbitrary; it likely isn't.

## Infrastructure

| Component | Technology | Purpose |
|---|---|---|
| Ingestion trigger | Azure Event Grid `Hearing_Resulted` → self-provisioned Service Bus queue (`pcr.hearing-resulted`, `HearingResultedQueueProvisioner`) → `HearingResultedProcessorService` (raw `ServiceBusProcessorClient`, **not** `spring-cloud-azure-stream-binder-servicebus` — ADR-002/AMP-889) | Pointer-only event (`hearingId`/`hearingDay`/`userId`), unwrapped from an `CPHearingResultedEventEnvelope`; malformed payloads are dead-lettered, not retried |
| Results Query Client | `HearingResultedCacheClient` (Redis, read-only `StringRedisTemplate`) first, `ResultsClient` (`RestClient`) REST fallback against `results-query-api/.../hearingDetails/internal/{hearingId}` | Two-step retrieval per design §4a/4b — **ingestion path only**; the synchronous `GET /pcr` path skips Redis entirely |
| Retry/escalation | `RetryServiceConfig` (`service-bus.retry-durations`/`max-tries`) + `ResultsIngestionService.escalateOrDeadLetter` | On `IncompleteHearingDetailsException`, schedules Service Bus redelivery (`ServiceBusSenderClient`) with increasing backoff; dead-letters once `max-tries` is exceeded |
| Reference Data — `ResultDefinition` | Lookups, offence metadata (e.g. `startDate`) | Not yet built — "to be analysed" per design §8 |
| Reference Data — `now-subscriptions` | `ReferenceDataClient` (`RestClient`) → `.../referencedata-query-api/.../now-subscriptions?on=<date>`; `CPNowSubscriptionMatcher` matches the PCR-flagged subset against a `CPVocabulary` | Built, unit-tested, **not called** — no caller passes a real `on` date yet (design §7's date-selection strategy is still open) |
| Generation-gate orchestrator | `CPVocabularyService` (fact computation) + `CPResultsPcrOrchestrator` (`excludePublishedForNows`, `isPrisonCourtRegisterRequired`) | Design §4 scope, confirmed with Common Platform TA per ADR-005/AMP-943 — generation-gate logic only; recipient resolution and Progression submission are explicitly out of scope |
| Data store | Flyway migrations (`V1.001`-`V1.008`), 7 JPA entities (`entities/`), 7 plain `JpaRepository`s (`repositories/`) — no custom query methods, nothing calls them yet | Schema + persistence layer built and integration-tested against a real, manually-started Postgres (`PostgresInitialise`, same pattern as HRDS); **not wired** — no service constructs/reads a `cp_version` row yet. Encryption (ADR-004) and the version-correlation mechanism (§7) are separate, still-open work |
| Version lookup / retention | Not implemented | Depends on the phase-2 data store + the still-undecided version-correlation mechanism (§7) |

## Source Structure

- `controllers/ResultsPcrController` — implements generated `PcrApi`; validates `caseURN`
  against `CASE_URN_REGEX` (`^[0-9a-zA-Z]{1,30}$`) before delegating
- `services/ResultsPcrService` — synchronous version lookup; `501`s on any `version` other than
  `latest`; `404`s if the case/defendant isn't found on the hearing
- `services/ResultsIngestionService` — Redis-then-REST hearing lookup, completeness check
  (`prosecutionCases` non-empty), retry/escalation via Service Bus
- `servicebus/services/HearingResultedProcessorService` — `ServiceBusProcessorClient` message
  loop; unwraps `CPHearingResultedEventEnvelope`, dead-letters on malformed payload or unrecoverable failure
- `servicebus/model/CPHearingResultedEventEnvelope`, `CPHearingResultedEventData` — Event Grid message shape; pointer
  fields nest under `.data`, not flat on the envelope
- `clients/ResultsClient` — `RestClient` call with `Accept: application/vnd.results.hearing-details-internal+json`
- `clients/HearingResultedCacheClient` — Redis read only; key format
  `INT_{hearingId}_{hearingDay}_result_`, matching the legacy Function App/`cpp-context-results`
  scheme exactly (no new scheme invented)
- `clients/HearingResultedServiceBusClientFactory` — builds processor/sender clients;
  connection-string (emulator) vs. `DefaultAzureCredentialBuilder` managed identity (Azure),
  switched on `ServiceBusProperties.isEmulator()` (`sb://` vs `https`)
- `clients/HearingResultedQueueProvisioner` — self-provisions the queue on startup if absent
- `mappers/PcrVersionMapper` — builds `PcrVersion` from `HearingDetailsResponse`; `id` is always
  `null` (no event-correlation pipeline in phase 1); several OpenAPI fields are deliberately left
  unset — see the inline comments and the field-mapping doc in the spec repo before adding
  a field back in
- `mappers/JudicialResultPromptParser` — extracts sentencing detail (`concurrent`, `fineAmount`,
  `imprisonmentPeriod`, etc.) from `judicialResultPrompts` by `promptReference` string lookup
- `config/RetryServiceConfig`, `ServiceBusProperties`, `AppPropertiesBackend` — `@Value`-backed
  config records/beans for retry schedule, Service Bus connection, and backend URLs
  (results-query-client, reference-data-client)

### The `orchestrator` sub-package (`services/orchestrator/`, `clients/orchestrator/`, `domain/orchestrator/`)

Every class here is built and unit-tested but **not called from `ResultsPcrController`,
`ResultsPcrService`, or the Service Bus listener** — it exists ahead of wiring, same status as
the phase-2 Flyway migrations. It's sub-packaged (not a new top-level layer — every sibling
`service-cp-*` repo's `controllers/services/clients/domain` shape stays intact) specifically so
this "not wired in yet" boundary is visible in the directory listing and import statements
instead of only living in this file's prose:

- `services/orchestrator/CPVocabularyService` — computes `CPVocabulary` (custody, custodial-result,
  CPS, age-group, court-language facts) from a defendant + hearing; merges custody/CPS scan
  across every `prosecutionCase`/`courtApplication` sharing the defendant's `masterDefendantId`
  on the same hearing (a real merge scenario, not a data-model bug — see repo architecture rules
  below)
- `services/orchestrator/CPNowSubscriptionMatcher` — matches a `CPNowSubscription`'s vocabulary
  requirements against a computed `CPVocabulary` + eligible `JudicialResult`s; every dimension
  fails closed when unconfigured except `applySubscriptionRules == false`; attendance matching
  is stubbed (any-flag-only) pending a confirmed `hearing.defendantAttendance` source
- `services/orchestrator/CPResultsPcrOrchestrator` — `excludePublishedForNows` (plain-field content
  filter) and `isPrisonCourtRegisterRequired` (the generation gate: fetches subscriptions via
  `ReferenceDataClient`, filters to `isPrisonCourtRegisterSubscription`, matches via
  `CPNowSubscriptionMatcher`)
- `clients/orchestrator/ReferenceDataClient` — `RestClient` call to Reference Data's
  `now-subscriptions` endpoint; deliberately generic (`CPNowSubscription`/`SubscriptionVocabulary`),
  not PCR-specific — the same config backs other distribution-channel kinds (NOW/EDT/informant/
  court register), per ADR-005
- `domain/orchestrator/CPNowSubscription`/`CPNowSubscriptionsResponse` — wire shape for the
  `now-subscriptions` response; every `SubscriptionVocabulary` field is boxed `Boolean`, not
  primitive — a real subscription omits a dimension's keys entirely rather than sending `false`
  when it doesn't configure that dimension
- `domain/orchestrator/CPVocabulary` — the eligibility-fact record `CPVocabularyService` computes;
  never surfaces in `PcrVersion`/`cp_version` — it exists only to decide *whether* a PCR is
  generated, not to describe its content (design doc §2)

`HearingDetailsResponse`/`HearingResultedPointer` stay in top-level `domain/` — they're genuinely
shared across all three code paths, unlike the orchestrator-only types above.

### Data store (`entities/`, `repositories/`)

Flat entity-per-table mapping, no JPA associations (`@ManyToOne`/`@OneToMany`) — foreign keys
are plain `UUID` fields, matching `service-cp-crime-hearing-results-document-subscription`'s
established convention. `cp_offence`/`cp_judicial_result`'s polymorphic parent (exactly one of
two nullable FKs set, design doc §1/§3) is enforced by the DB `CHECK` constraint only, not
modelled as inheritance in Java. `CPVersionEntity`'s PII columns (`title`/`firstName`/etc.) are
plain `String` today — no `EncryptionService` is wired yet (ADR-004 is a separate piece of work).
Every repository is a bare `JpaRepository<Entity, UUID>` with no custom query methods — nothing
calls them yet. Proven against a real Postgres, not an in-memory substitute — same pattern as
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
| `AZURE_SERVICE_BUS_ADMIN_URI` | Service Bus admin/management connection (queue provisioning) | local emulator connection string |
| `AZURE_SERVICE_BUS_URI` | Service Bus data-plane connection — `sb://` selects emulator auth, `https` selects managed identity | local emulator connection string |
| `PCR_HEARING_RESULTED_QUEUE` | Self-provisioned queue name | `pcr.hearing-resulted` |
| `SERVICE_BUS_RETRY_DURATION` | Comma-separated scheduled-redelivery backoff | `30s,1m,2m,3m` |
| `SERVICE_BUS_MAX_TRIES` | Max redelivery attempts before dead-letter | `3` |
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
  hearing by `masterDefendantId` for this reason (orchestrator design doc §2/§7). This is
  orthogonal to the point above: computing *eligibility* against the merged view does not mean
  the *persisted* `cp_version` row merges — it stays keyed per `(hearingId, defendantId)`.
- **Redis-first, REST-fallback-with-retry is mandatory, not an optimisation** — Redis is written
  synchronously before `Hearing_Resulted` fires (guaranteed populated); the REST viewstore is
  updated asynchronously and can race. Skipping the Redis check reintroduces a real, confirmed
  race condition (design §4a/§4b), not a theoretical one. **This rule currently applies to the
  ingestion listener only** — `GET /pcr` (`ResultsPcrService`) does not check Redis and has no
  completeness gate; don't assume the synchronous endpoint is race-safe just because the
  ingestion path is.
- **The ingestion listener does not persist anything today.** A successful
  `ResultsIngestionService.ingestHearingResults` call only proves the hearing data is complete
  and retryable-safe — it does not mean a `cp_version` row now exists, because there is no data
  store to write to until phase 2 (`docs/designs/2026-07-21-pcr-data-store-design.md`) is implemented.
  Don't wire `GET /pcr` to "whatever the listener last saw" as a shortcut.
- **Version correlation mechanism is still TBD** (design §7) — three options considered
  (`recorded_date` ruled out, `sharedTime` propagation, `resultEventId` propagation), none
  decided. Don't build against any of them as if settled; check design doc status first.
- **`PcrVersionCorrelationHandler` is the only component allowed to know Progression exists** —
  every other layer only ever reads `versionStatus`/`materialId` once the correlator has set them.
  (Not yet implemented — this rule takes effect whenever it is.)
- **`CPVocabularyService`/`CPNowSubscriptionMatcher` are in scope; `PrisonCourtRegisterSubscriptions`
  (recipient/email resolution) and Progression PDF submission are not — confirmed with the
  Common Platform TA, see ADR-005/AMP-943.** This service's generation gate
  (`CPResultsPcrOrchestrator.isPrisonCourtRegisterRequired`) only answers "would a PCR have been
  generated" — it never resolves *who* receives it. Subscriber registration and push
  notification stay owned by `service-cp-crime-hearing-results-document-subscription` and
  `now_subscriptions`. Long-term direction (agreed, not scheduled) is folding the remaining
  Function App/Progression logic into this same service — don't build against that as if
  already decided-and-scoped.
- **Confirmed dead, dropped.** `officerInCase` and `parentGuardianName`/`Address1-5`/`PostCode`
  are hardcoded blank in the legacy generator and have no real source data — not carried
  through. This is a "the data doesn't exist" exclusion, unrelated to the point below.
- **Defendant PII is carried and encrypted, per ADR-004 — this reverses an earlier decision.**
  `title`/`firstName`/`middleName`/`lastName`/`dateOfBirth`/`address` were previously dropped on
  the basis that one consumer resolved defendant identity via `defendantId`/`masterDefendantId`
  against their own systems and didn't need this data from this API. A confirmed new requirement
  now needs it carried through, so it's back in scope — see
  `docs/pipeline/adrs/004-AMP-891-carry-defendant-pii-encrypted-at-rest.md` for the reversal and the
  encryption-at-rest approach. The `api-cp-crime-results-pcr` `Defendant` schema (v1.0.3, already
  pinned in `build.gradle`) already declares these fields; nothing in the spec needs to change.
  What's still missing: the upstream `HearingDetailsResponse` DTO has no fields to source this
  data from yet, and `PcrVersionMapper.toDefendant()` doesn't populate them — both are open work,
  not yet done. Prosecution/defence counsel and aliases remain excluded for now — not reversed by
  this decision, revisit only if a real requirement surfaces for them too.
- Field-level mapping detail (base shape, aliasing fixes, enrichment additions) lives in the
  field-mapping doc in the `api-cp-crime-results-pcr` spec repo, not duplicated here.

## Debugging

| Symptom | Cause / Fix |
|---|---|
| `GET /pcr` returns an incomplete or empty `prosecutionCases` hearing | Expected today — `ResultsPcrService` has no completeness gate or retry; only the async ingestion listener (`ResultsIngestionService.isComplete`) guards against viewstore lag, and the two paths are independent (see Status above) |
| Retry logic assumes REST fallback fails cleanly on a race | Unconfirmed assumption per design §4b/§13 item 2 — verify against the Results team's actual code before relying on it |
| Service Bus emulator / Redis not available locally | Not yet wired into `docker-compose.yml` or an `apitest.gradle` project (ADR-002 consequence) — `docker-compose.yml` only starts the `app` container today |
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
- No `apiTest`/docker-compose integration coverage for the Service Bus + Redis path yet — the
  existing `src/test` suite (`HearingResultedProcessorServiceTest`, `ResultsIngestionServiceTest`,
  etc.) is unit-level with mocked Azure/Redis clients only.