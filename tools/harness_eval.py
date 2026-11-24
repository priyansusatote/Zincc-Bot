"""P7 offline experiment desk. All inputs and operations are immutable synthetic fixtures."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
STRATEGIES = ("direct", "lookup", "program")
ROUTES = ("nvidia/nemotron-3.5-lightning:free", "google/gemma-4-26b-a4b-it:free", "nvidia/nemotron-3.5-super-vl:free")
CASES = (
    ("inbox", "Give the first inbox subject.", "first", "Orange release"),
    ("calendar", "Count the calendar events across all pages.", "count", "5"),
    ("report", "Sum the report amounts across all pages.", "sum", "42"),
)
DATA = {"inbox": ["Orange release", "Notes"], "calendar": ["Demo", "Design", "Retro", "Review", "Planning"],
        "report": [3, 9, 7, 12, 11]}
DESCRIPTIONS = {"inbox": "Read inbox subjects", "calendar": "Read calendar events", "report": "Read report amounts"}


def encoded(value):
    return json.dumps(value, separators=(",", ":"), ensure_ascii=True)


def catalog(size):
    values = dict(DESCRIPTIONS)
    for i in range(size - len(values)):
        values[f"sample{i}"] = f"Read synthetic archive {i}"
    return [{"name": key, "description": value, "input": {"cursor": "integer"}, "returns": "page of rows"}
            for key, value in sorted(values.items())]


class Broker:
    def __init__(self):
        self.grants = {tool["name"] for tool in catalog(32)}
        self.receipts = []

    def discover(self, size):
        return [tool for tool in catalog(size) if tool["name"] in self.grants]

    def read(self, name, offset):
        if name not in self.grants or len(self.receipts) >= 3 or offset not in (0, 2, 4):
            raise ValueError("Operation outside budget or grant")
        self.receipts.append((name, offset))
        return DATA.get(name, [])[offset:offset + 2]


def dispatch(plan, broker, program):
    if set(plan) != {"tool", "op"} or plan["op"] not in ("first", "sum", "count"):
        raise ValueError("Invalid typed plan")
    rows = []
    for offset in (0, 2, 4):
        page = broker.read(plan["tool"], offset)
        rows.extend(page)
        if plan["op"] == "first" or len(page) < 2:
            break
    if not program:
        return rows
    return reduce_rows(plan["op"], rows)


def reduce_rows(op, rows):
    if op == "first":
        return str(rows[0])
    if op == "sum":
        return str(sum(rows))
    return str(len(rows))


def key_from_env():
    if os.getenv("OPENROUTER_API_KEY"):
        return os.environ["OPENROUTER_API_KEY"]
    for line in (ROOT / ".env").read_text(encoding="utf-8-sig").splitlines():
        if line.startswith("OPENROUTER_API_KEY="):
            return line.partition("=")[2].strip().strip("\"'")
    raise ValueError("OpenRouter key unavailable")


class Trial:
    def __init__(self, route, key):
        self.route, self.key = route, key
        self.calls = self.input_tokens = self.output_tokens = self.chars = 0
        self.usage_known = True

    def ask(self, message, replay):
        self.calls += 1
        self.chars += len(message)
        if self.route == "fixture":
            return replay
        if self.route not in ROUTES:
            raise ValueError("Only fixed free routes allowed")
        body = {"model": self.route, "messages": [{"role":"user","content":message}], "max_tokens":350,
                "reasoning":{"enabled":False},"provider":{"allow_fallbacks":True}}
        req = urllib.request.Request("https://openrouter.ai/api/v1/chat/completions", encoded(body).encode(),
                                     {"Authorization":"Bearer "+self.key,"Content-Type":"application/json"})
        with urllib.request.urlopen(req, timeout=22) as response:
            result = json.load(response)
        if result.get("model","").removesuffix(":free") != self.route.removesuffix(":free"):
            raise ValueError("Unexpected model")
        usage = result.get("usage") or {}
        for field, target in (("prompt_tokens","input_tokens"),("completion_tokens","output_tokens")):
            if isinstance(usage.get(field),int):
                setattr(self,target,getattr(self,target)+usage[field])
            else:
                self.usage_known=False
        return json.loads(result["choices"][0]["message"]["content"])


def evaluate_one(route, key, strategy, case, size):
    name, question, op, expected = case
    trial = Trial(route,key)
    broker = Broker()
    start = time.monotonic()
    error = None
    success = False
    result_chars = discovery_chars = 0
    try:
        tools = broker.discover(size)
        if strategy != "direct":
            term = trial.ask(question+' Return JSON only {"search":"one keyword describing the data source"}.',
                             {"search":name})["search"]
            if not isinstance(term, str):
                raise ValueError("Invalid search")
            term = term.strip().lower()
            if not term or len(term)>40:
                raise ValueError("Invalid search")
            tools = [t for t in tools if term in (t["name"]+" "+t["description"]).lower()][:4]
        discovery_chars=len(encoded(tools))
        plan=trial.ask(question+' Choose a listed tool. Return only JSON {"tool":"name","op":"first|count|sum"}. Tools '+encoded(tools),
                       {"tool":name,"op":op})
        if not isinstance(plan, dict) or plan.get("tool") not in [tool["name"] for tool in tools]:
            raise ValueError("Undiscovered tool")
        result=dispatch(plan,broker,strategy=="program")
        result_chars=len(encoded(result))
        if strategy!="program":
            result=trial.ask(question+' Respond in JSON {"answer":"value"} using '+encoded(result),
                             {"answer":reduce_rows(op,result)})["answer"]
        success=str(result)==expected and plan=={"tool":name,"op":op}
    except urllib.error.HTTPError as exc:
        error="http_"+str(exc.code)
    except (OSError,KeyError,IndexError,TypeError,ValueError):
        error="transport_or_invalid_result"
    return dict(route=route,strategy=strategy,case=name,catalog=size,correct=success,error=error,
                calls=trial.calls,tool_calls=len(broker.receipts),prompt_chars=trial.chars,discovery_chars=discovery_chars,
                result_chars=result_chars,input_tokens=trial.input_tokens if route!="fixture" and trial.usage_known else None,
                output_tokens=trial.output_tokens if route!="fixture" and trial.usage_known else None,
                elapsed_ms=round((time.monotonic()-start)*1000,2),retries=0)


def revision():
    return hashlib.sha256(Path(__file__).read_bytes().replace(b"\r\n", b"\n")).hexdigest()


def connection(path):
    path.parent.mkdir(parents=True,exist_ok=True)
    db=sqlite3.connect(path, timeout=10)
    db.executescript("""
        CREATE TABLE IF NOT EXISTS experiment(id INTEGER PRIMARY KEY, revision TEXT, results TEXT);
        CREATE TABLE IF NOT EXISTS selection(id INTEGER PRIMARY KEY, experiment INTEGER, route TEXT, strategy TEXT);
    """)
    return db


def eligible(rows,route,candidate):
    def cohort(strategy):
        return {(r["case"],r["catalog"]):r for r in rows if r["route"]==route and r["strategy"]==strategy}
    baseline, proposed=cohort("direct"),cohort(candidate)
    required={(c[0],n) for c in CASES for n in (3,32)}
    if set(baseline)!=required or set(proposed)!=required or candidate=="direct":
        return False
    if not all(r["correct"] and not r["error"] for r in [*baseline.values(),*proposed.values()]):
        return False
    def cost(row):
        if route=="fixture":
            return row["prompt_chars"]
        if row["input_tokens"] is None or row["output_tokens"] is None:
            raise ValueError("Missing usage")
        return row["input_tokens"]+row["output_tokens"]
    if route!="fixture" and any(r["input_tokens"] is None or r["output_tokens"] is None for r in [*baseline.values(),*proposed.values()]):
        return False
    return sum(map(cost,proposed.values()))<sum(map(cost,baseline.values()))


def select(db,route,strategy,experiment):
    with db:
        db.execute("BEGIN IMMEDIATE")
        row=db.execute("SELECT revision,results FROM experiment WHERE id=?",(experiment,)).fetchone()
        if not row or row[0]!=revision() or not eligible(json.loads(row[1]),route,strategy):
            raise ValueError("Candidate did not pass the fixed holdout gate")
        db.execute("INSERT INTO selection(experiment,route,strategy) VALUES(?,?,?)",(experiment,route,strategy))


def active(db,route):
    row=db.execute("""SELECT s.strategy FROM selection s JOIN experiment e ON e.id=s.experiment
        WHERE s.route=? AND e.revision=? ORDER BY s.id DESC LIMIT 1""",(route,revision())).fetchone()
    return row[0] if row else "direct"


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action",choices=["evaluate","promote","rollback","try"])
    parser.add_argument("--db",type=Path,default=ROOT/"target/p7-harness.sqlite")
    parser.add_argument("--report",type=Path)
    parser.add_argument("--route",choices=("fixture",*ROUTES),default="fixture")
    parser.add_argument("--strategy",choices=("lookup","program"),default="program")
    parser.add_argument("--experiment",type=int)
    parser.add_argument("--case",choices=list(DATA),default="calendar")
    args=parser.parse_args()
    db=connection(args.db)
    if args.action=="promote":
        select(db,args.route,args.strategy,args.experiment)
    elif args.action=="rollback":
        with db:
            db.execute("BEGIN IMMEDIATE")
            db.execute("""DELETE FROM selection WHERE id=(SELECT s.id FROM selection s JOIN experiment e
                ON e.id=s.experiment WHERE s.route=? AND e.revision=? ORDER BY s.id DESC LIMIT 1)""",(args.route,revision()))
    else:
        key=key_from_env() if args.route!="fixture" else None
        if args.action=="try":
            case=next(c for c in CASES if c[0]==args.case)
            print(encoded(evaluate_one(args.route,key,active(db,args.route),case,32)))
            return
        rows=[]
        jobs=[(args.route,key,strategy,case,size) for case in CASES for size in (3,32) for strategy in STRATEGIES]
        # Stop on explicit provider access or rate errors; keep incomplete evidence for review.
        for job in jobs:
            result=evaluate_one(*job)
            rows.append(result)
            if result["error"] in ("http_401","http_402","http_403","http_429"):
                break
        with db:
            run=db.execute("INSERT INTO experiment(revision,results) VALUES(?,?)",(revision(),encoded(rows))).lastrowid
        report=dict(experiment=run,revision=revision(),route=args.route,results=rows,
                    candidates={s:eligible(rows,args.route,s) for s in ("lookup","program")})
        if args.report:
            args.report.write_text(json.dumps(report,indent=2)+"\n",encoding="utf8")
        print(encoded({"experiment":run,"cases":len(rows),"correct":sum(r["correct"] for r in rows),"candidates":report["candidates"]}))
    print("Offline active strategy:",active(db,args.route))


if __name__=="__main__":
    main()
