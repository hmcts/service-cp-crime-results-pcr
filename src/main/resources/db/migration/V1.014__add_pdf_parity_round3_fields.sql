-- Round 3 legacy-PDF/Docmosis-template parity fields. Rows before this migration have them null.

-- defendantPresent (hearing-wide boolean) replaced by defendantAppearanceDetails (how they attended).
ALTER TABLE cp_version
    DROP COLUMN defendant_present,
    ADD COLUMN defendant_appearance_details varchar;

-- Local Justice Area name and the hearing's court house postal address (5 lines, unlike the defendant's own address).
ALTER TABLE cp_case_hearing
    ADD COLUMN lja_name varchar,
    ADD COLUMN court_address_line_1 varchar,
    ADD COLUMN court_address_line_2 varchar,
    ADD COLUMN court_address_line_3 varchar,
    ADD COLUMN court_address_line_4 varchar,
    ADD COLUMN court_address_line_5 varchar,
    ADD COLUMN court_post_code varchar;

-- verdict_code always held a human-readable description, not a code — renamed to match, same reasoning as the api-cp contract's rename.
ALTER TABLE cp_offence
    RENAME COLUMN verdict_code TO verdict;

ALTER TABLE cp_offence
    ADD COLUMN allocation_decision varchar,
    ADD COLUMN indicated_plea_value varchar;

-- Third polymorphic parent for cp_judicial_result: defendant-case-level (level='C') and hearing-wide
-- defendant-level (level='D', matched by masterDefendantId). Only meaningful when version_pk is set.
ALTER TABLE cp_judicial_result
    ADD COLUMN version_pk uuid REFERENCES cp_version(cp_version_pk) ON DELETE CASCADE,
    ADD COLUMN level varchar;

ALTER TABLE cp_judicial_result
    DROP CONSTRAINT chk_cp_judicial_result_one_parent;

ALTER TABLE cp_judicial_result
    ADD CONSTRAINT chk_cp_judicial_result_one_parent CHECK (
        (offence_id IS NOT NULL AND court_application_id IS NULL AND version_pk IS NULL)
        OR (offence_id IS NULL AND court_application_id IS NOT NULL AND version_pk IS NULL)
        OR (offence_id IS NULL AND court_application_id IS NULL AND version_pk IS NOT NULL)
    );

CREATE INDEX idx_cp_judicial_result_version ON cp_judicial_result (version_pk);