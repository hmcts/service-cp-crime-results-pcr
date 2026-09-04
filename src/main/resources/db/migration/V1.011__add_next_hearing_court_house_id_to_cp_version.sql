-- Matches the Court schema's courtHouseId used elsewhere — cp_version only had code/name columns.
ALTER TABLE cp_version
    ADD COLUMN next_hearing_court_house_id uuid;
