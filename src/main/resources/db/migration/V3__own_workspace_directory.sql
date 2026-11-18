-- Queries originate with the identity from the validated OIDC session.
CREATE FUNCTION zincc.list_oidc_workspaces(text, text)
RETURNS TABLE (workspace_id uuid, workspace_name text, role text)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog
AS $$
    SELECT w.id, w.display_name, m.role
    FROM zincc.oidc_identity i
    JOIN zincc.workspace_membership m ON m.principal_id = i.principal_id
    JOIN zincc.workspace w ON w.id = m.workspace_id
    WHERE i.issuer = $1 AND i.subject = $2
    ORDER BY w.display_name, w.id
$$;
REVOKE ALL ON FUNCTION zincc.list_oidc_workspaces(text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION zincc.list_oidc_workspaces(text, text) TO zincc_reader;
CREATE INDEX workspace_membership_by_principal ON zincc.workspace_membership (principal_id, workspace_id);
