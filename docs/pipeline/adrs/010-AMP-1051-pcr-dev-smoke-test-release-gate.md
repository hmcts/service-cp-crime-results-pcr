# 010. Gate SIT release on an automated dev smoke test with human QA approval

**Status:** Accepted, 27 Aug 2026

**Jira:** AMP-1051 — automated dev smoke test gating release promotion to SIT

## Context

Unit and integration tests verify PCR's own code, not that the deployed service actually works
end-to-end against the real CP backend, real Event Grid delivery, and the real relay function
once rolled out to a live environment. Nothing currently confirms a fresh dev deployment can
process a genuine hearing result before a release promotes it to SIT.

## Decision

A Karate-based smoke test drives a real result through the CP backend and confirms PCR ingests
it, wired into `ci-build-publish.yml` as a gate on release creation:

- **Setup** (`smokeTestSetup`) creates a case via SPI-IN, enters a guilty plea, and shares a
  custodial result on the deployed dev CP backend — the same production path a real hearing
  result would take, not a direct call to PCR's own internal ingestion endpoint. This proves the
  whole pipeline (Event Grid subscription, relay function, PCR's own ingestion) works post-deploy,
  not just PCR in isolation.
- **Run** (`smokeTestRun`) polls PCR's own query endpoint (through APIM, with a real Entra bearer
  token) until the result Setup recorded appears, since ingestion is asynchronous.
- CI wiring: `Deploy-Dev` (blocks until the ADO deploy pipeline confirms the pod is genuinely
  ready) → `Smoke-Test-Dev` → `Auto-Release`. A failing smoke test blocks `Auto-Release` outright
  (`needs:` dependency); no release means no SIT deploy is reachable.
- `Auto-Release` additionally holds on the `sit-release-approval` GitHub Environment, which
  requires a human QA reviewer to approve before `gh release create` runs — a green smoke test is
  necessary but not sufficient to reach SIT.
- SIT-side automated smoke-test verification is out of scope for this decision — SIT's own
  deploy (existing trigger on release, unchanged) still fires once approved, with no automated
  check after it.
