# 003. PostgreSQL + Flyway for the PCR data store

**Status:** Accepted, 24 Jul 2026
**Jira:** AMP-890 — data modelling
**Design docs:** [`2026-07-21-pcr-data-store-design.md`](../../designs/2026-07-21-pcr-data-store-design.md)
describes the schema this ADR adopts the engine and migration tool for; that doc links back
here at its **Database engine** line.

## Context

Phase 1 of this service (`2026-07-17-pcr-stateless-proxy-design.md`) is a stateless proxy with
no data store at all. Phase 2 needs real version history — an immutable row per
`(hearingId, defendantId)` PCR version, queryable by id and by history (v2 §7/§8a) — which
requires a real persistence layer for the first time in this repo.

Per `hmcts-standards.md`, any new external dependency requires an ADR before proceeding. This
introduces four: `org.postgresql:postgresql`, `org.flywaydb:flyway-core`,
`org.flywaydb:flyway-database-postgresql`, and `spring-boot-starter-jdbc`.

## Decision

- **PostgreSQL** as the database engine — matches the engine every other DB-backed
  `service-cp-*`/`cpp-context-*` service in this org already uses; no case for a different
  engine was identified.
- **Flyway** for schema migrations (`src/main/resources/db/migration/V<n>__<description>.sql`),
  auto-run on `bootRun`/test startup per `service-shared.md`'s standard convention.
- **`spring-boot-starter-jdbc`, not `spring-boot-starter-data-jpa`, at this stage.** Only the
  schema and datasource config exist so far — no JPA entities or repositories yet (that lands
  with the encryption work in ADR-004, which specifically needs Hibernate's event-listener
  system). `starter-jdbc` gives Flyway a `DataSource` to run migrations against without pulling
  in Hibernate ahead of any code that would actually use it.
- Schema itself — normalized tables, immutable version rows, surrogate `cp_version_pk` vs.
  source-propagated `source_id`, polymorphic `cp_offence`/`cp_judicial_result` parents — is
  specified in full in the linked design doc, not repeated here.

## Consequences

- This service gains a real datasource for the first time — `application.yaml`'s
  `spring.datasource.*`/`spring.flyway.*` block and `management.health.db.enabled: false`
  (nothing reads/writes this datasource yet, so it shouldn't fail the readiness probe) are both
  already wired ahead of any JPA layer landing.
- Local dev and CI need a real PostgreSQL instance available for Flyway to run against —
  Testcontainers handles this for tests per `service-shared.md`'s standard convention; no new
  infra pattern introduced.
- No JPA entities exist yet, so nothing in this service actually reads or writes through this
  schema today — the migrations exist so DevOps can provision the schema ahead of the JPA layer
  landing, not because anything depends on it yet.

## Alternatives considered

- **Liquibase** — rejected; Flyway is the convention already established across this org's
  `service-cp-*`/`cpp-context-*` fleet, and introducing a second migration tool would be
  inconsistent for no benefit.
- **A NoSQL/document store for version rows** — rejected; the design doc's normalized,
  relational model matches how CP itself already models this domain
  (`cpp-context-hearing`/`cpp-context-results` use fully normalized JPA entities, not JSON
  blobs), and the confirmed access patterns never need partial/granular querying inside a
  version's content.

## Compliance notes

- This datastore will carry OFFICIAL-SENSITIVE case/hearing data and, per ADR-004, encrypted
  defendant PII — retention is enforced via the 30-day TTL purge already specified in the
  design doc (§11), not by this ADR.