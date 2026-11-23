"""Fixed document operations invoked via one stdin/stdout exchange."""
import io
import json
import re
import subprocess
import sys
from decimal import Decimal

MAX_BYTES = 8 * 1024 * 1024
MAX_PAGES = 60
MAX_CHUNKS = 360
MAX_TABLES = 24


def normalize(s):
    return " ".join((s or "").split()).replace("\x00", "")


def scan(page):
    try:
        resolution = min(180, int(72 * 1800 / max(page.width, page.height)))
        if resolution < 55:
            return ""
        bitmap = page.to_image(resolution=resolution).original
        with io.BytesIO() as png:
            bitmap.save(png, format="PNG")
            result = subprocess.run(["tesseract", "stdin", "stdout", "-l", "eng", "--psm", "3"],
                                    input=png.getvalue(), capture_output=True, timeout=6, check=True)
        return normalize(result.stdout.decode("utf-8"))
    except (ValueError, OSError, subprocess.SubprocessError):
        return ""


def pdf(raw):
    import pdfplumber
    from pdfminer.pdfdocument import PDFEncryptionError, PDFPasswordIncorrect
    if not raw.startswith(b"%PDF-"):
        return {"error": "invalid_pdf"}
    try:
        with pdfplumber.open(io.BytesIO(raw)) as doc:
            if doc.doc.encryption:
                return {"error": "encrypted_pdf"}
            if len(doc.pages) > MAX_PAGES:
                return {"error": "too_many_pages"}
            chunks, tables, missing, scanned = [], [], [], []
            scan_budget = 6
            for page in doc.pages:
                text = normalize(page.extract_text())
                if not text and scan_budget:
                    scan_budget -= 1
                    text = scan(page)
                    if text:
                        scanned.append(page.page_number)
                if not text:
                    missing.append(page.page_number)
                while text and len(chunks) < MAX_CHUNKS:
                    end = min(len(text), 780)
                    if end < len(text) and text.rfind(" ", 0, end) > 500:
                        end = text.rfind(" ", 0, end)
                    chunks.append({"page": page.page_number, "text": text[:end]})
                    text = text[end:].lstrip()
                for table in page.find_tables():
                    if len(tables) == MAX_TABLES:
                        break
                    rows = table.extract()
                    if len(rows) >= 2 and 2 <= len(rows[0]) <= 12 and len(rows) <= 101:
                        tables.append({
                            "page": page.page_number, "box": [round(x, 2) for x in table.bbox],
                            "rows": [[normalize(cell)[:250] for cell in row] for row in rows],
                            "cells": [[None if cell is None else [round(x, 2) for x in cell]
                                       for cell in row.cells] for row in table.rows],
                        })
                page.close()
            if not chunks and not tables:
                return {"error": "no_text_layer"}
            return {"pages": len(doc.pages), "missing": missing, "scanned": scanned, "chunks": chunks,
                    "tables": tables, "bounded": len(chunks) == MAX_CHUNKS or len(tables) == MAX_TABLES}
    except (PDFEncryptionError, PDFPasswordIncorrect):
        return {"error": "encrypted_pdf"}
    except Exception:
        return {"error": "invalid_pdf"}


def embed(texts):
    import numpy as np
    import onnxruntime as ort
    from tokenizers import Tokenizer
    tokenizer = Tokenizer.from_file("/model/tokenizer.json")
    tokenizer.enable_padding()
    tokenizer.enable_truncation(max_length=256)
    options = ort.SessionOptions()
    options.intra_op_num_threads = 1
    options.inter_op_num_threads = 1
    session = ort.InferenceSession("/model/model.onnx", sess_options=options,
                                   providers=["CPUExecutionProvider"])
    result = []
    for start in range(0, len(texts), 24):
        enc = tokenizer.encode_batch(texts[start:start + 24])
        ids = np.array([e.ids for e in enc], dtype=np.int64)
        mask = np.array([e.attention_mask for e in enc], dtype=np.int64)
        by_name = {"input_ids": ids, "attention_mask": mask, "token_type_ids": np.zeros_like(ids)}
        tensor = session.run(None, {v.name: by_name[v.name] for v in session.get_inputs()})[0]
        pooled = (tensor * mask[:, :, None]).sum(axis=1) / mask.sum(axis=1)[:, None]
        norm = np.linalg.norm(pooled, axis=1, keepdims=True)
        result.extend((pooled / np.maximum(norm, 1e-12)).round(7).tolist())
    return result


def analyze(payload):
    rows, column, category, selected = (payload[k] for k in ("rows", "column", "category", "selected"))
    filtered, total, missing = [], Decimal(0), 0
    groups, units = {}, set()
    unit_columns = [i for i, name in enumerate(rows[0])
                    if name.lower().strip() in ("currency", "unit", "units")]
    for index, row in enumerate(rows[1:], start=1):
        if selected is not None and row[category] != selected:
            continue
        cell = row[column].strip()
        if cell in ("", "–", "—", "N/A"):
            missing += 1
        elif re.fullmatch(r"[+-]?(?:[0-9]{1,15}|[0-9]{1,3}(?:,[0-9]{3}){1,4})(?:\.[0-9]{1,8})?", cell):
            value = Decimal(cell.replace(",", ""))
            total += value
            label = row[category]
            groups[label] = groups.get(label, Decimal(0)) + value
            units.update(row[i].upper().strip() for i in unit_columns if row[i].strip())
        else:
            return {"error": "ambiguous_number", "row": index + 1}
        filtered.append(index)
    if len(units) > 1 or (units and payload["unit"].upper().strip() not in units):
        return {"error": "mixed_or_mismatched_units"}
    return {"total": format(total, "f"), "missing": missing, "rows": filtered,
            "groups": [{"label": label, "total": format(value, "f")} for label, value in groups.items()]}


def main():
    operation = sys.argv[1]
    raw = sys.stdin.buffer.read(MAX_BYTES + 1)
    if len(raw) > MAX_BYTES:
        return {"error": "too_large"}
    if operation == "parse":
        result = pdf(raw)
        if "chunks" in result:
            vectors = embed([c["text"] for c in result["chunks"]])
            for chunk, vector in zip(result["chunks"], vectors, strict=True):
                chunk["vector"] = vector
        return result
    payload = json.loads(raw)
    if operation == "embed":
        return {"vector": embed([payload["query"]])[0]}
    if operation == "aggregate":
        return analyze(payload)
    raise ValueError("Unsupported operation")


if __name__ == "__main__":
    json.dump(main(), sys.stdout, separators=(",", ":"), allow_nan=False)
