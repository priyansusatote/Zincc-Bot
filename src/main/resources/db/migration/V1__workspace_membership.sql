CREATE TABLE zincc.organization (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid()
);

CREATE TABLE zincc.principal (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid()
);

CREATE TABLE zincc.workspace (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES zincc.organization (id),
    display_name text NOT NULL CHECK (length(trim(display_name)) BETWEEN 1 AND 200)
);

CREATE TABLE zincc.workspace_membership (
    workspace_id uuid NOT NULL REFERENCES zincc.workspace (id),
    principal_id uuid NOT NULL REFERENCES zincc.principal (id),
    role text NOT NULL CHECK (role IN ('owner', 'member')),
    PRIMARY KEY (workspace_id, principal_id)
);

ALTER TABLE zincc.workspace_membership ENABLE ROW LEVEL SECURITY;
ALTER TABLE zincc.workspace ENABLE ROW LEVEL SECURITY;

CREATE POLICY own_membership ON zincc.workspace_membership FOR SELECT TO zincc_reader
    USING (workspace_id = nullif(current_setting('zincc.workspace_id', true), '')::uuid
       AND principal_id = nullif(current_setting('zincc.principal_id', true), '')::uuid);

CREATE POLICY member_workspace ON zincc.workspace FOR SELECT TO zincc_reader
    USING (id = nullif(current_setting('zincc.workspace_id', true), '')::uuid
       AND EXISTS (SELECT 1 FROM zincc.workspace_membership m WHERE m.workspace_id = id));

GRANT USAGE ON SCHEMA zincc TO zincc_reader;
GRANT SELECT ON zincc.workspace, zincc.workspace_membership TO zincc_reader;
