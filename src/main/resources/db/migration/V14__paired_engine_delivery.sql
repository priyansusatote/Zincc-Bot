ALTER TABLE zincc.engine_connection
 ADD COLUMN device uuid UNIQUE REFERENCES zincc.runner,
 ADD COLUMN provider text NOT NULL DEFAULT 'fixture',
 ADD COLUMN catalog text NOT NULL DEFAULT E'fixture/word-count\nfixture/checklist',
 ADD CONSTRAINT engine_location CHECK ((provider='fixture') = (device IS NULL));

ALTER TABLE zincc.engine_receipt ADD COLUMN claimed_at timestamptz;
GRANT UPDATE(claimed_at) ON zincc.engine_receipt TO zincc_reader;
CREATE INDEX engine_pending ON zincc.engine_receipt(connection_id,run_id) WHERE acknowledged IS NULL;
