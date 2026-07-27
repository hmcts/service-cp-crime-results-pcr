CREATE TABLE cp_case_marker (
    id uuid PRIMARY KEY NOT NULL,
    case_hearing_id uuid NOT NULL REFERENCES cp_case_hearing(id) ON DELETE CASCADE,
    code varchar,
    description varchar
);

CREATE INDEX idx_cp_case_marker_case_hearing ON cp_case_marker (case_hearing_id);