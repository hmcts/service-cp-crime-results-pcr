# PCR Persistence for Court-Application-Only Hearings — Design

**Status:** Implemented, 02 Sep 2026.
**Jira:** AMP-1079 (persistence), AMP-1080 (defendant type) — sub-tasks of AMP-1070, epic AMP-888.
**Depends on:** AMP-1070 (completeness-check fix) — lets a hearing with only `courtApplications`
(no `prosecutionCases`) pass `ResultsIngestionService.isComplete()` instead of retrying forever.
It didn't fix what happens next: `persist()` still assumed every hearing has `prosecutionCases`
and threw `NullPointerException` on one that doesn't. This design covers that next step.
**Scope:** persist a `cp_version` row for a defendant only reachable through
`hearing.courtApplications` — no matching `hearing.prosecutionCases` entry at all — and compute
their Applicant/Appellant/Respondent label.

---

## 1. Evidence this is real, not an edge case

- A real `hearingDetails/internal` response for a dev hearing with a populated
  `courtApplications` array and no `prosecutionCases` field at all (an "Appeal against
  conviction").
- Three CP-generated PCR register PDFs, dated the same day, each for a different
  court-application-only hearing (two appeals, one application to reopen a case). Each shows a
  full PCR entry — party details, case reference, offence + result, and an "Applications" block —
  generated with no `hearing.prosecutionCases` entry at all.

**Not "no case exists"** — the appeal hearing's application still references its case via
`courtApplicationCases[].prosecutionCaseId`/`.prosecutionCaseIdentifier` (real values in the
evidence above). What's actually missing is a full `ProsecutionCase` object on *this* hearing —
likely because the case wasn't being actively prosecuted at this (appeal) hearing, only
referenced by the application. §2/§3 use `applicationReference` for the case URN and
`courtApplicationCases[].prosecutionCaseId` only for `defendantId` matching — neither treats the
application as case-less.

CP's own Function App/Progression pipeline generates a PCR for this hearing shape today.

## 2. Rules, sourced from CP's own pipeline

Read directly from `cpp-context-azure-legalaidagency` and `cpp-context-progression` — not
inferred from the PDFs, which only show the final output and can't distinguish some of the rules
below.

- **Party source is `courtApplication.subject.masterDefendant`**, never `respondents[]`/
  `applicant` — confirmed by three independent code paths (`DefendantMapper.getFirstDefendants`,
  `ProsecutionCaseOrApplicationMapper.getDefendant`, `DefendantContextBaseService
  .setJudicialResultsAtCourtApplicationLevel`), all keying off `subject.masterDefendant
  .masterDefendantId`. Matches what this repo's `CPHearingResultEntityMapper`/`CPVocabularyService`
  already assume for the *enrichment* case (a court application linked to a defendant reached via
  a prosecution case) — this design extends the same rule to the *standalone* case.
- **Eligibility gate**: only usable if `subject.masterDefendant` is present
  (`DefendantContextBaseService.isEligible`). An application naming no defendant is skipped.
- **Offence source**: `courtApplicationCases[].offences[]` plus `courtOrder.courtOrderOffences[]`
  — already implemented in this repo's `CPHearingResultEntityMapper.linkedOffencesOf` for the
  enrichment case, reusable here unchanged.
- **Case reference** is `courtApplication.applicationReference`
  (`ProsecutionCaseOrApplicationMapper.getCourtApplicationInfoByApplicant`), not a nested case
  identifier — adopted as-is for `cp_case_hearing.case_urn`.
- **Applicant/Appellant/Respondent/Defendant is a real, computed, persisted field**, not
  presentation-only. `cpp-context-progression`'s `PrisonCourtRegisterHandler` computes and
  persists it as part of the `prison-court-register-recorded` event, before any document
  template is involved:
  ```java
  // handleAddPrisonCourtRegister — outer default and gate
  String defendantType = "Defendant";
  if (nonNull(courtApplicationId)) {   // only the defendant's FIRST prosecutionCasesOrApplications entry is checked
      defendantType = getDefendantType(request, courtApplication);
  }

  // getDefendantType — inner decision
  String defendantType = "Applicant";
  if (nonNull(courtApplication.getApplicant().getMasterDefendant())) {   // no equality check — a real quirk, ported as-is
      defendantType = appealFlag && applicantAppellantFlag ? "Appellant" : "Applicant";
  } else if (any respondent's masterDefendantId equals this defendant's) {
      defendantType = "Respondent";
  }
  ```
  Ported verbatim, quirks included, from `PrisonCourtRegisterHandler.handleAddPrisonCourtRegister`/
  `.getCourtApplicationId`/`.getDefendantType` (lines 57-166) — not reinterpreted. Needs
  `courtApplication.applicant`/`.respondents[]`, not previously modelled here (only `subject`
  was, for the enrichment case).
- **A prosecuting authority can occupy the applicant *or* a respondent slot** — Progression's own
  generated `CourtApplicationParty` type (used for both `applicant` and every `respondents[]`
  entry) declares `masterDefendant`/`prosecutingAuthority`/`organisation`/`personDetails` as
  independent nullable fields on one shared party shape, not separate types. `getDefendantType`'s
  respondent check filters out entries with no `masterDefendant` before matching — the applicant
  check has no equivalent filter (the quirk above), which is why it never verifies whose
  `masterDefendant` it is. Ported the respondent filter faithfully; without it a
  prosecuting-authority respondent would either NPE or (worse) never be correctly excluded.

## 3. What changes

| Component | Change |
|---|---|
| `domain/HearingDetailsResponse.MasterDefendant` | add `personDefendant`, `isYouth`, `defendantCase` (new `DefendantCase`: `caseId`, `caseReference`, `defendantId`) |
| `domain/HearingDetailsResponse.CourtApplicationCase` | add `prosecutionCaseId` — for `defendantId` matching only, not case URN |
| `domain/HearingDetailsResponse.CourtApplication` | add `applicant`, `respondents` (both `ApplicationParty`) |
| `domain/HearingDetailsResponse.ApplicationType` | add `appealFlag`, `applicantAppellantFlag` |
| `mappers/CPHearingResultEntityMapper` | new `applicationOnlyDefendant(CourtApplication)` — builds a `Defendant` from `subject.masterDefendant`; `offences = List.of()` since the existing enrichment machinery already supplies real content once `masterDefendantId` matches. `defendantId` resolved via `defendantCase[].caseId` ↔ `courtApplicationCases[].prosecutionCaseId`, falling back to the sole `defendantCase` entry when there's exactly one; empty (skip) when ambiguous — never guessed |
| `mappers/CPHearingResultEntityMapper` | new `defendantType(CourtApplication, masterDefendantId)` — the ported computation above. Only ever called for an application-only defendant; a prosecution-case-driven one keeps the literal `"Defendant"` (§4) |
| `entities/CPVersionEntity` + Flyway migration | new nullable `defendant_type` column |
| `mappers/CPHearingResultEntityMapper.toCaseHearingEntity`/`toWriteBundle` | new overloads taking a plain `caseUrn`/`defendantType` — existing `ProsecutionCase`-based signatures untouched |
| `services/ingestion/CPEntityPersistenceService.findOrCreateCaseHearing`/`.persist` | matching new overloads |
| `services/ingestion/ResultsIngestionService.resolveActiveAt`/`.persist` | scan `courtApplications` when `prosecutionCases` is null/empty, in addition to the existing prosecution-case path |

## 4. Why a prosecution-case-driven defendant never gets a computed label

CP's `ProsecutionCaseOrApplicationMapper.build()` always concatenates prosecution-case entries
before court-application entries. CP's `getCourtApplicationId` only ever checks index `[0]` of
that list, so any defendant with at least one prosecution case always has `courtApplicationId`
absent, and `defendantType` stays the literal `"Defendant"` — regardless of any court application
they're also linked to. CP's own code guarantees the computed branch only ever fires for a
defendant with zero prosecution cases, exactly this design's scope. No divergence needed: a
prosecution-case-driven defendant keeps `"Defendant"` unconditionally.

## 5. What does *not* change

- `CPEntityPersistenceService.persist(Defendant, ...)`, `CPVocabularyService.compute`,
  `CPResultsPcrFilter.isPrisonCourtRegisterRequired`, `eligibleResults`/`toWriteBundle` — all
  already operate generically on `Defendant` + `HearingDetail`, regardless of how the `Defendant`
  was built.
- One PCR record per `(hearingId, defendantId)` — an application-only defendant still gets
  exactly one `cp_version` row, keyed by the real `defendantId` resolved in §3, not by
  `masterDefendantId`.

## 6. Out of scope, tracked separately

- ~~**AMP-1081**: exposing `defendant_type` via `GET /pcr`~~ **Done.** `CourtApplication
  .defendantType` added to `api-cp-crime-results-pcr` (hmcts/api-cp-crime-results-pcr#69,
  additive/non-breaking). `PcrResultsMapper.toCourtApplication` sets the same value on every
  court application belonging to a defendant, since the label describes the defendant's role,
  not a per-application fact — a prosecution-case-driven defendant's applications all show the
  literal `"Defendant"` (§4), an application-only one's shows the computed value.
- **AMP-1082**: the "Prosecutor" name shown on every PCR (case-driven or application-driven)
  isn't captured anywhere in this repo's data model or contract today — a broader, pre-existing
  gap, not specific to this work.
- ~~Drift-detection fixture for the real payload above, and the `PcrDriftDetectionIntegrationTest`
  harness fix needed to run it~~ **Done.** A different real payload — an "Application to reopen
  case" (`fixture: jq780658399-application-to-reopen-case`) — exercises the `applicant` branch of
  `defendantType` for the first time against real data, and caught two bugs unit tests missed:
  `CPVocabularyService.matchingDefendants`/`.cpsProsecuted` NPE'd on a null `prosecutionCases`
  (a third NPE site beyond §3's Gap A), and `matchingDefendants` silently dropped an
  application-only defendant's own custody establishment from every check, since it only ever
  found defendants by scanning `prosecutionCases`, never including the input defendant itself.
- ~~Drift-detection coverage for the `Respondent` branch of `defendantType`~~ **Done.** A real
  appeal-against-conviction application (`fixture:
  case-with-application-defendant-respondent-xu780538628`, PII scrubbed) whose `applicant` slot
  is occupied by a prosecuting authority (`Organisation`/no `masterDefendant`) confirms
  `applicantDefendantType`'s check correctly fails in that case and `respondentDefendantType`
  matches the sole respondent's `masterDefendantId` and computes `"Respondent"` — against real
  data, not just the unit tests in `CPHearingResultEntityMapperTest`.

## 7. Open item

Multiple `defendantCase` entries with no matching `courtApplicationCases[].prosecutionCaseId`
(a party linked to several cases, application not linked to any of them) has no resolution rule —
`applicationOnlyDefendant` skips the application rather than guessing. Needs a real fixture
exercising this before revisiting.
