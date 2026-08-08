import hashlib
import json


def canonical_json(value):
    return json.dumps(value, ensure_ascii=True, separators=(",", ":"), sort_keys=True, default=str)


def fingerprint(value):
    return hashlib.sha256(canonical_json(value).encode()).hexdigest()


def compare_states(expected, actual):
    failures = []
    expected_tables = expected.get("tables", {})
    actual_tables = actual.get("tables", {})
    if set(expected_tables) != set(actual_tables):
        failures.append(
            {
                "field": "tables",
                "expected": sorted(expected_tables),
                "actual": sorted(actual_tables),
            }
        )
    for table_name in sorted(set(expected_tables) & set(actual_tables)):
        expected_table = expected_tables[table_name]
        actual_table = actual_tables[table_name]
        for field in sorted(set(expected_table) | set(actual_table)):
            if expected_table.get(field) != actual_table.get(field):
                failures.append(
                    {
                        "table": table_name,
                        "field": field,
                        "expected": expected_table.get(field),
                        "actual": actual_table.get(field),
                    }
                )
    return failures
