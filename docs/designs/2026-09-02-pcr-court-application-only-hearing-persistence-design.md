# PCR Persistence for Court-Application-Only Hearings — Design

**Status:** Draft, 02 Sep 2026.
**Jira:** none yet — raise under epic AMP-888 once this design is agreed.
**Depends on:** AMP-1070 (completeness-check fix, PR #102) — that fix lets a hearing with only
`courtApplications` (no `prosecutionCases`) pass `ResultsIngestionService.isComplete()` instead of
retrying forever. It does **not** fix what happens next: `persist()` still assumes every hearing
has `prosecutionCases` and throws `NullPointerException` on one that doesn't. This design covers
that next step.
**Scope:** persist a `cp_version` row (and its offences/judicial results/prompts) for a defendant
who is only reachable through `hearing.courtApplications` — no matching `hearing.prosecutionCases`
entry at all. Confirmed as real, current legacy behaviour (§1), not a hypothetical edge case.

---

## 1. Verification evidence

Two independent sources confirm this scenario is real and already handled by the system this repo
replaces:

1. **A real `hearingDetails/internal` response** for a dev-environment hearing whose `hearing`
   object has a populated `courtApplications` array and **no `prosecutionCases` field at all**
   (an "Appeal against conviction" application). This is the exact shape AMP-1070 fixed the
   completeness check for.
2. **Three legacy-generated PCR register PDFs**, dated the same day as that hearing, each for a
   different court-application-only hearing (an appeal, an application to reopen a case, and a
   second appeal). Each PDF shows a full PCR entry — party name/address/DOB, case reference,
   offence + result, and an "Applications" block with the application's own type and result —
   generated from a hearing with **no prosecution case**, only a court application.

This confirms the legacy Function App/Progression pipeline **does** generate a PCR for this
hearing shape. It is in scope, not an edge case to reject.

## 2. How legacy builds it (`cpp-context-azure-legalaidagency`)

Three independent code paths in the legacy repo all agree on the same rule, which is why it's
adopted here rather than invented fresh:

- **Party source is `courtApplication.subject.masterDefendant`, never `respondents[]` or
  `applicant`** — confirmed by `DefendantMapper.getFirstDefendants` (falls back to
  `courtApplication.subject.masterDefendant` only when no `prosecutionCases` defendant matches),
  `ProsecutionCaseOrApplicationMapper.getDefendant`/`getCourtApplicationOffences`/
  `getProsecutorName` (all key off `subject.masterDefendant.masterDefendantId`), and
  `DefendantContextBaseService.setJudicialResultsAtCourtApplicationLevel`. This matches what this
  repo's own `CPHearingResultEntityMapper`/`CPVocabularyService` already assume for the
  *enrichment* case (merging a court application's facts into a defendant already reached via a
  prosecution case) — this design extends the same rule to the *standalone* case, it doesn't
  introduce a new one.
- **Eligibility gate**: a court application is only usable as a register entry if
  `subject.masterDefendant !== undefined` (`DefendantContextBaseService.isEligible`, `isRegister`
  branch). An application naming no defendant (e.g. a pure prosecution-authority application) is
  skipped entirely.
- **Offence source**: `courtApplicationCases[].offences[]` plus `courtOrder.courtOrderOffences[]`
  (`DefendantContextBaseService.setJudicialResultsAtEachCourtApplicationCasesLevel`/
  `...CourtOrderLevel`, `ProsecutionCaseOrApplicationMapper.getCourtApplicationOffences`). This
  repo's `CPHearingResultEntityMapper.linkedOffencesOf` already implements exactly this merge —
  built for the enrichment case, directly reusable here unchanged.
- **Application-level result**: `courtApplication.judicialResults[]` — already modelled
  (`CourtApplication.judicialResults`), already read by `CPHearingResultEntityMapper.allResultsOf`.
- **Case/application reference**: legacy's PCR "Case reference" field is
  `courtApplication.applicationReference` (`ProsecutionCaseOrApplicationMapper
  .getCourtApplicationInfoByApplicant`), not the nested case's own URN — adopted as-is for
  `cp_case_hearing.case_urn` (§4), matching legacy exactly rather than diverging from it.
- **"Appellant"/"Respondent"/"Applicant"/"Defendant" wording *is* a real, computed, persisted
  field — correction to an earlier draft of this doc, which wrongly called it template-only.**
  `cpp-context-azure-legalaidagency`'s own JSON model carries no such field, but
  `cpp-context-progression` computes one and persists it as part of the
  `prison-court-register-recorded` event, before any downstream template is involved. The literal
  source (not paraphrased — see the correction below) is two separate methods:

  `PrisonCourtRegisterHandler.handleAddPrisonCourtRegister`, lines 57-65 (outer default and gate):
  ```java
  String defendantType = "Defendant";
  final UUID courtApplicationId = getCourtApplicationId(prisonCourtRegisterDocumentRequest);
  if (nonNull(courtApplicationId)) {
      final CourtApplication courtApplication = applicationAggregate.getCourtApplication();
      defendantType = getDefendantType(prisonCourtRegisterDocumentRequest, courtApplication);
  }
  ```
  `PrisonCourtRegisterHandler.getCourtApplicationId`, lines 78-86 — reads only the **first**
  entry of the defendant's `prosecutionCasesOrApplications` list:
  ```java
  return prisonCourtRegisterDocumentRequest.getDefendant()
          .getProsecutionCasesOrApplications().get(0).getCourtApplicationId();
  ```
  `PrisonCourtRegisterHandler.getDefendantType`, lines 149-166 (inner decision, only reached when
  a `courtApplicationId` was found above):
  ```java
  String defendantType = "Applicant";
  if (nonNull(courtApplication.getApplicant().getMasterDefendant())) {
      defendantType = "Applicant";
      if (courtApplication.getType().getAppealFlag() && courtApplication.getType().getApplicantAppellantFlag()) {
          defendantType = "Appellant";
      }
  } else {
      if (courtApplication.getRespondents().stream()
              .filter(respondent -> nonNull(respondent.getMasterDefendant()))
              .anyMatch(respondent -> respondent.getMasterDefendant().getMasterDefendantId()
                      .equals(prisonCourtRegisterDocumentRequest.getDefendant().getMasterDefendantId()))) {
          defendantType = "Respondent";
      }
  }
  ```
  **Correction — an earlier draft of this section paraphrased this from an agent summary and got
  it wrong in three ways**, caught only by reading the literal source directly (not by matching
  against PDF output, which can't distinguish these):
  1. There are **two independent fallbacks**, not one: `"Defendant"` (fixed literal) when the
     defendant has *no* court application at all; `"Applicant"` (the inner method's own default)
     when a court application exists but neither the applicant nor any respondent branch matches.
  2. `getCourtApplicationId` only inspects the defendant's **first** `prosecutionCasesOrApplications`
     entry — order-dependent. A defendant whose first entry is a prosecution case and whose
     *second* is a court application gets `"Defendant"`, not a computed application-based type.
  3. The applicant branch (`nonNull(courtApplication.getApplicant().getMasterDefendant())`) never
     compares *whose* `masterDefendantId` it is — unlike the respondent branch, which explicitly
     does (`.equals(...getDefendant().getMasterDefendantId())`). It trusts that if the applicant
     has any `masterDefendant` at all, it belongs to the defendant this call is already scoped to.
  This behaviour is ported **as-is**, quirks included — not "fixed" or reinterpreted — per this
  repo's convention for faithfully porting legacy logic (`shared-code-rules.md`).

  Confirmed against §1's PDF evidence only as a secondary sanity check, not as the source of the
  rule: that hearing's `courtApplication.applicant` was the prosecuting authority (no
  `masterDefendant`), the defendant was in `respondents[]` instead, giving `"Respondent"` — matches
  the PDF. The PDF alone could not have revealed points 1-3 above; only the source could.

  This needs `courtApplication.applicant` and `courtApplication.respondents[]` — fields this
  repo's domain model deliberately does **not** parse today (only `subject`, per the very
  scope-note comment cited above) — see §3/§4 for the model additions this now requires.

## 3. Two gaps this fixes, both confirmed against real code

**Gap A — `resolveActiveAt` and `persist()` assume `prosecutionCases` is never null.**
```java
// ResultsIngestionService, current main
private LocalDate resolveActiveAt(final HearingDetail hearing, final UUID hearingId) {
    return hearing.getProsecutionCases().stream()   // NPE — null for an application-only hearing
        ...
}
private void persist(...) {
    ...
    hearing.getProsecutionCases().forEach(...)       // NPE, same reason
}
```
Both need to also scan `hearing.getCourtApplications()` when `prosecutionCases` is null/empty.

**Gap B — the domain model doesn't capture what a standalone application party needs.**
`ApplicationParty`/`MasterDefendant` today carry only `masterDefendantId` — no `defendantId`, no
`personDefendant`. This was a deliberate choice (see the comment on `CourtApplication` in
`HearingDetailsResponse.java`) because the only prior use case was *linking* to a defendant already
reached via a prosecution case. §1/§2 confirm a standalone use case is real, so the model needs:
- `MasterDefendant.personDefendant` (`PersonDefendant`) — the real CP payload already carries this;
  just not deserialized yet.
- `MasterDefendant.defendantCase` (new, `List<DefendantCase>`: `caseId`, `caseReference`,
  `defendantId`) — needed to recover a genuine per-case `defendantId` (§4), which
  `masterDefendantId` alone cannot provide (see repo rule: one physical defendant can have several
  `defendantId`s, one per case, sharing one `masterDefendantId`).
- `CourtApplicationCase.prosecutionCaseId` (new) — the real payload carries this alongside
  `offences`; needed to match the right `defendantCase` entry when a party is linked to more than
  one case (§4). Case URN itself comes from `applicationReference` directly (§4), not from here.
- `CourtApplication.applicant` (new, `ApplicationParty` — reuse the existing type, it's already
  optional-`masterDefendant`-shaped) and `CourtApplication.respondents` (new,
  `List<ApplicationParty>`), plus `ApplicationType.appealFlag`/`.applicantAppellantFlag` (new,
  both `Boolean`) — needed to replicate Progression's `defendantType` computation (§2/§4).
  `legalaidagency`'s own `applicant`/`respondents[]` "not in this service's scope" comment was
  written for *its* defendant-linkage use case, which genuinely doesn't need them — it does not
  apply to computing `defendantType`, a different concern this design now also covers.

## 4. What changes

| Component | Change |
|---|---|
| `domain/HearingDetailsResponse.MasterDefendant` | add `personDefendant` (`PersonDefendant`), `isYouth` (`Boolean`), `defendantCase` (`List<DefendantCase>`, new type: `caseId`, `caseReference`, `defendantId`) |
| `domain/HearingDetailsResponse.CourtApplicationCase` | add `prosecutionCaseId` (`String`) — used only for `defendantId` matching (§4), not for case URN |
| `domain/HearingDetailsResponse.CourtApplication` | add `applicant` (`ApplicationParty`), `respondents` (`List<ApplicationParty>`) |
| `domain/HearingDetailsResponse.ApplicationType` | add `appealFlag`, `applicantAppellantFlag` (both `Boolean`) |
| `mappers/CPHearingResultEntityMapper` | new `applicationOnlyDefendant(CourtApplication)` — builds a `Defendant` from `subject.masterDefendant`, with `offences = List.of()` (the existing `matchingCourtApplications`/linked-offence machinery already supplies real content once `masterDefendantId` matches, so nothing to duplicate); `defendantId` resolved by matching `defendantCase[].caseId` against the application's `courtApplicationCases[].prosecutionCaseId` (falls back to the sole `defendantCase` entry if there's exactly one) |
| `mappers/CPHearingResultEntityMapper` | new `defendantType(CourtApplication, masterDefendantId)` — ports `getDefendantType` verbatim (§2): `applicant.masterDefendant` present (no equality check, per point 3 above) → `"Applicant"`/`"Appellant"` (per `appealFlag && applicantAppellantFlag`); else a `respondents[]` entry's `masterDefendantId` equals this defendant's → `"Respondent"`; else `"Applicant"` (inner default). Called only from the `applicationOnlyDefendant` path — every prosecution-case-driven defendant gets the literal `"Defendant"` instead, passed by the caller (§6 resolution) |
| `entities/CPVersionEntity` + new Flyway migration | new `defendant_type` column (nullable `varchar`), set on every write — literal `"Defendant"` for prosecution-case-driven defendants, computed value for application-only ones |
| `mappers/CPHearingResultEntityMapper.toCaseHearingEntity`/`toCaseMarkerEntities` | change signature from `(ProsecutionCase, ...)` to `(String caseUrn, ...)` — case markers become `List.of()` for an application-derived case (CP models case markers only against `ProsecutionCase`, never `CourtApplication`) |
| `repositories/CPEntityPersistenceService.findOrCreateCaseHearing` | same signature change — `caseUrn` extracted by the caller, not read off a `ProsecutionCase` internally |
| `services/ingestion/ResultsIngestionService.resolveActiveAt` | also scan `courtApplications[].courtApplicationCases[].offences[].judicialResults[]` and `courtApplications[].judicialResults[]` when `prosecutionCases` is null/empty |
| `services/ingestion/ResultsIngestionService.persist` | after the existing `prosecutionCases` loop, also process each `courtApplication` whose `subject.masterDefendant` exists **and** whose `masterDefendantId` isn't already covered by a `prosecutionCases` defendant (avoids double-processing a defendant reached both ways) |

