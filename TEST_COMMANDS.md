# Testing & Verification Guide

## 1. Build

```bash
cd /Users/shivengarisa/Context4DocuGen
mvn -DskipTests clean compile
```

Expected:

- `BUILD SUCCESS`

## 2. Run with Commons Lang default/override

```bash
mvn -DskipTests exec:java -Dexec.args="commons-lang3-3.12.0-src 14916"
```

Expected:

- No `commons-dbutils-1.7` missing-file error
- Single-target call graph output completes

## 3. Run batch export for every CSV row

```bash
mvn -DskipTests exec:java -Dexec.args="commons-lang3-3.12.0-src 14916 --batch"
```

Expected:

- JSON files appear under `commons-lang3-3.12.0-src/method_context_json/`
- `json_file_path` is written into the CSV for each updated row
- Legacy embedded JSON fields are cleared

## 3b. Run selective export for specific IDs (generate only missing)

```bash
mvn -DskipTests exec:java -Dexec.args="commons-lang3-3.12.0-src --ids 1888,1889,50000"
```

Expected:

- Precheck summary prints: `requested_count`, `found_in_csv`, `missing_in_csv`, `missing_json_for_requested_rows`
- Selective detail lines show the root cause for each requested ID
- Existing JSON files are skipped (default behavior)
- Only missing JSON artifacts are generated
- Final summary prints: `generated`, `skipped_existing`, `skipped_method_not_found`, `errored`

## 4. Verify JSON folder generation

```bash
ls -1 commons-lang3-3.12.0-src/method_context_json | head -20
```

Expected:

- Many files named like `id_<id>__<class>__<method>.json`

## 5. Verify CSV stores only JSON path reference

```bash
python3 << 'PY'
import csv, sys
csv.field_size_limit(sys.maxsize)

with open('commons-lang3-3.12.0-src/inputs_with_id.csv') as f:
    r = csv.DictReader(f)
    rows = list(r)

print('rows:', len(rows))
print('has_json_file_path_col:', 'json_file_path' in r.fieldnames)

target = next((x for x in rows if x.get('id','').strip() == '14916'), None)
if target:
    print('id_14916_json_file_path:', target.get('json_file_path',''))
    legacy_cols = [
        'mut_code','mut_javadoc','mut_class_hierarchy','callers_info','callees_info',
        'mut_method_name','mut_signature','mut_line_number','mut_parameters'
    ]
    for c in legacy_cols:
        if c in r.fieldnames:
            print(c, 'len=', len((target.get(c) or '').strip()))
PY
```

Expected:

- `json_file_path` exists and is populated
- Legacy embedded columns are empty (or absent)

## 6. Verify Javadoc is source-driven only

```bash
python3 << 'PY'
import csv, json, os

with open('commons-lang3-3.12.0-src/inputs_with_id.csv') as f:
    rows = list(csv.DictReader(f))

target = next((x for x in rows if x.get('id','').strip() == '14916'), None)
if not target:
    raise SystemExit('id 14916 not found')

path = target.get('json_file_path','').strip()
if not path:
    raise SystemExit('json_file_path is empty')

full = os.path.join('commons-lang3-3.12.0-src', path) if not path.startswith('commons-lang3-3.12.0-src') else path
with open(full) as jf:
    data = json.load(jf)

print('mut_javadoc_len:', len((data.get('MUT',{}).get('javadoc') or '').strip()))
print('sample_caller_javadoc_lens:', [len((c.get('javadoc') or '').strip()) for c in data.get('callers',[])[:5]])
PY
```

Expected:

- Javadocs are present only where source contains them
- Missing source Javadocs remain empty strings

## 7. Verify non-collision for multiple IDs

```bash
mvn -DskipTests exec:java -Dexec.args="commons-lang3-3.12.0-src 14916"
mvn -DskipTests exec:java -Dexec.args="commons-lang3-3.12.0-src 14917"

python3 << 'PY'
import csv
with open('commons-lang3-3.12.0-src/inputs_with_id.csv') as f:
    rows = list(csv.DictReader(f))

r1 = next((x for x in rows if x.get('id','').strip() == '14916'), None)
r2 = next((x for x in rows if x.get('id','').strip() == '14917'), None)

p1 = (r1 or {}).get('json_file_path','')
p2 = (r2 or {}).get('json_file_path','')
print('14916:', p1)
print('14917:', p2)
print('different_paths:', p1 != p2)
PY
```

Expected:

- Both rows have JSON paths
- Paths differ

## 8. Validate malformed or duplicate --ids input

```bash
mvn -DskipTests exec:java -Dexec.args="commons-lang3-3.12.0-src --ids 1888,,1889"
mvn -DskipTests exec:java -Dexec.args="commons-lang3-3.12.0-src --ids 1888,1888"
```

Expected:

- First command fails with malformed ID token message
- Second command fails with duplicate ID message

## 9. Verify all-existing no-op behavior

```bash
mvn -DskipTests exec:java -Dexec.args="commons-lang3-3.12.0-src --ids 1888"
```

Expected:

- If `id_1888__...json` already exists, output includes:
- `No-op: all requested IDs already have JSON artifacts.`
- `generated=0`

## 10. Quick end-to-end check

```bash
cd /Users/shivengarisa/Context4DocuGen && \
mvn -DskipTests clean compile && \
mvn -DskipTests exec:java -Dexec.args="commons-lang3-3.12.0-src 14916" && \
python3 - << 'PY'
import csv
rows = list(csv.DictReader(open('commons-lang3-3.12.0-src/inputs_with_id.csv')))
t = next((x for x in rows if x.get('id','').strip() == '14916'), None)
print('json_file_path:', (t or {}).get('json_file_path',''))
PY
```

## 11. Verify constructor-target resolution (ID 500)

```bash
mvn -DskipTests exec:java -Dexec.args="commons-lang3-3.12.0-src 500"
```

Expected:

- The run resolves constructor target `CharSequenceUtils()` instead of reporting method not found
- Output includes `JSON output saved to:` for ID 500
