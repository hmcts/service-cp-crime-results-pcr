# PCR Design Review — Fixes Log

Tracks fixes applied to `2026-07-16-pcr-api-marketplace-design-v2.md` following design
review, against PR #5. Each entry: what was flagged, what changed, why.

---

## Query API paths aligned with the actual OpenAPI contract

**Flagged:** §10's endpoints (`GET /pcrs/{hearingId}/{defendantId}`, `GET /pcrs/{id}/{defendantId}`)
didn't match the real `api-cp-crime-results-pcr` contract — no `caseURN`, and no way to fetch
a resource's latest version without already knowing an id.

**Fix:** replaced with the three operations the contract actually exposes, all under
`/pcrs/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}`:
- base path — full version history for the resource
- `.../versions/{id}` — a specific version, by its source correlation id
- `.../versions/latest` — the most recently recorded version

Updated throughout: §10, the sequence diagram (§3b), and cross-references in §7/§8a.

---

## `versionStatus` removed; correlation handler and `materialId` retained

**Flagged:** the `versionStatus` (`PENDING`/`CORRELATED`/`ORPHANED`) tri-state field is redundant
— `materialId`'s null/non-null state already tells you whether a version is correlated.

**Fix:** dropped `versionStatus` everywhere it appeared — the `pcr_version` schema (§8a), the
layering table (§8), the sequence diagram's correlation step (§3b), and the drift-detection
description (§9), which now describes the same "for free" signal via an unset `materialId`
rather than an explicit `ORPHANED` label.

**Not changed** — kept exactly as originally designed:
- `PcrVersionCorrelationHandler` (§7, §8) — still subscribes to Progression's
  `prison-court-register-generated(-v2)` event, joins on id equality, and stamps `materialId`
  once a match is found.
- `materialId` itself (§6, §8a) — correlation state, set only once matched.
- The version identity (§7) — a source correlation id, minted once at `cpp-context-results`
  and propagated through CP end-to-end (Redis → Function App → Progression → HRDS), never
  invented by this service. Id shape (ULID vs UUID+`sharedResultTime`) remains open (§7b, §13
  item 5).

The ask was scoped narrowly to `versionStatus` — the correlation handler, `materialId`, and the
source-propagated id are load-bearing parts of the design and stay.