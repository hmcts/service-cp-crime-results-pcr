-- custodyType is a real, separate field on the API contract's CustodyLocation schema
-- (alongside name) — cp_version.custody_location only ever held the establishment name.
-- Sourced going forward from custodialEstablishment.custody; rows written before this
-- migration have it null.
ALTER TABLE cp_version
    ADD COLUMN custody_type varchar;
