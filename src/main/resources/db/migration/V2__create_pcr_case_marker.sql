CREATE TABLE pcr_case_marker (
    id uuid PRIMARY KEY NOT NULL,
    case_hearing_id uuid NOT NULL REFERENCES pcr_case_hearing(id) ON DELETE CASCADE,
    code varchar,
    description varchar
);

CREATE INDEX idx_pcr_case_marker_case_hearing ON pcr_case_marker (case_hearing_id);