package io.zincc.knowledge;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
@Profile("knowledge")
class KnowledgeStore {

	record Scope(UUID workspace, UUID principal) {
	}

	record Document(UUID id, String name, String checksum, String state, String detail, int pages,
			JsonNode missingPages, JsonNode scannedPages, String workerRevision, String embeddingRevision) {
	}

	record Claim(UUID id, Instant started) {
	}

	record Dataset(int ordinal, int page, JsonNode data) {
	}

	record Evidence(int page, String text) {
	}

	record Dashboard(UUID id, int dataset, String name, int measure, int category, String unit, String selected,
			int revision) {
	}

	record Settings(int dataset, String name, int measure, int category, String unit, String selected,
			Integer expectedRevision) {
	}

	private record Candidate(int ordinal, Evidence evidence, double score) {
	}

	private final JdbcTemplate db;

	private final TransactionTemplate tx;

	private final JsonMapper json;

	KnowledgeStore(DataSource source, JsonMapper json) {
		this.db = new JdbcTemplate(source);
		this.tx = new TransactionTemplate(new DataSourceTransactionManager(source));
		this.json = json;
		if (!Boolean.TRUE.equals(db.queryForObject("""
				SELECT row_security_active('zincc.document') AND row_security_active('zincc.document_chunk')
				AND row_security_active('zincc.document_dataset') AND row_security_active('zincc.document_dashboard')
				""", Boolean.class)))
			throw new IllegalStateException("Knowledge requires active row security");
	}

