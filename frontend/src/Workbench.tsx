import { ZinccEmblem } from "./ZinccEmblem";
import { useCallback, useEffect, useState } from "react";
import {
	ArrowRight,
	ChevronDown,
	Layers,
	PanelsTopLeft,
	LogIn,
	LogOut,
	MessageSquare,
	RefreshCw,
	UserRound,
} from "lucide-react";
import {
	ApiError,
	endSession,
	listWorkspaces,
	readSession,
	readWorkspace,
} from "./client";
import type { Membership, Session } from "./client";
import { Conversations } from "./Conversations";

const loginUrl = "/oauth2/authorization/zincc";
type Home =
	| { state: "loading" | "signed-out" | "unconfigured" | "error" }
	| { state: "ready"; session: Session; workspaces: Membership[] };

function Panel({
	title,
	text,
	children,
}: {
	title: string;
	text?: string;
	children?: React.ReactNode;
}) {
	return (
		<section className="panel">
			<span className="panel-icon" aria-hidden="true">
				<MessageSquare size={24} />
			</span>
			<div className="panel-copy">
				<h2>{title}</h2>
				{text && <p>{text}</p>}
				{children}
			</div>
		</section>
	);
}

function Retry({ onClick }: { onClick: () => void }) {
	return (
		<button className="secondary" onClick={onClick} type="button">
			<RefreshCw size={15} />
			Try again
		</button>
	);
}

function Workspace({
	id,
	refresh,
	session,
}: {
	id: string;
	refresh: () => void;
	session: Session;
}) {
	const [result, setResult] = useState<
		Membership | "loading" | "unavailable" | "error"
	>("loading");
	const [attempt, setAttempt] = useState(0);
	// biome-ignore lint/correctness/useExhaustiveDependencies: retry starts a fresh request for the same ID.
	useEffect(() => {
		const controller = new AbortController();
		setResult("loading");
		readWorkspace(id, controller.signal)
			.then((value) => {
				if (!controller.signal.aborted) setResult(value);
			})
			.catch((error) => {
				if (controller.signal.aborted) return;
				if (error instanceof ApiError && error.status === 401) {
					refresh();
					return;
				}
				setResult(
					error instanceof ApiError && error.status === 404
						? "unavailable"
						: "error",
				);
			});
		return () => controller.abort();
	}, [id, attempt, refresh]);
	if (result === "loading") return <p role="status">Opening workspace…</p>;
	if (result === "unavailable")
		return (
			<Panel
				title="Workspace unavailable"
				text="Your access may have changed. Refresh your workspace list to continue."
			>
				<button className="secondary" onClick={refresh} type="button">
					Refresh workspaces
				</button>
			</Panel>
		);
	if (result === "error")
		return (
			<Panel title="Couldn’t open this workspace">
				<Retry onClick={() => setAttempt((n) => n + 1)} />
			</Panel>
		);
	return <Conversations workspace={id} session={session} refresh={refresh} />;
}

