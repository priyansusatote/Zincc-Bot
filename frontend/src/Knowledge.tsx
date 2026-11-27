/** biome-ignore-all lint/suspicious/noArrayIndexKey: PDF page fragments and table columns use immutable source positions. */
import { useEffect, useState } from "react";
import { ApiError, check, client, type Session } from "./client";
import type { components } from "./generated/api";

const root = "/api/workspaces/{workspaceId}/knowledge" as const;
type Schemas = components["schemas"];
type Doc = Schemas["PrivateDocument"];
type Evidence = Schemas["DocumentEvidence"];
type Dataset = Schemas["DocumentDataset"];
type Dashboard = Schemas["DocumentDashboard"];
type Report = Schemas["DocumentReport"];
type Auth = { workspace: string; session: Session };

function requestError(error: unknown) {
	switch (error instanceof ApiError ? error.status : 0) {
		case 404:
			return "This document is no longer available to you.";
		case 409:
			return "The document or dashboard changed. Reload before retrying.";
		case 413:
			return "Upload a PDF of 8 MB or less.";
		case 429:
			return "The document worker is busy. Try again.";
		default:
			return "Couldn’t finish the document request. Try again.";
	}
}

export function Knowledge({ workspace, session }: Auth) {
	const [documents, setDocuments] = useState<Doc[]>([]);
	const [selected, setSelected] = useState("");
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState("");
	const params = { path: { workspaceId: workspace } };
	const headers = { [session.csrfHeader]: session.csrfToken };
	async function refresh() {
		const { data, response } = await client.GET(root, { params });
		check(response);
		setDocuments(data ?? []);
	}
	useEffect(() => {
		const controller = new AbortController();
		client
			.GET(root, {
				params: { path: { workspaceId: workspace } },
				signal: controller.signal,
			})
			.then(({ data, response }) => {
				check(response);
				if (!controller.signal.aborted) setDocuments(data ?? []);
			})
			.catch((error) => {
				if (!controller.signal.aborted) setError(requestError(error));
			});
		return () => controller.abort();
	}, [workspace]);

	async function act(operation: () => Promise<void>) {
		setBusy(true);
		setError("");
		try {
			await operation();
		} catch (error) {
			setError(requestError(error));
		} finally {
			setBusy(false);
		}
	}
	const doc = documents.find((item) => item.id === selected);
	return (
		<section aria-label="Private documents">
			<form
				onSubmit={(event) => {
					event.preventDefault();
					const form = event.currentTarget;
					const file = new FormData(form).get("file");
					if (!(file instanceof File) || !file.size) return;
					void act(async () => {
						const response = await fetch(
							`/api/workspaces/${workspace}/knowledge`,
							{
								method: "POST",
								body: new FormData(form),
								headers,
								credentials: "same-origin",
								cache: "no-store",
							},
						);
						try {
							check(response);
							const saved = (await response.json()) as Doc;
							setSelected(saved.id);
							form.reset();
						} finally {
							await refresh();
						}
					});
				}}
			>
				<label>
					PDF file
					<input
						name="file"
						type="file"
						accept=".pdf,application/pdf"
						required
					/>
				</label>
				<button type="submit" disabled={busy}>
					{busy ? "Working…" : "Upload PDF"}
				</button>
			</form>
			{error && <p role="alert">{error}</p>}
			<label>
				Private document
				<select
					value={selected}
					onChange={(event) => setSelected(event.target.value)}
				>
					<option value="">Choose a document</option>
					{documents.map((item) => (
						<option key={item.id} value={item.id}>
							{item.name} · {item.state}
						</option>
					))}
				</select>
			</label>
			{doc && (
				<>
					<p>{doc.detail}</p>
					{doc.state === "processing" && (
						<button
							type="button"
							disabled={busy}
							onClick={() => void act(refresh)}
						>
							Refresh status
						</button>
					)}
					{doc.missingPages.length > 0 && (
						<p>Pages with no extractable text: {doc.missingPages.join(", ")}</p>
					)}
					{doc.scannedPages.length > 0 && (
						<p>
							OCR text, verify against the scan on pages{" "}
							{doc.scannedPages.join(", ")}.
						</p>
					)}
					{doc.state === "ready" && (
						<DocumentPanel
							key={doc.id}
							doc={doc}
							workspace={workspace}
							session={session}
						/>
					)}
					<details>
						<summary>Delete document</summary>
						<p>This removes the source, passages, tables, and dashboards.</p>
						<button
							type="button"
							disabled={busy}
							onClick={() =>
								void act(async () => {
									check(
										(
											await client.DELETE(`${root}/{doc}`, {
												params: { path: { ...params.path, doc: doc.id } },
												headers,
											})
										).response,
									);
									setSelected("");
									await refresh();
								})
							}
						>
							Confirm deletion
						</button>
					</details>
				</>
			)}
		</section>
	);
}

