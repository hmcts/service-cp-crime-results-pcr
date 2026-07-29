-- CP's own offence/application ids, retained for correlation/debugging even though the API
-- contract doesn't expose them and the surrogate id column stays the primary key (a real id
-- can legitimately repeat across multiple cp_version rows when the same court application or
-- case-level offence applies to more than one defendant sharing a masterDefendantId — it can't
-- be the PK for that reason, but is still worth keeping as plain data).
ALTER TABLE cp_offence
    ADD COLUMN source_offence_id uuid;

ALTER TABLE cp_court_application
    ADD COLUMN source_application_id uuid;