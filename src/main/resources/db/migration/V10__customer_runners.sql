CREATE TABLE zincc.runner (
    id uuid PRIMARY KEY,
    workspace_id uuid NOT NULL REFERENCES zincc.workspace ON DELETE CASCADE,
    owner_id uuid NOT NULL REFERENCES zincc.principal ON DELETE CASCADE,
    token_hash bytea,
    state text NOT NULL CHECK (state IN ('pairing','active','paused','revoked')),
    pair_until timestamptz NOT NULL DEFAULT now()+interval '10 minutes',
    seen timestamptz,
    folder boolean NOT NULL DEFAULT false,
    browser boolean NOT NULL DEFAULT false
);
CREATE TABLE zincc.runner_job (
    id uuid PRIMARY KEY,
    device uuid NOT NULL REFERENCES zincc.runner ON DELETE CASCADE,
    kind text NOT NULL CHECK (kind IN ('inventory','browser')),
    state text NOT NULL CHECK (state IN ('queued','leased','complete','failed','cancelled','expired')),
    created timestamptz NOT NULL DEFAULT now(),
    lease_until timestamptz,
    deadline timestamptz,
    result integer CHECK(result BETWEEN 0 AND 10000)
);
CREATE UNIQUE INDEX runner_one_active ON zincc.runner_job(device) WHERE state IN ('queued','leased');
CREATE INDEX runner_owner ON zincc.runner(workspace_id,owner_id);
CREATE INDEX runner_history ON zincc.runner_job(device,created DESC);
ALTER TABLE zincc.runner ENABLE ROW LEVEL SECURITY;
ALTER TABLE zincc.runner_job ENABLE ROW LEVEL SECURITY;
CREATE POLICY own_runner ON zincc.runner TO zincc_reader USING (
    workspace_id=zincc.active_workspace() AND owner_id=zincc.actor()
    AND EXISTS(SELECT 1 FROM zincc.workspace w WHERE w.id=workspace_id));
CREATE POLICY own_runner_job ON zincc.runner_job TO zincc_reader USING (
    EXISTS(SELECT 1 FROM zincc.runner r WHERE r.id=device));
GRANT SELECT,INSERT,UPDATE ON zincc.runner,zincc.runner_job TO zincc_reader;
