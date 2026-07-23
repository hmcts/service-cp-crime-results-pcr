CREATE TABLE pcr_version (
    pcr_version_pk uuid PRIMARY KEY NOT NULL,
    source_id varchar,
    defendant_id uuid NOT NULL,
    case_hearing_id uuid NOT NULL REFERENCES pcr_case_hearing(id),
    custody_location varchar,
    master_defendant_id uuid,
    next_hearing_date date,
    next_hearing_time varchar,
    next_hearing_court_house_code varchar,
    next_hearing_court_house_name varchar,
    next_hearing_id uuid,
    created_at timestamp NOT NULL,
    expires_at timestamp NOT NULL
);

-- source_id is nullable and not part of the PK (id shape not yet locked — design doc §5);
-- this partial index still guarantees no two rows ever claim the same real id once one exists.
CREATE UNIQUE INDEX idx_pcr_version_source_defendant ON pcr_version (source_id, defendant_id) WHERE source_id IS NOT NULL;
-- Retention purge sweeps directly off this column (design doc §4) — no event-driven delete.
CREATE INDEX idx_pcr_version_expires_at ON pcr_version (expires_at);
-- version=latest lookup path (design doc §6).
CREATE INDEX idx_pcr_version_case_hearing_defendant ON pcr_version (case_hearing_id, defendant_id);