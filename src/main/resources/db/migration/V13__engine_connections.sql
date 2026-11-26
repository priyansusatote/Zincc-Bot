CREATE TABLE zincc.engine_connection (
 id uuid PRIMARY KEY, workspace_id uuid NOT NULL REFERENCES zincc.workspace, actor_id uuid NOT NULL REFERENCES zincc.principal,
 label text NOT NULL, active boolean NOT NULL DEFAULT true, generation integer NOT NULL DEFAULT 1,
 UNIQUE(workspace_id,actor_id,label)
);
CREATE TABLE zincc.engine_choice (
 workspace_id uuid NOT NULL REFERENCES zincc.workspace, actor_id uuid NOT NULL REFERENCES zincc.principal,
 agent_id uuid NOT NULL REFERENCES zincc.agent, thread_id uuid REFERENCES zincc.conversation,
 connection_id uuid NOT NULL REFERENCES zincc.engine_connection, model text NOT NULL,
 UNIQUE NULLS NOT DISTINCT(workspace_id,actor_id,agent_id,thread_id)
);
CREATE TABLE zincc.engine_receipt (
 run_id uuid PRIMARY KEY REFERENCES zincc.agent_run, connection_id uuid NOT NULL REFERENCES zincc.engine_connection,
 generation integer NOT NULL, model text NOT NULL, thread_id uuid REFERENCES zincc.conversation,
 acknowledged text
);
ALTER TABLE zincc.engine_connection ENABLE ROW LEVEL SECURITY;
ALTER TABLE zincc.engine_choice ENABLE ROW LEVEL SECURITY;
ALTER TABLE zincc.engine_receipt ENABLE ROW LEVEL SECURITY;
CREATE POLICY private_engine ON zincc.engine_connection TO zincc_reader USING (
 workspace_id=zincc.active_workspace() AND actor_id=zincc.actor() AND EXISTS(SELECT 1 FROM zincc.workspace w WHERE w.id=workspace_id)
);
CREATE POLICY private_choice ON zincc.engine_choice TO zincc_reader USING (
 workspace_id=zincc.active_workspace() AND actor_id=zincc.actor() AND EXISTS(SELECT 1 FROM zincc.agent a WHERE a.id=agent_id)
 AND EXISTS(SELECT 1 FROM zincc.engine_connection c WHERE c.id=connection_id)
 AND (thread_id IS NULL OR EXISTS(SELECT 1 FROM zincc.conversation t WHERE t.id=thread_id))
);
CREATE POLICY private_engine_receipt ON zincc.engine_receipt TO zincc_reader USING (
 EXISTS(SELECT 1 FROM zincc.agent_run r WHERE r.id=run_id)
 AND EXISTS(SELECT 1 FROM zincc.engine_connection c WHERE c.id=connection_id)
);
GRANT SELECT,INSERT ON zincc.engine_connection,zincc.engine_choice,zincc.engine_receipt TO zincc_reader;
GRANT UPDATE(active,generation) ON zincc.engine_connection TO zincc_reader;
GRANT UPDATE(connection_id,model),DELETE ON zincc.engine_choice TO zincc_reader;
GRANT UPDATE(acknowledged) ON zincc.engine_receipt TO zincc_reader;
