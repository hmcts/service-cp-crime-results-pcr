-- Individual case URNs for a court-application-only hearing linking multiple prosecution
-- cases -- cp_case_hearing.case_urn now resolves to one real case URN (the first of these)
-- rather than CP's comma-joined applicationReference; this table carries the full set so
-- nothing is lost. Rows created before this migration (case_urn still holds the old
-- comma-joined value) have no rows here.
CREATE TABLE cp_case_hearing_related_case (
    id uuid PRIMARY KEY NOT NULL,
    case_hearing_id uuid NOT NULL REFERENCES cp_case_hearing(id) ON DELETE CASCADE,
    case_urn varchar NOT NULL
);

CREATE INDEX idx_cp_case_hearing_related_case_case_hearing ON cp_case_hearing_related_case (case_hearing_id);
