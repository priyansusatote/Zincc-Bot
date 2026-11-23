ALTER TABLE zincc.conversation_turn ADD COLUMN failure text;
GRANT UPDATE(failure) ON zincc.conversation_turn TO zincc_reader;
