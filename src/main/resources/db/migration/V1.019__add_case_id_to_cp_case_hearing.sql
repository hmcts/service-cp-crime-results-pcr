-- CP's own prosecutionCase carries an internal case UUID alongside its human-facing case_urn
-- reference. cp_case_hearing only had the URN. Rows written before this migration have it null.
ALTER TABLE cp_case_hearing
    ADD COLUMN case_id uuid;
