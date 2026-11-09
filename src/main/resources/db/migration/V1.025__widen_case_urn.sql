-- A case URN can be up to 36 characters (AMP-1301), but these columns were capped at 30, so
-- ingesting a hearing for a longer URN failed and the case was never stored. Unbounded varchar
-- matches cp_case_hearing_related_case.case_urn; widening a varchar is a catalogue-only change in
-- Postgres, so no table rewrite or index rebuild.
ALTER TABLE cp_case_hearing
    ALTER COLUMN case_urn TYPE varchar;

ALTER TABLE cp_offence
    ALTER COLUMN case_urn TYPE varchar;
