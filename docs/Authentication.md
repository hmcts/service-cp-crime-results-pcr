# Entra Token Validation

In-application validation of Microsoft Entra app-only access tokens, deliberately duplicating
APIM's `validate-jwt` policy — it covers paths APIM never sees (in-cluster callers, a
port-forward, a misrouted ingress, the Service Bus ingestion path). See
`docs/pipeline/adrs/006-AMP-890-pcr-bearer-auth-security-model.md` for the wider security model
this sits inside.

Implementation: `auth/` (`EntraAuthProperties`, `EntraTokenValidator`, `AuthorizationPolicy`,
`TokenValidationException`, `AuthMode`, `ValidatedCaller`) and
`filters/security/ClientIdResolutionFilter`.

## Config

| Property | Env var | Default | Purpose |
|---|---|---|---|
| `auth.mode` | `AUTH_MODE` | `OFF` | `OFF` / `OBSERVE` / `ENFORCE` — see below |
| `auth.tenant-id` | `AUTH_TENANT_ID` | blank | The **issuing** Entra tenant |
| `auth.audience` | `AUTH_AUDIENCE` | blank | This API's own audience |
| `auth.issuer` | `AUTH_ISSUER` | blank → derived from tenant id | Override |
| `auth.jwks-uri` | `AUTH_JWKS_URI` | blank → derived from tenant id | Override |
| `auth.clock-skew-seconds` | `AUTH_CLOCK_SKEW_SECONDS` | `60` (capped at `300`) | `exp`/`nbf` tolerance |
| `auth.jwks-cache-ttl-seconds` | `AUTH_JWKS_CACHE_TTL_SECONDS` | `600` | JWKS cache lifetime |

`tenant-id`/`audience` are required once `mode` is not `OFF`; startup fails otherwise.

### Modes

- `OFF` — no validation, identity unverified. Only mode permitted locally/in tests.
- `OBSERVE` — validated, every failure logged and counted (`cp.auth.observed.failure`), but
  nothing is rejected. Diagnostic only — **provides no protection**.
- `ENFORCE` — validated, invalid requests rejected.

`OFF`/`OBSERVE` fail startup in any deployed environment (`environment.name` in
`DEV`/`STE`/`SIT`/`PRP`/`PRD`) — treat a deployed environment running either as a live incident.

## Claims checked

| Claim | Requirement |
|---|---|
| `aud` | Exact match against `auth.audience` |
| `iss` | Exact match — never prefix/contains |
| `exp` | Required, within clock skew |
| `nbf` | Within clock skew, when present |
| `tid` | Exact match against `auth.tenant-id` |
| `ver` | Must be `2.0` |
| `azp` | The caller identity — a UUID. **Never `oid`/`sub`** |
| `roles` | Required, non-empty |
| `scp` | Must be absent (its presence means a delegated, not app-only, token) |

App-only is proven by `sub == oid` + non-empty `roles` + absent `scp` — **never by `idtyp`**,
which Entra omits unless explicitly enabled on the app registration; requiring it would reject
all legitimate traffic.

## Exempt paths

Enumerated only, in `AuthorizationPolicy` — never prefix-matched: `/`, `/actuator`,
`/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info`,
`/actuator/prometheus`. Every other endpoint (currently the one PCR endpoint) requires a
validated bearer token once `auth.mode` is not `OFF`.

## Entra prerequisites (not code — owned by the Entra/platform team)

- App registration exposing this API's own `aud`.
- App roles **declared and assigned, with admin consent** — a declared role that isn't assigned
  produces a token that looks correct but silently carries no `roles`.
- `requestedAccessTokenVersion` pinned to `2`.
- Per-environment tenant id (the **issuing** tenant, which may differ from the hosting tenant)
  and audience values.

## Running locally

`auth.mode` defaults to `OFF`, so `GET /cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}`
works with no `Authorization` header out of the box. To exercise `ENFORCE` against a real
tenant, set `AUTH_MODE=ENFORCE`, `AUTH_TENANT_ID`, and `AUTH_AUDIENCE` and supply a real bearer
token. See `EntraTokenValidatorTest` for the conformance suite and `EntraAuthIntegrationTest`
for the same behaviour proven through the real Spring filter chain against an in-process JWKS.
