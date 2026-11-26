package io.zincc.agents;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import io.zincc.model.ModelGateway;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Repository
@Profile("agents")
class AgentStore {

	record Scope(UUID workspace, UUID actor) {
	}

	record Work(UUID workspace, UUID actor, UUID run) {
		Scope scope() {
			return new Scope(workspace, actor);
		}
	}

	record Definition(String name, String instructions, String route, boolean canRead, boolean canWrite,
			UUID delegateId, Integer expectedVersion, String framework) {
		Definition(String name, String instructions, String route, boolean canRead, boolean canWrite, UUID delegateId,
				Integer expectedVersion) {
			this(name, instructions, route, canRead, canWrite, delegateId, expectedVersion, null);
		}
	}

	record Start(UUID requestId, String goal, String mode) {
	}

	record Agent(UUID id, int version, String name, String instructions, String route, boolean canRead,
			boolean canWrite, UUID delegateId, boolean mine, String framework) {
	}

	record Run(UUID id, UUID agentId, int version, String goal, String mode, String state, int steps,
			String observation, String lastStep, String outcome, String title, String digest, String receipt,
			String resource) {
	}

	record Connection(boolean active, UUID generation, String resource, String reference) {
	}

	record View(List<Agent> agents, List<Run> runs, Connection connection, boolean available, String provider,
			List<String> routes, List<AgentTeams.Member> teamMembers, List<String> frameworks) {
	}

	record Access(Agent version, Agent latest, Connection connection) {
	}

	final JdbcTemplate db;

	final AgentTeams teams = new AgentTeams(this);

	final AgentMemory memory = new AgentMemory(this);

	final ConnectedEngines engines = new ConnectedEngines(this);

	private final TransactionTemplate tx;

	private final TasksConnector connector;

	private final ModelGateway models;

	@org.springframework.beans.factory.annotation.Value("${zincc.framework-image:}")
	String frameworkImage = "";

	@org.springframework.beans.factory.annotation.Value("${zincc.local-only:false}")
	boolean localOnly;

	List<String> frameworks() {
		return frameworkImage.matches("sha256:[a-f0-9]{64}") ? List.of("native", "adk", "langgraph", "pydantic-ai")
				: List.of("native");
	}

	AgentStore(DataSource data, TasksConnector connector, ModelGateway models) {
		this.db = new JdbcTemplate(data);
		if (!Boolean.TRUE.equals(db.queryForObject(
				"SELECT row_security_active('zincc.agent_run') AND row_security_active('zincc.agent_connection') AND row_security_active('zincc.team_member') AND row_security_active('zincc.agent_memory') AND row_security_active('zincc.memory_epoch')",
				Boolean.class)))
			throw new IllegalStateException("Agent runtime requires active row security");
		this.tx = new TransactionTemplate(new DataSourceTransactionManager(data));
		this.connector = connector;
		this.models = models;
	}

