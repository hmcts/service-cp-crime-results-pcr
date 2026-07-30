# PCR Read Path — Data Store + Contract v3.0.2 Design

**Status:** Draft, 28 Jul 2026.
**Jira:** AMP-890.
**Scope:** the follow-on work explicitly deferred by
[`2026-07-28-pcr-persistence-wiring-design.md`](2026-07-28-pcr-persistence-wiring-design.md)
§8 — bump `api-cp-crime-results-pcr` from `1.0.3` to the latest released contract (`v3.0.2`),
and switch `GET /pcr` from a live `ResultsClient` call to reading the data store that
persistence-wiring branch just started populating.

**Not in scope:** the `/versions` endpoint (`getPcrHearingResultsMetadata`) — the generated
`PcrApi` interface already has a `default` implementation returning `501` for it, so leaving it
un-overridden is sufficient; no stub code is needed. Tracked as a separate follow-on.

---

## 1. The contract change is bigger than one commit

Earlier investigation (during the persistence-wiring design) found a single commit
(`dd3a8e3`) that flattened `caseURN`, added a shared `Court` object, and dropped some ids.
Re-checking against the actual current spec (`v3.0.2`) surfaced a much larger change: an entire
`feature/AMP-890-pcr-contract-redesign` PR (spec repo PR #16, `44b6579`) redesigned the whole
versioning model, on top of which `dd3a8e3` and several more commits landed. The full picture:

- **The old `version=latest`/`version={id}` query-param mechanism is gone entirely.**
  `GET /pcrs/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}` now takes **path
  params only** and returns a **bare JSON array** of `PcrHearingResult` — the full recorded
  history for that key, not a single "latest" pick.
- **A second endpoint now exists**, `.../versions` (`getPcrHearingResultsMetadata`), returning
  `PcrVersionMetadataList` (id/hearingId/defendantId/recordedAt) — out of scope here (see above).
- **Renamed/reshaped fields**, all confirmed to already match this repo's entity model
  1:1 (no new mapping gaps): `Offence.judicialResults` (was `results`), `JudicialResultPrompt.reference`
  (was `promptReference`), `JudicialResult.financial`/`convicted` are now real booleans (already
  `Boolean` on `CPJudicialResultEntity` — no `Y`/`N` conversion needed anymore, unlike the old
  `PcrVersionMapper`).
- **`CourtApplication.offences`** stayed embedded `Offence[]` (an earlier PR#16 draft proposed
  `relatedOffenceIds: uuid[]` instead, but a later commit, `10c6afb`, reverted to embedded objects)
  — matches what `CPHearingResultEntityMapper` already persists (full offence rows, not just ids).
- **`CustodyLocation` is now `{name, custodyType}`**, not a plain string — `cp_version` only has
  a `custody_location` varchar today; `custodyType` needs a new column (§3).
- `PcrHearingResult` itself has **no `id` field** at all (moved to `PcrVersionMetadata`, the
  deferred `/versions` schema) — matches `cp_version.event_id` already being `null` in phase 1/2.
- Old schemas removed entirely from the spec: `PcrVersion`, `HearingDetails`, `NextHearing`
  (renamed/restructured into `PcrHearingResult`, `HearingDetails` — same name, new shape — and
  `NextHearing` respectively), `ProsecutionCase` (flattened away). This means `PcrVersionMapper`
  and its test **will not compile** once the dependency bumps — they must be deleted, not
  incrementally migrated.

## 2. Architecture / data flow

```
PcrResultsController.getPcrHearingResults(caseURN, hearingId, defendantId)
  → PcrResultsService.getPcrHearingResults(...)
      1. caseHearingRepository.findByCaseUrnAndHearingId(caseURN, hearingId)
         → absent: return List.of() (200, empty — see §4)
      2. versionRepository.findByCaseHearingIdAndDefendantIdOrderByCreatedAtAsc(caseHearingId, defendantId)
         → empty: return List.of()
      3. for each CPVersionEntity: gather its children —
         courtApplicationRepository (by versionPk), offenceRepository (by versionPk, direct;
         by courtApplicationId, linked — for each court application), judicialResultRepository
         (by offenceId; by courtApplicationId), judicialResultPromptRepository (by
         judicialResultId), caseMarkerRepository (by caseHearingId — same set for every version
         on this case, case-level not per-version)
      4. PcrResultsMapper.toPcrHearingResult(version, courtApplications, offences,
         judicialResults, prompts, caseMarkers) → PcrHearingResult
  → List<PcrHearingResult>
```

## 3. New/changed components

| Component | Change |
|---|---|
| `build.gradle` | bump `api-cp-crime-results-pcr` `1.0.3` → `3.0.2` |
| New migration `V1.010__add_custody_type_to_cp_version.sql` | `ALTER TABLE cp_version ADD COLUMN custody_type varchar;` |
| `entities/CPVersionEntity` | add `custodyType` field |
| `mappers/CPHearingResultEntityMapper` | add `toCustodyType(Defendant)` (mirrors `toCustodyLocation`, sources `custodialEstablishment.getCustody()`), wire into `toVersionEntity`'s builder chain |
| `repositories/CPVersionRepository` | add `findByCaseHearingIdAndDefendantIdOrderByCreatedAtAsc(UUID, UUID): List<CPVersionEntity>` — second justified custom query method |
| `mappers/PcrResultsMapper` (new) | entities → `PcrHearingResult`; owns all `.builder()` calls for the new generated DTOs, per the mapper-creates-objects rule |
| `mappers/PcrVersionMapper` + `PcrVersionMapperTest` | **deleted** — target DTOs no longer exist |
| `services/PcrResultsService` | rewritten — no `ResultsClient` dependency; queries repositories per §2 |
| `controllers/PcrResultsController` | rewritten — implements the new path-params-only `getPcrHearingResults` signature; `CASE_URN_REGEX` validation unchanged |
| `integration/PcrResultsControllerIntegrationTest` | rewritten — seeds Postgres directly via `CPHearingResultEntityMapper` + repositories (same pattern as the persistence-wiring design's Task 6), no more WireMock stub of the Results Query API |

## 4. Settled decisions

- **404-vs-empty-array distinction dropped.** The contract still declares a `404` response is
  possible ("the case, hearing, or defendant does not exist"), but this service's data store has
  no independent signal for "this hearing/defendant is real" separate from "a PCR was recorded
  for it" — `cp_version` rows only ever exist for defendants that passed the eligibility gate.
  Distinguishing the two would require a live call back to `ResultsClient` just to check
  existence, defeating the purpose of reading from the store. Always `200` + empty array when
  nothing is found. A declared-but-never-emitted response code is a permissible implementation
  choice, not a contract violation.
- **`custodyType` sourced going forward, not backfilled.** New column defaults `null` on existing
  rows; only rows written after this ships get a real value, from `custodialEstablishment.custody`
  (already available on the domain model, just not persisted until now).
- **Version ordering: oldest→newest by `created_at`.** The spec doesn't mandate an order for the
  array response (only `PcrVersionMetadataList`, the deferred endpoint, is explicitly documented
  as "no guaranteed order"). Chosen for a natural reading order; revisit if a real consumer need
  for newest-first surfaces.
- **`ResultsClient` is untouched.** `ResultsIngestionService`'s async ingestion path still needs
  it for the Redis-miss REST fallback — only `PcrResultsService`'s dependency on it is removed.

## 5. Testing

- Unit: `PcrResultsMapper` (field-by-field, mirroring `CPHearingResultEntityMapperTest`'s style),
  `PcrResultsService` (mock repositories, cover: case-hearing absent → empty, versions absent →
  empty, single version, multiple versions ordered correctly).
- Integration: rewrite `PcrResultsControllerIntegrationTest` to seed real Postgres rows (via
  `CPHearingResultEntityMapper` + repositories) instead of stubbing a live backend, then assert the
  JSON response shape matches the new contract.
- No changes needed to `ResultsIngestionServiceTest`/`HearingResultedProcessorServiceTest` —
  the write path is unaffected by this design.