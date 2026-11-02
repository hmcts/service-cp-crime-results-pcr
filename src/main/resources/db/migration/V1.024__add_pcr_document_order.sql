-- CP's PCR document for a defendant lists every one of their cases resulted in the hearing, in a
-- fixed order, with each case's offences in turn. document_case_order holds that case URN order
-- (comma-joined) on each version so a consumer reading one case can place it in the document;
-- offence sequence holds the order offences were sent in, which the random surrogate id loses.
-- Both stay null on rows ingested before this migration.
ALTER TABLE cp_version
    ADD COLUMN document_case_order varchar;

ALTER TABLE cp_offence
    ADD COLUMN sequence integer;
