CREATE TABLE zincc.conversation (
    id uuid PRIMARY KEY,
    workspace_id uuid NOT NULL REFERENCES zincc.workspace,
    owner_id uuid NOT NULL REFERENCES zincc.principal,
    title text NOT NULL,
    revision bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX conversation_directory ON zincc.conversation (workspace_id, owner_id, updated_at DESC);

CREATE TABLE zincc.conversation_turn (
    conversation_id uuid NOT NULL REFERENCES zincc.conversation,
    id uuid NOT NULL,
    base_revision bigint NOT NULL,
    route text NOT NULL,
    question text NOT NULL,
    answer text NOT NULL DEFAULT '',
    state text NOT NULL CHECK (state IN ('running','completed','interrupted')),
    expires_at timestamptz NOT NULL DEFAULT now() + interval '2 minutes',
    PRIMARY KEY (conversation_id, id),
    UNIQUE (conversation_id, base_revision)
);
CREATE UNIQUE INDEX one_running_turn ON zincc.conversation_turn (conversation_id) WHERE state = 'running';

CREATE TABLE zincc.conversation_event (
    conversation_id uuid NOT NULL,
    seq bigint NOT NULL,
    turn_id uuid NOT NULL,
    kind text NOT NULL CHECK (kind IN ('started','delta','completed','interrupted')),
    body text NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (conversation_id, seq),
    FOREIGN KEY (conversation_id, turn_id) REFERENCES zincc.conversation_turn
);

ALTER TABLE zincc.conversation ENABLE ROW LEVEL SECURITY;
ALTER TABLE zincc.conversation_turn ENABLE ROW LEVEL SECURITY;
ALTER TABLE zincc.conversation_event ENABLE ROW LEVEL SECURITY;
CREATE POLICY private_thread ON zincc.conversation TO zincc_reader
    USING (workspace_id = nullif(current_setting('zincc.workspace_id', true), '')::uuid
       AND owner_id = nullif(current_setting('zincc.principal_id', true), '')::uuid
       AND EXISTS (SELECT 1 FROM zincc.workspace w WHERE w.id = workspace_id));
CREATE POLICY private_turn ON zincc.conversation_turn TO zincc_reader
    USING (EXISTS (SELECT 1 FROM zincc.conversation c WHERE c.id = conversation_id));
CREATE POLICY private_event ON zincc.conversation_event TO zincc_reader
    USING (EXISTS (SELECT 1 FROM zincc.conversation c WHERE c.id = conversation_id));

GRANT SELECT, INSERT ON zincc.conversation, zincc.conversation_turn, zincc.conversation_event TO zincc_reader;
GRANT UPDATE (revision, updated_at) ON zincc.conversation TO zincc_reader;
GRANT UPDATE (answer, state) ON zincc.conversation_turn TO zincc_reader;
GRANT DELETE ON zincc.conversation_event TO zincc_reader;
