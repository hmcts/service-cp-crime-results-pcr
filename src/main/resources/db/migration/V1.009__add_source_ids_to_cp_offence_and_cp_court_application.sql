-- CP's own ids, retained for correlation/debugging — not the PK, since a real id can legitimately
-- repeat across cp_version rows when it applies to more than one defendant sharing a masterDefendantId.
ALTER TABLE cp_offence
    ADD COLUMN source_offence_id uuid;

ALTER TABLE cp_court_application
    ADD COLUMN source_application_id uuid;