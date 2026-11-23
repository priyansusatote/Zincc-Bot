package io.zincc.integrations;

import io.zincc.identity.SessionPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static io.zincc.integrations.IntegrationStore.*;

@RestController
@Profile("agents & oidc")
class RunnerApi {

	static final String BASE = "/api/workspaces/{workspace}/runners";

	private static final SecureRandom RANDOM = new SecureRandom();

	private final IntegrationStore store;

	private final SessionPrincipal principals;

	RunnerApi(IntegrationStore store, SessionPrincipal principals) {
		this.store = store;
		this.principals = principals;
	}

	private Scope scope(UUID workspace, OidcUser user) {
		return new Scope(workspace, principals.resolve(user).orElseThrow(IntegrationStore::missing));
	}

	record View(List<Map<String, Object>> devices, List<Map<String, Object>> jobs) {
	}

	@GetMapping(BASE)
	View list(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace) {
		return store.scoped(scope(workspace, user), () -> new View(store.db.queryForList("""
				SELECT id,CASE WHEN state='pairing' AND pair_until<=now() THEN 'expired' ELSE state END AS state,
				coalesce(seen>now()-interval '25 seconds',false) AS online,folder,browser FROM zincc.runner
				ORDER BY pair_until DESC LIMIT 100"""), store.db.queryForList("""
				SELECT id,device,kind,CASE WHEN (state='leased' AND lease_until<=now()) OR
				(state='queued' AND created<=now()-interval '1 minute') THEN 'expired' ELSE state END AS state,result
				FROM zincc.runner_job ORDER BY created DESC LIMIT 50""")));
	}

	record Pair(String code) {
	}

	@PostMapping(BASE)
	ResponseEntity<Pair> invite(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace) {
		var s = scope(workspace, user);
		return store.scoped(s, () -> {
			var id = UUID.randomUUID();
			String key = key();
			store.db.update(
					"INSERT INTO zincc.runner(id,workspace_id,owner_id,token_hash,state) VALUES(?,?,?,?,'pairing')", id,
					workspace, s.owner(), hash(key));
			return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(new Pair(workspace + "." + s.owner() + "." + id + "." + key));
		});
	}

	record Command(String action) {
	}

	@PostMapping(BASE + "/{device}")
	void command(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace, @PathVariable UUID device,
			@RequestBody Command command) {
		store.scoped(scope(workspace, user), () -> {
			var r = lock(device);
			String state = (String) r.get("state");
			String action = Objects.toString(command.action, "");
			switch (action) {
				case "pause", "revoke" -> {
					if (state.equals("revoked") || (action.equals("pause") && !state.equals("active")))
						throw conflict("Device unavailable");
					boolean revoke = action.equals("revoke");
					store.db.update(
							"UPDATE zincc.runner SET state=?,token_hash=CASE WHEN ? THEN NULL ELSE token_hash END WHERE id=?",
							revoke ? "revoked" : "paused", revoke, device);
					store.db.update(
							"UPDATE zincc.runner_job SET state='cancelled' WHERE device=? AND state IN ('leased','queued')",
							device);
				}
				case "resume" -> {
					if (!state.equals("paused"))
						throw conflict("Device is not paused");
					store.db.update("UPDATE zincc.runner SET state='active' WHERE id=?", device);
				}
				case "inventory", "browser" -> {
					if (!state.equals("active") || !Boolean.TRUE.equals(r.get("online"))
							|| !Boolean.TRUE.equals(r.get(action.equals("browser") ? "browser" : "folder")))
						throw conflict("Connect runner with the required local grant");
					expire(device);
					int n = store.db.update(
							"INSERT INTO zincc.runner_job(id,device,kind,state) VALUES(?,?,?,'queued') ON CONFLICT DO NOTHING",
							UUID.randomUUID(), device, action);
					if (n != 1)
						throw conflict("Device already has work");
				}
				default -> throw bad("Unknown runner action");
			}
			return true;
		});
	}

