CREATE TABLE cp_offence (
    id uuid PRIMARY KEY NOT NULL,
    version_pk uuid REFERENCES cp_version(cp_version_pk) ON DELETE CASCADE,
    court_application_id uuid REFERENCES cp_court_application(id) ON DELETE CASCADE,
    code varchar,
    title varchar,
    wording varchar,
    start_date date,
    end_date date,
    listing_number integer,
    conviction_date date,
    plea_value varchar,
    plea_date date,
    verdict_code varchar,
    -- Exactly one of version_pk/court_application_id is set (design doc §1/§3).
    CONSTRAINT chk_cp_offence_one_parent CHECK (
        (version_pk IS NOT NULL AND court_application_id IS NULL)
        OR (version_pk IS NULL AND court_application_id IS NOT NULL)
    )
);

CREATE INDEX idx_cp_offence_version ON cp_offence (version_pk);
CREATE INDEX idx_cp_offence_court_application ON cp_offence (court_application_id);