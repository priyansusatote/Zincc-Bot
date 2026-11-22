-- Existing turns have no reliable usage record or provider identity.
ALTER TABLE zincc.conversation_turn
    ADD COLUMN provider text,
    ADD COLUMN model_name text,
    ADD COLUMN accepted_at timestamptz,
    ADD COLUMN input_tokens integer CHECK (input_tokens >= 0),
    ADD COLUMN output_tokens integer CHECK (output_tokens >= 0);
ALTER TABLE zincc.conversation_turn ALTER COLUMN accepted_at SET DEFAULT clock_timestamp();
GRANT UPDATE (input_tokens, output_tokens) ON zincc.conversation_turn TO zincc_reader;
