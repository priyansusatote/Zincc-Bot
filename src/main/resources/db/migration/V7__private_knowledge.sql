CREATE TABLE zincc.document (
    id uuid PRIMARY KEY, workspace_id uuid NOT NULL REFERENCES zincc.workspace,
    owner_id uuid NOT NULL REFERENCES zincc.principal, name text NOT NULL, checksum text NOT NULL,
    source bytea NOT NULL, state text NOT NULL CHECK(state IN ('processing','ready','failed')),
    detail text NOT NULL DEFAULT '', pages int NOT NULL DEFAULT 0,
    missing_pages jsonb NOT NULL DEFAULT '[]', scanned_pages jsonb NOT NULL DEFAULT '[]', worker_revision text NOT NULL,
    embedding_revision text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE(workspace_id, owner_id, checksum)
);
CREATE TABLE zincc.document_chunk (
    document_id uuid NOT NULL REFERENCES zincc.document ON DELETE CASCADE,
    ordinal int NOT NULL, page int NOT NULL, content text NOT NULL, vector real[] NOT NULL,
    search tsvector GENERATED ALWAYS AS (to_tsvector('english',content)) STORED,
    PRIMARY KEY(document_id, ordinal)
);
CREATE INDEX document_chunk_search ON zincc.document_chunk USING gin(search);
CREATE TABLE zincc.document_dataset (
    document_id uuid NOT NULL REFERENCES zincc.document ON DELETE CASCADE,
    ordinal int NOT NULL, page int NOT NULL, data jsonb NOT NULL,
    PRIMARY KEY(document_id, ordinal)
);
CREATE TABLE zincc.document_dashboard (
    id uuid PRIMARY KEY, document_id uuid NOT NULL, dataset int NOT NULL,
    name text NOT NULL, measure int NOT NULL, category int NOT NULL, unit text NOT NULL,
    selected text, revision int NOT NULL CHECK(revision>0),
    FOREIGN KEY (document_id, dataset) REFERENCES zincc.document_dataset ON DELETE CASCADE
);

ALTER TABLE zincc.document ENABLE ROW LEVEL SECURITY;
CREATE POLICY private_document ON zincc.document TO zincc_reader
    USING (workspace_id=zincc.active_workspace() AND owner_id=zincc.actor()
        AND EXISTS(SELECT 1 FROM zincc.workspace w WHERE w.id=workspace_id));
ALTER TABLE zincc.document_chunk ENABLE ROW LEVEL SECURITY;
CREATE POLICY private_chunk ON zincc.document_chunk TO zincc_reader
    USING (EXISTS(SELECT 1 FROM zincc.document d WHERE d.id=document_id));
ALTER TABLE zincc.document_dataset ENABLE ROW LEVEL SECURITY;
CREATE POLICY private_dataset ON zincc.document_dataset TO zincc_reader
    USING (EXISTS(SELECT 1 FROM zincc.document d WHERE d.id=document_id));
ALTER TABLE zincc.document_dashboard ENABLE ROW LEVEL SECURITY;
CREATE POLICY private_dashboard ON zincc.document_dashboard TO zincc_reader
    USING (EXISTS(SELECT 1 FROM zincc.document d WHERE d.id=document_id));

GRANT SELECT,INSERT,DELETE ON zincc.document,zincc.document_dashboard TO zincc_reader;
GRANT SELECT,INSERT ON zincc.document_chunk,zincc.document_dataset TO zincc_reader;
GRANT UPDATE(state,detail,pages,missing_pages,scanned_pages,worker_revision,embedding_revision,updated_at) ON zincc.document TO zincc_reader;
GRANT UPDATE(name,measure,category,unit,selected,revision) ON zincc.document_dashboard TO zincc_reader;
