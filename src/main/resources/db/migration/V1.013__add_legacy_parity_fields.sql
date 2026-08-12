-- Legacy PCR parity fields, confirmed against the legacy Function App's own PCR mappers:
-- gender/nationality/postHearingCustodyStatus (defendant-level aggregate) and defendantPresent
-- on cp_version; jurisdiction on cp_case_hearing (hearing-wide, unlike defendantPresent which
-- varies per defendant); offence_legislation on cp_offence. Rows written before this migration
-- have them null.
ALTER TABLE cp_version
    ADD COLUMN gender varchar,
    ADD COLUMN nationality varchar,
    ADD COLUMN defendant_present boolean,
    ADD COLUMN post_hearing_custody_status varchar;

ALTER TABLE cp_case_hearing
    ADD COLUMN jurisdiction varchar;

ALTER TABLE cp_offence
    ADD COLUMN offence_legislation varchar;

-- Confirmed dead: legacy's own ResultMapper never surfaces a per-result custody status at any
-- level (offence/case/application/defendant) — only the defendant-level aggregate above is a
-- real PCR concept. This column was never populated with anything but the raw per-result
-- passthrough this migration removes.
ALTER TABLE cp_judicial_result
    DROP COLUMN post_hearing_custody_status;