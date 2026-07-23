CREATE TABLE pcr_judicial_result_prompt (
    id uuid PRIMARY KEY NOT NULL,
    judicial_result_id uuid NOT NULL REFERENCES pcr_judicial_result(id) ON DELETE CASCADE,
    label varchar,
    value varchar,
    prompt_reference varchar,
    type varchar
);

CREATE INDEX idx_pcr_judicial_result_prompt_result ON pcr_judicial_result_prompt (judicial_result_id);