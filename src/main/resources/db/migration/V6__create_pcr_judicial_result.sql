CREATE TABLE pcr_judicial_result (
    id uuid PRIMARY KEY NOT NULL,
    offence_id uuid REFERENCES pcr_offence(id) ON DELETE CASCADE,
    court_application_id uuid REFERENCES pcr_court_application(id) ON DELETE CASCADE,
    result_code varchar,
    result_text varchar,
    post_hearing_custody_status varchar,
    financial boolean,
    category varchar,
    convicted boolean,
    concurrent boolean,
    consecutive_to_date date,
    consecutive_to_court_name varchar,
    fine_amount numeric(12,2),
    imprisonment_period varchar,
    total_custodial_period varchar,
    -- Polymorphic parent (design doc §3) — exactly one of offence_id/court_application_id is set.
    CONSTRAINT chk_pcr_judicial_result_one_parent CHECK (
        (offence_id IS NOT NULL AND court_application_id IS NULL)
        OR (offence_id IS NULL AND court_application_id IS NOT NULL)
    )
);

CREATE INDEX idx_pcr_judicial_result_offence ON pcr_judicial_result (offence_id);
CREATE INDEX idx_pcr_judicial_result_court_application ON pcr_judicial_result (court_application_id);