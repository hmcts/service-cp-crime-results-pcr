-- Applicant/Appellant/Respondent/Defendant — ported from cpp-context-progression's
-- PrisonCourtRegisterHandler.getDefendantType, not invented here (design doc 2026-09-02).
-- Literal "Defendant" for a prosecution-case-driven defendant; computed for a
-- court-application-only one. Rows written before this migration have it null.
ALTER TABLE cp_version
    ADD COLUMN defendant_type varchar;
