-- Round 3 legacy-PDF/Docmosis-template parity fields, confirmed against the legacy Function App's
-- own PCR mappers (HearingMapper.js, HearingVenueMapper.js, OffenceMapper.js,
-- DefendantContextBaseService.js). Rows written before this migration have them null.

-- defendantPresent (a hearing-wide "did the defendant sit that day" boolean) is replaced by
-- defendantAppearanceDetails (how they attended — "In person"/"By video link"/"Not present"),
-- confirmed against HearingMapper.js:getDefendantAppearanceDetails.
ALTER TABLE cp_version
    DROP COLUMN defendant_present,
    ADD COLUMN defendant_appearance_details varchar;

-- Local Justice Area name and the hearing's court house postal address — both hearing-venue facts
-- confirmed via HearingVenueMapper.js (hearing.courtCentre.lja.ljaName / hearing.courtCentre.address,
-- which genuinely carries all 5 address lines, unlike the defendant's own address).
ALTER TABLE cp_case_hearing
    ADD COLUMN lja_name varchar,
    ADD COLUMN court_address_line_1 varchar,
    ADD COLUMN court_address_line_2 varchar,
    ADD COLUMN court_address_line_3 varchar,
    ADD COLUMN court_address_line_4 varchar,
    ADD COLUMN court_address_line_5 varchar,
    ADD COLUMN court_post_code varchar;

-- verdict_code was always sourced from verdict.verdictType.description (a human-readable
-- description, not a code) — renamed to match what it actually holds, same reasoning as the
-- api-cp contract's own Offence.verdictCode -> Offence.verdict rename.
ALTER TABLE cp_offence
    RENAME COLUMN verdict_code TO verdict;

-- allocationDecision/indicatedPleaValue confirmed via OffenceMapper.js
-- (offence.allocationDecision.motReasonDescription / offence.indicatedPlea.indicatedPleaValue).
ALTER TABLE cp_offence
    ADD COLUMN allocation_decision varchar,
    ADD COLUMN indicated_plea_value varchar;

-- Third polymorphic parent for cp_judicial_result: defendant-case-level (level='C', from
-- defendant.defendantCaseJudicialResults) and hearing-wide defendant-level (level='D', from
-- hearing.defendantJudicialResults, matched by masterDefendantId) results — confirmed reaching the
-- real PDF as caseResults[]/defendantResults[] (PrisonCourtRegisterPdfPayloadGenerator). Letter
-- codes match legacy's own LevelTypeEnum literally. level is only meaningful when version_pk is
-- set; offence/application-parented rows leave it null.
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