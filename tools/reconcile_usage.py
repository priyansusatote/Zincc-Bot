"""Reconcile one UTC billing window's Zincc conversation receipts with provider totals."""
import argparse
import csv
import json
from collections import defaultdict
from decimal import Decimal
from pathlib import Path


def read_csv(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def number(value):
    parsed = Decimal(value)
    if not parsed.is_finite() or parsed < 0:
        raise ValueError("Expected a nonnegative finite amount")
    return parsed


def tokens(value):
    value = int(value)
    if value < 0:
        raise ValueError("Negative token count")
    return value


def key(row):
    if not row["provider"] or not row["model"]:
        raise ValueError("Provider and model are required")
    return row["provider"], row["model"]


def reconcile(receipts, statements, labels):
    """Amounts are USD. Unknown usage stays visible even when totals happen to match."""
    grouped = defaultdict(list)
    attempts = {}
    for row in receipts:
        identifier = row["attempt"]
        if not identifier or identifier in attempts:
            raise ValueError("Missing or duplicate attempt ID")
        attempts[identifier] = row
        grouped[key(row)].append(row)

    billing = {}
    for row in statements:
        model = key(row)
        if model in billing:
            raise ValueError("Use one statement line per provider/model and window")
        billing[model] = row

    judged = {}
    scores = []
    for label in labels:
        identifier = label["attempt"]
        if identifier in judged or identifier not in attempts or label["success"] not in ("0", "1"):
            raise ValueError("Invalid evaluation label")
        outcome = int(label["success"])
        if outcome and attempts[identifier]["state"] != "completed":
            raise ValueError("An unfinished turn cannot be a verified success")
        judged[identifier] = outcome
        if label.get("confidence"):
            confidence = number(label["confidence"])
            if confidence > 1:
                raise ValueError("Confidence must be between zero and one")
            scores.append((confidence - outcome) ** 2)

    result = []
    total_billed = Decimal(0)
    all_billed = True
    for provider, model in sorted(set(grouped) | set(billing)):
        rows = grouped[provider, model]
        bill = billing.get((provider, model))
        detail = {"provider": provider, "model": model, "attempts": len(rows)}
        complete = True
        match = True
        for column in ("input_tokens", "output_tokens"):
            counts = [tokens(row[column]) for row in rows if row[column] != ""]
            known = sum(counts)
            missing = len(rows) - len(counts)
            complete &= missing == 0
            detail[column] = {"known": known, "missing_receipts": missing}
            if bill:
                delta = tokens(bill[column]) - known
                detail[column]["statement_minus_known"] = delta
                match &= delta == 0
        if bill:
            amount = number(bill["billed_usd"])
            total_billed += amount
            detail["billed_usd"] = str(amount)
        else:
            all_billed = False
        detail["status"] = ("missing_statement" if bill is None else "missing_receipts" if not rows or not complete
                            else "matched" if match else "mismatch")
        result.append(detail)

    successes = sum(judged.values())
    same_population = all(item["status"] == "matched" for item in result)
    cost_per_success = None
    if all_billed and same_population and successes and len(judged) == len(attempts):
        cost_per_success = str(total_billed / successes)
    return {"models": result, "attempts": len(attempts), "judged": len(judged),
            "verified_successes": successes, "billed_usd": str(total_billed) if all_billed and result else None,
            "model_usd_per_verified_success": cost_per_success, "confidence_labels": len(scores),
            "brier_score": str(sum(scores) / len(scores)) if scores else None}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("receipts", type=Path)
    parser.add_argument("statement", type=Path)
    parser.add_argument("--labels", type=Path)
    args = parser.parse_args()
    report = reconcile(read_csv(args.receipts), read_csv(args.statement),
                       read_csv(args.labels) if args.labels else [])
    print(json.dumps(report, indent=2))
