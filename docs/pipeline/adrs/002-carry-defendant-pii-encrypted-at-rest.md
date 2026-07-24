# 002. Carry defendant PII, encrypted at rest

**Status:** Accepted, 24 Jul 2026
**Jira:** AMP-891 — PII redaction/encryption
**Design docs:** this decision changes the contract/persistence shape described in
[`2026-07-16-pcr-api-marketplace-design-v2.md`](../../2026-07-16-pcr-api-marketplace-design-v2.md),
[`2026-07-17-pcr-stateless-proxy-design.md`](../../2026-07-17-pcr-stateless-proxy-design.md), and
[`2026-07-21-pcr-data-store-design.md`](../../2026-07-21-pcr-data-store-design.md) — each links
back to this ADR at the point it carries an encrypted PII field.

## Context

Earlier in this service's design, `title`/`firstName`/`middleName`/`lastName`/`dateOfBirth`/
`address` were deliberately dropped from the `Defendant`/`Address` contract on the basis that
one confirmed consumer resolves defendant identity entirely via `defendantId`/`masterDefendantId`
against their own systems and did not need this data from this API. That decision is recorded in
this repo's own history (`CLAUDE.md`, the orchestrator design doc, and
`api-cp-crime-results-pcr`'s `PCR-HMPPS-FIELD-MAPPING.md`) as settled.

That requirement has since changed: a confirmed new requirement now needs this data carried
through the API. This service is published via the API Marketplace, so the contract is not
scoped to any single named consumer's stated needs — a decision one consumer doesn't need
something isn't a reason to withhold it from the contract if another confirmed need exists.
Per `hmcts-standards.md`, reintroducing personal data into a contract that had explicitly
excluded it is a data-handling decision requiring an ADR before proceeding.

Two things are true at once, and this ADR only resolves the second:
- The `api-cp-crime-results-pcr` OpenAPI contract (v1.0.3, currently pinned in this service's
  `build.gradle`) **already declares** `title`/`firstName`/`middleName`/`lastName`/`dateOfBirth`/
  `address` on `Defendant` — a prior fix removing them (`api-cp-crime-results-pcr` PR #11) was
  closed unmerged, so the generated Java model already has these fields and setters today.
  Nothing in the spec needs to change to carry this data.
- Nothing populates them yet. `PcrVersionMapper.toDefendant()` only sets `id`/`masterDefendantId`,
  and `HearingDetailsResponse.Defendant`/`PersonDefendant` (the upstream DTO this service parses
  the Results Query API response into) has no fields to source name/DOB/address from at all —
  that upstream modelling is new work, out of this ADR's scope, tracked separately.

This ADR covers: given that this data will now flow through this service, how it's protected
once it does.

## Decision

Encrypt `firstName`/`middleName`/`lastName`/`dateOfBirth`/`address` (and `title`, though it's not
sensitive on its own, for consistency) at the persistence layer, using the same transparent
field-level encryption pattern already demonstrated in
`service-hmcts-springboot-demo/postgres-encrypt-demo`:

- A custom `@Encrypted` field annotation on the JPA entity (a new `PcrDefendant`-shaped entity
  or fields added to the existing `pcr_version` entity once the JPA layer exists — this service
  has no JPA entities yet, see `docs/2026-07-21-pcr-data-store-design.md`; adding this pattern
  and adding the phase-2 JPA layer are naturally sequenced together, not two independent efforts).
- A Hibernate `PreInsert`/`PreUpdate`/`PostLoad` event listener
  (`EncryptionEventListener`/`HibernateListenerRegistrar` in the demo) that encrypts/decrypts
  transparently against Hibernate's own state array — entity, repository, and service code never
  see ciphertext and never need to know encryption exists.
- An `EncryptionService` interface, stubbed initially the same way the demo does
  (`StubEncryptionService`, clearly marked as **not real encryption**, for wiring and testing the
  lifecycle only), then replaced with a real implementation backed by Azure Key Vault
  (`CryptographyClient`, `RSA_OAEP` in the demo — confirm the actual algorithm/key with whoever
  owns this service's Key Vault instance before taking that as settled) before this reaches any
  real environment.
- This requires adding `spring-boot-starter-data-jpa` (Hibernate) to this service for the first
  time — currently only `spring-boot-starter-jdbc` is wired (per the phase-2 Flyway PR), deliberately
  avoiding JPA until real persistence logic existed. This ADR is what changes that: the encryption
  mechanism itself depends on Hibernate's event system, not just a JDBC connection.

## Consequences

- **Real personal data now flows through this service's data store and (for whichever fields the
  contract exposes them on) its API responses**, for the first time. `pcr_version`'s retention
  policy (30-day TTL purge, already designed) becomes a real data-minimisation control, not just
  a storage-cost one — confirm the purge job is built and tested before any PII-bearing row can be
  written in a real environment.
- **The upstream data source for this data doesn't exist yet.** `HearingDetailsResponse` (and,
  further upstream, whatever the Results Query API's `hearingDetails/internal` response actually
  carries) needs a real fixture check to confirm these fields are even present and sourceable —
  this ADR does not do that work; it only decides how the data is protected once a separate piece
  of work sources it.