**Case URN resolution for an application-only defendant**: `cp_case_hearing.case_urn` =
`courtApplication.applicationReference`, matching legacy exactly (§2) — no fallback logic needed,
since every court application carries its own `applicationReference` unconditionally (unlike the
nested `courtApplicationCases[].prosecutionCaseIdentifier.caseURN`, which is genuinely absent on
some application types). `CourtApplicationCase.prosecutionCaseIdentifier` is therefore **not**
needed and dropped from §3/§4's model changes — only `prosecutionCaseId` is still needed, for
`defendantId` matching (below).

## 5. What does *not* change

- `CPEntityPersistenceService.persist(Defendant, ...)` — already takes a `Defendant` and
  `HearingDetail`, no `ProsecutionCase` dependency. Works unchanged once handed a
  `Defendant` built from `subject.masterDefendant`.
- `CPVocabularyService.compute`, `CPResultsPcrFilter.isPrisonCourtRegisterRequired`,
  `CPHearingResultEntityMapper.eligibleResults`/`toWriteBundle` — all already operate generically
  on `Defendant` + `HearingDetail`, matching court applications by `masterDefendantId` regardless
  of how the `Defendant` object was constructed. No changes needed.
- The one-PCR-record-per-`(hearingId, defendantId)` rule — unaffected. An application-only
  defendant still gets exactly one `cp_version` row, keyed by the real `defendantId` resolved in
  §4, not by `masterDefendantId`.

