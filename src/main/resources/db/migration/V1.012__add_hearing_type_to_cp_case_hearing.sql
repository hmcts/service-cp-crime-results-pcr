-- hearing.type is an object (id + description); only description is stored, matching court application type.
ALTER TABLE cp_case_hearing
    ADD COLUMN hearing_type varchar;