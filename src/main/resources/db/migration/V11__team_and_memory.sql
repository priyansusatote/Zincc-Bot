CREATE TABLE zincc.team_member (
 parent_id uuid NOT NULL REFERENCES zincc.agent_run,
 slot integer NOT NULL CHECK (slot BETWEEN 1 AND 3),
 child_id uuid NOT NULL UNIQUE REFERENCES zincc.agent_run,
 PRIMARY KEY(parent_id,slot), CHECK(parent_id<>child_id)
);
ALTER TABLE zincc.team_member ENABLE ROW LEVEL SECURITY;
CREATE POLICY team_scope ON zincc.team_member TO zincc_reader USING (
 EXISTS(SELECT 1 FROM zincc.agent_run r WHERE r.id=parent_id)
 AND EXISTS(SELECT 1 FROM zincc.agent_run r WHERE r.id=child_id));
GRANT SELECT,INSERT ON zincc.team_member TO zincc_reader;

CREATE TABLE zincc.agent_memory (
 id uuid PRIMARY KEY, workspace_id uuid NOT NULL REFERENCES zincc.workspace,
 owner_id uuid NOT NULL REFERENCES zincc.principal, source_id uuid NOT NULL REFERENCES zincc.agent_run ON DELETE CASCADE,
 topic text NOT NULL CHECK(length(topic) BETWEEN 1 AND 80),
 fact text NOT NULL CHECK(length(fact) BETWEEN 1 AND 600),
 revision integer NOT NULL DEFAULT 1 CHECK(revision>0),
 UNIQUE(workspace_id,owner_id,topic)
);
ALTER TABLE zincc.agent_memory ENABLE ROW LEVEL SECURITY;
CREATE POLICY own_memory ON zincc.agent_memory TO zincc_reader USING (
 workspace_id=zincc.active_workspace() AND owner_id=zincc.actor()
 AND EXISTS(SELECT 1 FROM zincc.workspace w WHERE w.id=workspace_id)
 AND EXISTS(SELECT 1 FROM zincc.agent_run r WHERE r.id=source_id));
GRANT SELECT,INSERT,DELETE ON zincc.agent_memory TO zincc_reader;
GRANT UPDATE(fact,revision,source_id) ON zincc.agent_memory TO zincc_reader;
CREATE TABLE zincc.memory_epoch (
 workspace_id uuid NOT NULL REFERENCES zincc.workspace, owner_id uuid NOT NULL REFERENCES zincc.principal,
 generation bigint NOT NULL DEFAULT 0, PRIMARY KEY(workspace_id,owner_id)
);
ALTER TABLE zincc.memory_epoch ENABLE ROW LEVEL SECURITY;
CREATE POLICY own_epoch ON zincc.memory_epoch TO zincc_reader USING (
 workspace_id=zincc.active_workspace() AND owner_id=zincc.actor()
 AND EXISTS(SELECT 1 FROM zincc.workspace w WHERE w.id=workspace_id));
GRANT SELECT ON zincc.memory_epoch TO zincc_reader;
-- The same transaction that edits or cascades a note invalidates its context projection.
CREATE FUNCTION zincc.advance_memory_epoch() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 INSERT INTO zincc.memory_epoch(workspace_id,owner_id,generation)
 VALUES(coalesce(NEW.workspace_id,OLD.workspace_id),coalesce(NEW.owner_id,OLD.owner_id),1)
 ON CONFLICT(workspace_id,owner_id) DO UPDATE SET generation=zincc.memory_epoch.generation+1;
 RETURN NULL;
END $$;
REVOKE ALL ON FUNCTION zincc.advance_memory_epoch() FROM PUBLIC;
CREATE TRIGGER memory_changed AFTER INSERT OR UPDATE OR DELETE ON zincc.agent_memory
 FOR EACH ROW EXECUTE FUNCTION zincc.advance_memory_epoch();