function DocumentPanel({ doc, workspace, session }: Auth & { doc: Doc }) {
	const [datasets, setDatasets] = useState<Dataset[]>([]);
	const [dashboards, setDashboards] = useState<Dashboard[]>([]);
	const [selected, setSelected] = useState("");
	const [dataset, setDataset] = useState(0);
	const [query, setQuery] = useState("");
	const [matches, setMatches] = useState<Evidence[]>();
	const [page, setPage] = useState<{ number: number; excerpts: Evidence[] }>();
	const [report, setReport] = useState<Report>();
	const [error, setError] = useState("");
	const [busy, setBusy] = useState(false);
	const params = { path: { workspaceId: workspace, doc: doc.id } };
	const url = `/api/workspaces/${workspace}/knowledge/${doc.id}`;
	const headers = { [session.csrfHeader]: session.csrfToken };

	useEffect(() => {
		const ctl = new AbortController();
		const params = { path: { workspaceId: workspace, doc: doc.id } };
		Promise.all([
			client.GET(`${root}/{doc}/datasets`, { params, signal: ctl.signal }),
			client.GET(`${root}/{doc}/dashboards`, { params, signal: ctl.signal }),
		])
			.then(([data, boards]) => {
				check(data.response);
				check(boards.response);
				if (!ctl.signal.aborted) {
					setDatasets(data.data ?? []);
					setDashboards(boards.data ?? []);
				}
			})
			.catch((error) => {
				if (!ctl.signal.aborted) setError(requestError(error));
			});
		return () => ctl.abort();
	}, [workspace, doc.id]);

	async function act(operation: () => Promise<void>) {
		setBusy(true);
		setError("");
		try {
			await operation();
		} catch (error) {
			setError(requestError(error));
		} finally {
			setBusy(false);
		}
	}
	function showPage(number: number) {
		void act(async () => {
			const result = await client.GET(`${root}/{doc}/pages/{page}`, {
				params: { path: { ...params.path, page: number } },
			});
			check(result.response);
			setPage({ number, excerpts: result.data ?? [] });
		});
	}
	async function loadReport(id: string) {
		const result = await client.GET(`${root}/{doc}/dashboards/{id}`, {
			params: { path: { ...params.path, id } },
		});
		check(result.response);
		setReport(result.data);
	}
	const current = dashboards.find((value) => value.id === selected);
	const table = datasets.find(
		(value) => value.ordinal === (current?.dataset ?? dataset),
	);
	return (
		<>
			<p>
				{doc.pages} pages · <a href={`${url}/source`}>Download original PDF</a>
			</p>
			<form
				onSubmit={(event) => {
					event.preventDefault();
					setMatches(undefined);
					void act(async () => {
						const result = await client.POST(`${root}/{doc}/search`, {
							params,
							headers,
							body: { query: query.trim() },
						});
						check(result.response);
						setMatches(result.data ?? []);
					});
				}}
			>
				<label>
					Question about this PDF
					<input
						value={query}
						onChange={(event) => setQuery(event.target.value)}
						maxLength={300}
						required
					/>
				</label>
				<button type="submit" disabled={busy || !query.trim()}>
					Find cited passages
				</button>
			</form>
			{matches && (
				<section aria-label="Cited passages">
					{!matches.length && <p>No supporting passages found.</p>}
					{matches.map((match, index) => (
						<figure key={`${index}-${match.page}`}>
							<blockquote>{match.text}</blockquote>
							<button
								type="button"
								disabled={busy}
								onClick={() => showPage(match.page)}
							>
								Source page {match.page}
							</button>
						</figure>
					))}
				</section>
			)}
			{page && (
				<section aria-label="Source page">
					<h3>Page {page.number} text</h3>
					{page.excerpts.length ? (
						page.excerpts.map((item, index) => (
							<p key={`${index}-${item.page}`}>{item.text}</p>
						))
					) : (
						<p>No text extracted.</p>
					)}
				</section>
			)}
			{error && <p role="alert">{error}</p>}
			<h3>Table dashboards</h3>
			{!datasets.length && <p>No ruled tables available.</p>}
			{datasets.length > 0 && (
				<>
					<label>
						Dashboard
						<select
							value={selected}
							disabled={busy}
							onChange={(event) => {
								const id = event.target.value;
								setSelected(id);
								setReport(undefined);
								if (id) void act(() => loadReport(id));
							}}
						>
							<option value="">New dashboard</option>
							{dashboards.map((board) => (
								<option key={board.id} value={board.id}>
									{board.name}
								</option>
							))}
						</select>
					</label>
					{!current && (
						<label>
							Extracted table
							<select
								value={dataset}
								disabled={busy}
								onChange={(event) => setDataset(Number(event.target.value))}
							>
								<option value={0}>Choose a table</option>
								{datasets.map((value) => (
									<option key={value.ordinal} value={value.ordinal}>
										Table {value.ordinal} · page {value.page}
									</option>
								))}
							</select>
						</label>
					)}
					{table && (
						<DashboardForm
							key={`${current?.id ?? table.ordinal}-${current?.revision ?? 0}`}
							table={table}
							dashboard={current}
							busy={busy}
							save={(settings) =>
								void act(async () => {
									setReport(undefined);
									const id = current?.id ?? crypto.randomUUID();
									const updated = await client.PUT(
										`${root}/{doc}/dashboards/{id}`,
										{
											params: { path: { ...params.path, id } },
											headers,
											body: settings,
										},
									);
									check(updated.response);
									const next = await client.GET(`${root}/{doc}/dashboards`, {
										params,
									});
									check(next.response);
									setDashboards(next.data ?? []);
									setSelected(id);
									await loadReport(id);
								})
							}
						/>
					)}
				</>
			)}
			{report && (
				<section aria-label="Dashboard results">
					<h4>{report.dashboard.name}</h4>
					{report.error ? (
						<p role="alert">{report.error}</p>
					) : (
						<>
							<p>
								<strong>
									Total {report.total} {report.dashboard.unit}
								</strong>{" "}
								· {report.missing} missing values
							</p>
							<p>
								<a
									href={`${url}/dashboards/${report.dashboard.id}/export?revision=${report.dashboard.revision}`}
								>
									Export filtered CSV
								</a>
							</p>
							<Chart report={report} />
							<div className="report-scroll">
								<table>
									<caption>Filtered rows from page {report.page}</caption>
									<thead>
										<tr>
											{report.headers.map((label, index) => (
												<th key={`${index}-${label}`} scope="col">
													{label}
												</th>
											))}
											<th scope="col">Source</th>
										</tr>
									</thead>
									<tbody>
										{report.rows.map((row) => (
											<tr key={row.sourceRow}>
												{row.values.map((value, index) => (
													<td key={`${index}-${value}`}>{value || "—"}</td>
												))}
												<td>
													<button
														type="button"
														disabled={busy}
														onClick={() => showPage(report.page)}
													>
														Page {report.page}, row {row.sourceRow}
													</button>
													{row.cells[report.dashboard.measure] && (
														<small>
															{" "}
															{row.cells[report.dashboard.measure]?.join(", ")}{" "}
															pt
														</small>
													)}
												</td>
											</tr>
										))}
									</tbody>
								</table>
							</div>
						</>
					)}
				</section>
			)}
		</>
	);
}

