-- CP's nextHearing carries a booking reference, not the next hearing's identifier, so there is
-- no source for this column.
ALTER TABLE cp_version
    DROP COLUMN next_hearing_id;
