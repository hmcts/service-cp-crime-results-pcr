# PCR dev smoke-test release gate design

**Jira:** AMP-1051. See
[`docs/pipeline/adrs/010-AMP-1051-pcr-dev-smoke-test-release-gate.md`](../pipeline/adrs/010-AMP-1051-pcr-dev-smoke-test-release-gate.md)
for the decision this design implements.

## 1. Setup step (`smokeTestSetup`)

A REST-only chain (no browser, no manual webhook call):

1. Generate a `caseUrn` locally.
2. `POST stagingprosecutorsspi-service/CJSEService` — SPI-IN SOAP case+hearing creation
   (`spi-in-minimal.xml` fixture), submitted under an identity with `CJSE` group membership.
3. Poll `prosecutioncasefile-query-api/.../cases?prosecutionCaseReference={urn}` for `caseId`,
   then `progression-query-api/.../prosecutioncases/{caseId}` for `hearingId`/`defendantId`/
   `offenceId`, then `hearing-query-api/.../hearings/{hearingId}` for `hearingDay` — SPI-IN's SOAP
   response doesn't hand these back directly, and CP's query APIs are eventually consistent, so
   each poll retries (10 attempts, 5s interval).
4. `POST .../hearing-command-api/command/api/rest/hearing/hearings/{hearingId}`,
   `Content-Type: application/vnd.hearing.update-plea+json` — enter a GUILTY plea.
5. Same path + `hearingDay`, `Content-Type: application/vnd.hearing.save-draft-result-v2+json` —
   save an IMP (custodial) result.
6. Same path, `Content-Type: application/vnd.hearing.shared-results+json` — finalise and share.
   This is the real trigger for the downstream chain: results viewstore population → Event Grid
   `Hearing_Resulted` → `pcr-eventgrid-relay-function` → PCR's `/internal/hearing-results`.
7. Does not seed a `now-subscriptions` entry — assumes dev already carries a standing PCR
   subscription for the smoke test's court/offence combination.
8. Write `build/smoke-test-config/<env>.json` (`environment`, `caseUrn`, `hearingId`,
   `defendantId`, `source`, `lastConfirmedDate`, `notes`) for Run to read.

Steps 4-6 require different `CJSCPPUID` group membership than SPI-IN — `Listing Officers`/
`Court Clerks`/`Legal Advisers`/`Court Associate`/`Court Administrators`, confirmed against
`cpp-context-hearing`'s access-control rules. One identity can satisfy both sets of groups if
granted them explicitly in dev's user-groups data; the smoke test doesn't need to juggle separate
identities per step.

## 2. Run step (`smokeTestRun`)

