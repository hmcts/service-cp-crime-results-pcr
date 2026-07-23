CREATE TABLE pcr_offence (
    id uuid PRIMARY KEY NOT NULL,
    version_pk uuid REFERENCES pcr_version(pcr_version_pk) ON DELETE CASCADE,
    court_application_id uuid REFERENCES pcr_court_application(id) ON DELETE CASCADE,
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
    -- Polymorphic parent (design doc §1/§3) — exactly one of version_pk/court_application_id is set.
    CONSTRAINT chk_pcr_offence_one_parent CHECK (
        (version_pk IS NOT NULL AND court_application_id IS NULL)
        OR (version_pk IS NULL AND court_application_id IS NOT NULL)
    )
);

CREATE INDEX idx_pcr_offence_version ON pcr_offence (version_pk);
CREATE INDEX idx_pcr_offence_court_application ON pcr_offence (court_application_id);