import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError, type Session } from "./client";
import { Research } from "./Research";
import {
	directory,
	replay,
	snapshot,
	submit,
	type Command,
	type Directory,
	type Snapshot,
} from "./conversation-client";

type Props = { workspace: string; session: Session; refresh: () => void };
export function Conversations(props: Props) {
	const { workspace, refresh } = props;
	const [index, setIndex] = useState<Directory>();
	const [id, setId] = useState("");
	const [failed, setFailed] = useState(false);
	const [attempt, setAttempt] = useState(0);
	// biome-ignore lint/correctness/useExhaustiveDependencies: retry loads the same directory.
	useEffect(() => {
		const controller = new AbortController();
		setFailed(false);
		directory(workspace, controller.signal)
			.then((d) => {
				if (controller.signal.aborted) return;
				setIndex(d);
				setId(d.threads[0]?.id ?? crypto.randomUUID());
			})
			.catch((e) => {
				if (controller.signal.aborted) return;
				if (e instanceof ApiError && [401, 404].includes(e.status)) refresh();
				else setFailed(true);
			});
		return () => controller.abort();
	}, [workspace, refresh, attempt]);
	const saved = useCallback((key: string, title: string) => {
		setIndex((old) =>
			old && !old.threads.some((t) => t.id === key)
				? { ...old, threads: [{ id: key, title }, ...old.threads] }
				: old,
		);
	}, []);
	if (failed)
		return (
			<p role="alert">
				Couldn’t load conversations.{" "}
				<button type="button" onClick={() => setAttempt((n) => n + 1)}>
					Retry
				</button>
			</p>
		);
	if (!index || !id) return <p role="status">Loading conversations…</p>;
	return (
		<section className="conversation">
			{index.publicResearch && (
				<Research
					{...props}
					source={index.researchSource ?? "English Wikipedia"}
				/>
			)}
			<div className="thread-tools">
				<label>
					Conversation{" "}
					<select value={id} onChange={(e) => setId(e.target.value)}>
						{!index.threads.some((t) => t.id === id) && (
							<option value={id}>New conversation</option>
						)}
						{index.threads.map((t) => (
							<option key={t.id} value={t.id}>
								{t.title}
							</option>
						))}
					</select>
				</label>
				<button
					type="button"
					className="secondary"
					onClick={() => setId(crypto.randomUUID())}
				>
					New conversation
				</button>
			</div>
			<Thread
				key={id}
				{...props}
				id={id}
				routes={index.routes}
				presets={index.presets ?? {}}
				saved={saved}
				existing={index.threads.some((t) => t.id === id)}
			/>
		</section>
	);
}

