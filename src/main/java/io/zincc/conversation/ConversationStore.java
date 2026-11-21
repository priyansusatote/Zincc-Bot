package io.zincc.conversation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

@Repository
@Profile("postgres")
public class ConversationStore {

	public record Scope(UUID principal, UUID workspace) {
	}

	public record ThreadInfo(UUID id, String title) {
	}

	public record Command(UUID turnId, Long revision, String route, String question) {
	}

	public record Turn(UUID id, String route, String question, String answer, String state) {
	}

	public record Snapshot(long revision, List<Turn> turns) {
	}

	public record Event(long seq, UUID turnId, String kind, String body, Instant at, String route) {
	}

	private static final int REPLAY_WINDOW = 128;

	private final JdbcTemplate db;

	private final TransactionTemplate transactions;

	public ConversationStore(DataSource source) {
		this.db = new JdbcTemplate(source);
		this.transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
		this.transactions
			.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		if (!Boolean.TRUE.equals(db.queryForObject("""
				SELECT row_security_active('zincc.conversation')
				   AND row_security_active('zincc.conversation_turn')
				   AND row_security_active('zincc.conversation_event')
				""", Boolean.class))) {
			throw new IllegalStateException("Conversation storage requires active row security");
		}
	}

	private <T> T scoped(Scope scope, Supplier<T> operation) {
		return transactions.execute(status -> {
			db.queryForMap(
					"SELECT set_config('zincc.principal_id', ?, true) AS p, set_config('zincc.workspace_id', ?, true) AS w",
					scope.principal().toString(), scope.workspace().toString());
			if (!db.queryForObject("SELECT EXISTS (SELECT FROM zincc.workspace WHERE id=?)", Boolean.class,
					scope.workspace()))
				throw new ResponseStatusException(HttpStatus.NOT_FOUND);
			return operation.get();
		});
	}

	public List<ThreadInfo> directory(Scope scope) {
		return scoped(scope, () -> db.query("SELECT id,title FROM zincc.conversation ORDER BY updated_at DESC,id",
				(rs, i) -> new ThreadInfo(rs.getObject(1, UUID.class), rs.getString(2))));
	}

	// Serialize revision allocation and read a consistent projection for one private
	// conversation.
	private long lock(UUID id) {
		var revisions = db.queryForList("SELECT revision FROM zincc.conversation WHERE id=? FOR UPDATE", Long.class,
				id);
		if (revisions.isEmpty())
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		var stale = db.queryForList("""
				UPDATE zincc.conversation_turn SET state='interrupted'
				WHERE conversation_id=? AND state='running' AND expires_at<=clock_timestamp() RETURNING id
				""", UUID.class, id);
		if (!stale.isEmpty())
			return event(id, stale.getFirst(), "interrupted", "");
		return revisions.getFirst();
	}

	private long event(UUID id, UUID turn, String kind, String body) {
		long seq = db.queryForObject(
				"""
						UPDATE zincc.conversation SET revision=revision+1, updated_at=clock_timestamp() WHERE id=? RETURNING revision
						""",
				Long.class, id);
		db.update("INSERT INTO zincc.conversation_event(conversation_id,seq,turn_id,kind,body) VALUES(?,?,?,?,?)", id,
				seq, turn, kind, body);
		if (seq > REPLAY_WINDOW)
			db.update("DELETE FROM zincc.conversation_event WHERE conversation_id=? AND seq<=?", id,
					seq - REPLAY_WINDOW);
		return seq;
	}

	private List<Turn> turns(UUID id) {
		return db.query(
				"""
						SELECT id,route,question,answer,state FROM zincc.conversation_turn WHERE conversation_id=? ORDER BY base_revision
						""",
				(rs, i) -> new Turn(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
						rs.getString(5)),
				id);
	}

	public Snapshot snapshot(Scope scope, UUID id) {
		return scoped(scope, () -> new Snapshot(lock(id), turns(id)));
	}

	public List<Event> replay(Scope scope, UUID id, long after) {
		return scoped(scope, () -> {
			long revision = lock(id);
			if (after < 0 || after > revision)
				throw new ResponseStatusException(HttpStatus.CONFLICT);
			if (after < revision - REPLAY_WINDOW)
				throw new ResponseStatusException(HttpStatus.GONE);
			return db.query("""
					SELECT e.seq,e.turn_id,e.kind,e.body,e.occurred_at,t.route FROM zincc.conversation_event e
					JOIN zincc.conversation_turn t ON (e.conversation_id,e.turn_id)=(t.conversation_id,t.id)
					WHERE e.conversation_id=? AND e.seq>? ORDER BY e.seq
					""", (rs, i) -> new Event(rs.getLong(1), rs.getObject(2, UUID.class), rs.getString(3),
					rs.getString(4), rs.getTimestamp(5).toInstant(), rs.getString(6)), id, after);
		});
	}

	/** Empty optional means this exact command was already accepted. */
	public Optional<List<Turn>> reserve(Scope scope, UUID id, Command cmd) {
		return scoped(scope, () -> {
			if (cmd.revision() == 0)
				db.update(
						"""
								INSERT INTO zincc.conversation(id,workspace_id,owner_id,title) VALUES(?,?,?,left(?,80)) ON CONFLICT DO NOTHING
								""",
						id, scope.workspace(), scope.principal(), cmd.question());
			long current = lock(id);
			var existing = db.queryForList(
					"""
							SELECT base_revision=? AND route=? AND question=? FROM zincc.conversation_turn WHERE conversation_id=? AND id=?
							""",
					Boolean.class, cmd.revision(), cmd.route(), cmd.question(), id, cmd.turnId());
			if (!existing.isEmpty() && existing.getFirst())
				return Optional.empty();
			if (!existing.isEmpty() || current != cmd.revision())
				throw new ResponseStatusException(HttpStatus.CONFLICT);
			var history = turns(id);
			if (!history.isEmpty() && history.getLast().state().equals("running"))
				throw new ResponseStatusException(HttpStatus.CONFLICT);
			if (history.size() >= 64)
				throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
			db.update("""
					INSERT INTO zincc.conversation_turn(conversation_id,id,base_revision,route,question,state)
					VALUES(?,?,?,?,?,'running')
					""", id, cmd.turnId(), current, cmd.route(), cmd.question());
			event(id, cmd.turnId(), "started", cmd.question());
			return Optional.of(history);
		});
	}

	public void append(Scope scope, UUID id, UUID turn, String kind, String body) {
		scoped(scope, () -> {
			lock(id);
			String state = switch (kind) {
				case "delta" -> "running";
				case "completed", "interrupted" -> kind;
				default -> throw new IllegalArgumentException("Invalid generation event");
			};
			int updated = db.update(
					"""
							UPDATE zincc.conversation_turn SET answer=answer || ?,state=? WHERE conversation_id=? AND id=? AND state='running'
							""",
					body, state, id, turn);
			if (updated == 1)
				event(id, turn, kind, body);
			return updated;
		});
	}

}
