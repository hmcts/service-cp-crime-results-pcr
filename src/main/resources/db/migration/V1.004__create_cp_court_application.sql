CREATE TABLE cp_court_application (
    id uuid PRIMARY KEY NOT NULL,
    version_pk uuid NOT NULL REFERENCES cp_version(cp_version_pk) ON DELETE CASCADE,
    reference varchar,
    type varchar,
    decision varchar,
    decision_date date,
    response varchar,
    response_date date
);

CREATE INDEX idx_cp_court_application_version ON cp_court_application (version_pk);