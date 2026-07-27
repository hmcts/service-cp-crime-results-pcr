CREATE TABLE cp_case_hearing (
    id uuid PRIMARY KEY NOT NULL,
    case_urn varchar(30) NOT NULL,
    hearing_id uuid NOT NULL,
    court_house_code varchar,
    court_house_name varchar,
    hearing_date date,
    hearing_outcome varchar,
    created_at timestamp NOT NULL
);

CREATE UNIQUE INDEX idx_cp_case_hearing_urn_hearing ON cp_case_hearing (case_urn, hearing_id);
