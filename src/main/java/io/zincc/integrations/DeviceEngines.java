package io.zincc.integrations;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import static io.zincc.integrations.IntegrationStore.*;

/**
 * Text-only jobs claimed by a paired foreground process. Provider credentials stay on the
 * device.
 */
final class DeviceEngines {

	record Catalog(String provider, String label, List<String> models) {
	}

	record Allowance(double remaining, int minutes) {
	}

	record Event(String model, String answer, String failure, Allowance allowance) {
		Event(String model, String answer, String failure) {
			this(model, answer, failure, null);
		}
	}

	private final JdbcTemplate db;

	DeviceEngines(JdbcTemplate db) {
		this.db = db;
	}

	void register(RunnerApi.Pulse p) {
		var c = p.catalog();
		if (c == null)
			return;
		if (!List.of("chatgpt-plan", "opencode-free", "antigravity")
			.contains(java.util.Objects.toString(c.provider(), "")) || c.models() == null || c.models().isEmpty()
				|| c.models().size() > 128
				|| c.models().stream().anyMatch(m -> m == null || !m.matches("[a-zA-Z0-9][a-zA-Z0-9._:/-]{0,159}"))
				|| c.label() == null || !c.label().matches("[a-zA-Z0-9 _.-]{1,48}"))
			throw bad("Invalid device catalog");
		if (c.provider().equals("opencode-free") && !c.models().equals(List.of("opencode/deepseek-v4.1-flash-free")))
			throw bad("Unexpected free route");
		db.update("""
				INSERT INTO zincc.engine_connection(id,workspace_id,actor_id,label,device,provider,catalog)
				VALUES(?,?,?,?,?,?,?)
				""", p.device(), p.workspace(), p.owner(), c.label() + " · " + p.device(), p.device(), c.provider(),
				String.join("\n", c.models().stream().distinct().toList()));
	}

	private static final String ELIGIBLE = """
			FROM zincc.engine_receipt e JOIN zincc.engine_connection c ON c.id=e.connection_id
			JOIN zincc.agent_run r ON r.id=e.run_id JOIN zincc.agent a ON a.id=r.agent_id
			JOIN zincc.agent_version v ON v.agent_id=a.id AND v.version=r.version
			WHERE c.device=? AND c.active AND c.generation=e.generation AND r.state='running'
			  AND (a.owner_id=zincc.actor() OR v.delegate_id=zincc.actor())
			""";

	RunnerApi.Reply exchange(RunnerApi.Pulse p) {
		if (p.action().equals("engine-poll")) {
			var offered = db.query("SELECT e.run_id,e.model " + ELIGIBLE + """
					AND e.claimed_at IS NULL AND r.created_at>clock_timestamp()-interval '2 minutes'
					ORDER BY r.created_at,r.id LIMIT 1
					""", (rs, n) -> new RunnerApi.Reply("offered", null, rs.getObject(1, UUID.class), "engine",
					rs.getString(2), null, null), p.device());
			return offered.isEmpty() ? new RunnerApi.Reply("idle", null, null, null) : offered.getFirst();
		}
		if (p.job() == null || p.event() == null || p.event().model() == null)
			throw bad("Missing model acknowledgement");
		var rows = db.queryForList("SELECT r.goal,v.instructions,e.acknowledged,e.model " + ELIGIBLE + """
				AND e.run_id=? AND e.model=? AND
				   ((e.claimed_at IS NULL AND r.created_at>clock_timestamp()-interval '2 minutes') OR
				    e.claimed_at>clock_timestamp()-interval '100 seconds') FOR UPDATE OF r,e FOR SHARE OF c,a
				""", p.device(), p.job(), p.event().model());
		if (rows.size() != 1)
			throw conflict("Run, grant or model changed");
		var row = rows.getFirst();
		if (p.action().equals("engine-claim")) {
			if (row.get("acknowledged") != null)
				throw conflict("Task already delivered");
			db.update("UPDATE zincc.engine_receipt SET acknowledged=model,claimed_at=clock_timestamp() WHERE run_id=?",
					p.job());
			return new RunnerApi.Reply("claimed", null, p.job(), "engine", p.event().model(),
					(String) row.get("instructions"), (String) row.get("goal"));
		}
		if (row.get("acknowledged") == null)
			throw conflict("Unclaimed task");
		if (p.action().equals("engine-renew"))
			return new RunnerApi.Reply("active", null, p.job(), null);
		String state, outcome;
		if (p.action().equals("engine-complete")) {
			outcome = p.event().answer();
			if (outcome == null || outcome.isBlank() || outcome.length() > 32000 || outcome.indexOf(0) >= 0)
				throw bad("Invalid engine output");
			state = "completed";
		}
		else if (p.action().equals("engine-failed")) {
			outcome = switch (java.util.Objects.toString(p.event().failure(), "")) {
				case "allowance" -> "Provider allowance or rate limit reached.";
				case "authorization" -> "Provider sign-in expired or permission denied.";
				case "model" -> "Provider did not confirm the selected model.";
				default -> "Device could not confirm completion. Check provider activity before retrying.";
			};
			state = "unknown";
		}
		else
			throw bad("Invalid engine event");
		var meter = p.event().allowance();
		if (meter != null && meter.remaining() >= 0 && meter.remaining() <= 100 && meter.minutes() >= 1
				&& meter.minutes() <= 525600)
			db.update(
					"""
							UPDATE zincc.engine_connection SET primary_remaining=?,primary_window_minutes=?,allowance_observed_at=clock_timestamp()
							WHERE device=? AND provider='chatgpt-plan'
							""",
					meter.remaining(), meter.minutes(), p.device());
		db.update("UPDATE zincc.agent_run SET state=?,outcome=? WHERE id=?", state, outcome, p.job());
		return new RunnerApi.Reply(state, null, p.job(), null);
	}

}
