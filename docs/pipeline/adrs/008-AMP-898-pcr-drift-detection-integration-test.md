# 008. Drift detection as a parameterized integration test, not an apiTest

**Status:** Accepted, 31 Jul 2026
**Jira:** AMP-898 — implement the drift detection mechanism design doc §9 specified but never built

## Context

`docs/designs/2026-07-16-pcr-api-marketplace-design-v2.md` §9 ("Drift detection via Integration
test suite") already settled the high-level strategy: pick real hearings that have both a
`Hearing_Resulted` occurrence and a generated PDF, feed the resulting payload through this
service's real code path, assert the output matches the PDF's content — run once before launch,
then on an ongoing (e.g. nightly) cadence after. That decision is not reopened here.

What wasn't yet decided: the concrete test mechanism. This service already has several unused
fixture directories at `src/test/resources/pcr-*/` — each an input JSON paired with one or more
PDFs generated from it — that are exactly the raw material design doc §9 calls for, but nothing
in `src/test` or `apiTest` currently reads them. `pcr-multiple-defendants-multiple-offences` is
the first one being wired up, so this ADR fixes the pattern the rest will follow.

`pcr-multiple-defendants-multiple-offences` has 3 defendants but only 2 PDFs — the third
(`9cd40664-9497-4334-8caf-bdf0ac44ac9f`) never got a PCR generated for it. That's a second
correctness signal beyond field mapping: the generation gate excluding that defendant is itself
part of what "matches Progression's stored output" means, not just the two defendants who did
get a document.

## Decision

- **Integration test, not `apiTest`.** `apiTest` in this repo is docker-compose + WireMock,
  scoped to happy-path HTTP-surface coverage (`hmcts-standards.md`'s test-pyramid rule). Drift
  detection needs deep field-by-field payload equivalence per hearing, which belongs in this
  repo's own `src/test` integration suite alongside `HearingResultedIngestionE2EIntegrationTest`
  — the existing e2e test already drives the exact same real path (Redis seed → webhook POST →
  `GET /pcr`) this needs, just without a stored-answer diff.
- **New test class `PcrDriftDetectionIntegrationTest`**, extending the existing
  `IngestionE2ETestBase`. One `@ParameterizedTest` scans every subdirectory under
  `src/test/resources/drift-detection/` — adding a new historical hearing later is "add a
  folder," not new Java, since this pattern is meant to grow past this one fixture.
- **Fixture layout per hearing folder:**
  ```
  drift-detection/<hearing-name>/
    event.json                 # the real hearing payload (Redis-seed input)
    now-subscriptions.json     # WireMock stub for the generation-gate lookup
    expected/
      <defendantId>.json       # exact expected GET /pcr response body for that defendant
    reference/
      <original>.pdf           # kept for provenance / re-verification, not read by the test
  ```
  `hearingId`/`hearingDay`/`caseURN` are parsed out of `event.json` itself, not hardcoded
  constants — required once one test method covers every fixture.
- **`expected/<defendantId>.json` is bootstrapped from a real run, then verified against the
  PDF** — not typed from scratch. Running the real ingestion path once and capturing its actual
  `GET /pcr` output, then checking every value against the archived PDF text field-by-field, is
  less error-prone than hand-authoring JSON blind and gives the same result once verified.
- **A defendant with no corresponding PDF gets `expected/<defendantId>.json` containing `[]`** —
  asserting the generation gate correctly excluded them is as much a drift-detection concern as
  the field mapping is.
- **Diff is `JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT)`** — full
  structural equality, no ignore-list. Confirmed safe: `PcrResultsMapper` never surfaces any of
  `cp_version`'s DB-generated columns (`cpVersionPk`, `eventId`, `createdAt`, `expiresAt`) into
  the `PcrHearingResult` DTO, so every field in the response is deterministic from the input
  fixture — there is no volatile field an ignore-list would need to cover.
- **Test class, package, and fixture root all use "drift detection" naming** — `PcrDriftDetectionIntegrationTest`,
  `driftdetection` package, `src/test/resources/drift-detection/`.
- **Runs in the existing `src/test` suite** against real Postgres/Redis (`docker compose up -d
  postgres redis`), same infra this repo's other integration tests already require — not a new
  `apiTest`/docker-compose project. Matches design doc §9's before-launch one-time check plus
  after-launch nightly cadence with no new CI wiring needed beyond what already runs `./gradlew
  test`.

## Consequences

- `PcrDriftDetectionIntegrationTest` becomes the reusable home for every future
  drift-detection fixture — the existing untouched `pcr-*` directories can be migrated into
  `drift-detection/` incrementally, each one just adding `now-subscriptions.json` +
  `expected/*.json` alongside the JSON/PDF pair it already has.
  `pcr-multiple-defendants-multiple-offences` is the first, not the only, fixture this pattern
  is meant to serve.
- No new test dependency — `org.skyscreamer:jsonassert` already ships transitively via
  `spring-boot-starter-test`.
- Bootstrapping `expected/*.json` from a real run means a genuine regression in mapping logic,
  introduced *before* the fixture is first captured, would be captured as correct. This is an
  accepted limitation of capturing an expected file from a real run after the fact — the PDF
  cross-check at capture time is the mitigation, not a guarantee.

## Alternatives considered

- **`apiTest`/docker-compose + WireMock** — rejected; scoped to happy-path HTTP coverage per the
  test-pyramid rule, and this needs full-body structural diffing per hearing, not endpoint-level
  smoke coverage.
- **One hand-written test method per fixture**, matching `HearingResultedIngestionE2EIntegrationTest`'s
  style — rejected; doesn't scale as more historical hearings are added, and duplicates the same
  seed/POST/GET/diff shape for every fixture with only the file names changing.
- **`JSONAssert` with an ignore-list for volatile fields** — rejected as unnecessary once
  `PcrResultsMapper` was confirmed to never surface DB-generated fields; an ignore-list with
  nothing to ignore is dead configuration.