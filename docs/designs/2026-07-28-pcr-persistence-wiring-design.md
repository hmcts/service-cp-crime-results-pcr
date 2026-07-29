# PCR Persistence Wiring — Design

**Status:** Draft, 28 Jul 2026.
**Jira:** AMP-890 (parent epic AMP-888).
**Scope:** wire the three independent code paths this repo's own `CLAUDE.md` describes as
"not talking to each other yet" — `ResultsIngestionService` (proves completeness),
`CPResultsPcrOrchestrator`/`CPVocabularyService` (the generation gate, built and unit-tested but
uncalled), and the Flyway-migrated JPA entities/repositories (built and integration-tested but
nothing writes to them) — into one real write path: a `Hearing_Resulted` event, once complete,
results in a `cp_version` row (plus its children) for every defendant the legacy Function App
would have generated a PCR for, and no row for every defendant it wouldn't.

**Explicitly not in scope** (tracked as a separate follow-on spec, agreed up front): bumping the
`api-cp-crime-results-pcr` dependency to its latest released contract, and switching `GET /pcr`
to read from this data store instead of live `ResultsClient`. `GET /pcr` is untouched by this
design — it keeps its current behaviour throughout.

---

## 1. Architecture / data flow

```
HearingResultedProcessorService (Service Bus listener)
  → ResultsIngestionService.ingestAndPersist(hearingId, hearingDay)   [NEW — @Transactional]
      1. ingestHearingResults(...) — existing completeness check, unchanged
      2. resolveActiveAt(hearing) — NEW, one date for the whole hearing's subscription gate
      3. for each prosecutionCase → for each defendant:
           a. CPVocabularyService.compute(defendant, hearing)         [existing, unchanged]
           b. gather this defendant's own case + linked-application judicial results
           c. CPResultsPcrOrchestrator.excludePublishedForNows(...)    [existing, unchanged]
           d. CPResultsPcrOrchestrator.isPrisonCourtRegisterRequired(vocabulary, filtered, activeAt)
           e. false → log INFO "skipped, not required", no row written
              true  → find-or-create cp_case_hearing (+cp_case_marker on first creation)
                      → CPVersionEntityMapper.toWriteBundle(...)      [NEW mapper]
                      → save cp_version, cp_court_application, cp_offence,
                        cp_judicial_result, cp_judicial_result_prompt (FK-safe order)
```

## 2. New/changed components

| Component | Change |
|---|---|
| `domain/HearingDetailsResponse.JudicialResult` | add `LocalDate orderedDate` |
| `domain/HearingDetailsResponse.PersonDefendant` | add `PersonDetails personDetails` |
| `domain/HearingDetailsResponse.PersonDetails` (new) | `title`, `firstName`, `middleName`, `lastName`, `dateOfBirth`, `Address address` |
| `domain/HearingDetailsResponse.Address` (new) | `address1`, `address2`, `address3`, `postcode` |
| `services/ResultsIngestionService` | new `ingestAndPersist(...)` + private helpers (`resolveActiveAt`, `processProsecutionCase`, `processDefendant`); existing `ingestHearingResults` unchanged |
| `mappers/CPVersionEntityMapper` (new) | owns all entity `.builder()` calls — `toCaseHearingEntity`, `toCaseMarkerEntities`, `toWriteBundle` (version + court applications + offences + judicial results + prompts) |
| `repositories/CPCaseHearingRepository` | add `Optional<CPCaseHearingEntity> findByCaseUrnAndHearingId(String caseUrn, UUID hearingId)` — first justified custom query method on any of the 7 repositories |
| `servicebus/services/HearingResultedProcessorService` | call `ingestAndPersist(...)` instead of discarding the result of `ingestHearingResults(...)` |
| `exceptions/NoOrderedDateFoundException` (new) | thrown by `resolveActiveAt` — see §4.2 |

## 3. Domain model additions in full

```java
// HearingDetailsResponse.JudicialResult — new field
private LocalDate orderedDate;
// Sourced from CP's own hearing payload — needs a real fixture check per the orchestrator
// design doc §7, same "confirm before relying on" caveat as publishedForNows was under.

// HearingDetailsResponse.PersonDefendant — new field
private PersonDetails personDetails;

// New nested class
public static class PersonDetails {
    private String title;
    private String firstName;
    private String middleName;
    private String lastName;
    private LocalDate dateOfBirth;
    private Address address;
}

// New nested class
public static class Address {
    private String address1;
    private String address2;
    private String address3;
    private String postcode;
}
```

Confirmed present in CP's own hearing payload (not a new/external source) via cross-reference
against `cpp-context-azure-legalaidagency`'s Redis-seeded integration-test fixtures and its
`DefendantMapper.js`, which reads these exact fields off the identical cache-or-API object with
no separate enrichment call — see `docs/pipeline/adrs/004-AMP-891-carry-defendant-pii-encrypted-at-rest.md`
(updated 28 Jul 2026) for the full evidentiary trail. Encryption at rest remains deferred to a
future phase per that ADR — unaffected by this design.

Mapping to `CPVersionEntity`: `address1`→`addressLine1`, `address2`→`addressLine2`,
`address3`→`addressLine3`, `postcode`→`postCode`. `addressLine4`/`addressLine5` stay `null` — no
4th/5th address line exists upstream.

## 4. Interpretation decisions (settled during design)

### 4.1 `eligibleResults` scope for the gate call

This defendant's own case-level offence judicial results, plus judicial results from any court
application where a respondent's `masterDefendantId` matches this defendant's — the same filter
`PcrVersionMapper.toCourtApplications` already uses for the read path. **Not** the vocabulary's
hearing-wide merged view. Vocabulary computation stays merged (existing `CPVocabularyService`
behaviour, unchanged); the content/eligibility list stays per-defendant — consistent with this
repo's own architecture rule that computing eligibility against a merged view doesn't mean the
persisted row merges.

