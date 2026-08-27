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
  no route from `ubuntu-latest` GitHub-hosted runners to HMCTS's internal network — the same gap
  `Wait-Dev-Ready` hit. `Deploy-Dev`'s ADO pipeline (`vp-deploy.yml` in `cp-vp-aks-deploy`) already
  runs on self-hosted agents inside that network; a proposed fix is a small, PCR-scoped piggyback
  step in that pipeline's existing per-service deploy loop, sent to DevOps for review rather than
  applied here. Not resolved by this design.
- SIT-side smoke-test automation (`Wait-Sit-Ready`/`Smoke-Test-Sit`) — deferred to a later phase,
  cherry-picked from this branch when scheduled.
