-- Defendant PII, per ADR-004 (docs/pipeline/adrs/004-AMP-891-carry-defendant-pii-encrypted-at-rest.md).
-- Every column is varchar, including date_of_birth -- application layer stores ciphertext here,
-- not a value Postgres could parse natively.
ALTER TABLE cp_version
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