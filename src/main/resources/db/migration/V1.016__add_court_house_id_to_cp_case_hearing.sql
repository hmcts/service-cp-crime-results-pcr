-- CP's own hearing.courtCentre carries a courthouse UUID alongside its code/name, matching
-- the Court schema's courtHouseId used elsewhere in the contract (see next_hearing_court_house_id
-- on cp_version, V1.011) — cp_case_hearing only had the code/name columns. Rows written before
-- this migration have it null.
ALTER TABLE cp_case_hearing
    ADD COLUMN court_house_id uuid;
