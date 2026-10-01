#!/usr/bin/env python3
"""Check, set, and locally tag Sidecar versions without changing dependencies."""
import argparse
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

SKILL = Path("agent-skills/loomspan-sidecar-authoring/SKILL.md")
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


class VersionError(Exception):
    pass


def validate(version):
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?", version):
        raise VersionError(f"Invalid version: {version!r}")


def git(root, *args):
    result = subprocess.run(["git", "-C", str(root), *args], capture_output=True, text=True)
    if result.returncode:
        raise VersionError(result.stderr.strip() or "Git command failed")
    return result.stdout.strip()


def clean(root):
    if git(root, "status", "--porcelain", "--untracked-files=normal"):
        raise VersionError("Commit or stash existing changes first; a clean worktree is required")


def check(root, expected=None):
    project = ET.parse(root / "pom.xml").getroot()
    version = project.findtext("m:version", namespaces=NS) or ""
    framework = project.findtext("m:properties/m:loomspan.version", namespaces=NS) or ""
    validate(version)
    validate(framework)
    if expected is not None and version != expected:
        raise VersionError(f"POM version {version} does not match {expected}")
    text = (root / SKILL).read_text(encoding="utf-8")
    for key, value in (("loomspan-version", version), ("loomspan-framework-version", framework)):
        values = re.findall(r'^  ' + key + r': "([^"\r\n]+)"\s*$', text, re.M)
        if values != [value]:
            raise VersionError(f"Skill {key} must equal {value}")
    return version, framework


def set_version(root, version):
    validate(version)
    clean(root)
    old, _ = check(root)
    if old == version:
        raise VersionError(f"Project already uses {version}")
    replacements = {}
    for relative, pattern in (
        (Path("pom.xml"), rb"(<version>)" + re.escape(old.encode()) + rb"(</version>)"),
        (SKILL, rb'(  loomspan-version: ")' + re.escape(old.encode()) + rb'(")'),
    ):
        path = root / relative
        if path.is_symlink():
            raise VersionError(f"Version file must not be a symlink: {relative}")
        raw = path.read_bytes()
        updated, count = re.subn(pattern, lambda m: m[1] + version.encode() + m[2], raw)
        if count != 1:
            raise VersionError(f"Expected exactly one version field in {relative}")
        replacements[path] = updated
    for path, content in replacements.items():
        path.write_bytes(content)
    check(root, version)


def tag(root, version):
    validate(version)
    if "SNAPSHOT" in version.upper():
        raise VersionError("Cannot tag a SNAPSHOT version")
    clean(root)
    _, framework = check(root, version)
    if "SNAPSHOT" in framework.upper():
        raise VersionError("Cannot release with a SNAPSHOT framework dependency")
    name = "v" + version
    if git(root, "tag", "--list", name):
        raise VersionError(f"Tag {name} already exists")
    git(root, "tag", "-a", name, "-m", f"Loomspan Sidecar {version}")
    return name


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("check")
    for command in ("set", "tag"):
        commands.add_parser(command).add_argument("version")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    try:
        if args.command == "check":
            version, framework = check(root)
            print(f"Sidecar {version}; framework {framework}; skill metadata matches")
        elif args.command == "set":
            set_version(root, args.version)
            print(f"Set Sidecar to {args.version}. Review, test, and commit before tagging.")
        else:
            print(f"Created local annotated tag {tag(root, args.version)}. Nothing was pushed.")
        return 0
    except (VersionError, OSError, ET.ParseError) as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
