package io.zincc.agents;

import java.util.List;
import java.util.UUID;
import static io.zincc.agents.AgentStore.*;

/**
 * A device-independent conformance engine with a private, revocable fixture connection.
 */
final class ConnectedEngines {

	static final List<String> MODELS = List.of("fixture/word-count", "fixture/checklist");

	private final AgentStore store;

	ConnectedEngines(AgentStore store) {
		this.store = store;
	}

	record Account(UUID id, String label, boolean active, int generation) {
	}

	record Choice(UUID connection, String model, UUID thread) {
	}

	record StartEngine(UUID requestId, String goal, UUID thread) {
	}

	record Receipt(UUID run, UUID connection, String model, String acknowledged, int generation, UUID thread) {
	}

	record Picker(List<Account> accounts, List<String> models, List<Choice> choices, List<Receipt> receipts) {
	}

	Picker picker(Scope s, UUID agent) {
		return store.authorized(s, () -> {
			visible("agent", agent);
			var accounts = store.db.query(
					"SELECT id,label,active,generation FROM zincc.engine_connection ORDER BY label",
					(r, n) -> new Account(r.getObject(1, UUID.class), r.getString(2), r.getBoolean(3), r.getInt(4)));
			var choices = store.db.query(
					"SELECT connection_id,model,thread_id FROM zincc.engine_choice WHERE agent_id=? ORDER BY thread_id NULLS FIRST",
					(r, n) -> new Choice(r.getObject(1, UUID.class), r.getString(2), r.getObject(3, UUID.class)),
					agent);
			var receipts = store.db.query(
					"SELECT e.run_id,e.connection_id,e.model,e.acknowledged,e.generation,e.thread_id FROM zincc.engine_receipt e JOIN zincc.agent_run r ON r.id=e.run_id WHERE r.agent_id=? ORDER BY r.created_at DESC LIMIT 20",
					(r, n) -> new Receipt(r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getString(3),
							r.getString(4), r.getInt(5), r.getObject(6, UUID.class)),
					agent);
			return new Picker(accounts, MODELS, choices, receipts);
		});
	}

	void connect(Scope s, String label) {
		if (!text(label, 80))
			throw bad();
		store.authorized(s,
				() -> store.db.update(
						"""
								INSERT INTO zincc.engine_connection(id,workspace_id,actor_id,label) VALUES(?,?,?,?)
								ON CONFLICT(workspace_id,actor_id,label) DO UPDATE SET active=true,generation=engine_connection.generation+1 WHERE NOT engine_connection.active
								""",
						UUID.randomUUID(), s.workspace(), s.actor(), label.strip()));
	}

	void revoke(Scope s, UUID connection) {
		store.authorized(s,
				() -> store.db.update(
						"UPDATE zincc.engine_connection SET active=false,generation=generation+1 WHERE id=? AND active",
						connection));
	}

	void choose(Scope s, UUID agent, Choice choice) {
		store.authorized(s, () -> {
			visible("agent", agent);
			if (choice.thread != null)
				visible("conversation", choice.thread);
			if (choice.connection == null) {
				store.db.update("DELETE FROM zincc.engine_choice WHERE agent_id=? AND thread_id IS NOT DISTINCT FROM ?",
						agent, choice.thread);
				return true;
			}
			if (!MODELS.contains(choice.model == null ? "" : choice.model))
				throw bad();
			if (store.db
				.queryForList("SELECT id FROM zincc.engine_connection WHERE id=? AND active FOR SHARE", UUID.class,
						choice.connection)
				.isEmpty())
				throw missing();
			store.db.update(
					"""
							INSERT INTO zincc.engine_choice(workspace_id,actor_id,agent_id,thread_id,connection_id,model) VALUES(?,?,?,?,?,?)
							ON CONFLICT(workspace_id,actor_id,agent_id,thread_id) DO UPDATE SET connection_id=excluded.connection_id,model=excluded.model
							""",
					s.workspace(), s.actor(), agent, choice.thread, choice.connection, choice.model);
			return true;
		});
	}

	private void visible(String kind, UUID id) {
		String sql = kind.equals("agent") ? "SELECT id FROM zincc.agent WHERE id=?"
				: "SELECT id FROM zincc.conversation WHERE id=?";
		if (store.db.queryForList(sql, UUID.class, id).isEmpty())
			throw missing();
	}

	UUID start(Scope s, UUID agent, StartEngine cmd) {
		if (cmd.requestId == null || !text(cmd.goal, 2000))
			throw bad();
		return store.authorized(s, () -> {
			if (cmd.thread != null)
				visible("conversation", cmd.thread);
			var id = store.startInternal(s, agent, new Start(cmd.requestId, cmd.goal, "engine-fixture"));
			var prior = store.db.queryForList(
					"SELECT run_id FROM zincc.engine_receipt WHERE run_id=? AND thread_id IS NOT DISTINCT FROM ?",
					UUID.class, id, cmd.thread);
			if (!prior.isEmpty())
				return id;
			if (store.db.queryForObject("SELECT count(*) FROM zincc.engine_receipt WHERE run_id=?", Integer.class,
					id) > 0)
				throw conflict();
			// Select the pinned choice before testing its active grant. Revocation never
			// falls back.
			var rows = store.db.queryForList(
					"""
							SELECT c.connection_id,c.model,e.active,e.generation FROM zincc.engine_choice c JOIN zincc.engine_connection e ON e.id=c.connection_id
							WHERE c.agent_id=? AND (c.thread_id IS NULL OR c.thread_id=?) ORDER BY c.thread_id NULLS LAST LIMIT 1 FOR SHARE OF c,e
							""",
					agent, cmd.thread);
			if (rows.isEmpty() || !Boolean.TRUE.equals(rows.getFirst().get("active")))
				throw conflict();
			var row = rows.getFirst();
			store.db.update(
					"INSERT INTO zincc.engine_receipt(run_id,connection_id,generation,model,thread_id) VALUES(?,?,?,?,?)",
					id, row.get("connection_id"), row.get("generation"), row.get("model"), cmd.thread);
			return id;
		});
	}

	String advance(Run r, boolean allowed) {
		var rows = store.db.queryForList(
				"""
						SELECT e.model,e.acknowledged FROM zincc.engine_receipt e JOIN zincc.engine_connection c ON c.id=e.connection_id
						WHERE e.run_id=? AND c.active AND c.generation=e.generation FOR SHARE OF c
						""",
				r.id());
		if (!allowed || rows.isEmpty())
			return store.set(r.id(), "failed", "Engine grant expired or revoked.");
		var row = rows.getFirst();
		var model = (String) row.get("model");
		if (!MODELS.contains(model))
			return store.set(r.id(), "failed", "Unsupported engine model.");
		if (row.get("acknowledged") == null) {
			// The fixture accepts both catalog IDs. The checkpoint precedes delivery of
			// the goal.
			store.db.update("UPDATE zincc.engine_receipt SET acknowledged=? WHERE run_id=?", model, r.id());
			return store.set(r.id(), "running", "Local fixture acknowledged " + model);
		}
		if (!model.equals(row.get("acknowledged")))
			return store.set(r.id(), "failed", "Engine model mismatch.");
		String answer = model.equals("fixture/word-count") ? "Words: " + r.goal().strip().split("\\s+").length
				: "Unchecked items: " + r.goal().lines().filter(l -> l.strip().startsWith("- [ ] ")).count();
		return store.set(r.id(), "completed", answer);
	}

}
