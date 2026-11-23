package io.zincc.integrations;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import io.zincc.knowledge.KnowledgeReports;
import static io.zincc.integrations.IntegrationStore.*;

@Component
@Profile("agents")
class Routines {

	record Create(String name, String skill, int version, String trigger, String zone, String time, List<UUID> feeds,
			UUID document, UUID dashboard) {
	}

	private final java.util.concurrent.ExecutorService workers = java.util.concurrent.Executors
		.newVirtualThreadPerTaskExecutor();

	private final Set<UUID> active = java.util.concurrent.ConcurrentHashMap.newKeySet();

	@jakarta.annotation.PreDestroy
	void stop() {
		workers.shutdownNow();
	}

	private final IntegrationStore store;

	private final BriefingTool tool;

	private final ObjectProvider<KnowledgeReports> reports;

	Routines(IntegrationStore store, BriefingTool tool, ObjectProvider<KnowledgeReports> reports) {
		this.store = store;
		this.tool = tool;
		this.reports = reports;
	}

	static Instant next(Instant after, ZoneId zone, LocalTime time) {
		var date = after.atZone(zone).toLocalDate();
		while (true) {
			var local = date.atTime(time);
			var offsets = zone.getRules().getValidOffsets(local);
			if (!offsets.isEmpty()) {
				var candidate = local.toInstant(offsets.getFirst());
				if (candidate.isAfter(after))
					return candidate;
			}
			date = date.plusDays(1);
		}
	}

	private SkillCatalog.Skill installed(String name, int version) {
		var skill = store.skill(name, version);
		if (store.db
			.queryForList("SELECT 1 FROM zincc.skill_install WHERE package=? AND version=? AND digest=? FOR SHARE",
					name, version, skill.digest())
			.isEmpty())
			throw conflict("Install and approve the selected version first");
		return skill;
	}

	UUID create(Scope s, Create c) {
		return store.scoped(s, () -> {
			store.lock(s);
			var skill = installed(c.skill, c.version);
			if (c.name == null || c.name.isBlank() || c.name.length() > 80
					|| !Set.of("daily", "event").contains(Objects.toString(c.trigger, "")) || c.feeds == null
					|| c.feeds.size() > 4 || new HashSet<>(c.feeds).size() != c.feeds.size()
					|| store.db.queryForObject("SELECT count(*) FROM zincc.routine", Integer.class) >= 64)
				throw bad("Invalid routine");
			ZoneId zone;
			LocalTime time;
			try {
				zone = ZoneId.of(c.zone);
				time = LocalTime.parse(c.time);
				if (time.getSecond() != 0 || time.getNano() != 0)
					throw new IllegalArgumentException();
			}
			catch (Exception e) {
				throw bad("Choose a time zone and minute");
			}
			if (c.skill.equals("report")) {
				if (!c.feeds.isEmpty() || !c.trigger.equals("daily") || c.document == null || c.dashboard == null
						|| store.db
							.queryForList("SELECT 1 FROM zincc.document_dashboard WHERE document_id=? AND id=?",
									c.document, c.dashboard)
							.isEmpty())
					throw bad("Select your dashboard");
			}
			else {
				if (c.feeds.isEmpty() || c.document != null || c.dashboard != null)
					throw bad("Select connections for this briefing");
				var available = store.feeds();
				for (UUID id : c.feeds)
					if (available.stream()
						.noneMatch(f -> f.id().equals(id) && !f.state().equals("revoked")
								&& skill.permissions().contains(f.provider())))
						throw bad("Connection outside grant");
			}
			UUID id = UUID.randomUUID();
			store.db.update(
					"INSERT INTO zincc.routine(id,workspace_id,owner_id,name,package,version,digest,trigger_kind,zone,local_time,feeds,document_id,dashboard_id,next_time) VALUES(?,?,?,?,?,?,?,?,?,CAST(? AS time),CAST(? AS uuid[]),?,?,?)",
					id, s.workspace(), s.owner(), c.name.strip(), c.skill, c.version, skill.digest(), c.trigger,
					zone.getId(), time.toString(),
					"{" + String.join(",", c.feeds.stream().map(UUID::toString).toList()) + "}", c.document,
					c.dashboard, c.trigger.equals("daily") ? Timestamp.from(next(Instant.now(), zone, time)) : null);
			return id;
		});
	}

	void command(Scope s, UUID id, String action, UUID attempt) {
		store.scoped(s, () -> {
			var r = store.db.queryForList("SELECT * FROM zincc.routine WHERE id=? FOR UPDATE", id)
				.stream()
				.findFirst()
				.orElseThrow(IntegrationStore::missing);
			if ("pause".equals(action)) {
				store.db.update("UPDATE zincc.routine SET paused=true WHERE id=?", id);
				store.db.update(
						"UPDATE zincc.routine_run SET state='skipped',receipt='Paused' WHERE routine_id=? AND state='queued'",
						id);
				return null;
			}
			installed((String) r.get("package"), (int) r.get("version"));
			if ("resume".equals(action)) {
				var due = next(Instant.now(), ZoneId.of((String) r.get("zone")),
						((java.sql.Time) r.get("local_time")).toLocalTime());
				store.db.update("UPDATE zincc.routine SET paused=false,next_time=? WHERE id=?",
						"daily".equals(r.get("trigger_kind")) ? Timestamp.from(due) : null, id);
			}
			else if ("run".equals(action) && attempt != null && !(boolean) r.get("paused"))
				enqueue(id, "manual/" + attempt);
			else
				throw conflict("Routine is paused or command invalid");
			return null;
		});
	}

