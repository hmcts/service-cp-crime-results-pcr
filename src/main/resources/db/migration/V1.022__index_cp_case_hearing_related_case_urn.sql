-- Supports resolving a court-application hearing by any of its linked case URNs, not just the
-- one held on cp_case_hearing.case_urn.
CREATE INDEX idx_cp_case_hearing_related_case_urn ON cp_case_hearing_related_case (case_urn, case_hearing_id);