	private Map<String, Object> lock(UUID id) {
		var rows = store.db.queryForList("""
				SELECT *,coalesce(seen>clock_timestamp()-interval '25 seconds',false) online,
				pair_until>clock_timestamp() pair_valid FROM zincc.runner WHERE id=? FOR UPDATE""", id);
		if (rows.isEmpty())
			throw missing();
		return rows.getFirst();
	}

	private void expire(UUID device) {
		store.db
			.update("""
					UPDATE zincc.runner_job SET state='expired' WHERE device=? AND
					((state='leased' AND lease_until<=clock_timestamp()) OR (state='queued' AND created<=clock_timestamp()-interval '1 minute'))""",
					device);
	}

	record Pulse(UUID workspace, UUID owner, UUID device, String action, boolean folder, boolean browser, UUID job,
			Integer result) {
	}

	record Reply(String state, String credential, UUID job, String kind) {
	}

	@PostMapping("/api/device/exchange")
	ResponseEntity<Reply> exchange(@RequestHeader(value = "Authorization", defaultValue = "") String auth,
			@RequestBody Pulse p) {
		if (!auth.matches("Bearer [A-Za-z0-9_-]{43}") || p.workspace == null || p.owner == null || p.device == null)
			throw unauthorized();
		var s = new Scope(p.workspace, p.owner);
		var reply = store.scoped(s, () -> {
			var r = lock(p.device);
			var digest = (byte[]) r.get("token_hash");
			if (digest == null || !MessageDigest.isEqual(digest, hash(auth.substring(7))))
				throw unauthorized();
			String state = (String) r.get("state");
			if ("pair".equals(p.action)) {
				if (!"pairing".equals(state) || !Boolean.TRUE.equals(r.get("pair_valid")))
					throw unauthorized();
				String token = key();
				store.db.update(
						"UPDATE zincc.runner SET state='active',seen=now(),folder=?,browser=?,token_hash=? WHERE id=?",
						p.folder, p.browser, hash(token), p.device);
				return new Reply("active", token, null, null);
			}
			if ("pairing".equals(state))
				throw unauthorized();
			if ("paused".equals(state))
				return new Reply(state, null, null, null);
			expire(p.device);
			store.db.update("UPDATE zincc.runner SET seen=now() WHERE id=?", p.device);
			if ("poll".equals(p.action)) {
				var jobs = store.db.queryForList(
						"""
								UPDATE zincc.runner_job SET state='leased',deadline=now()+interval '5 minutes',lease_until=now()+interval '25 seconds'
								WHERE device=? AND state='queued' RETURNING id,kind""",
						p.device);
				if (jobs.isEmpty())
					return new Reply("idle", null, null, null);
				return new Reply("leased", null, (UUID) jobs.getFirst().get("id"),
						(String) jobs.getFirst().get("kind"));
			}
			if (!Set.of("renew", "complete", "failed").contains(Objects.toString(p.action, "")) || p.job == null)
				throw bad("Invalid device event");
			boolean renew = "renew".equals(p.action);
			if ("complete".equals(p.action) && (p.result == null || p.result < 0 || p.result > 10000))
				throw bad("Invalid receipt");
			int updated = store.db.update(
					"""
							UPDATE zincc.runner_job SET state=?,result=?,lease_until=least(deadline,clock_timestamp()+interval '25 seconds')
							WHERE id=? AND device=? AND state='leased' AND lease_until>clock_timestamp() AND deadline>clock_timestamp()
							AND (kind<>'browser' OR ?<>'complete' OR ? BETWEEN 0 AND 2)""",
					renew ? "leased" : p.action, "complete".equals(p.action) ? p.result : null, p.job, p.device,
					p.action, p.result);
			if (updated != 1)
				throw conflict("Lease ended");
			return new Reply(renew ? "leased" : p.action, null, p.job, null);
		});
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reply);
	}

	private static ResponseStatusException unauthorized() {
		return new ResponseStatusException(HttpStatus.UNAUTHORIZED);
	}

	private static String key() {
		byte[] b = new byte[32];
		RANDOM.nextBytes(b);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}

	private static byte[] hash(String key) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.US_ASCII));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
