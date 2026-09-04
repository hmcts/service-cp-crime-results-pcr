-- custodyType is a separate field on CustodyLocation — custody_location only ever held the establishment name.
ALTER TABLE cp_version
    ADD COLUMN custody_type varchar;