### 4.2 `resolveActiveAt` — replicate legacy exactly, including its failure mode

Confirmed against `cpp-context-azure-legalaidagency/azure-functions/durable-functions/PrisonCourtRegisterSubscriptions/index.js:52-57`
(`getOrderedDate`) and `NowsHelper/service/ReferenceDataService.js:33-38`
(`getSubscriptionsMetadata`):

- The date sent to Reference Data's `now-subscriptions?on=<date>` is the first hearing defendant
  (in array order) with any judicial result carrying a non-null `orderedDate`, that specific
  result's `orderedDate`, truncated to `YYYY-MM-DD` (no time component). It is computed **once
  per hearing event** and reused for every defendant on that hearing — not recomputed per
  defendant.
- Legacy has **no designed fallback** if nobody on the hearing has any `orderedDate` —
  `.find()` returns `undefined`, the next line dereferences `undefined.registerDefendant` and
  throws a `TypeError`, silently caught and logged by the outer `try/catch` (`index.js:74-76`).
  The activity effectively fails; there's no "use today" or "use the hearing date" fallback
  actually reached in that case (the `on === undefined` fallback in `ReferenceDataService.js:34`
  only fires if `getOrderedDate()` returns cleanly with `undefined`, which the code never does —
  it throws first).
- **This design replicates that failure mode explicitly, not silently**: `resolveActiveAt`
  throws `NoOrderedDateFoundException` when no judicial result anywhere on the hearing has
  `orderedDate` set, with a comment citing this exact legacy crash path. Unlike legacy, this
  propagates as a real, typed, loggable exception through `ingestAndPersist` to the existing
  Service Bus dead-letter catch-all in `HearingResultedProcessorService` — visible and
  traceable, rather than vanishing the way legacy's silently-swallowed crash does.

### 4.3 Court-application content duplication across a shared `masterDefendantId`

If one physical person has two case-specific `defendantId`s on the same hearing sharing a
`masterDefendantId`, and a court application matches that `masterDefendantId`, its content
(`cp_offence`/`cp_judicial_result` rows parented by that `cp_court_application`) is embedded via
**separate, duplicated rows** under both persisted `cp_version` rows. This mirrors what the
existing phase-1 `PcrVersionMapper.toCourtApplications` already does for the synchronous read
path — this design doesn't introduce new duplication, it persists the same shape the read path
would already produce for either defendant queried individually.

## 5. Known, accepted limitations (flagged, not solved here)

- **No idempotency on Service Bus redelivery.** If persistence throws partway through and the
  message redelivers, duplicate `cp_version` rows are possible for the same hearing/defendant.
  Not solved here — the same "version correlation mechanism is still TBD" gap this repo's
  `CLAUDE.md` already flags (design doc §7); a real fix depends on the still-undecided
  `source_id` propagation mechanism, out of this design's scope.
- **`cp_offence.id` is a generated surrogate for now** (`UUID.randomUUID()`), not CP's real
  offence id — `HearingDetailsResponse.Offence` has no id field to source one from yet. The
  data-store design doc calls this column "CP's own offence UUID, not a surrogate" as the target
  end state; this design knowingly diverges from that until the real id is confirmed available
  and added upstream.
- **`orderedDate` and PII fields both need a real fixture check** before this ships against a
  live environment — confirmed present in legacy fixtures and legacy code, not yet confirmed
  against a live `hearingDetails/internal` response from this service's own `ResultsClient` call.
  Same caveat this repo already applies to `publishedForNows`.

## 6. Error handling

- `IncompleteHearingDetailsException` from `ingestHearingResults` propagates unchanged — the
  existing retry/dead-letter path in `HearingResultedProcessorService` is untouched.
- `NoOrderedDateFoundException` (§4.2) and any DB write failure during persistence propagate as
  unhandled exceptions up to the listener's existing catch-all → dead-lettered, same as any other
  unrecoverable failure. No new retry policy is introduced — reuses what's already there.
- The whole `ingestAndPersist` call is one `@Transactional` boundary — all-or-nothing per hearing
  event (every required defendant's rows, or none), not per defendant.

## 7. Testing

- **Unit:** `CPVersionEntityMapper` (field-by-field, mirroring `PcrVersionMapperTest`'s existing
  style), the new `ResultsIngestionService` methods (mock repositories/orchestrator/vocabulary
  service), `resolveActiveAt` (both the found-date branch and the `NoOrderedDateFoundException`
  branch).
- **Repository/integration:** extend the existing `PostgresInitialise`-based suite — a real
  save→read round-trip for `CPCaseHearingRepository.findByCaseUrnAndHearingId`, and a full
  hearing→persisted-rows integration test asserting the FK graph lands correctly (one
  `cp_version` per required defendant, correct polymorphic parent set on
  `cp_offence`/`cp_judicial_result`, a defendant who fails the gate produces no row at all).
- **No `GET /pcr` test changes** — untouched by this design.

## 8. Follow-on work (separate spec, not this one)

- Bump `api-cp-crime-results-pcr` from `1.0.3` to the latest released contract (currently
  `v3.0.2`) across `PcrVersionMapper`/`ResultsPcrController`/`ResultsPcrService`.
- Switch `GET /pcr` to read from `cp_version` (this design's output) instead of live
  `ResultsClient`, mapping entities to the bumped contract's shape — dropping ids from the API
  response that the entities still retain (`cp_offence.id`, `cp_court_application.id`), per the
  contract's own `dd3a8e3` refactor.