## 6. Open items

- **Multiple `defendantCase` entries with no matching `courtApplicationCases[].prosecutionCaseId`**
  (party linked to several cases, application not linked to any of them) — `defendantId`
  resolution in §4 has no rule for this. Needs a real fixture before deciding; likely "skip this
  application" rather than guessing.
- Not covered by AMP-1070 or this design: the domain-model extensions in §3 reverse a documented
  scope note (`HearingDetailsResponse.java`'s comment on `CourtApplication`). Once this design is
  agreed, that comment needs updating alongside the code change, not left contradicting it.
- **`defendantType` has nowhere to live yet.** `CPVersionEntity`/`CPCourtApplicationEntity` have
  no column for it — needs a new Flyway migration. More significantly, the `api-cp-crime-results-pcr`
  contract (a separate repo) has no field for it either — `GET /pcr` can't expose it without a
  spec change and version bump there, reviewed and released independently of this design. This
  design computes and persists `defendantType` (§4); exposing it on the read API is a follow-on,
  not assumed done here.
- ~~`defendantType` computation is order-dependent on Progression's own flattened
  `prosecutionCasesOrApplications` list~~ **Resolved.** `legalaidagency`'s own
  `ProsecutionCaseOrApplicationMapper.build()` (lines 15-18) always concatenates prosecution-case
  entries before court-application entries:
  ```js
  build() {
      const associatedProsecutionCases = this.getAssociatedProsecutionCases();
      const associatedCourtApplications = this.getAssociatedCourtApplications();
      return associatedProsecutionCases.concat(associatedCourtApplications);
  }
  ```
  Since Progression's `getCourtApplicationId` (§2) only ever checks index `[0]`, **any defendant
  with at least one prosecution case always has that case at index 0** — `courtApplicationId` is
  therefore always absent for them, and `defendantType` always stays the outer literal
  `"Defendant"`, regardless of any court application they're also linked to. Progression's own
  code guarantees `getDefendantType` only ever fires for a defendant with **zero** prosecution
  cases — exactly this design's application-only scope, never the existing enrichment case. No
  divergence needed: this design computes `defendantType` only in `applicationOnlyDefendant`'s
  path (§4); every prosecution-case-driven defendant gets the literal `"Defendant"`, matching
  Progression exactly.
- **Docmosis/template-level logic wasn't ruled out entirely.** Progression sends the computed
  `defendantType` string onward via a `systemdocgenerator.generate-document` command referencing
  template `OEE_Layout5` — the actual template lives in a separate `systemdocgenerator` context
  repo, not inspected. Confirmed: Progression computes and persists the label itself before that
  handoff (§2), so the template can't be the sole source — but whether the template does anything
  further with it (or with any other field) wasn't checked.