export function Workbench() {
	const [home, setHome] = useState<Home>({ state: "loading" });
	const [selected, setSelected] = useState("");
	const [view, setView] = useState<"workbench" | "account">("workbench");
	const [revision, setRevision] = useState(0);
	const [action, setAction] = useState<"idle" | "pending" | "error">("idle");
	const refresh = useCallback(() => {
		setHome({ state: "loading" });
		setAction("idle");
		setRevision((n) => n + 1);
	}, []);
	// biome-ignore lint/correctness/useExhaustiveDependencies: a refresh invalidates the session and directory together.
	useEffect(() => {
		const controller = new AbortController();
		async function load() {
			try {
				let session: Session;
				try {
					session = await readSession(controller.signal);
				} catch (e) {
					if (
						!controller.signal.aborted &&
						e instanceof ApiError &&
						e.status === 404
					) {
						setHome({ state: "unconfigured" });
						return;
					}
					throw e;
				}
				const workspaces = session.provisioned
					? await listWorkspaces(controller.signal)
					: [];
				if (controller.signal.aborted) return;
				setSelected((current) =>
					workspaces.some((w) => w.workspaceId === current)
						? current
						: (workspaces[0]?.workspaceId ?? ""),
				);
				setHome({ state: "ready", session, workspaces });
			} catch (e) {
				if (!controller.signal.aborted)
					setHome({
						state:
							e instanceof ApiError && e.status === 401
								? "signed-out"
								: "error",
					});
			}
		}
		void load();
		return () => controller.abort();
	}, [revision]);
	useEffect(() => {
		const visible = () => {
			if (document.visibilityState === "visible") refresh();
		};
		const restored = (event: PageTransitionEvent) => {
			if (event.persisted) refresh();
		};
		document.addEventListener("visibilitychange", visible);
		window.addEventListener("pageshow", restored);
		return () => {
			document.removeEventListener("visibilitychange", visible);
			window.removeEventListener("pageshow", restored);
		};
	}, [refresh]);
	async function logout(switchAccount: boolean) {
		if (home.state !== "ready" || action === "pending") return;
		setAction("pending");
		try {
			await endSession(home.session);
			setSelected("");
			setView("workbench");
			setHome({ state: "signed-out" });
			setAction("idle");
			if (switchAccount) window.location.assign(`${loginUrl}?account=switch`);
		} catch {
			setAction("error");
		}
	}
	const ready = home.state === "ready";
	const current = ready
		? home.workspaces.find((w) => w.workspaceId === selected)
		: undefined;
	const account = view === "account" && ready;
	return (
		<div className="shell">
			<a className="skip" href="#main">
				Skip to content
			</a>
			<header className="appbar">
				<div className="brand">
					<span className="mark" aria-hidden="true">
						Z
					</span>
					Zincc
				</div>
				<div className="workspace-meta">
					{ready && home.workspaces.length > 0 && (
						<label className="select">
							<Layers size={15} />
							<span className="sr-only">Switch workspace</span>
							<select
								value={selected}
								onChange={(e) => {
									setSelected(e.target.value);
									setView("workbench");
								}}
							>
								{home.workspaces.map((w) => (
									<option key={w.workspaceId} value={w.workspaceId}>
										{w.workspaceName}
									</option>
								))}
							</select>
							<ChevronDown size={14} />
						</label>
					)}
					{current && <span className="role">{current.role}</span>}
				</div>
				{ready && (
					<button
						type="button"
						className="account-link"
						onClick={() => setView(account ? "workbench" : "account")}
						aria-label={account ? "Workbench" : "Account"}
					>
						{account ? <PanelsTopLeft size={17} /> : <UserRound size={17} />}
						{account ? "Workbench" : home.session.accountLabel}
					</button>
				)}
			</header>
			<main id="main" tabIndex={-1}>
				<div
					className={ready && current && !account ? "content" : "access-screen"}
				>
					<h1
						className={
							(ready && current && !account) || home.state === "signed-out"
								? "sr-only"
								: undefined
						}
					>
						{account ? "Your account" : (current?.workspaceName ?? "Workbench")}
					</h1>
					{home.state === "loading" && (
						<p role="status">Loading your workbench…</p>
					)}
					{home.state === "signed-out" && (
						<section className="sign-in">
							<ZinccEmblem />
							<h2>Sign in to your workspace</h2>
							<p>Open your conversations, agents and routines.</p>
							<a className="primary" href={loginUrl}>
								Sign in <ArrowRight size={16} />
							</a>
						</section>
					)}
					{home.state === "unconfigured" && (
						<Panel
							title="Workspace sign-in is unavailable"
							text="Ask your administrator to enable the OIDC profile on the Zincc service."
						>
							<Retry onClick={refresh} />
						</Panel>
					)}
					{home.state === "error" && (
						<Panel
							title="Couldn’t reach your workbench"
							text="Check the connection and try again."
						>
							<Retry onClick={refresh} />
						</Panel>
					)}
					{ready && account && (
						<section className="account-panel">
							<span className="avatar">
								<UserRound size={22} />
							</span>
							<h2>{home.session.accountLabel}</h2>
							<p>
								{home.session.provisioned
									? "Connected to Zincc"
									: "Awaiting workspace access"}
							</p>
							<div className="actions">
								<button
									type="button"
									className="secondary"
									disabled={action === "pending"}
									onClick={() => void logout(true)}
								>
									<LogIn size={15} /> Switch account
								</button>
								<button
									type="button"
									className="secondary"
									disabled={action === "pending"}
									onClick={() => void logout(false)}
								>
									<LogOut size={15} /> Sign out
								</button>
							</div>
							{action === "error" && (
								<div role="alert">
									<p>
										Couldn’t confirm sign-out. Refresh your session and try
										again.
									</p>
									<button className="secondary" type="button" onClick={refresh}>
										Refresh session
									</button>
								</div>
							)}
						</section>
					)}
					{ready &&
						!account &&
						(current ? (
							<Workspace
								key={current.workspaceId}
								id={current.workspaceId}
								refresh={refresh}
								session={home.session}
							/>
						) : (
							<Panel
								title={
									home.session.provisioned
										? "No workspaces assigned"
										: "Workspace access pending"
								}
								text="Ask your workspace administrator for access."
							>
								<button className="secondary" onClick={refresh} type="button">
									Check access
								</button>
							</Panel>
						))}
				</div>
			</main>
		</div>
	);
}