Reads `smoke-test-config/<env>.json` (written by Setup). Because ingestion is asynchronous (real
Event Grid delivery + relay-function forward + PCR's own internal completeness retry), Run cannot
be a single GET:

```
Given url tokenUrl ... (Entra client-credentials)
When method post
Then status 200
* def accessToken = response.access_token

* configure retry = { count: 30, interval: 10000 }
* retry until responseStatus == 200 && response[0] != null
Given url serviceBaseUrl + '/cases/' + caseUrn + '/hearings/' + hearingId + '/defendants/' + defendantId
And header Authorization = 'Bearer ' + accessToken
And header Ocp-Apim-Subscription-Key = apimSubscriptionKey
When method get

And match response[0].prosecutionCase.caseURN == caseUrn
And match response[0].hearing.id == hearingId
```

A bounded retry (10s interval, 30 attempts — 5 minutes) so it fails with a clear "timed out
waiting for ingestion" distinct from a genuine 4xx/5xx.

## 3. CI wiring

Extends `ci-build-publish.yml`'s existing job graph:

```
push to main (ci-draft.yml)
  Artefact-Version → Build → Provider-Deploy → Build-Docker → Trigger-ACR-Copy → Wait-ACR-Copy
    → Deploy-Dev       (action-ado-deploy, wait: true — blocks until ADO pipeline 434 completes)
    → Smoke-Test-Dev   (./gradlew smokeTestSetup smokeTestRun, dev env vars)
    → Auto-Release     (needs: Smoke-Test-Dev, holds on sit-release-approval — ADR-010)
         │
         └─ gh release create → triggers ci-released.yml (existing trigger, unchanged) → SIT
            deploy. No automated verification after that deploy.
```

`Deploy-Dev` calls `hmcts/action-ado-deploy@v1` with its default `wait: true`, so the job blocks
until ADO pipeline 434 (`vp-deploy.yml` in `cp-vp-aks-deploy`) actually completes — and that
pipeline's own `helm upgrade --install --wait` already blocks until Kubernetes confirms the pod
is genuinely ready, not just that the deploy was accepted. A separate readiness-polling job
(`Wait-Dev-Ready`, removed) was both redundant given that and unreachable anyway — `devamp01`'s
ingress sits on HMCTS's internal network, which this repo's `ubuntu-latest` GitHub-hosted runners
have no route to. `action-ado-deploy@v1` polls Azure DevOps's own API over HTTPS instead, which
these runners can reach.

**Env vars / secrets:** `SMOKE_SERVICE_BASE_URL`, `SMOKE_ENVIRONMENT`, `CP_BACKEND_URL`,
`CJSCPPUID`, `SMOKE_ENTRA_TENANT_ID`/`SMOKE_ENTRA_CLIENT_ID`/`SMOKE_ENTRA_CLIENT_SECRET`/
`SMOKE_ENTRA_SCOPE`, `SMOKE_APIM_SUBSCRIPTION_KEY`. `CJSCPPUID`/`SMOKE_ENTRA_*`/
`SMOKE_APIM_SUBSCRIPTION_KEY` resolve from the `dev` GitHub Environment (same mechanism
`Artefact-Version`/`Build` already use); the two URL values (`DEV_SMOKE_SERVICE_BASE_URL`/
`DEV_CP_BACKEND_URL`) are plain repo-level secrets instead — the `dev` GitHub Environment has no
protection rules, so environment-scoping bought no actual gating for those two, only naming.

`SMOKE_SERVICE_BASE_URL` is the APIM-fronted gateway host (`amp.dev.<internal-domain>/amp/pcr`) —
Run calls it with an Entra bearer token and `Ocp-Apim-Subscription-Key`, headers that only make
sense against APIM. `CP_BACKEND_URL` hits CP's own backend stack ingress directly
(`<stack>.ingress01.dev.<internal-domain>`, no path prefix) — Setup's SPI-IN/hearing calls carry
no Entra/APIM headers, so there's no reason to route them through APIM either. This still needs
the same internal-network access `Wait-Dev-Ready` lacked (§4).

## 4. Open items

- Poll interval/timeout for Run (10s/30 attempts) is a starting point, not yet tuned against
  sustained real-environment timing.
- `Smoke-Test-Dev`'s `CP_BACKEND_URL` calls (Setup's SPI-IN/plea/draft/shared-result chain) have
  no route from `ubuntu-latest` GitHub-hosted runners to HMCTS's internal network. Not resolved by
  this design — see §5 for the proposed architecture.
- SIT-side smoke-test automation (`Wait-Sit-Ready`/`Smoke-Test-Sit`) — deferred to a later phase,
  cherry-picked from this branch when scheduled.

## 5. Proposed: split Setup/Run execution by network reachability (not yet implemented)

### Constraints (established, not assumed)