const empty: Snapshot = { revision: 0, turns: [] };
function Thread({
	workspace,
	session,
	refresh,
	id,
	routes,
	presets,
	saved,
	existing,
}: Props & {
	id: string;
	routes: string[];
	presets: Record<string, string>;
	saved: (id: string, title: string) => void;
	existing: boolean;
}) {
	const [view, setView] = useState<Snapshot>(empty);
	const [ready, setReady] = useState(!existing);
	const [question, setQuestion] = useState("");
	const [route, setRoute] = useState("");
	const [sending, setSending] = useState(false);
	const [retry, setRetry] = useState<Command>();
	const [error, setError] = useState("");
	const [link, setLink] = useState("");
	const [reload, setReload] = useState(0);
	const alive = useRef(false);
	useEffect(() => {
		alive.current = true;
		return () => {
			alive.current = false;
		};
	}, []);
	const follow = existing || sending || !!retry;
	// biome-ignore lint/correctness/useExhaustiveDependencies: reload requests an authoritative snapshot after a command.
	useEffect(() => {
		if (!follow) return;
		const controller = new AbortController();
		let timer: ReturnType<typeof setTimeout>;
		let state = empty;
		let needsSnapshot = existing;
		async function read() {
			try {
				const next = needsSnapshot
					? await snapshot(workspace, id, controller.signal)
					: await replay(workspace, id, state, controller.signal);
				if (controller.signal.aborted) return;
				state = next;
				needsSnapshot = false;
				setView(next);
				if (!existing && next.turns[0])
					saved(id, next.turns[0].question.slice(0, 80));
				setReady(true);
				setLink("");
			} catch (e) {
				if (controller.signal.aborted) return;
				if (
					e instanceof ApiError &&
					(e.status === 401 ||
						e.status === 403 ||
						(e.status === 404 && (existing || state.revision > 0)))
				) {
					refresh();
					return;
				}
				if (!(e instanceof ApiError && e.status === 404))
					setLink("Reconnecting…");
			}
			timer = setTimeout(
				read,
				state.turns.at(-1)?.state === "running" || !existing ? 500 : 2000,
			);
		}
		void read();
		return () => {
			controller.abort();
			clearTimeout(timer);
		};
	}, [workspace, id, existing, follow, reload, refresh, saved]);
	async function send() {
		if (sending) return;
		const cmd = retry ?? {
			turnId: crypto.randomUUID(),
			revision: view.revision,
			route: route.startsWith("@") ? presets[route.slice(1)] : route,
			question,
		};
		setSending(true);
		setError("");
		try {
			await submit(workspace, id, cmd, session);
			saved(id, cmd.question.slice(0, 80));
			if (!alive.current) return;
			setRetry(undefined);
			setQuestion("");
			setReady(false);
			setReload((n) => n + 1);
		} catch (e) {
			if (!alive.current) return;
			if (e instanceof ApiError && [401, 403, 404].includes(e.status)) {
				refresh();
				return;
			}
			if (e instanceof ApiError && [400, 409, 422].includes(e.status)) {
				setRetry(undefined);
				setReload((n) => n + 1);
				setError(
					e.status === 409
						? "Conversation changed. Review it before sending again."
						: e.status === 422
							? "This conversation is full. Start a new one."
							: "Check your message and model route.",
				);
			} else {
				setRetry(cmd);
				setError("Delivery is uncertain. Retry the same message to check.");
			}
		} finally {
			if (alive.current) setSending(false);
		}
	}
	return (
		<>
			{view.turns.map((t) => (
				<article key={t.id} className="exchange">
					<h3>You</h3>
					<p>{t.question}</p>
					<h3>{t.route}</h3>
					{t.answer && <p>{t.answer}</p>}
					{t.state !== "completed" && (
						<small>
							{t.state === "running" ? "Responding…" : "Response interrupted"}
						</small>
					)}
				</article>
			))}
			{link && <p role="status">{link}</p>}
			{error && <p role="alert">{error}</p>}
			{routes.length === 0 ? (
				<p>Configure a model route to send a message.</p>
			) : (
				<form
					onSubmit={(e) => {
						e.preventDefault();
						void send();
					}}
				>
					<label>
						Model route{" "}
						<select
							value={route}
							disabled={sending || !!retry}
							onChange={(e) => setRoute(e.target.value)}
						>
							<option value="">Choose a route</option>
							{Object.entries(presets).map(([name, model]) => (
								<option key={name} value={`@${name}`}>
									{name} · {model}
								</option>
							))}
							{routes.map((r) => (
								<option key={r}>{r}</option>
							))}
						</select>
					</label>
					<small>
						The selected model receives your message and recent completed turns.
					</small>
					<label>
						Message
						<textarea
							value={question}
							maxLength={8000}
							rows={3}
							disabled={sending || !!retry}
							onChange={(e) => setQuestion(e.target.value)}
						/>
					</label>
					<button
						type="submit"
						className="primary"
						disabled={
							!ready ||
							sending ||
							(!retry &&
								(view.turns.at(-1)?.state === "running" ||
									!question.trim() ||
									!route))
						}
					>
						{sending ? "Sending…" : retry ? "Retry message" : "Send message"}
					</button>
				</form>
			)}
		</>
	);
}
