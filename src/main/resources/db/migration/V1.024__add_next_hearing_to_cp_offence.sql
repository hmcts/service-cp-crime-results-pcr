-- An offence's own next hearing, alongside cp_version's earliest-across-offences value — offences
-- on one hearing can be adjourned to different dates. Null when none of its results carries one.
ALTER TABLE cp_offence
    ADD COLUMN next_hearing_date date,
    ADD COLUMN next_hearing_time varchar,
    ADD COLUMN next_hearing_court_house_id uuid,
    ADD COLUMN next_hearing_court_house_code varchar,
    ADD COLUMN next_hearing_court_house_name varchar;