| # | Constraint | Evidence |
|---|---|---|
| 1 | GitHub-hosted `ubuntu-latest` runners have no route to HMCTS's internal ingress (`*.ingress01.*.nl.cjscp.org.uk`) | `Wait-Dev-Ready` failed identically 30/30 attempts, sub-second each (DNS-fail pattern, not a slow timeout); same class of gap documented in sibling repo `crime-case-readiness` |
| 2 | ADO pipeline 434's self-hosted agents already have internal-network access | Directly perform `helm upgrade` against the AKS API server, `vault read` against HashiCorp Vault, `az acr login` — proven, not inferred |
| 3 | The public WAF→APIM path (`SMOKE_SERVICE_BASE_URL`) is reachable from an ordinary internet client | `smokeTestRun` got a real `401` from APIM (invalid subscription key), not a connection/DNS failure — the request completed the full WAF→APIM round trip |
| 4 | `helm upgrade --install --wait` already blocks on genuine K8s readiness | Pod's `readinessProbe`/`livenessProbe`/`startupProbe` all hit `/actuator/health` directly; `--wait --timeout 3600s` polls that |
| 5 | `action-ado-deploy@v1` already supports synchronous wait and exposes `run_id`/`result` | Read directly from `action.yml`; `wait: true` is the default (`Deploy-Dev` was overriding it — fixed in §3) |
| 6 | ADO's `DeployService` job matrix redeploys every service in `apps_to_deploy` on each triggered run, not just the one that pushed | `Setup` job generates the full matrix from `apps_to_deploy` every run |
| 7 | Running the smoke test twice (once in ADO, once in GitHub Actions) is not acceptable | Confirmed decision |
| 8 | ADO agents already have ACR access; general public internet access (GitHub, Maven Central) is unconfirmed and shouldn't be assumed | Agent already does `az acr login` — proven. Self-hosted agents are commonly locked to an allowlist that excludes general internet egress even when internal/Azure access works fine — no evidence either way for GitHub/Maven reachability specifically |
| 9 | The exact ADO artifact-download REST shape is unconfirmed (classic Build API vs modern Pipelines API) | Not yet checked against a real run — verify before building |

### Decision shape

Setup needs `CP_BACKEND_URL` (internal-only, constraint 1). Run needs `SMOKE_SERVICE_BASE_URL`
(public, constraint 3). These have different network requirements, so they run in different
places — each where its own dependency is actually satisfied, not both forced into one runner
that can only reach one of them.

Getting the smoke-test code onto the ADO agent doesn't need `git clone`/Gradle dependency
resolution there at all (which would need unconfirmed public internet egress — constraint 8).
GitHub Actions already has full internet access and already builds this repo; it can build a
self-contained smoke-test artifact there and push it to ACR, which the ADO agent already
authenticates against (`az acr login`, constraint 8) to pull the app image itself. The ADO
piggyback step then only needs to *pull*, not build.

```
GitHub Actions (service-cp-crime-results-pcr, hosted runner, public network)
  Build:
    ./gradlew build                              (existing)
    Build smoke-test artifact (fat jar or small image, self-contained)
    Push to ACR: <acrUrl>/hmcts/service-cp-crime-results-pcr-smoke:$(tag)

  Deploy-Dev:
    action-ado-deploy@v1  wait: true             (existing, §3)
    → outputs: run_id, result

  Smoke-Test-Dev:
    needs: [Deploy-Dev]
    Download artifact "pcr-smoke-config" from ADO run $(Deploy-Dev.run_id)
    place at build/smoke-test-config/dev.json
    ./gradlew smokeTestRun                        (drop smokeTestSetup from this job)
    → needs SMOKE_SERVICE_BASE_URL only - constraint 3 already covers it

  Auto-Release:
    needs: [Smoke-Test-Dev]
    holds on sit-release-approval                 (unchanged - ADR-010)

ADO pipeline 434 (cp-vp-aks-deploy, self-hosted agent, internal network)
  DeployService[service-cp-crime-results-pcr]:
    helm upgrade --install --wait ...             (existing)
    if PCR:
      docker pull <acrUrl>/hmcts/service-cp-crime-results-pcr-smoke:$(TAG)  (ACR already reachable)
      run smokeTestSetup from that image           (needs CP_BACKEND_URL - constraint 2 has it)
      PublishPipelineArtifact "pcr-smoke-config"    (build/smoke-test-config/dev.json)
```

### Component design

**GitHub Actions-side artifact build** (extends the existing `Build` job):
- Packages `src/smokeTest/` and its runtime dependencies into a self-contained artifact — a fat
  jar is simplest (no image-runtime overhead on the ADO side), a small Docker image is the
  alternative if the ADO step would rather `docker run` than manage a JRE itself.
