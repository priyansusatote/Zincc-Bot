CREATE TABLE zincc.feed (
 id uuid PRIMARY KEY, workspace_id uuid NOT NULL REFERENCES zincc.workspace, owner_id uuid NOT NULL REFERENCES zincc.principal,
 provider text NOT NULL CHECK(provider IN ('gmail','slack','calendar')), label text NOT NULL, resource text NOT NULL,
 fixture boolean NOT NULL, sample_version int NOT NULL DEFAULT 1, secret text NOT NULL, grant_scope text NOT NULL, external_team text NOT NULL DEFAULT '',
 state text NOT NULL DEFAULT 'syncing', checkpoint text NOT NULL DEFAULT '', page text NOT NULL DEFAULT '', anchor text NOT NULL DEFAULT '',
 epoch uuid NOT NULL DEFAULT gen_random_uuid(), changed boolean NOT NULL DEFAULT false,
 next_sync timestamptz NOT NULL DEFAULT now(), synced_at timestamptz
);
CREATE INDEX feed_due ON zincc.feed(next_sync) WHERE state <> 'revoked';
CREATE TABLE zincc.feed_item (
 feed_id uuid NOT NULL REFERENCES zincc.feed ON DELETE CASCADE, external_id text NOT NULL, summary text NOT NULL,
 stamp text NOT NULL, epoch uuid NOT NULL, PRIMARY KEY(feed_id,external_id)
);
CREATE TABLE zincc.skill_install (
 workspace_id uuid NOT NULL REFERENCES zincc.workspace, owner_id uuid NOT NULL REFERENCES zincc.principal,
 package text NOT NULL, version int NOT NULL, digest text NOT NULL, PRIMARY KEY(workspace_id,owner_id,package)
);
CREATE TABLE zincc.routine (
 id uuid PRIMARY KEY, workspace_id uuid NOT NULL REFERENCES zincc.workspace, owner_id uuid NOT NULL REFERENCES zincc.principal,
 name text NOT NULL, package text NOT NULL, version int NOT NULL, digest text NOT NULL,
 trigger_kind text NOT NULL CHECK(trigger_kind IN ('daily','event')), zone text NOT NULL, local_time time NOT NULL,
 feeds uuid[] NOT NULL, document_id uuid, dashboard_id uuid, paused boolean NOT NULL DEFAULT false, next_time timestamptz
);
CREATE TABLE zincc.routine_run (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), routine_id uuid NOT NULL REFERENCES zincc.routine ON DELETE CASCADE,
 cause text NOT NULL, state text NOT NULL DEFAULT 'queued', receipt text NOT NULL DEFAULT '', used_feeds uuid[] NOT NULL DEFAULT '{}', created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(routine_id,cause)
);
CREATE INDEX routine_due ON zincc.routine(next_time) WHERE NOT paused;
CREATE INDEX routine_run_due ON zincc.routine_run(created_at) WHERE state='queued';
CREATE TABLE zincc.feed_signal (
 feed_id uuid NOT NULL REFERENCES zincc.feed ON DELETE CASCADE, event_id text NOT NULL, arrived timestamptz DEFAULT now(), PRIMARY KEY(feed_id,event_id)
);
DO $$
DECLARE t text;
BEGIN
 FOREACH t IN ARRAY ARRAY['feed','skill_install','routine'] LOOP
  EXECUTE format('ALTER TABLE zincc.%I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('CREATE POLICY private_scope ON zincc.%I TO zincc_reader USING
    (workspace_id=zincc.active_workspace() AND owner_id=zincc.actor() AND EXISTS(SELECT 1 FROM zincc.workspace w WHERE w.id=workspace_id))',t);
 END LOOP;
 FOREACH t IN ARRAY ARRAY['feed_item','feed_signal'] LOOP
  EXECUTE format('ALTER TABLE zincc.%I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('CREATE POLICY private_feed ON zincc.%I TO zincc_reader USING (EXISTS(SELECT 1 FROM zincc.feed f WHERE f.id=feed_id))',t);
 END LOOP;
END $$;
ALTER TABLE zincc.routine_run ENABLE ROW LEVEL SECURITY;
CREATE POLICY private_run ON zincc.routine_run TO zincc_reader USING (EXISTS(SELECT 1 FROM zincc.routine r WHERE r.id=routine_id));
GRANT SELECT,INSERT,UPDATE,DELETE ON zincc.feed,zincc.feed_item,zincc.skill_install,zincc.routine,zincc.routine_run,zincc.feed_signal TO zincc_reader;

-- The poller discovers routing IDs only. Every operation rechecks membership under RLS.
CREATE FUNCTION zincc.integration_due() RETURNS TABLE(kind text,id uuid,workspace_id uuid,owner_id uuid)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
 SELECT q.kind,q.id,q.workspace_id,q.owner_id FROM (
  SELECT 'feed' AS kind,f.id,f.workspace_id,f.owner_id,f.next_sync AS due FROM zincc.feed f WHERE f.state<>'revoked' AND f.next_sync<=now()
  UNION ALL SELECT 'daily',r.id,r.workspace_id,r.owner_id,r.next_time FROM zincc.routine r WHERE NOT r.paused AND r.next_time<=now()
  UNION ALL SELECT 'run',j.id,r.workspace_id,r.owner_id,j.created_at FROM zincc.routine_run j JOIN zincc.routine r ON r.id=j.routine_id WHERE j.state='queued'
 ) q JOIN zincc.workspace_membership m ON m.workspace_id=q.workspace_id AND m.principal_id=q.owner_id ORDER BY q.due,q.id LIMIT 32
$$;
CREATE FUNCTION zincc.slack_routes(team text, channel text) RETURNS TABLE(id uuid,workspace_id uuid,owner_id uuid)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
 SELECT f.id,f.workspace_id,f.owner_id FROM zincc.feed f JOIN zincc.workspace_membership m ON m.workspace_id=f.workspace_id AND m.principal_id=f.owner_id
 WHERE f.provider='slack' AND NOT f.fixture AND f.external_team=team AND f.resource=channel AND f.state<>'revoked'
$$;
REVOKE ALL ON FUNCTION zincc.integration_due(),zincc.slack_routes(text,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION zincc.integration_due(),zincc.slack_routes(text,text) TO zincc_reader;