function DashboardForm({
	table,
	dashboard,
	busy,
	save,
}: {
	table: Dataset;
	dashboard?: Dashboard;
	busy: boolean;
	save: (settings: Schemas["DashboardSettings"]) => void;
}) {
	const [category, setCategory] = useState(dashboard?.category ?? 0);
	const [filter, setFilter] = useState(dashboard?.selected ?? null);
	const columns = table.data.rows[0];
	const categories = [
		...new Set(table.data.rows.slice(1).map((row) => row[category])),
	];
	return (
		<form
			onSubmit={(event) => {
				event.preventDefault();
				const data = new FormData(event.currentTarget);
				save({
					dataset: table.ordinal,
					name: String(data.get("name")),
					unit: String(data.get("unit")),
					measure: Number(data.get("measure")),
					category,
					selected: filter,
					expectedRevision: dashboard?.revision ?? 0,
				});
			}}
		>
			<label>
				Dashboard name
				<input
					name="name"
					defaultValue={dashboard?.name ?? ""}
					maxLength={100}
					required
				/>
			</label>
			<label>
				Measure column
				<select name="measure" defaultValue={dashboard?.measure ?? 1}>
					{columns.map((label, index) => (
						<option key={index} value={index}>
							{label || `Column ${index + 1}`}
						</option>
					))}
				</select>
			</label>
			<label>
				Measure unit
				<input
					name="unit"
					defaultValue={dashboard?.unit ?? ""}
					maxLength={40}
					required
					placeholder="USD, items, or another unit"
				/>
			</label>
			<label>
				Filter column
				<select
					value={category}
					onChange={(event) => {
						setCategory(Number(event.target.value));
						setFilter(null);
					}}
				>
					{columns.map((label, index) => (
						<option key={index} value={index}>
							{label || `Column ${index + 1}`}
						</option>
					))}
				</select>
			</label>
			<label>
				Include rows
				<select
					value={filter === null ? "" : String(categories.indexOf(filter))}
					onChange={(event) =>
						setFilter(
							event.target.value === ""
								? null
								: categories[Number(event.target.value)],
						)
					}
				>
					<option value="">All rows</option>
					{categories.map((value, index) => (
						<option key={value} value={index}>
							{value || "Empty cell"}
						</option>
					))}
				</select>
			</label>
			<button type="submit" disabled={busy}>
				Save dashboard
			</button>
		</form>
	);
}

function Chart({ report }: { report: Report }) {
	const max = Math.max(
		1,
		...report.groups.map((value) => Math.abs(Number(value.total))),
	);
	return (
		<figure className="report-chart">
			<figcaption>
				Totals by {report.headers[report.dashboard.category]}
			</figcaption>
			{report.groups.map((group) => {
				const value = Number(group.total);
				const width = (50 * Math.abs(value)) / max;
				return (
					<div key={group.label}>
						<span>{group.label || "Empty cell"}</span>
						<span className="report-bar" aria-hidden="true">
							<i
								style={{
									left: `${value < 0 ? 50 - width : 50}%`,
									width: `${width}%`,
								}}
							/>
						</span>
						<span>
							{group.total} {report.dashboard.unit}
						</span>
					</div>
				);
			})}
		</figure>
	);
}
