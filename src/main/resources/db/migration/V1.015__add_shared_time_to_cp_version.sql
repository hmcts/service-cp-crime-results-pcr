-- sharedTime is scoped to one Hearing_Resulted delivery, so captured per version, not on the shared cp_case_hearing row.
ALTER TABLE cp_version
    ADD COLUMN shared_time timestamptz;
