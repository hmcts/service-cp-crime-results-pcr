-- AMP-1101: an offence linked via a court application that spans more than one prosecution
-- case (CP's courtApplicationCases) previously had no way to identify which of those cases it
-- belongs to. Nullable — null for direct prosecution-case offences (unambiguous via
-- cp_case_hearing.case_urn already) and for historical rows written before this migration.
ALTER TABLE cp_offence
    ADD COLUMN case_urn varchar(30);
