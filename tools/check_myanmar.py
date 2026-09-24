"""Read-only native UI dictionary audit. Does not claim linguistic/human acceptance."""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
assets = ROOT / "app/src/main/assets"

def unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate translation: {key}")
        result[key] = value
    return result

words = {}
for name in ("myanmar.json", "native-myanmar.json"):
    entries = json.loads((assets / name).read_text(encoding="utf-8"), object_pairs_hook=unique)
    for key, value in entries.items():
        if not value.strip():
            raise ValueError(f"Blank translation: {key}")
        if name == "native-myanmar.json" and set(re.findall(r"\{\w+\}", key)) != set(re.findall(r"\{\w+\}", value)):
            raise ValueError(f"Placeholder mismatch: {key}")
    words.update(entries)

calls = re.compile(r'\b(?:tr|button|label|heading|field|choice|copyText|eyebrow|panel|options|header|Metric|setTitle|setMessage|setPositiveButton|setNegativeButton|setNeutralButton|error|message)\("((?:[^"\\]|\\.)*)"')
labels = re.compile(r'\b(?:Card|section|group|panel|header)\("(?:[^"\\]|\\.)*",\s*"((?:[^"\\]|\\.)*)"|\b(?:eyebrow|detail)\s*=\s*"((?:[^"\\]|\\.)*)"')
missing = set()
technical = {"Super King", "superkingmyanmar.com", "name", "customer_type", "phone", "township", "address", "notes",
             "Accept", "Origin", "Referer", "X-Requested-With", "X-XSRF-TOKEN", "XMLHttpRequest", "application/json"}
def covered(value):
    if value in technical or value in words or value.strip().rstrip(":") in words:
        return True
    for separator in ("\n", " · "):
        if separator in value:
            return all(covered(part) or not part.strip() for part in value.split(separator))
    return False
for path in (ROOT / "app/src/main/java").rglob("*.kt"):
    source = path.read_text(encoding="utf-8")
    for raw in calls.findall(source) + [a or b for a, b in labels.findall(source)]:
        if "$" in raw or not re.search("[A-Za-z]{3}", raw):
            continue
        value = json.loads('"' + raw + '"')
        if not covered(value):
            missing.add(value)
print(f"Dictionary entries: {len(words)}; unmapped static UI/error calls: {len(missing)}")
for value in sorted(missing):
    print(value)
if "--strict" in sys.argv and missing:
    sys.exit(1)
