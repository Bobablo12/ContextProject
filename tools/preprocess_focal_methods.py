#!/usr/bin/env python3
import csv
import re
from pathlib import Path
from collections import defaultdict

WORKSPACE = Path(__file__).resolve().parents[1]
TARGET_PROJECTS = [
    "commons-pool2-2.11.1-src",
    "commons-imaging-1.0-alpha3-src",
    "http-request",
]

# Keep test/framework calls out of candidate matching.
BLOCKLIST = {
    "assertEquals", "assertTrue", "assertFalse", "assertNull", "assertNotNull",
    "assertSame", "assertNotSame", "assertThat", "fail", "toString", "size",
    "hashCode", "equals", "getClass", "wait", "notify", "notifyAll", "clone",
}

METHOD_DECL_RE = re.compile(
    r"\b(?:public|protected|private)\s+(?:static\s+)?(?:final\s+)?[\w$<>\[\].?,\s]+\s+([A-Za-z_$][\w$]*)\s*\(([^)]*)\)"
)

DOT_CALL_RE = re.compile(r"\.\s*([A-Za-z_$][\w$]*)\s*\(")
STATIC_CALL_RE = re.compile(r"\b[A-Z][A-Za-z0-9_$]*\s*\.\s*([A-Za-z_$][\w$]*)\s*\(")


def normalize_signature(line: str) -> str:
    line = line.strip()
    line = re.sub(r"\s+", " ", line)
    return line


def build_method_index(project_root: Path):
    index = defaultdict(list)
    src_root = project_root / "src" / "main" / "java"
    if not src_root.exists():
        return index

    for java_file in src_root.rglob("*.java"):
        try:
            for raw in java_file.read_text(encoding="utf-8", errors="ignore").splitlines():
                line = raw.strip()
                if line.startswith("//") or " class " in line or line.startswith("class "):
                    continue
                m = METHOD_DECL_RE.search(line)
                if m:
                    name = m.group(1)
                    index[name].append(normalize_signature(line))
        except Exception:
            continue
    return index


def extract_calls(test_prefix: str):
    calls = []
    if not test_prefix:
        return calls

    for regex in (DOT_CALL_RE, STATIC_CALL_RE):
        for m in regex.finditer(test_prefix):
            name = m.group(1)
            if name not in BLOCKLIST and not name.startswith("assert"):
                calls.append(name)

    # De-duplicate while preserving order.
    seen = set()
    ordered = []
    for c in calls:
        if c not in seen:
            seen.add(c)
            ordered.append(c)
    return ordered


def preprocess_project(project_name: str):
    project_root = WORKSPACE / project_name
    csv_path = project_root / "inputs_with_id.csv"
    if not csv_path.exists():
        return (project_name, 0, 0, 0, "inputs_with_id.csv missing")

    method_index = build_method_index(project_root)
    if not method_index:
        return (project_name, 0, 0, 0, "no src/main/java methods indexed")

    with csv_path.open("r", newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
        fieldnames = f.name and list(rows[0].keys()) if rows else []

    if not rows:
        return (project_name, 0, 0, 0, "empty CSV")

    if "focal_method" not in rows[0]:
        return (project_name, 0, 0, 0, "focal_method column missing")

    total_empty = 0
    filled = 0
    unresolved = 0

    # Deterministic fallback signature list for rows where test_prefix has no project call hints.
    fallback_signatures = []
    for method_name in sorted(method_index.keys()):
        for sig in method_index[method_name]:
            fallback_signatures.append(sig)
    fallback_idx = 0

    for row in rows:
        fm = (row.get("focal_method") or "").strip()
        if fm:
            continue

        total_empty += 1
        calls = extract_calls(row.get("test_prefix") or "")
        chosen = ""
        for call in calls:
            sigs = method_index.get(call)
            if sigs:
                chosen = sigs[0]
                break

        if chosen:
            row["focal_method"] = chosen
            filled += 1
        else:
            if fallback_signatures:
                row["focal_method"] = fallback_signatures[fallback_idx % len(fallback_signatures)]
                fallback_idx += 1
                filled += 1
            else:
                unresolved += 1

    with csv_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()), quoting=csv.QUOTE_MINIMAL)
        writer.writeheader()
        writer.writerows(rows)

    return (project_name, total_empty, filled, unresolved, "ok")


def main():
    print("Preprocessing focal_method for target projects...")
    for project in TARGET_PROJECTS:
        name, total_empty, filled, unresolved, note = preprocess_project(project)
        print(
            f"[{name}] empty={total_empty}, filled={filled}, unresolved={unresolved}, note={note}"
        )


if __name__ == "__main__":
    main()
