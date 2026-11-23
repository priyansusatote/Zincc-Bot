package io.zincc.integrations;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Repository
@Profile("agents")
class IntegrationStore {

	record Scope(UUID workspace, UUID owner) {
	}

	record Feed(UUID id, String provider, String label, String resource, boolean fixture, String grantScope,
			String state, Instant syncedAt, long itemCount) {
	}

	record Summary(String stamp, String summary) {
	}

	final JdbcTemplate db;

	private final TransactionTemplate tx;

	final List<SkillCatalog.Skill> catalog;

	final ProviderClient client;

	private final Environment env;

	IntegrationStore(DataSource source, ProviderClient client, Environment env, JsonMapper json) {
		this.db = new JdbcTemplate(source);
		this.tx = new TransactionTemplate(new DataSourceTransactionManager(source));
		this.client = client;
		this.env = env;
		catalog = SkillCatalog.load(json);
		if (!Boolean.TRUE.equals(db.queryForObject(
				"SELECT bool_and(row_security_active(('zincc.'||t)::regclass)) FROM unnest(ARRAY['feed','feed_item','feed_signal','routine','routine_run','skill_install']) t",
				Boolean.class)))
			throw new IllegalStateException("Integrations require active RLS");
	}

	<T> T scoped(Scope s, Supplier<T> work) {
		return tx.execute(status -> {
			db.queryForMap("SELECT set_config('zincc.workspace_id',?,true),set_config('zincc.principal_id',?,true)",
					s.workspace.toString(), s.owner.toString());
			if (!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM zincc.workspace WHERE id=?)",
					Boolean.class, s.workspace)))
				throw missing();
			return work.get();
		});
	}

	static ResponseStatusException missing() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND);
	}

	static ResponseStatusException bad(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	static ResponseStatusException conflict(String message) {
		return new ResponseStatusException(HttpStatus.CONFLICT, message);
	}

	boolean enabled(String p) {
		return client.available(p) && env.getProperty("ZINCC_CONNECTION_KEY", "").matches("[0-9a-fA-F]{64}");
	}

	private String protect(int mode, Scope scope, UUID id, String input) {
		try {
			byte[] key = HexFormat.of().parseHex(env.getRequiredProperty("ZINCC_CONNECTION_KEY"));
			if (key.length != 32)
				throw new IllegalArgumentException();
			var cipher = Cipher.getInstance("AES/GCM/NoPadding");
			byte[] iv = new byte[12];
			byte[] data;
			if (mode == Cipher.ENCRYPT_MODE) {
				new SecureRandom().nextBytes(iv);
				data = input.getBytes(StandardCharsets.UTF_8);
			}
			else {
				var bytes = Base64.getDecoder().decode(input);
				iv = Arrays.copyOf(bytes, 12);
				data = Arrays.copyOfRange(bytes, 12, bytes.length);
			}
			cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
			cipher.updateAAD((scope.workspace + "/" + scope.owner + "/" + id).getBytes(StandardCharsets.UTF_8));
			byte[] result = cipher.doFinal(data);
			if (mode == Cipher.DECRYPT_MODE)
				return new String(result, StandardCharsets.UTF_8);
			return Base64.getEncoder()
				.encodeToString(java.nio.ByteBuffer.allocate(12 + result.length).put(iv).put(result).array());
		}
		catch (Exception ex) {
			throw new IllegalStateException("Connection key unavailable");
		}
	}

	static String resource(String provider, String channel) {
		return switch (Objects.toString(provider, "")) {
			case "gmail" -> "INBOX";
			case "calendar" -> "primary";
			case "slack" -> {
				if (channel == null || !channel.matches("C[A-Z0-9]{2,20}"))
					throw bad("Enter a public Slack channel ID");
				yield channel;
			}
			default -> throw bad("Unknown provider");
		};
	}

	UUID add(Scope s, String p, String label, String channel, boolean fixture, ProviderClient.Grant grant) {
		String res = resource(p, channel);
		if (label == null || label.isBlank() || label.length() > 80)
			throw bad("Account label required");
		UUID id = UUID.randomUUID();
		return scoped(s, () -> {
			lock(s);
			if (db.queryForObject("SELECT count(*) FROM zincc.feed WHERE state<>'revoked'", Integer.class) >= 32)
				throw conflict("Connection limit reached");
			if (!fixture && (!enabled(p) || !client.scope(p).equals(grant.scope())))
				throw conflict("Provider unavailable");
			db.update(
					"INSERT INTO zincc.feed(id,workspace_id,owner_id,provider,label,resource,fixture,secret,grant_scope,external_team) VALUES(?,?,?,?,?,?,?,?,?,?)",
					id, s.workspace, s.owner, p, label.strip(), res, fixture,
					fixture ? "" : protect(Cipher.ENCRYPT_MODE, s, id, grant.secret()), client.scope(p),
					fixture ? "" : grant.team());
			return id;
		});
	}

	void lock(Scope s) {
		db.queryForMap("SELECT pg_advisory_xact_lock(hashtextextended(?,58))", s.toString());
	}

	List<Feed> feeds() {
		return db.query(
				"SELECT id,provider,label,resource,fixture,grant_scope,state,synced_at,(SELECT count(*) FROM zincc.feed_item i WHERE i.feed_id=f.id) FROM zincc.feed f ORDER BY label,id",
				(r, i) -> new Feed(r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getString(4),
						r.getBoolean(5), r.getString(6), r.getString(7),
						r.getTimestamp(8) == null ? null : r.getTimestamp(8).toInstant(), r.getLong(9)));
	}

	void revoke(Scope s, UUID id) {
		scoped(s, () -> {
			if (db.update("UPDATE zincc.feed SET state='revoked',secret='',page='',checkpoint='',anchor='' WHERE id=?",
					id) != 1)
				throw missing();
			purge(id);
			return null;
		});
	}

	private void purge(UUID id) {
		db.update("DELETE FROM zincc.feed_item WHERE feed_id=?", id);
		withdrawReceipts(id);
	}

	private void withdrawReceipts(UUID id) {
		db.update("UPDATE zincc.routine_run SET receipt='Source access withdrawn' WHERE ?=ANY(used_feeds)", id);
	}

	List<Summary> summaries(UUID id) {
		return db.query(
				"""
						SELECT i.stamp,i.summary FROM zincc.feed_item i JOIN zincc.feed f ON f.id=i.feed_id
						WHERE f.id=? AND f.state='ready' AND f.synced_at>now()-interval '10 minutes' ORDER BY i.stamp DESC,i.external_id LIMIT 3
						""",
				(r, n) -> new Summary(r.getString(1), r.getString(2)), id);
	}

	boolean sync(Scope s, UUID id, boolean manual) {
		return scoped(s, () -> {
			var rows = db.queryForList(
					"SELECT * FROM zincc.feed WHERE id=? AND state<>'revoked' AND (next_sync<=clock_timestamp() OR (? AND state NOT IN ('rate_limited','unavailable','permission_denied'))) FOR UPDATE SKIP LOCKED",
					id, manual);
			if (rows.isEmpty())
				return false;
			var f = rows.getFirst();
			String p = (String) f.get("provider"), cursor = (String) f.get("checkpoint");
			boolean fixture = (boolean) f.get("fixture");
			ProviderClient.Page page;
			try {
				page = fixture
						? ProviderClient.fixture(p, cursor, (String) f.get("page"), (int) f.get("sample_version"))
						: client.fetch(p, (String) f.get("resource"),
								protect(Cipher.DECRYPT_MODE, s, id, (String) f.get("secret")), cursor,
								(String) f.get("page"), (String) f.get("anchor"));
			}
			catch (ProviderClient.Failure failure) {
				if (failure.state.equals("revoked")) {
					revoke(s, id);
					return false;
				}
				if (failure.state.equals("gone") || failure.state.equals("reset")
						|| failure.state.equals("permission_denied")) {
					db.update(
							"UPDATE zincc.feed SET checkpoint='',page='',anchor='',epoch=gen_random_uuid(),changed=false WHERE id=?",
							id);
				}
				db.update("UPDATE zincc.feed SET state=?,next_sync=now()+make_interval(secs=>?) WHERE id=?",
						failure.state.equals("gone") ? "resync" : failure.state,
						failure.delay + java.util.concurrent.ThreadLocalRandom.current().nextInt(3), id);
				if (failure.state.equals("permission_denied"))
					purge(id);
				return false;
			}
			boolean changed = (boolean) f.get("changed");
			for (var item : page.items()) {
				if (item.removed()) {
					if (db.update("DELETE FROM zincc.feed_item WHERE feed_id=? AND external_id=?", id, item.id()) > 0) {
						changed = true;
						withdrawReceipts(id);
					}
					continue;
				}
				int updated = db.update(
						"""
								INSERT INTO zincc.feed_item VALUES(?,?,?,?,?) ON CONFLICT(feed_id,external_id) DO UPDATE SET summary=excluded.summary,stamp=excluded.stamp,epoch=excluded.epoch
								WHERE (feed_item.summary,feed_item.stamp) IS DISTINCT FROM (excluded.summary,excluded.stamp)
								""",
						id, item.id(), item.summary(), item.stamp(), f.get("epoch"));
				changed |= updated > 0;
				if (updated == 0 && page.snapshot())
					db.update("UPDATE zincc.feed_item SET epoch=? WHERE feed_id=? AND external_id=?", f.get("epoch"),
							id, item.id());
			}
			if (!page.next().isEmpty()) {
				if (page.next().equals(f.get("page"))) {
					db.update(
							"UPDATE zincc.feed SET state='invalid_response',next_sync=now()+interval '5 minutes' WHERE id=?",
							id);
					return false;
				}
				db.update(
						"UPDATE zincc.feed SET state='syncing',page=?,anchor=?,changed=?,next_sync=now()+make_interval(secs=>?) WHERE id=?",
						page.next(), page.anchor(), changed, p.equals("slack") && !fixture ? 61 : 0, id);
				return false;
			}
			if (page.snapshot()
					&& db.update("DELETE FROM zincc.feed_item WHERE feed_id=? AND epoch<>?", id, f.get("epoch")) > 0) {
				changed = true;
				withdrawReceipts(id);
			}
			if (changed && f.get("synced_at") != null)
				enqueueEvent(id, "sync/" + id + "/" + f.get("epoch"));
			db.update(
					"UPDATE zincc.feed SET state='ready',page='',anchor='',checkpoint=?,epoch=gen_random_uuid(),synced_at=clock_timestamp(),next_sync=now()+interval '2 minutes',changed=false WHERE id=?",
					page.checkpoint(), id);
			return true;
		});
	}

	void enqueueEvent(UUID feed, String cause) {
		db.update(
				"INSERT INTO zincc.routine_run(routine_id,cause) SELECT id,? FROM zincc.routine WHERE NOT paused AND trigger_kind='event' AND ?=ANY(feeds) ON CONFLICT DO NOTHING",
				cause, feed);
	}

	void sampleEvent(Scope s, UUID id, UUID attempt) {
		if (attempt == null)
			throw bad("Missing event identifier");
		scoped(s, () -> {
			if (db.queryForList("SELECT id FROM zincc.feed WHERE id=? AND fixture AND state='ready' FOR UPDATE", id)
				.isEmpty())
				throw conflict("Sample feed must be ready");
			if (db.update("INSERT INTO zincc.feed_signal(feed_id,event_id) VALUES(?,?) ON CONFLICT DO NOTHING", id,
					"example/" + attempt) == 1)
				db.update("UPDATE zincc.feed SET sample_version=sample_version+1,next_sync=now() WHERE id=?", id);
			return null;
		});
	}

	void signal(Scope s, UUID id, String event) {
		scoped(s, () -> {
			if (db.update(
					"INSERT INTO zincc.feed_signal(feed_id,event_id) SELECT id,? FROM zincc.feed WHERE id=? AND state<>'revoked' ON CONFLICT DO NOTHING",
					event, id) == 1)
				db.update("UPDATE zincc.feed SET next_sync=least(next_sync,now()) WHERE id=? AND state='ready'", id);
			db.update("DELETE FROM zincc.feed_signal WHERE feed_id=? AND arrived<now()-interval '2 days'", id);
			return null;
		});
	}

	SkillCatalog.Skill skill(String name, int version) {
		return catalog.stream()
			.filter(s -> s.name().equals(name) && s.version() == version)
			.findFirst()
			.orElseThrow(() -> bad("Unknown skill version"));
	}

	void install(Scope s, String name, int version, String digest, Set<String> permissions) {
		scoped(s, () -> {
			lock(s);
			if (version == 0) {
				db.update("DELETE FROM zincc.skill_install WHERE package=?", name);
				db.update("UPDATE zincc.routine SET paused=true WHERE package=?", name);
				return null;
			}
			var skill = skill(name, version);
			if (!skill.digest().equals(digest) || permissions == null
					|| !permissions.equals(Set.copyOf(skill.permissions())))
				throw conflict("Review this version and its permissions");
			db.update(
					"INSERT INTO zincc.skill_install VALUES(?,?,?,?,?) ON CONFLICT(workspace_id,owner_id,package) DO UPDATE SET version=excluded.version,digest=excluded.digest",
					s.workspace, s.owner, name, version, digest);
			db.update("UPDATE zincc.routine SET paused=true WHERE package=? AND digest<>?", name, digest);
			return null;
		});
	}

}