- **`StubEncryptionService` must never reach a real environment.** It exists only to prove the
  encrypt-on-write/decrypt-on-read lifecycle works; the demo's README is explicit that it performs
  no real cryptography. Swapping in the Key Vault-backed implementation is a hard gate before this
  reaches dev/SIT with real data, not a follow-up nicety.
- **Logging discipline becomes load-bearing, not just good practice.** Per `shared-code-rules.md`,
  PII must never be logged — this already applies today via the field-level filtering this service
  observes elsewhere, but once real name/DOB/address values exist in memory (decrypted, in the
  entity) the risk of an incidental `log.info("...{}", defendant)`-style leak becomes real rather
  than hypothetical. Add this to whatever code-review checklist covers this change.
- **Key management (rotation, access control on the Key Vault instance, break-glass recovery) is
  out of scope for this ADR** — it decides the pattern (field-level, pluggable `EncryptionService`),
  not the operational detail of running it. Track that separately with whoever owns this service's
  Azure Key Vault provisioning.

## Alternatives considered

- **Column/row-level encryption at the Postgres level (e.g. `pgcrypto`, transparent data
  encryption on the volume)** — rejected for now. Whole-disk/TDE encryption protects against
  physical media theft but not against a compromised database credential or a misconfigured query
  tool reading plaintext directly; application-level field encryption is defence-in-depth on top
  of whatever infrastructure-level encryption already exists, not a replacement for it.
- **Encrypting the whole `pcr_version` row as a single opaque blob** — rejected; breaks the
  ability to query/index on any non-PII column (`case_hearing_id`, `defendant_id`,
  `expires_at`), which the retention sweep and the `version=latest` lookup both depend on.
- **Doing the encryption in application code before calling the repository (manual
  encrypt/decrypt in the service layer)** — rejected in favour of the Hibernate-listener approach;
  it would require every call site that reads or writes a PII field to remember to
  encrypt/decrypt, whereas the listener pattern makes it structurally impossible to forget.

## Compliance notes

- This data is personal data under the Data Protection Act 2018 / UK GDPR — see this repo's
  `hmcts-standards.md` (OFFICIAL-SENSITIVE classification, applies to all case/hearing/party data
  by default).
- The prior "not needed" decision was scoped to one consumer's stated requirement; it does not
  imply this data is inherently unnecessary for the API as a whole. This ADR and the accompanying
  documentation cleanup (removing consumer-specific framing from `CLAUDE.md`/design docs) reflect
  that the contract's shape is not owned by any single consumer's needs.
- Retention: this data must still be purged on the same 30-day TTL as the rest of `pcr_version`
  (design doc §11) — carrying PII doesn't change the retention window, and arguably makes enforcing
  it correctly more important than before.