	private void enqueue(UUID id, String cause) {
		store.db.update("INSERT INTO zincc.routine_run(routine_id,cause) VALUES(?,?) ON CONFLICT DO NOTHING", id,
				cause);
	}

	void daily(Scope s, UUID id) {
		store.scoped(s, () -> {
			var rows = store.db.queryForList(
					"SELECT * FROM zincc.routine WHERE id=? AND NOT paused AND next_time<=now() FOR UPDATE SKIP LOCKED",
					id);
			if (rows.isEmpty())
				return null;
			var r = rows.getFirst();
			var now = Instant.now();
			var due = ((Timestamp) r.get("next_time")).toInstant();
			var zone = ZoneId.of((String) r.get("zone"));
			var time = ((java.sql.Time) r.get("local_time")).toLocalTime();
			// Coalesce downtime into one run; the receipt identifies the first missed
			// slot.
			enqueue(id, "daily/" + due);
			store.db.update("UPDATE zincc.routine SET next_time=? WHERE id=?", Timestamp.from(next(now, zone, time)),
					id);
			return null;
		});
	}

	void execute(Scope s, UUID job) {
		try {
			executeLocked(s, job);
		}
		catch (org.springframework.transaction.UnexpectedRollbackException rollback) {
			store.scoped(s, () -> store.db.update(
					"UPDATE zincc.routine_run SET state='failed',receipt='Source changed while reading' WHERE id=? AND state='queued'",
					job));
		}
	}

	private void executeLocked(Scope s, UUID job) {
		store.scoped(s, () -> {
			var rows = store.db.queryForList("""
					SELECT r.*,j.id job FROM zincc.routine_run j JOIN zincc.routine r ON r.id=j.routine_id
					WHERE j.id=? AND j.state='queued' FOR UPDATE OF r,j SKIP LOCKED
					""", job);
			if (rows.isEmpty())
				return null;
			var r = rows.getFirst();
			String state = "complete", receipt;
			var usedFeeds = new ArrayList<String>();
			try {
				if ((boolean) r.get("paused"))
					throw conflict("Paused");
				var skill = installed((String) r.get("package"), (int) r.get("version"));
				if (skill.name().equals("report")) {
					var reporter = reports.getIfAvailable();
					if (reporter == null)
						throw conflict("Reports offline");
					receipt = reporter.refresh(s.workspace(), s.owner(), (UUID) r.get("document_id"),
							(UUID) r.get("dashboard_id"));
				}
				else {
					var lines = new ArrayList<String>();
					var feeds = store.db.queryForList(
							"""
									SELECT f.id,f.provider,f.label,(f.state='ready' AND f.synced_at>now()-interval '10 minutes') fresh,f.state
									FROM zincc.feed f JOIN zincc.routine r ON f.id=ANY(r.feeds) WHERE r.id=? ORDER BY f.id FOR SHARE OF f
									""",
							r.get("id"));
					for (var f : feeds) {
						String label = (String) f.get("label");
						if (!skill.permissions().contains(f.get("provider")))
							throw conflict("Grant changed");
						if (!Boolean.TRUE.equals(f.get("fresh"))) {
							lines.add(label + " · source unavailable (" + f.get("state") + ")");
							state = "partial";
							continue;
						}
						usedFeeds.add(f.get("id").toString());
						var items = store.summaries((UUID) f.get("id"));
						if (items.isEmpty())
							lines.add(label + " · no items");
						for (var item : items)
							lines.add(label + " · " + item.summary());
					}
					receipt = tool.run(lines);
				}
			}
			catch (RuntimeException ex) {
				state = "failed";
				receipt = "Could not complete this run. Check its grants, sources and worker.";
			}
			store.db.update("UPDATE zincc.routine_run SET state=?,receipt=?,used_feeds=CAST(? AS uuid[]) WHERE id=?",
					state, receipt, "{" + String.join(",", usedFeeds) + "}", job);
			return null;
		});
	}

	List<Map<String, Object>> history() {
		return store.db.queryForList(
				"""
						SELECT j.id,r.name,j.cause,j.state,j.created_at,
						 CASE WHEN r.document_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM zincc.document_dashboard d WHERE d.id=r.dashboard_id AND d.document_id=r.document_id)
						 THEN 'Dashboard removed'
						 WHEN EXISTS(SELECT 1 FROM zincc.feed f WHERE f.id=ANY(j.used_feeds) AND (f.state<>'ready' OR f.synced_at IS NULL OR f.synced_at<now()-interval '10 minutes')) THEN 'Source access pending or withdrawn'
						 ELSE j.receipt END receipt
						FROM zincc.routine_run j JOIN zincc.routine r ON r.id=j.routine_id ORDER BY j.created_at DESC LIMIT 50
						""");
	}

	@Scheduled(fixedDelayString = "${zincc.integrations.poll-ms:3000}")
	void poll() {
		for (var due : store.db.queryForList("SELECT * FROM zincc.integration_due()")) {
			var s = new Scope((UUID) due.get("workspace_id"), (UUID) due.get("owner_id"));
			var id = (UUID) due.get("id");
			if (active.size() >= 2)
				break;
			if (!active.add(id))
				continue;
			workers.submit(() -> {
				try {
					switch ((String) due.get("kind")) {
						case "feed" -> store.sync(s, id, false);
						case "daily" -> daily(s, id);
						case "run" -> execute(s, id);
						default -> throw new IllegalStateException();
					}
				}
				catch (RuntimeException e) {
					org.slf4j.LoggerFactory.getLogger(Routines.class)
						.warn("Integration work deferred for {} ({})", id, e.getClass().getSimpleName());
				}
				finally {
					active.remove(id);
				}
			});
		}
	}

}
