package io.zincc.agents;

import java.util.*;
import static io.zincc.agents.AgentStore.*;

final class AgentTeams {

	record Request(UUID requestId, String goal, List<UUID> reviewers, Map<UUID, String> checks) {
		Request(UUID requestId, String goal, List<UUID> reviewers) {
			this(requestId, goal, reviewers, Map.of());
		}

		String method(UUID id) {
			return checks == null ? "opinion" : checks.getOrDefault(id, "opinion");
		}
	}

	record Member(UUID parentId, UUID childId, UUID agentId, int slot, String state, String outcome, String method) {
	}

	private final AgentStore store;

	AgentTeams(AgentStore store) {
		this.store = store;
	}

	UUID start(Scope scope, UUID lead, Request req) {
		if (req.requestId == null || !text(req.goal, 2000) || req.reviewers == null || req.reviewers.isEmpty()
				|| req.reviewers.size() > 3 || req.reviewers.stream().anyMatch(Objects::isNull)
				|| req.reviewers.contains(lead) || new HashSet<>(req.reviewers).size() != req.reviewers.size())
			throw bad();
		if (req.checks != null && (!req.reviewers.containsAll(req.checks.keySet()) || req.checks.values()
			.stream()
			.anyMatch(v -> !List.of("opinion", "word-count", "checklist").contains(Objects.toString(v, "")))))
			throw bad();
		return store.authorized(scope, () -> {
			store.db.queryForMap("SELECT pg_advisory_xact_lock(hashtextextended(?,72))", scope.toString());
			UUID parent = store.startInternal(scope, lead, new Start(req.requestId, req.goal, "team"));
			store.lock(parent);
			var prior = members(parent);
			if (!prior.isEmpty()) {
				if (!prior.stream().map(Member::agentId).toList().equals(req.reviewers)
						|| prior.stream().anyMatch(m -> !m.method.equals(req.method(m.agentId))))
					throw conflict();
				return parent;
			}
			if (store.db.queryForObject(
					"SELECT count(*) FROM zincc.agent_run WHERE mode='team' AND state IN ('queued','running','waiting')",
					Long.class) > 4)
				throw conflict();
			int slot = 0;
			for (UUID agent : req.reviewers) {
				var child = store.startInternal(scope, agent,
						new Start(UUID.randomUUID(), req.goal, req.method(agent)));
				store.db.update("INSERT INTO zincc.team_member(parent_id,slot,child_id) VALUES(?,?,?)", parent, ++slot,
						child);
			}
			return parent;
		});
	}

	List<Member> members(UUID parent) {
		return list(" WHERE m.parent_id=? ORDER BY m.slot", parent);
	}

	List<Member> recent() {
		return list(
				" WHERE m.parent_id IN (SELECT id FROM zincc.agent_run WHERE mode='team' AND state='waiting' UNION SELECT id FROM (SELECT id FROM zincc.agent_run WHERE mode='team' ORDER BY created_at DESC,id LIMIT 50) recent) ORDER BY m.parent_id,m.slot");
	}

	private List<Member> list(String filter, Object... args) {
		return store.db
			.query("""
					SELECT m.parent_id,m.child_id,r.agent_id,m.slot,r.state,r.outcome,r.mode FROM zincc.team_member m
					JOIN zincc.agent_run r ON r.id=m.child_id
					""" + filter,
					(r, n) -> new Member(r.getObject(1, UUID.class), r.getObject(2, UUID.class),
							r.getObject(3, UUID.class), r.getInt(4), r.getString(5), r.getString(6), r.getString(7)),
					args);
	}

	Run lockParent(UUID child) {
		var ids = store.db.queryForList("SELECT parent_id FROM zincc.team_member WHERE child_id=?", UUID.class, child);
		return ids.isEmpty() ? null : store.lock(ids.getFirst());
	}

	void stopChildren(UUID parent) {
		store.db.update("""
				UPDATE zincc.agent_run SET state='cancelled',outcome='Parent run ended.'
				WHERE id IN (SELECT child_id FROM zincc.team_member WHERE parent_id=?) AND state IN ('queued','running')
				""", parent);
	}

	String join(Scope scope, Run parent, boolean expired, Access access) {
		var children = members(parent.id());
		if (expired || access == null || children.isEmpty()
				|| children.stream().anyMatch(c -> Set.of("failed", "cancelled").contains(c.state))) {
			stopChildren(parent.id());
			return store.set(parent.id(), "failed", "Team stopped before collecting all reviews.");
		}
		if (children.stream().anyMatch(c -> !c.state.equals("completed"))) {
			store.set(parent.id(), "running", "Collecting independent reviews.");
			return "joining";
		}
		for (var child : children)
			if (store.access(scope, store.lock(child.childId)) == null)
				return store.set(parent.id(), "failed", "Reviewer access changed.");
		var answers = children.stream().map(Member::outcome).distinct().toList();
		if (answers.size() == 1)
			return store.set(parent.id(), "completed", answers.getFirst());
		return store.set(parent.id(), "waiting", "Reviews differ. Select the result to keep.");
	}

	void select(Scope scope, Run parent, String id) {
		if (!parent.mode().equals("team") || !parent.state().equals("waiting") || store.access(scope, parent) == null)
			throw conflict();
		var reviews = members(parent.id());
		if ("all".equals(id)) {
			var merged = new StringJoiner("\n\n");
			for (var child : reviews) {
				if (!child.state.equals("completed") || store.access(scope, store.lock(child.childId)) == null)
					throw missing();
				merged.add("Reviewer " + child.slot + " � " + child.outcome);
			}
			store.set(parent.id(), "completed", merged.toString());
			return;
		}
		var chosen = reviews.stream()
			.filter(c -> c.childId.toString().equals(id) && c.state.equals("completed"))
			.findFirst()
			.orElseThrow(AgentStore::conflict);
		if (store.access(scope, store.lock(chosen.childId)) == null)
			throw missing();
		store.set(parent.id(), "completed", chosen.outcome);
	}

}
