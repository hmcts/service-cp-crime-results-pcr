-- CP's own hearing.type (e.g. "First hearing") — sent as an object (id + description); only the
-- description is stored, matching the convention for court application type. Rows written before
-- this migration have it null.
ALTER TABLE cp_case_hearing
    ADD COLUMN hearing_type varchar;