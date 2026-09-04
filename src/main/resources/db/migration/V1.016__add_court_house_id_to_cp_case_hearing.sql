-- Matches the Court schema's courtHouseId used elsewhere (see next_hearing_court_house_id on cp_version, V1.011).
ALTER TABLE cp_case_hearing
    ADD COLUMN court_house_id uuid;
