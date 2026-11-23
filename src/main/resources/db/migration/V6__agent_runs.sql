CREATE FUNCTION zincc.actor() RETURNS uuid LANGUAGE sql STABLE
AS $$ SELECT nullif(current_setting('zincc.principal_id',true),'')::uuid $$;
CREATE FUNCTION zincc.active_workspace() RETURNS uuid LANGUAGE sql STABLE
AS $$ SELECT nullif(current_setting('zincc.workspace_id',true),'')::uuid $$;

CREATE FUNCTION zincc.can_delegate_to(candidate uuid) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
 SELECT EXISTS(SELECT 1 FROM zincc.workspace_membership m JOIN zincc.workspace_membership d USING(workspace_id)
 WHERE m.workspace_id=zincc.active_workspace() AND m.principal_id=zincc.actor() AND d.principal_id=candidate)
$$;
REVOKE ALL ON FUNCTION zincc.can_delegate_to(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION zincc.can_delegate_to(uuid) TO zincc_reader;

CREATE TABLE zincc.agent (
 id uuid PRIMARY KEY, workspace_id uuid NOT NULL REFERENCES zincc.workspace,
 owner_id uuid NOT NULL REFERENCES zincc.principal, delegate_id uuid REFERENCES zincc.principal,
 version integer NOT NULL CHECK (version>0)
);
CREATE TABLE zincc.agent_version (
 agent_id uuid NOT NULL REFERENCES zincc.agent, version integer NOT NULL,
 name text NOT NULL, instructions text NOT NULL, route text NOT NULL,
 can_read boolean NOT NULL, can_write boolean NOT NULL, delegate_id uuid,
 PRIMARY KEY(agent_id,version)
);
CREATE TABLE zincc.agent_connection (
 workspace_id uuid NOT NULL REFERENCES zincc.workspace, actor_id uuid NOT NULL REFERENCES zincc.principal,
 generation uuid NOT NULL, resource text NOT NULL, reference text NOT NULL, active boolean NOT NULL,
 PRIMARY KEY(workspace_id,actor_id)
);
CREATE TABLE zincc.agent_run (
 id uuid PRIMARY KEY, workspace_id uuid NOT NULL REFERENCES zincc.workspace, actor_id uuid NOT NULL REFERENCES zincc.principal,
 agent_id uuid NOT NULL, version integer NOT NULL, goal text NOT NULL, mode text NOT NULL,
 state text NOT NULL CHECK(state IN ('queued','running','waiting','approved','dispatching','unknown','completed','failed','cancelled')),
 steps integer NOT NULL DEFAULT 0, observation text NOT NULL DEFAULT '', observation_generation uuid, last_step text NOT NULL DEFAULT '',
 outcome text NOT NULL DEFAULT '', created_at timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY (agent_id,version) REFERENCES zincc.agent_version
);
CREATE INDEX agent_runs_owner ON zincc.agent_run(workspace_id,actor_id,created_at DESC);
CREATE TABLE zincc.agent_outbox (run_id uuid PRIMARY KEY REFERENCES zincc.agent_run, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE zincc.agent_action (
 run_id uuid PRIMARY KEY REFERENCES zincc.agent_run, generation uuid NOT NULL, resource text NOT NULL,
 title text NOT NULL, digest text NOT NULL, receipt text, approved_at timestamptz
);
ALTER TABLE zincc.agent ENABLE ROW LEVEL SECURITY;
CREATE POLICY view_agent ON zincc.agent FOR SELECT TO zincc_reader USING (
 workspace_id=zincc.active_workspace() AND zincc.actor() IN (owner_id,delegate_id));
CREATE POLICY edit_agent ON zincc.agent FOR ALL TO zincc_reader USING (
 workspace_id=zincc.active_workspace() AND owner_id=zincc.actor());
ALTER TABLE zincc.agent_version ENABLE ROW LEVEL SECURITY;
CREATE POLICY view_version ON zincc.agent_version FOR SELECT TO zincc_reader USING (
 EXISTS(SELECT 1 FROM zincc.agent a WHERE a.id=agent_id));
CREATE POLICY add_version ON zincc.agent_version FOR INSERT TO zincc_reader WITH CHECK (
 EXISTS(SELECT 1 FROM zincc.agent a WHERE a.id=agent_id AND a.owner_id=zincc.actor() AND a.version=agent_version.version));
ALTER TABLE zincc.agent_connection ENABLE ROW LEVEL SECURITY;
CREATE POLICY own_connection ON zincc.agent_connection TO zincc_reader USING(workspace_id=zincc.active_workspace() AND actor_id=zincc.actor());
ALTER TABLE zincc.agent_run ENABLE ROW LEVEL SECURITY;
CREATE POLICY own_run ON zincc.agent_run TO zincc_reader USING(workspace_id=zincc.active_workspace() AND actor_id=zincc.actor());
ALTER TABLE zincc.agent_action ENABLE ROW LEVEL SECURITY;
CREATE POLICY own_action ON zincc.agent_action TO zincc_reader USING(EXISTS(SELECT 1 FROM zincc.agent_run r WHERE r.id=run_id));
ALTER TABLE zincc.agent_outbox ENABLE ROW LEVEL SECURITY;
CREATE POLICY own_outbox ON zincc.agent_outbox TO zincc_reader USING(EXISTS(SELECT 1 FROM zincc.agent_run r WHERE r.id=run_id));

GRANT SELECT,INSERT ON zincc.agent,zincc.agent_version,zincc.agent_connection,zincc.agent_run,zincc.agent_action,zincc.agent_outbox TO zincc_reader;
GRANT UPDATE(version,delegate_id) ON zincc.agent TO zincc_reader;
GRANT UPDATE(generation,active,resource,reference) ON zincc.agent_connection TO zincc_reader;
GRANT UPDATE(state,steps,observation,observation_generation,last_step,outcome) ON zincc.agent_run TO zincc_reader;
GRANT UPDATE(approved_at,receipt) ON zincc.agent_action TO zincc_reader;
GRANT DELETE ON zincc.agent_outbox TO zincc_reader;

-- Dispatcher may discover only routing IDs. Each activity establishes its own scope.
CREATE FUNCTION zincc.pending_agent_work() RETURNS TABLE(run_id uuid,workspace_id uuid,actor_id uuid)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
 SELECT r.id,r.workspace_id,r.actor_id FROM zincc.agent_outbox o JOIN zincc.agent_run r ON r.id=o.run_id
 ORDER BY o.created_at LIMIT 32
$$;
REVOKE ALL ON FUNCTION zincc.pending_agent_work() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION zincc.pending_agent_work() TO zincc_reader;
