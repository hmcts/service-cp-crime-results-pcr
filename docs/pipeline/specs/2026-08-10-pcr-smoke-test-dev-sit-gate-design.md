# PCR smoke-test automation: dev → SIT release gate

Status: implemented (branch `docs/pcr-smoke-test-dev-sit-gate-design`) — pending real dev/sit
secrets and poll-timing tuning against a live environment (§8)
Date: 2026-08-10
Author: Srivani Muddineni (with Claude)

## 1. Origin and relationship to the blanket pattern

This implements, for `service-cp-crime-results-pcr`, the "Smoke Testing Framework — API
Marketplace" pattern (Confluence draft, checked in verbatim at
`service-cp-crime-hearing/docs/pipeline/specs/2026-07-03-smoke-testing-framework-confluence-draft.md`).
That pattern is Karate-based, create-then-check, Setup/Run split, and covers Phase 1 (dev) and
Phase 2 (sit); PRP/PRD are explicitly out of scope for this round (the doc's own "Decision" section
rules out PRD test-data creation, and PRP is a later round).

**`service-cp-crime-hearing` is a partial reference, not a finished one.** Reading its actual code
(not just the doc) confirms:

- Built and working: `gradle/smoketest.gradle` (`smokeTestSetup`/`smokeTestRun` Gradle tasks),
  Karate feature files, `karate-config.js`, `smoke-utils.js` (writes/reads a flat
  `build/smoke-test-config/<env>.json`, falling back to a checked-in `case-config/<env>.json` on
  the classpath for tiers with no Setup step).
- **Not built anywhere yet**: no workflow calls `smokeTestSetup`/`smokeTestRun`, no release gate, no
  versioning-scheme change. `grep -i smoke .github/workflows/*.yml` in that repo returns nothing.

This design covers both halves for PCR: the Karate scaffold (following the proven half of the
pattern) **and** the CI wiring (which nothing in this workspace has done yet, for any service).

## 2. Scope

**In scope:**
- Karate smoke-test scaffold in `service-cp-crime-results-pcr`, mirroring
  `service-cp-crime-hearing`'s Setup/Run split and Gradle task shape.
- Real CI wiring in `ci-build-publish.yml`: Deploy-Dev → readiness wait → smoke test → auto-release
  → SIT deploy (existing trigger) → readiness wait → smoke test again.
- A hard, technically-enforced gate: if the dev smoke test fails, no release is created and SIT
  deployment is unreachable (not just a red pipeline a human is expected to notice).

**Out of scope (this round):**
- PRP/PRD. No changes to their (non-existent) config or pipeline.
- Any change to `pcr-eventgrid-relay-function`. This design *depends on* that repo's Event Grid →
  webhook path working, but makes no code changes there.
- The proposal doc's "compute version once, one image dev→sit, no retag" versioning scheme. PCR
  keeps its current scheme: `draft_version` (git-sha-suffixed) built and deployed to dev, a
  separate `release_version` rebuilt/retagged/pushed to GHCR for the release that deploys to SIT —
  same as every other `service-cp-*` repo today. The proposal doc itself flags the new scheme as
  "not decided yet"; changing it is a bigger, cross-repo decision this round doesn't take.

## 3. Why Setup uses the real Event Grid path, not a direct webhook call

