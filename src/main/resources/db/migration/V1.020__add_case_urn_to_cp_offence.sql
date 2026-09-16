-- Which linked case (CP's courtApplicationCases) an offence belongs to. Nullable — null for
-- direct prosecution-case offences (unambiguous via cp_case_hearing.case_urn already) and for
-- historical rows written before this migration.
ALTER TABLE cp_offence
    ADD COLUMN case_urn varchar(30);
