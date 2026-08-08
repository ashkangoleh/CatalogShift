import io
import re
import sys
import tokenize
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEXT_SUFFIXES = {
    ".json",
    ".md",
    ".py",
    ".sh",
    ".toml",
    ".yaml",
    ".yml",
}
COMMENT_LINE_SUFFIXES = {".sh", ".yaml", ".yml"}
PERSIAN = re.compile(r"[\u0600-\u06ff]")
failures = []

for path in sorted(ROOT.rglob("*")):
    if (
        not path.is_file()
        or ".git" in path.parts
        or ".state" in path.parts
        or ".ruff_cache" in path.parts
        or "third_party" in path.parts
    ):
        continue
    if path.suffix not in TEXT_SUFFIXES and path.name not in {
        "Dockerfile",
        "Makefile",
        "LICENSE",
        ".gitignore",
        ".dockerignore",
    }:
        continue
    text = path.read_text(encoding="utf-8")
    relative = path.relative_to(ROOT)
    if PERSIAN.search(text):
        failures.append(f"{relative}: contains Persian text")
    if path.name in {"compose.yaml", "Dockerfile"} and re.search(
        r"(?im)^\s*(?:image:|FROM)\s+\S*latest(?:-main)?", text
    ):
        failures.append(f"{relative}: contains an unpinned latest reference")
    if path.suffix == ".py":
        tokens = tokenize.generate_tokens(io.StringIO(text).readline)
        for token in tokens:
            if token.type == tokenize.COMMENT and not (
                token.start[0] == 1 and token.string.startswith("#!")
            ):
                failures.append(f"{relative}:{token.start[0]}: contains a code comment")
    if path.suffix in COMMENT_LINE_SUFFIXES or path.name in {"Dockerfile", "Makefile"}:
        for line_number, line in enumerate(text.splitlines(), 1):
            if re.match(r"\s*#", line) and not (line_number == 1 and line.startswith("#!")):
                failures.append(f"{relative}:{line_number}: contains a code comment")

for forbidden in ("cred.json", ".env", "failed_identifiers.txt", "failed_to_delete_at_source.txt"):
    if (ROOT / forbidden).exists():
        failures.append(f"{forbidden}: forbidden tracked or generated artifact")

if failures:
    print("\n".join(failures), file=sys.stderr)
    raise SystemExit(1)
print("Repository policy checks passed")
