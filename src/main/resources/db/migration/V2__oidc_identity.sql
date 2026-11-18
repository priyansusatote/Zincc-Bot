CREATE TABLE zincc.oidc_identity (
    issuer text COLLATE "C" NOT NULL CHECK (issuer <> ''),
    subject text COLLATE "C" NOT NULL CHECK (length(subject) BETWEEN 1 AND 255),
    principal_id uuid NOT NULL REFERENCES zincc.principal (id),
    PRIMARY KEY (issuer, subject)
);

-- Limit the runtime login to resolving an exact, provisioned identity.
CREATE FUNCTION zincc.resolve_oidc_principal(text, text) RETURNS uuid
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog
AS $$
    SELECT principal_id FROM zincc.oidc_identity WHERE issuer = $1 AND subject = $2
$$;
REVOKE ALL ON FUNCTION zincc.resolve_oidc_principal(text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION zincc.resolve_oidc_principal(text, text) TO zincc_reader;
