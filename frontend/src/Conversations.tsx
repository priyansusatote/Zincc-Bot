import { ZinccEmblem } from "./ZinccEmblem";
import { useCallback, useEffect, useRef, useState } from "react";
import {
	ArrowUp,
	ArrowUpRight,
	Bot,
	BookOpen,
	ChevronDown,
	Compass,
	FileText,
	Layers,
	Menu,
	MessageSquare,
	Monitor,
	Plus,
	Workflow,
	X,
} from "lucide-react";
import { ApiError, type Session } from "./client";
import { Research } from "./Research";
import { Knowledge } from "./Knowledge";
import { Integrations } from "./Integrations";
import { Runners } from "./Runners";
import { Agents } from "./Agents";
import {
	directory,
	replay,
	snapshot,
	submit,
	type Command,
	type Directory,
	type Snapshot,
} from "./conversation-client";

const interruption: Record<string, string> = {
	RATE_LIMITED:
		"Provider rate limit reached. Try another route or retry later.",
	OUTPUT_LIMIT: "The model reached its output limit before finishing.",
	TIMEOUT: "The model timed out. Retry or choose another route.",
	PROVIDER_ERROR: "The model provider could not complete this request.",
	INVALID_OUTPUT: "The model returned an incomplete or invalid response.",
};
type Props = { workspace: string; session: Session; refresh: () => void };
const destinations = [
	{ id: "chat", label: "Chat", title: "Conversations", icon: MessageSquare },
	{
		id: "agents",
		label: "Agents and runs",
		title: "Agent workspace",
		icon: Bot,
	},
	{
		id: "research",
		label: "Public web research",
		title: "Research",
		icon: Compass,
	},
	{
		id: "knowledge",
		label: "Documents & dashboards",
		title: "Document library",
		icon: BookOpen,
	},
	{
		id: "integrations",
		label: "Connections and routines",
		title: "Automation",
		icon: Workflow,
	},
	{ id: "runners", label: "Customer runners", title: "Devices", icon: Monitor },
] as const;
type Destination = (typeof destinations)[number]["id"];
export function Conversations(props: Props) {
	const { workspace, refresh } = props;
	const [index, setIndex] = useState<Directory>();
	const [id, setId] = useState("");
	const [active, setActive] = useState<Destination>("chat");
	const [menu, setMenu] = useState(false);
	const menuButton = useRef<HTMLButtonElement>(null);
	function navigate(destination: Destination) {
		setActive(destination);
		setMenu(false);
		if (menu) menuButton.current?.focus();
	}
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
	const enabled = {
		chat: true,
		agents: index.agentsEnabled,
		runners: index.agentsEnabled,
		integrations: index.agentsEnabled,
		research: index.publicResearch,
		knowledge: index.knowledgeEnabled,
	};
	const current = destinations.find((d) => d.id === active) ?? destinations[0];
	const thread = index.threads.find((item) => item.id === id);
	return (
		<div className="workstation">
			<button
				className="mobile-menu"
				type="button"
				aria-expanded={menu}
				aria-controls="workspace-sidebar"
				ref={menuButton}
				onClick={() => setMenu(!menu)}
			>
				{menu ? <X /> : <Menu />} Workspace menu
			</button>
			<aside
				id="workspace-sidebar"
				className={menu ? "rail expanded" : "rail"}
				onKeyDown={(e) => {
					if (e.key === "Escape") {
						setMenu(false);
						menuButton.current?.focus();
					}
				}}
			>
				<button
					className="new-chat"
					type="button"
					onClick={() => {
						setId(crypto.randomUUID());
						navigate("chat");
					}}
				>
					<Plus size={17} /> New conversation
				</button>
				<nav aria-label="Workspace tools">
					<h2>Workspace</h2>
					{destinations
						.filter((d) => enabled[d.id])
						.map((d) => (
							<button
								type="button"
								key={d.id}
								aria-current={active === d.id ? "page" : undefined}
								onClick={() => navigate(d.id)}
							>
								<d.icon size={17} />
								{d.label}
							</button>
						))}
				</nav>
				<div className="history">
					<h2>Recent conversations</h2>
					{index.threads.length === 0 && (
						<small>Your conversations will appear here.</small>
					)}
					{index.threads.map((t) => (
						<button
							key={t.id}
							type="button"
							title={t.title}
							aria-current={
								id === t.id && active === "chat" ? "true" : undefined
							}
							onClick={() => {
								setId(t.id);
								navigate("chat");
							}}
						>
							<MessageSquare size={14} />
							<span>{t.title}</span>
						</button>
					))}
				</div>
			</aside>
			<section className="stage" aria-label={current.title}>
				<div className="stage-bar">
					<current.icon size={16} />
					<span>{current.title}</span>
					{active === "chat" && (
						<>
							<span className="slash">/</span>
							<strong>{thread?.title ?? "New thread"}</strong>
						</>
					)}
				</div>
				<div className="chat-page" hidden={active !== "chat"}>
					<Thread
						key={id}
						{...props}
						id={id}
						routes={index.routes}
						presets={index.presets ?? {}}
						saved={saved}
						existing={!!thread}
						shortcuts={
							<div className="shortcuts">
								{enabled.agents && (
									<button type="button" onClick={() => navigate("agents")}>
										<Bot />
										Run an agent
										<ArrowUpRight />
									</button>
								)}
								{enabled.research && (
									<button type="button" onClick={() => navigate("research")}>
										<Compass />
										Research a topic
										<ArrowUpRight />
									</button>
								)}
								{enabled.knowledge && (
									<button type="button" onClick={() => navigate("knowledge")}>
										<FileText />
										Explore a PDF
										<ArrowUpRight />
									</button>
								)}
							</div>
						}
					/>
				</div>
				{active !== "chat" && (
					<div className="tool-view">
						<h2>{current.title}</h2>
						{active === "agents" && <Agents {...props} />}
						{active === "runners" && <Runners {...props} />}
						{active === "knowledge" && <Knowledge {...props} />}
						{active === "integrations" && <Integrations {...props} />}
						{active === "research" && (
							<Research
								{...props}
								source={index.researchSource ?? "English Wikipedia"}
							/>
						)}
					</div>
				)}
			</section>
		</div>
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
	shortcuts,
}: Props & {
	id: string;
	routes: string[];
	presets: Record<string, string>;
	saved: (id: string, title: string) => void;
	existing: boolean;
	shortcuts: React.ReactNode;
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
	const fresh = !existing && !view.turns.length;
	const transcript = useRef<HTMLDivElement>(null);
	const followEnd = useRef(true);
	useEffect(() => {
		const el = transcript.current;
		if (el && followEnd.current && view.revision > 0)
			el.scrollTop = el.scrollHeight;
	}, [view.revision]);
	return (
		<div className={fresh ? "thread empty" : "thread"}>
			{fresh ? (
				<div className="welcome">
					<ZinccEmblem />
					<h2>What are we working on?</h2>
				</div>
			) : (
				<div
					className="transcript"
					ref={transcript}
					onScroll={(e) => {
						const el = e.currentTarget;
						followEnd.current =
							el.scrollHeight - el.scrollTop - el.clientHeight < 90;
					}}
				>
					{view.turns.map((t) => (
						<article key={t.id} className="turn">
							<div className="user-message">
								<h3 className="sr-only">You</h3>
								<p>{t.question}</p>
							</div>
							<h3 className="speaker">
								<span className="mini-mark">Z</span> Zincc{" "}
								<small>{t.route}</small>
							</h3>
							{t.answer && <p>{t.answer}</p>}
							{t.state !== "completed" && (
								<small>
									{t.state === "running"
										? "Responding…"
										: (interruption[t.failure ?? ""] ?? "Response interrupted")}
								</small>
							)}
						</article>
					))}
				</div>
			)}
			<div className="compose-area">
				{link && <p role="status">{link}</p>}
				{error && <p role="alert">{error}</p>}
				{routes.length === 0 ? (
					<p className="no-route">Configure a model route to send a message.</p>
				) : (
					<form
						className="composer"
						onSubmit={(e) => {
							e.preventDefault();
							void send();
						}}
					>
						<label className="sr-only" htmlFor="chat-message">
							Message
						</label>
						<textarea
							id="chat-message"
							placeholder="Message Zincc…"
							value={question}
							maxLength={8000}
							rows={3}
							disabled={sending || !!retry}
							onChange={(e) => setQuestion(e.target.value)}
						/>
						<div className="compose-tools">
							<label className="model-control">
								<Layers size={15} />
								<span className="sr-only">Model route</span>
								<select
									value={route}
									disabled={sending || !!retry}
									onChange={(e) => setRoute(e.target.value)}
								>
									<option value="">Choose a model</option>
									{Object.entries(presets).map(([name, model]) => (
										<option key={name} value={`@${name}`}>
											{name} · {model}
										</option>
									))}
									{routes.map((r) => (
										<option key={r}>{r}</option>
									))}
								</select>
								<ChevronDown size={13} />
							</label>
							<button
								className="send"
								type="submit"
								aria-label={
									sending
										? "Sending…"
										: retry
											? "Retry message"
											: "Send message"
								}
								disabled={
									!ready ||
									sending ||
									(!retry &&
										(view.turns.at(-1)?.state === "running" ||
											!question.trim() ||
											!route))
								}
							>
								{retry ? "Retry" : <ArrowUp size={19} />}
							</button>
						</div>
					</form>
				)}
				{fresh && shortcuts}
				{route && (
					<small className="route-note">
						The selected model receives your message and recent completed turns.
					</small>
				)}
			</div>
		</div>
	);
}
