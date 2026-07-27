CREATE TABLE cp_judicial_result_prompt (
    id uuid PRIMARY KEY NOT NULL,
    judicial_result_id uuid NOT NULL REFERENCES cp_judicial_result(id) ON DELETE CASCADE,
    label varchar,
    value varchar,
    prompt_reference varchar,
    type varchar
);

CREATE INDEX idx_cp_judicial_result_prompt_result ON cp_judicial_result_prompt (judicial_result_id);