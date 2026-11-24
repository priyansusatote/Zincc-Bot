package io.zincc.agents;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import static io.zincc.agents.AgentStore.*;

final class AgentMemory {

	record Note(UUID id, String topic, String fact, UUID sourceId, String sourceGoal, int revision) {
	}

	record Edit(UUID id, String topic, String fact, UUID sourceId, int expectedVersion) {
	}

	private record Snapshot(long epoch, long expires, List<Note> notes) {
	}

	private final AgentStore store;

	private final Map<Scope, Snapshot> cache = new LinkedHashMap<>(16, 0.75f, true);

	private final Object[] locks = new Object[32];

	final LongAdder loads = new LongAdder();

	AgentMemory(AgentStore store) {
		this.store = store;
		Arrays.setAll(locks, i -> new Object());
	}

	List<Note> recall(Scope scope, String query) {
		if (query == null || query.length() > 2000)
			throw bad();
		return store.authorized(scope, () -> {
			var words = Arrays.stream(query.strip().toLowerCase(Locale.ROOT).split("\\s+")).distinct().toList();
			var notes = snapshot(scope);
			if (query.isBlank())
				return notes;
			return notes.stream().filter(n -> {
				String haystack = (n.topic + " " + n.fact).toLowerCase(Locale.ROOT);
				return words.stream().allMatch(haystack::contains);
			}).limit(8).toList();
		});
	}

	private List<Note> snapshot(Scope scope) {
		synchronized (locks[Math.floorMod(scope.hashCode(), locks.length)]) {
			long epoch = store.db.queryForObject("SELECT coalesce(max(generation),0) FROM zincc.memory_epoch",
					Long.class);
			long now = System.nanoTime();
			synchronized (cache) {
				var old = cache.get(scope);
				if (old != null && old.epoch == epoch && now - old.expires < 0)
					return old.notes;
			}
			var notes = List.copyOf(store.db.query("""
					SELECT m.id,m.topic,m.fact,m.source_id,r.goal,m.revision FROM zincc.agent_memory m
					JOIN zincc.agent_run r ON r.id=m.source_id ORDER BY m.topic LIMIT 64
					""", (r, n) -> new Note(r.getObject(1, UUID.class), r.getString(2), r.getString(3),
					r.getObject(4, UUID.class), r.getString(5), r.getInt(6))));
			loads.increment();
			synchronized (cache) {
				cache.put(scope, new Snapshot(epoch, now + TimeUnit.MINUTES.toNanos(2), notes));
				if (cache.size() > 64)
					cache.remove(cache.keySet().iterator().next());
			}
			return notes;
		}
	}

	void purgeExpired() {
		long now = System.nanoTime();
		synchronized (cache) {
			cache.values().removeIf(value -> now - value.expires >= 0);
		}
	}

	private void evict(Scope scope) {
		synchronized (cache) {
			cache.remove(scope);
		}
	}

	void edit(Scope scope, Edit e) {
		if (e.id == null || e.sourceId == null || !text(e.topic, 80) || !text(e.fact, 600) || e.expectedVersion < 0)
			throw bad();
		String topic = e.topic.strip().toLowerCase(Locale.ROOT);
		store.authorized(scope, () -> {
			store.db.queryForMap("SELECT pg_advisory_xact_lock(hashtextextended(?,71))", scope.toString());
			if (!Boolean.TRUE.equals(store.db.queryForObject(
					"SELECT EXISTS(SELECT 1 FROM zincc.agent_run WHERE id=? AND state='completed')", Boolean.class,
					e.sourceId)))
				throw missing();
			if (e.expectedVersion == 0) {
				if (store.db.queryForObject("SELECT count(*) FROM zincc.agent_memory", Long.class) >= 64)
					throw conflict();
				if (store.db.update(
						"INSERT INTO zincc.agent_memory(id,workspace_id,owner_id,source_id,topic,fact) VALUES(?,?,?,?,?,?) ON CONFLICT DO NOTHING",
						e.id, scope.workspace(), scope.actor(), e.sourceId, topic, e.fact.strip()) != 1)
					throw conflict();
			}
			else if (store.db.update(
					"UPDATE zincc.agent_memory SET fact=?,source_id=?,revision=revision+1 WHERE id=? AND topic=? AND revision=?",
					e.fact.strip(), e.sourceId, e.id, topic, e.expectedVersion) != 1)
				throw conflict();
			return true;
		});
		evict(scope);
	}

	void remove(Scope scope, UUID id, int revision) {
		store.authorized(scope, () -> {
			if (store.db.update("DELETE FROM zincc.agent_memory WHERE id=? AND revision=?", id, revision) != 1)
				throw conflict();
			return true;
		});
		evict(scope);
	}

}