The PCR service's ingestion (`ResultsIngestionService.ingestAndPersist`) does **not** build the
persisted record from the webhook request body — that body only carries `hearingId` + `hearingDay`.
Real case/defendant/disposal data comes from a Redis-first / REST-fallback lookup
(`results-query-api`'s `hearingDetails/internal/{hearingId}`), which only returns non-empty
`prosecutionCases` once the hearing has genuinely been resulted on the CP backend. So creating a
queryable PCR record always requires driving a real result onto the CP backend first, regardless of
how ingestion is triggered afterward.

Given that, there were two ways to trigger ingestion once a real result exists:

1. **POST directly to `/internal/hearing-results`** — deterministic, isolates "does PCR's own
   ingestion+query work" from "is Event Grid/relay-function plumbing correct in this tier."
2. **Let the real `Hearing_Resulted` Event Grid event flow through `pcr-eventgrid-relay-function`
   naturally** — slower and coupled to infrastructure this repo doesn't own, but proves the actual
   production path end-to-end, which is exactly what "works end-to-end in dev, only then promote to
   SIT" means.

**Decision: option 2.** The explicit intent for this gate is "does the whole pipeline actually work
post-deploy," including the Event Grid subscription and relay function — not just the PCR service in
isolation. Setup therefore never calls the internal webhook; it only drives a real result onto the
CP backend and lets production infrastructure do the rest. Run polls for eventual arrival (§5).

**Accepted trade-off:** a red gate can mean "PCR is fine, but Event Grid/relay-function wiring is
wrong" — indistinguishable, from the gate's result alone, from a real PCR regression. This is a
deliberate choice, not an oversight — surfacing that class of failure is the point.

## 4. Setup step (`smokeTestSetup`)

A REST-only chain (no browser, no manual webhook call):

1. Generate a `caseUrn` locally (same helper approach as `service-cp-crime-hearing`'s smoke test).
2. `POST stagingprosecutorsspi-service/CJSEService` — SPI-IN SOAP case+hearing creation, reusing a
   `spi-in-minimal.xml`-style fixture with the generated `caseUrn`.
3. Resolve the server-assigned `hearingId` and `defendantId` for that `caseUrn` via a query call
   (mirrors `cpp-ui-e2e-serenity`'s `getHearingIDOfSpecificDefendantHearing` pattern) — SPI-IN's SOAP
   response doesn't hand these back directly.
4. `POST .../hearing-command-api/command/api/rest/hearing/hearings/{hearingId}`,
   `Content-Type: application/vnd.hearing.update-plea+json` — enter a GUILTY plea.
5. Same path + `hearingDay`, `Content-Type: application/vnd.hearing.save-draft-result-v2+json` —
   save an IMP (custodial) result: `resultLines[]` with `resultDefinitionId`, `orderedDate`,
   custodial-period/prison/probation-team prompts. Reuse the fixture shape proven in
   `cpp-apitests`' `HearingResultsDocumentSubscriptionIT` (`HearingHelper.createShareResults`,
   `draftresultsV2/DRAFT_RESULT_V2_IMP_DURATION_IS_GREATERTHAN_999.json`).
6. Same path, `Content-Type: application/vnd.hearing.shared-results+json` — finalise and share,
   publishing `public.hearing.resulted`. This is the real trigger for the downstream chain: results
   viewstore population → (existing, out-of-repo mechanism) Event Grid `Hearing_Resulted` →
   `pcr-eventgrid-relay-function` → PCR's `/internal/hearing-results`.
7. **Superseded (§8): dropped, not implemented.** Setup does not seed a `now-subscriptions` entry —
   dev/SIT are assumed to already carry a standing PCR subscription for the smoke test's chosen
   court/offence combination (confirmed decision; no `now-subscriptions` creation payload exists
   anywhere in the workspace to adapt from, and this avoids inventing one from scratch).
8. Write `build/smoke-test-config/<env>.json`:
   ```json
   {
     "environment": "dev",
     "caseUrn": "...",
     "hearingId": "...",
     "defendantId": "...",
     "source": "automated-setup",
     "lastConfirmedDate": "2026-08-10",
     "notes": ""
   }
   ```
   Flat shape, matching what `service-cp-crime-hearing` actually ships — **not** the doc's example
   nested `lookupKey` object, which was never implemented that way. Extended with `hearingId` and
   `defendantId` because PCR's query endpoint has a 3-part composite key
   (`caseURN`/`hearingId`/`defendantId`), unlike the hearing service's single `caseUrn`.

Step 3, 5 and 7's exact fixtures/payloads are implementation-time work, not fully specified here —
see "Open items" (§8).

## 5. Run step (`smokeTestRun`)

Reads `smoke-test-config/<env>.json` (written by Setup on dev/sit — no fallback to a checked-in
config exists yet since PRP/PRD are out of scope). Because ingestion is now asynchronous (real Event
Grid delivery + relay-function forward + PCR's own internal 3-retry/backoff on an incomplete
`hearingDetails` response), Run cannot be a single GET:

```
Given url tokenUrl ... (Entra client-credentials, same as the hearing-service example)
When method post
Then status 200
* def accessToken = response.access_token

* retry until responseStatus == 200
Given url serviceBaseUrl + '/cases/' + caseUrn + '/hearings/' + hearingId + '/defendants/' + defendantId
And header Authorization = 'Bearer ' + accessToken
And header Ocp-Apim-Subscription-Key = apimSubscriptionKey
When method get

And match response.caseURN == caseUrn
And match response.hearingId == hearingId
```

Bounded retry (e.g. every 10s, several-minute cap — exact values are implementation-time tuning, see
§8) so it fails with a clear "timed out waiting for ingestion" distinct from a genuine 4xx/5xx.

## 6. CI wiring

Extends `ci-build-publish.yml`'s existing job graph (not a new workflow file):

```
push to main (ci-draft.yml)
  Artefact-Version → Build → Provider-Deploy → Build-Docker → Trigger-ACR-Copy → Wait-ACR-Copy
    → Deploy-Dev (existing, action-ado-deploy wait:false)
    → [NEW] Wait-Dev-Ready   (poll health endpoint until it responds)
    → [NEW] Smoke-Test-Dev   (./gradlew smokeTestSetup smokeTestRun, dev env vars)
    → [NEW] Auto-Release     (needs: Smoke-Test-Dev)
         │
         └─ gh release create → triggers ci-released.yml (existing trigger, unchanged)
                                   Artefact-Version(release) → Build → Provider-Deploy
                                   → Build-Docker (new release_version tag) → ACR copy
                                   → Deploy-Sit (existing)
                                   → [NEW] Wait-Sit-Ready
                                   → [NEW] Smoke-Test-Sit (smokeTestSetup smokeTestRun, sit env vars)
```

If `Smoke-Test-Dev` fails, `Auto-Release` never runs (`needs:` dependency) — no release, no SIT
deploy reachable. `Smoke-Test-Sit` is not itself a gate on anything further this round (Phase 2: "if
it passes, that's as far as this round of automation goes").

**Superseded (§9): `Auto-Release` is no longer fully automatic.** A green `Smoke-Test-Dev` is
necessary but not sufficient to reach SIT — a human QA approval is required in between. See §9.

**Readiness gap, found while reading the current pipeline, not in the source proposal:**
`Deploy-Dev`/`Deploy-Sit` call `hmcts/action-ado-deploy@v1` with `wait: false` — the job returns once
the GitOps deploy is *triggered*, not once the pod is actually rolled out. Nothing depended on
completion before; a smoke test job placed right after would risk a false pass (hitting the old pod)
or false fail (hitting a not-yet-ready one). Fix: add a local `Wait-Dev-Ready`/`Wait-Sit-Ready` step
that polls the service's health endpoint with a bounded timeout, rather than changing
`action-ado-deploy`'s `wait` semantics (shared across other repos — riskier to touch).

**Env vars / secrets** (per tier, mirroring the hearing-service `karate-config.js` pattern):
`SMOKE_SERVICE_BASE_URL`, `SMOKE_HEALTH_CHECK_URL`, `SMOKE_ENVIRONMENT`, `CP_BACKEND_URL`,
`CJSCPPUID`, `SMOKE_ENTRA_TENANT_ID`/`SMOKE_ENTRA_CLIENT_ID`/`SMOKE_ENTRA_CLIENT_SECRET`/
`SMOKE_ENTRA_SCOPE`, `SMOKE_APIM_SUBSCRIPTION_KEY`.

**Superseded (§8): dev tier only uses the existing "dev" GitHub Environment** (same mechanism
already used by `Artefact-Version`/`Build`, `environment: name: dev`) — **SIT tier uses plain
repo-level secrets with a `SIT_` prefix instead of a dedicated "sit" Environment** (confirmed
decision, deviates from this section's original per-tier-Environment proposal).

**Auto-Release changelog — a real conflict with the existing `/release` skill, not a detail:** the
`/release` skill is LLM-mediated (reads PR bodies, synthesises plain-English changelog prose) with an
explicit human-confirmation gate before creating anything — it cannot run inside a GitHub Actions
job as-is. `Auto-Release` instead uses a simpler, deterministic changelog: list merged PR
titles/links since the last tag, apply the same dependabot/chore/docs/`bump X from Y to Z` filter the
skill uses, default to a patch bump. This is mechanically equivalent but reads as a bare list rather
than the skill's curated prose. **Accepted trade-off, confirmed with the user** — the `/release`
skill stops being the normal promotion path for this repo; it remains available for manual
minor/major bumps or off-cycle releases.

## 7. Error handling

Mirrors the query endpoint's own documented contract (200/etc., unchanged by this work), plus new
smoke-test-specific failure modes to distinguish in logs/CI output:
- SPI-IN or hearing-command-api call failure (bad fixture/environment misconfiguration) — fails
  Setup immediately, before Run ever attempts to poll.
- Run poll-timeout (ingestion never arrived within the bounded window) — could mean a PCR regression
  or an Event Grid/relay-function wiring problem in that tier (§3's accepted trade-off).
- Wait-Dev-Ready/Wait-Sit-Ready timeout — dev/sit never came up; fails before Setup runs at all.

## 8. Open items

Resolved during implementation:

- Fixture payloads for plea/draft-result/shared-results — ported from `cpp-apitests`'
  `HearingHelper`/`draftresultsV2`/`newSharedResults` fixtures into
  `src/smokeTest/resources/fixtures/`, with named result-line placeholders (`IMP_LINE_ID` etc.
  instead of positional `RESULT_LINE01_ID`) so the same generated UUID threads through both the
  draft and shared payloads for the same underlying result, and fabricated `example-prison.gov.uk`/
  `example-probation.gov.uk` domains in place of the source fixtures' real `justice.gov.uk`
  addresses (HMCTS data-classification rule).
- ID resolution — `cpp-apitests`' `CaseHelper.createSpiCaseWithHearing` chain, not
  `getHearingIDOfSpecificDefendantHearing` (that method doesn't exist in this workspace): poll
  `prosecutioncasefile-query-api/.../cases?prosecutionCaseReference={urn}` for `caseId`, then
  `progression-query-api/.../prosecutioncases/{caseId}` for `hearingId`/`defendantId`/`offenceId`,
  then `hearing-query-api/.../hearings/{hearingId}` for `hearingDay`.
- now-subscriptions seed — decided against seeding at all; Setup assumes dev/SIT already carry a
  standing PCR subscription for the smoke test's court/offence combination.
- SIT smoke-test secrets — plain repo-level secrets with a `SIT_` prefix, not a dedicated "sit"
  GitHub Environment (confirmed decision, deviates from this doc's original §6 proposal).
- Auto-Release changelog script — `.github/scripts/generate-release-notes.sh`.

Still open (needs a live dev/sit environment to resolve, not guessable upfront):

- Poll interval/timeout for Run (§5) and Wait-Dev-Ready/Wait-Sit-Ready (§6) — currently 10s/30
  attempts and 20s/30 attempts respectively, unvalidated against real ingestion/rollout timing.
- The actual value of `SMOKE_HEALTH_CHECK_URL`/`SIT_SMOKE_HEALTH_CHECK_URL` — the APIM-fronted
  `SMOKE_SERVICE_BASE_URL` likely doesn't expose `/actuator/health` through the gateway, so the
  readiness wait needs its own ops-provided internal URL; not something this repo can determine.
- Every `SMOKE_*`/`SIT_SMOKE_*` secret still needs populating in GitHub before the gate can run
  for real — nothing in this repo's code can create them.

## 9. QA approval gate before SIT promotion (2026-08-19 addendum)

**Decision: a green `Smoke-Test-Dev` is necessary but no longer sufficient to reach SIT.** §6's
`Auto-Release` fired automatically the moment `Smoke-Test-Dev` passed, with no human in the loop
before a release — and therefore a SIT deploy — was created. That's now an explicit manual QA gate
instead of a fully automatic promotion.

**Mechanism: a GitHub Environment with required reviewers**, not a custom approval step. The
`Auto-Release` job now declares `environment: { name: sit-release-approval }`. When a job targets an
environment with a `required_reviewers` protection rule, GitHub Actions holds that job at "Waiting"
in the Actions UI — it does not run `gh release create` (or anything else in the job) until one of
the configured reviewers approves it there. No new code, no polling, no bespoke sign-off tooling —
this is the same `environment:` mechanism the `dev` tier already uses for `Wait-Dev-Ready`/
`Smoke-Test-Dev`, just with a protection rule attached instead of none.

**Why gate the release itself, not the SIT deploy inside `ci-released.yml`:** holding `Auto-Release`
means that until QA approves, no GitHub Release exists at all — nothing SIT-related is even
reachable, and a rejected/still-pending approval leaves zero trace in Releases. The alternative
(create the release immediately, hold `Deploy-Sit` inside `ci-released.yml` instead) would mean a
release can sit in GitHub with SIT not yet deployed, which is a more confusing state to reason
about. Confirmed decision.

**Reviewers are configured on the environment, not in this doc.** The `sit-release-approval`
environment's required-reviewers list is set via the GitHub API/UI (Settings → Environments), not
hardcoded here — this doc describes the mechanism, and reviewer membership is expected to change
over time without needing a doc update. Any repo admin can add/remove reviewers.

**Trade-off accepted:** `Smoke-Test-Sit` (§6) still runs unconditionally once `Deploy-Sit` fires —
it was never itself a promotion gate (Phase 2's "if it passes, that's as far as this round of
automation goes," §6), and that's unchanged. This addendum only inserts a human checkpoint between
dev and SIT; it does not add a second approval between SIT and anything further, since nothing
further exists yet in this pipeline.