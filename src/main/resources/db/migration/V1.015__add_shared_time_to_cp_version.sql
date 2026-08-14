-- sharedTime is CP's own field, a sibling of hearing rather than nested under it — one of the
-- candidate version-correlation mechanisms under consideration (design §7), captured per version
-- since it is scoped to one Hearing_Resulted delivery, not the shared cp_case_hearing row.
ALTER TABLE cp_version
    ADD COLUMN shared_time timestamptz;
