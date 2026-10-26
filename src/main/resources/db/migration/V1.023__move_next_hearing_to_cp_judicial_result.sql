-- CP records a next hearing against the judicial result that sets it, and one offence or
-- application can carry several results each setting a different next hearing, so it is held per
-- result rather than once per version. next_hearing_id is dropped outright: CP's nextHearing
-- carries a booking reference, not the next hearing's identifier, so there is no source for it.
ALTER TABLE cp_judicial_result
    ADD COLUMN next_hearing_date date,
    ADD COLUMN next_hearing_time varchar,
    ADD COLUMN next_hearing_court_house_id uuid,
    ADD COLUMN next_hearing_court_house_code varchar,
    ADD COLUMN next_hearing_court_house_name varchar;

ALTER TABLE cp_version
    DROP COLUMN next_hearing_date,
    DROP COLUMN next_hearing_time,
    DROP COLUMN next_hearing_court_house_id,
    DROP COLUMN next_hearing_court_house_code,
    DROP COLUMN next_hearing_court_house_name,
    DROP COLUMN next_hearing_id;