- Pushed to ACR under a distinct tag (e.g. `-smoke` suffix), alongside the existing app image
  push — same registry, same credentials already configured for `Build-Docker`.

**ADO-side piggyback** (append to `vp-deploy.yml`'s existing `Deploy $(APP)` script, after
`echo "Deployed $APP"`, gated `[ "$APP" == "service-cp-crime-results-pcr" ]`):
- Credentials sourced via `vault read -field value secret/${ENV}/pcr/...` — the same mechanism
  this pipeline already uses for every other service, not GitHub-passed, not a new secret store.
- `az acr login` (already done earlier in this same script for the app image) + pull the
  smoke-test artifact, run it — no `git clone`, no Gradle dependency resolution on the agent.
- Publishes `build/smoke-test-config/${ENV}.json` via `PublishPipelineArtifact@1` (already used
  elsewhere in this same file for the deploy artifact) as a distinctly-named artifact.
- **Isolation (constraint 6, 7):** a failure here must not fail the shared matrix run for every
  other service redeployed in the same batch. Needs `continueOnError` or equivalent per-step
  isolation so a PCR smoke-test failure blocks only PCR's own promotion, not other teams' deploys
  that happened to land in the same triggered run.

**GitHub Actions-side download step** (new, between `Deploy-Dev` and the trimmed
`Smoke-Test-Dev`):
- Uses `${{ needs.Deploy-Dev.outputs.run_id }}` (already exposed) and `${{ secrets.HMCTS_ADO_PAT }}`
  (already available) to call Azure DevOps's REST API for the published artifact.
- `vp-deploy.yml` uses modern YAML `stages:`/`jobs:` syntax, so this is almost certainly the
  Pipelines API (`_apis/pipelines/{pipelineId}/runs/{runId}/artifacts`), not the classic Build
  API — confirm against a real run before relying on it (constraint 9).
- Extracted to `build/smoke-test-config/dev.json` — the exact path `smoke-utils.js`'s
  `readCaseConfig` already reads from. No changes needed to `karate-config.js` or
  `run-check-pcr-result.feature`.

### Failure-mode analysis

| Failure point | What happens | SIT correctly blocked? |
|---|---|---|
| ADO's `smokeTestSetup` fails (e.g. SPI-IN rejected) | Isolated ADO failure → `Deploy-Dev` reports failure → `Smoke-Test-Dev`/`Auto-Release` skip | Yes |
| Artifact download step fails (wrong REST shape, run_id mismatch) | GitHub Actions step fails explicitly → `Smoke-Test-Dev` fails → `Auto-Release` skips | Yes, though this is a false failure (infra problem, not a real regression) — worth alerting distinctly |
| `smokeTestRun` fails (genuine PCR ingestion/query regression) | `Smoke-Test-Dev` fails normally → `Auto-Release` skips | Yes, and cleanly attributable to PCR's own query-side logic |
| Everything passes | `Auto-Release` holds on `sit-release-approval` as designed | Correct |

Failure attribution stays clean this way: `Deploy-Dev` failing means a deploy/CP-side Setup
problem; `Smoke-Test-Dev` failing means a PCR-side query problem. Running the whole test inside
ADO instead (an earlier, rejected alternative) would have conflated both into one signal.

### Verification required before implementation

1. **ADO artifact REST API shape** (constraint 9) — verify against a real pipeline run, don't
   assume.
2. **Vault secret path convention** — the paths above are illustrative, matching this file's
   existing style; real paths need confirming with whoever owns this Vault instance.
3. **Matrix isolation semantics** — whether `continueOnError`/per-step isolation actually prevents
   a PCR failure from red-flagging the whole shared `DeployService` run.
4. **Artifact shape (fat jar vs image)** — whichever is simpler for the ADO step to run given
   what's already available on the agent (a JRE it can reuse vs `docker run`).

Building the smoke-test artifact in GitHub Actions and pulling it via ACR (rather than cloning
and building on the agent) removes constraint 8 as a hard blocker — ACR access is already
proven, unlike general internet egress. No throwaway network diagnostic is needed before starting
this work as a result.
