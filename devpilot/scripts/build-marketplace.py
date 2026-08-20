"""Builds the marketplace manifest from the skill sources in this repository.

The manifest inlines every file of every package, which is what lets the backend install a skill
without unpacking an archive — and therefore without an archive-extraction path traversal. Keeping
the scripts as ordinary readable files and generating the JSON from them means the thing a reviewer
reads is the thing that ships.

    python scripts/build-marketplace.py

Writes demo-data/marketplace.json. Commit and push it; the URL that serves it must be HTTPS,
because the backend refuses anything else: a manifest that can be rewritten in transit is a
code-execution channel.
"""

import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SKILLS_DIR = ROOT / "demo-data" / "skills"
OUTPUT = ROOT / "demo-data" / "marketplace.json"

# Only these are shipped inside a package; anything else in the directory is repository furniture.
SHIPPED_SUFFIXES = {".py", ".js", ".mjs", ".json", ".md", ".txt"}
METADATA_FILE = "skill.json"


def build_package(directory):
    metadata_path = directory / METADATA_FILE
    if not metadata_path.exists():
        raise SystemExit(f"{directory.name}: missing {METADATA_FILE}")

    package = json.loads(metadata_path.read_text(encoding="utf-8"))
    for required in ("key", "name", "version", "runtime", "entrypoint"):
        if not package.get(required):
            raise SystemExit(f"{directory.name}: {required} is required")
    if package["key"] != directory.name:
        raise SystemExit(f"{directory.name}: key must equal the directory name")

    files = {}
    for path in sorted(directory.rglob("*")):
        if not path.is_file() or path.name == METADATA_FILE:
            continue
        if path.suffix not in SHIPPED_SUFFIXES:
            continue
        relative = path.relative_to(directory).as_posix()
        files[relative] = path.read_text(encoding="utf-8")

    if package["entrypoint"] not in files:
        raise SystemExit(
            f"{directory.name}: entrypoint {package['entrypoint']} is not among the shipped files")

    package["files"] = files
    return package


def main():
    if not SKILLS_DIR.is_dir():
        raise SystemExit(f"no skills directory at {SKILLS_DIR}")

    packages = [build_package(directory)
                for directory in sorted(SKILLS_DIR.iterdir()) if directory.is_dir()]
    if not packages:
        raise SystemExit("no skill packages found")

    manifest = {
        "name": "DevPilot 演示 Skill 市场",
        "description": "随仓库发布的示例技能，通过 HTTPS 提供，用于演示安装、启用、审批与沙箱执行的完整链路。",
        "skills": packages,
    }
    OUTPUT.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(f"wrote {OUTPUT.relative_to(ROOT)}")
    for package in packages:
        total = sum(len(content) for content in package["files"].values())
        print(f"  {package['key']:<20} {package['runtime']:<8} "
              f"{len(package['files'])} file(s), {total} chars")
    return 0


if __name__ == "__main__":
    sys.exit(main())
