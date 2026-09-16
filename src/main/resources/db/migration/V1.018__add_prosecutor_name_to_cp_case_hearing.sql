-- CP's ProsecutionCaseIdentifier.prosecutionAuthorityName — a case-level fact, same placement
-- as case_urn. Rows written before this migration have it null.
ALTER TABLE cp_case_hearing
    ADD COLUMN prosecutor_name varchar;