	<T> T scoped(Scope scope, Supplier<T> call) {
		return tx.execute(status -> {
			db.queryForMap(
					"SELECT set_config('zincc.workspace_id',?,true) w, set_config('zincc.principal_id',?,true) p",
					scope.workspace.toString(), scope.principal.toString());
			if (!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM zincc.workspace WHERE id=?)",
					Boolean.class, scope.workspace)))
				throw missing();
			return call.get();
		});
	}

	private static final String DOCUMENTS = """
			SELECT id,name,checksum,state,detail,pages,missing_pages::text,scanned_pages::text,worker_revision,embedding_revision
			FROM zincc.document
			""";

	List<Document> documents(Scope scope) {
		return scoped(scope, () -> documents(" ORDER BY updated_at DESC,id LIMIT 100"));
	}

	private List<Document> documents(String where, Object... args) {
		return db.query(DOCUMENTS + where,
				(rs, i) -> new Document(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
						rs.getString(5), rs.getInt(6), json.readTree(rs.getString(7)), json.readTree(rs.getString(8)),
						rs.getString(9), rs.getString(10)),
				args);
	}

	Document ready(UUID id) {
		var doc = documents(" WHERE id=? FOR SHARE", id).stream().findFirst().orElseThrow(KnowledgeStore::missing);
		if (!doc.state.equals("ready"))
			throw conflict("Document is not ready");
		return doc;
	}

	Document document(Scope scope, UUID id) {
		return scoped(scope,
				() -> documents(" WHERE id=?", id).stream().findFirst().orElseThrow(KnowledgeStore::missing));
	}

	Claim claim(Scope scope, String name, byte[] source, String revision) {
		String checksum;
		try {
			checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
		return scoped(scope, () -> {
			db.queryForMap("SELECT pg_advisory_xact_lock(hashtextextended(?,47))", scope.toString());
			var matches = documents(" WHERE checksum=? FOR UPDATE", checksum);
			if (!matches.isEmpty() && matches.getFirst().state.equals("ready"))
				return new Claim(matches.getFirst().id, null);
			UUID id;
			if (matches.isEmpty()) {
				if (Boolean.TRUE.equals(db.queryForObject("SELECT count(*)>=100 FROM zincc.document", Boolean.class)))
					throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Document library is full");
				id = UUID.randomUUID();
				db.update(
						"""
								INSERT INTO zincc.document(id,workspace_id,owner_id,name,checksum,source,state,worker_revision,embedding_revision)
								VALUES(?,?,?,?,?,?,'processing',?,?)
								""",
						id, scope.workspace, scope.principal, name, checksum, source, revision,
						KnowledgeWorker.EMBEDDING_REVISION);
			}
			else {
				id = matches.getFirst().id;
				if (db.update(
						"""
								UPDATE zincc.document SET state='processing',detail='',worker_revision=?,embedding_revision=?,updated_at=clock_timestamp()
								WHERE id=? AND (state='failed' OR updated_at<clock_timestamp()-interval '2 minutes')
								""",
						revision, KnowledgeWorker.EMBEDDING_REVISION, id) == 0)
					return new Claim(id, null);
			}
			return new Claim(id, db.queryForObject("SELECT updated_at FROM zincc.document WHERE id=?",
					(rs, i) -> rs.getTimestamp(1).toInstant(), id));
		});
	}

	void failed(Scope scope, Claim claim, String detail) {
		scoped(scope, () -> db.update("""
				UPDATE zincc.document SET state='failed',detail=?,updated_at=clock_timestamp()
				WHERE id=? AND state='processing' AND updated_at=?
				""", detail, claim.id, java.sql.Timestamp.from(claim.started)));
	}

	void publish(Scope scope, Claim claim, JsonNode parsed) {
		scoped(scope, () -> {
			if (db.update("""
					UPDATE zincc.document SET state='ready',detail=?,pages=?,missing_pages=CAST(? AS jsonb),
						scanned_pages=CAST(? AS jsonb),updated_at=clock_timestamp()
					WHERE id=? AND state='processing' AND updated_at=?
					""", parsed.path("bounded").asBoolean() ? "Extraction reached its configured limit." : "",
					parsed.path("pages").asInt(), parsed.path("missing").toString(), parsed.path("scanned").toString(),
					claim.id, java.sql.Timestamp.from(claim.started)) != 1)
				throw missing();
			int ordinal = 0;
			for (var chunk : parsed.path("chunks"))
				db.update(
						"INSERT INTO zincc.document_chunk(document_id,ordinal,page,content,vector) VALUES(?,?,?,?,CAST(? AS real[]))",
						claim.id, ++ordinal, chunk.path("page").asInt(), chunk.path("text").asString(),
						chunk.path("vector").toString().replace('[', '{').replace(']', '}'));
			ordinal = 0;
			for (var table : parsed.path("tables"))
				db.update("INSERT INTO zincc.document_dataset VALUES(?,?,?,CAST(? AS jsonb))", claim.id, ++ordinal,
						table.path("page").asInt(), table.toString());
			return true;
		});
	}

	void delete(Scope scope, UUID id) {
		scoped(scope, () -> {
			if (db.update("DELETE FROM zincc.document WHERE id=?", id) != 1)
				throw missing();
			return true;
		});
	}

	byte[] source(Scope scope, UUID id) {
		return scoped(scope,
				() -> db.query("SELECT source FROM zincc.document WHERE id=?", (rs, i) -> rs.getBytes(1), id)
					.stream()
					.findFirst()
					.orElseThrow(KnowledgeStore::missing));
	}

	List<Evidence> page(Scope scope, UUID id, int page) {
		return scoped(scope, () -> {
			var doc = ready(id);
			if (page < 1 || page > doc.pages)
				throw missing();
			return db.query("SELECT content FROM zincc.document_chunk WHERE document_id=? AND page=? ORDER BY ordinal",
					(rs, i) -> new Evidence(page, rs.getString(1)), id, page);
		});
	}

	List<Evidence> retrieve(Scope scope, UUID id, String query, JsonNode vector) {
		return scoped(scope, () -> {
			var doc = ready(id);
			if (!doc.embeddingRevision.equals(KnowledgeWorker.EMBEDDING_REVISION))
				throw conflict("Upload a new document for this index revision");
			var lexical = db.query("""
					SELECT ordinal FROM zincc.document_chunk, websearch_to_tsquery('english',?) q
					WHERE document_id=? AND search @@ q ORDER BY ts_rank_cd(search,q) DESC,ordinal LIMIT 20
					""", (rs, i) -> rs.getInt(1), query, id);
			var pool = db.query("SELECT ordinal,page,content,vector FROM zincc.document_chunk WHERE document_id=?",
					(rs, i) -> {
						var array = rs.getArray(4);
						try {
							var values = (Float[]) array.getArray();
							double score = 0;
							for (int j = 0; j < values.length; j++)
								score += values[j] * vector.get(j).asDouble();
							return new Candidate(rs.getInt(1), new Evidence(rs.getInt(2), rs.getString(3)), score);
						}
						finally {
							array.free();
						}
					}, id);
			var order = pool.stream()
				.filter(c -> c.score >= 0.30)
				.sorted(java.util.Comparator.comparingDouble(Candidate::score)
					.reversed()
					.thenComparingInt(Candidate::ordinal))
				.limit(20)
				.map(Candidate::ordinal)
				.toList();
			var scores = new HashMap<Integer, Double>();
			fuse(scores, lexical);
			fuse(scores, order);
			return pool.stream()
				.filter(c -> scores.containsKey(c.ordinal))
				.sorted(java.util.Comparator.<Candidate>comparingDouble(c -> scores.get(c.ordinal))
					.reversed()
					.thenComparingInt(Candidate::ordinal))
				.limit(5)
				.map(Candidate::evidence)
				.toList();
		});
	}

	private static void fuse(HashMap<Integer, Double> score, List<Integer> ranks) {
		for (int i = 0; i < ranks.size(); i++)
			score.merge(ranks.get(i), 1.0 / (60 + i + 1), Double::sum);
	}

	List<Dataset> datasets(Scope scope, UUID id) {
		return scoped(scope, () -> {
			ready(id);
			return datasets(id);
		});
	}

	private List<Dataset> datasets(UUID id) {
		return db.query(
				"SELECT ordinal,page,data::text FROM zincc.document_dataset WHERE document_id=? ORDER BY ordinal",
				(rs, i) -> new Dataset(rs.getInt(1), rs.getInt(2), json.readTree(rs.getString(3))), id);
	}

	record Snapshot(Dashboard dashboard, Dataset dataset) {
	}

	Snapshot snapshot(Scope scope, UUID id, UUID dashboard) {
		return scoped(scope, () -> {
			ready(id);
			var config = dashboards(id).stream()
				.filter(d -> d.id.equals(dashboard))
				.findFirst()
				.orElseThrow(KnowledgeStore::missing);
			return new Snapshot(config, dataset(id, config.dataset));
		});
	}

	private Dataset dataset(UUID id, int dataset) {
		return datasets(id).stream().filter(d -> d.ordinal == dataset).findFirst().orElseThrow(KnowledgeStore::missing);
	}

	List<Dashboard> dashboards(Scope scope, UUID id) {
		return scoped(scope, () -> {
			ready(id);
			return dashboards(id);
		});
	}

	private List<Dashboard> dashboards(UUID id) {
		return db.query("""
				SELECT id,dataset,name,measure,category,unit,selected,revision FROM zincc.document_dashboard
				WHERE document_id=? ORDER BY name,id
				""", (rs, i) -> new Dashboard(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3), rs.getInt(4),
				rs.getInt(5), rs.getString(6), rs.getString(7), rs.getInt(8)), id);
	}

	int save(Scope scope, UUID id, UUID dashboard, Settings config) {
		return scoped(scope, () -> {
			ready(id);
			var rows = dataset(id, config.dataset).data.path("rows");
			int width = rows.get(0).size();
			if (config.expectedRevision == null || config.expectedRevision < 0 || config.measure < 0
					|| config.measure >= width || config.category < 0 || config.category >= width
					|| !valid(config.name, 100) || !valid(config.unit, 40)
					|| (config.selected != null && (config.selected.length() > 250 || rows.valueStream()
						.skip(1)
						.noneMatch(r -> config.selected.equals(r.get(config.category).asString())))))
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid dashboard settings");
			int v = config.expectedRevision;
			int changes;
			if (v == 0) {
				db.queryForMap("SELECT pg_advisory_xact_lock(hashtextextended(?,48))", id.toString());
				if (dashboards(id).size() >= 24)
					throw conflict("Dashboard limit reached");
				changes = db.update(
						"""
								INSERT INTO zincc.document_dashboard(id,document_id,dataset,name,measure,category,unit,selected,revision)
								VALUES(?,?,?,?,?,?,?,?,1) ON CONFLICT DO NOTHING
								""",
						dashboard, id, config.dataset, config.name, config.measure, config.category, config.unit,
						config.selected);
			}
			else
				changes = db.update(
						"""
								UPDATE zincc.document_dashboard SET name=?,measure=?,category=?,unit=?,selected=?,revision=revision+1
								WHERE document_id=? AND id=? AND dataset=? AND revision=?
								""",
						config.name, config.measure, config.category, config.unit, config.selected, id, dashboard,
						config.dataset, v);
			if (changes != 1)
				throw conflict("Dashboard revision changed");
			return v + 1;
		});
	}

	static boolean valid(String value, int max) {
		return value != null && !value.isBlank() && value.length() <= max
				&& value.chars().noneMatch(Character::isISOControl);
	}

	static ResponseStatusException missing() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND);
	}

	static ResponseStatusException conflict(String reason) {
		return new ResponseStatusException(HttpStatus.CONFLICT, reason);
	}

}
