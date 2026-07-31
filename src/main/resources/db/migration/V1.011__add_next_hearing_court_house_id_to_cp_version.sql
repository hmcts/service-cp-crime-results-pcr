-- CP's own nextHearing.courtCentre carries a courthouse UUID alongside its code/name, matching
-- the Court schema's courtHouseId used elsewhere in the contract — cp_version only had the
-- code/name columns. Rows written before this migration have it null.
ALTER TABLE cp_version
    ADD COLUMN next_hearing_court_house_id uuid;
