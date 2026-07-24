-- Defendant PII, per ADR-002 (docs/pipeline/adrs/002-carry-defendant-pii-encrypted-at-rest.md).
-- Reverses the earlier "defendant identity is out of scope" decision -- a confirmed new
-- requirement now needs this data carried through the API.
--
-- Every column here is varchar, including date_of_birth: the application layer encrypts each
-- field as ciphertext before it reaches this table (transparent field-level encryption via a
-- Hibernate PreInsert/PreUpdate listener, matching service-hmcts-springboot-demo's
-- postgres-encrypt-demo pattern), so no column here ever holds a plain, typed value Postgres
-- itself could parse as a date -- it holds an opaque encrypted string regardless of the
-- underlying field's real type.
ALTER TABLE pcr_version
    ADD COLUMN title varchar,
    ADD COLUMN first_name varchar,
    ADD COLUMN middle_name varchar,
    ADD COLUMN last_name varchar,
    ADD COLUMN date_of_birth varchar,
    ADD COLUMN address_line_1 varchar,
    ADD COLUMN address_line_2 varchar,
    ADD COLUMN address_line_3 varchar,
    ADD COLUMN address_line_4 varchar,
    ADD COLUMN address_line_5 varchar,
    ADD COLUMN post_code varchar;