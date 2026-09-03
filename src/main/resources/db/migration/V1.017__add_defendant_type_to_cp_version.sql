-- Applicant/Appellant/Respondent/Defendant, ported from cpp-context-progression. Rows written
-- before this migration have it null.
ALTER TABLE cp_version
    ADD COLUMN defendant_type varchar;
