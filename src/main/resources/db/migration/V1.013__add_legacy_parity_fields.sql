-- Legacy PCR parity fields: defendant-level aggregates on cp_version, hearing-wide jurisdiction
-- on cp_case_hearing, offence_legislation on cp_offence. Rows before this migration have them null.
ALTER TABLE cp_version
    ADD COLUMN gender varchar,
    ADD COLUMN nationality varchar,
    ADD COLUMN defendant_present boolean,
    ADD COLUMN post_hearing_custody_status varchar;

ALTER TABLE cp_case_hearing
    ADD COLUMN jurisdiction varchar;

ALTER TABLE cp_offence
    ADD COLUMN offence_legislation varchar;

-- Dead column — legacy never surfaces a per-result custody status; only the defendant-level aggregate above is real.
ALTER TABLE cp_judicial_result
    DROP COLUMN post_hearing_custody_status;