	static ResponseStatusException bad() {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST);
	}

	static ResponseStatusException missing() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND);
	}

	static ResponseStatusException conflict() {
		return new ResponseStatusException(HttpStatus.CONFLICT);
	}

	static boolean text(String s, int max) {
		return s != null && !s.isBlank() && s.length() <= max && s.indexOf(0) < 0;
	}

	<T> T scoped(Scope s, Supplier<T> fn) {
		return tx.execute(status -> {
			db.queryForMap("SELECT set_config('zincc.principal_id',?,true) p,set_config('zincc.workspace_id',?,true) w",
					s.actor.toString(), s.workspace.toString());
			return fn.get();
		});
	}

	boolean member(Scope s) {
		return Boolean.TRUE.equals(db.queryForObject(
				"SELECT EXISTS(SELECT 1 FROM zincc.workspace_membership WHERE workspace_id=? AND principal_id=?)",
				Boolean.class, s.workspace, s.actor));
	}

	<T> T authorized(Scope s, Supplier<T> fn) {
		return scoped(s, () -> {
			if (!member(s))
				throw missing();
			return fn.get();
		});
	}

	private static final String AGENTS = """
			SELECT a.id,v.version,v.name,v.instructions,v.route,v.can_read,v.can_write,v.delegate_id,a.owner_id=zincc.actor() AS mine,v.framework
			FROM zincc.agent a JOIN zincc.agent_version v ON v.agent_id=a.id
			""";

	private List<Agent> agents(String condition, Object... args) {
		return db.query(AGENTS + condition,
				(r, n) -> new Agent(r.getObject(1, UUID.class), r.getInt(2), r.getString(3), r.getString(4),
						r.getString(5), r.getBoolean(6), r.getBoolean(7), r.getObject(8, UUID.class), r.getBoolean(9),
						r.getString(10)),
				args);
	}

	private static final String RUNS = """
			SELECT r.id,r.agent_id,r.version,r.goal,r.mode,r.state,r.steps,r.observation,r.last_step,r.outcome,a.title,a.digest,a.receipt,a.resource
			FROM zincc.agent_run r LEFT JOIN zincc.agent_action a ON r.id=a.run_id
			""";

	private List<Run> runs(String condition, Object... args) {
		return db.query(RUNS + condition,
				(r, n) -> new Run(r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getInt(3), r.getString(4),
						r.getString(5), r.getString(6), r.getInt(7), r.getString(8), r.getString(9), r.getString(10),
						r.getString(11), r.getString(12), r.getString(13), r.getString(14)),
				args);
	}

	Run lock(UUID id) {
		return runs(" WHERE r.id=? FOR UPDATE OF r", id).stream().findFirst().orElseThrow(AgentStore::missing);
	}

	Connection connection() {
		return db
			.query("SELECT active,generation,resource,reference FROM zincc.agent_connection FOR SHARE",
					(r, n) -> new Connection(r.getBoolean(1), r.getObject(2, UUID.class), r.getString(3),
							r.getString(4)))
			.stream()
			.findFirst()
			.orElse(new Connection(false, null, "", ""));
	}

	View view(Scope s) {
		return authorized(s,
				() -> new View(agents(" WHERE a.version=v.version ORDER BY v.name,a.id"),
						runs(" ORDER BY r.created_at DESC LIMIT 50"), connection(), connector.configured(s),
						connector.reference(), models.routes().stream().map(ModelGateway.Route::id).toList(),
						teams.recent(), frameworks()));
	}

	List<Agent> history(Scope s, UUID id, int before) {
		if (before < 1)
			throw bad();
		return authorized(s,
				() -> agents(
						" WHERE a.id=? AND a.owner_id=zincc.actor() AND v.version<? ORDER BY v.version DESC LIMIT 20",
						id, before));
	}

	void connect(Scope s, boolean active) {
		authorized(s, () -> {
			if (active && !connector.configured(s))
				throw conflict();
			db.update(
					"""
							INSERT INTO zincc.agent_connection(workspace_id,actor_id,generation,resource,reference,active) VALUES(?,?,?,?,?,?)
							ON CONFLICT(workspace_id,actor_id) DO UPDATE SET generation=excluded.generation,resource=excluded.resource,reference=excluded.reference,active=excluded.active
							WHERE (agent_connection.active,agent_connection.resource,agent_connection.reference) IS DISTINCT FROM (excluded.active,excluded.resource,excluded.reference)
							""",
					s.workspace, s.actor, UUID.randomUUID(), connector.resource(), connector.reference(), active);
			return true;
		});
	}

	// Row locks require UPDATE visibility, which delegates deliberately lack.
	private void lockDefinition(UUID id, boolean editing) {
		String function = editing ? "pg_advisory_xact_lock" : "pg_advisory_xact_lock_shared";
		db.queryForMap("SELECT " + function + "(hashtextextended(?,31))", id.toString());
	}

	int save(Scope s, UUID id, Definition d) {
		if (!text(d.name, 80) || !text(d.instructions, 3000) || !text(d.route, 64) || d.expectedVersion == null
				|| d.expectedVersion < 0 || models.routes().stream().noneMatch(r -> r.id().equals(d.route)))
			throw bad();
		return authorized(s, () -> {
			lockDefinition(id, true);
			int v = d.expectedVersion + 1;
			String framework = d.framework;
			if (framework == null) {
				framework = v == 1 ? "native"
						: agents(" WHERE a.id=? AND a.owner_id=zincc.actor() AND a.version=v.version AND v.version=?",
								id, d.expectedVersion)
							.stream()
							.findFirst()
							.orElseThrow(AgentStore::conflict)
							.framework();
			}
			if (!frameworks().contains(framework))
				throw bad();
			if (d.delegateId != null && !Boolean.TRUE
				.equals(db.queryForObject("SELECT zincc.can_delegate_to(?)", Boolean.class, d.delegateId)))
				throw bad();
			int rows = v == 1 ? db.update(
					"INSERT INTO zincc.agent(id,workspace_id,owner_id,delegate_id,version) VALUES(?,?,?,?,1) ON CONFLICT DO NOTHING",
					id, s.workspace, s.actor, d.delegateId)
					: db.update(
							"UPDATE zincc.agent SET version=?,delegate_id=? WHERE id=? AND owner_id=? AND version=?", v,
							d.delegateId, id, s.actor, d.expectedVersion);
			if (rows != 1)
				throw conflict();
			db.update(
					"INSERT INTO zincc.agent_version(agent_id,version,name,instructions,route,can_read,can_write,delegate_id,framework) VALUES(?,?,?,?,?,?,?,?,?)",
					id, v, d.name, d.instructions, d.route, d.canRead, d.canWrite, d.delegateId, framework);
			return v;
		});
	}

	UUID start(Scope s, UUID agent, Start req) {
		if (req.requestId == null || !text(req.goal, 2000)
				|| !List.of("model", "list", "create", "recall").contains(req.mode == null ? "" : req.mode)
				|| ("create".equals(req.mode) && !text(req.goal, 250)))
			throw bad();
		return startInternal(s, agent, req);
	}

	UUID startInternal(Scope s, UUID agent, Start req) {
		return authorized(s, () -> {
			lockDefinition(agent, false);
			var versions = agents(" WHERE a.id=? AND a.version=v.version", agent);
			if (versions.isEmpty())
				throw missing();
			int rows = db.update(
					"""
							INSERT INTO zincc.agent_run(id,workspace_id,actor_id,agent_id,version,goal,mode,state) VALUES(?,?,?,?,?,?,?,'queued') ON CONFLICT DO NOTHING
							""",
					req.requestId, s.workspace, s.actor, agent, versions.getFirst().version, req.goal, req.mode);
			if (rows == 1)
				db.update("INSERT INTO zincc.agent_outbox(run_id) VALUES(?)", req.requestId);
			else {
				var prior = lock(req.requestId);
				boolean sameMode = prior.mode.equals(req.mode)
						|| (prior.mode.equals("engine-fixture") && req.mode.equals("connected-engine"));
				if (!prior.agentId.equals(agent) || !prior.goal.equals(req.goal) || !sameMode)
					throw conflict();
			}
			return req.requestId;
		});
	}

	Access access(Scope s, Run r) {
		if (!member(s))
			return null;
		lockDefinition(r.agentId, false);
		var latest = agents(" WHERE a.id=? AND a.version=v.version", r.agentId);
		var pinned = agents(" WHERE a.id=? AND v.version=?", r.agentId, r.version);
		if (latest.isEmpty() || pinned.isEmpty()
				|| (!pinned.getFirst().mine && !s.actor.equals(pinned.getFirst().delegateId)))
			return null;
		return new Access(pinned.getFirst(), latest.getFirst(), connection());
	}

	boolean permitted(Scope s, Access a, boolean write) {
		return a != null && a.connection.active && connector.configured(s)
				&& connector.resource().equals(a.connection.resource)
				&& connector.reference().equals(a.connection.reference)
				&& (write ? TaskTool.CREATE : TaskTool.LIST).granted(a.version)
				&& (write ? TaskTool.CREATE : TaskTool.LIST).granted(a.latest);
	}

	String set(UUID id, String state, String outcome) {
		db.update("UPDATE zincc.agent_run SET state=?,outcome=? WHERE id=?", state, outcome, id);
		return state;
	}

	String propose(Run r, Connection c, String title) {
		String digest = digest(r, c, title);
		db.update("INSERT INTO zincc.agent_action(run_id,generation,resource,title,digest) VALUES(?,?,?,?,?)", r.id,
				c.generation, c.resource, title, digest);
		return set(r.id, "waiting", "Review the exact task title and destination.");
	}

	private String digest(Run r, Connection c, String title) {
		try {
			String binding = String.join("\n", r.id.toString(), Integer.toString(r.version), "tasks.create",
					c.generation.toString(), c.reference, c.resource, title);
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(binding.getBytes(StandardCharsets.UTF_8)));
		}
		catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	boolean bound(Run r, Access a) {
		return a != null && r.title != null && a.connection.generation != null
				&& digest(r, a.connection, r.title).equals(r.digest);
	}

	void decision(Scope s, UUID id, String operation, String digest) {
		authorized(s, () -> {
			teams.lockParent(id);
			var r = lock(id);
			switch (operation) {
				case "select" -> teams.select(s, r, digest);
				case "approve" -> {
					var a = access(s, r);
					if (!"waiting".equals(r.state) || !permitted(s, a, true) || !bound(r, a)
							|| !r.digest.equals(digest))
						throw conflict();
					db.update("UPDATE zincc.agent_action SET approved_at=clock_timestamp() WHERE run_id=?", id);
					set(id, "approved", "Approval recorded.");
				}
				case "cancel" -> {
					if (!List.of("queued", "running", "waiting", "approved").contains(r.state))
						throw conflict();
					if (r.mode.equals("team"))
						teams.stopChildren(id);
					set(id, "cancelled", r.mode.equals("connected-engine")
							? "Cancelled. A device that already received the task may have consumed provider allowance."
							: "Cancelled before dispatch.");
				}
				default -> throw bad();
			}
			return true;
		});
	}

	List<Work> pending() {
		return db.query("SELECT workspace_id,actor_id,run_id FROM zincc.pending_agent_work()",
				(r, n) -> new Work(r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getObject(3, UUID.class)));
	}

	void dispatched(Work w) {
		scoped(w.scope(), () -> db.update("DELETE FROM zincc.agent_outbox WHERE run_id=?", w.run));
	}

}
