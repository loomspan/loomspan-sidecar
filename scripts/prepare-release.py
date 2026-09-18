#!/usr/bin/env python3
"""Validate release inputs and reproducibly archive the executable JAR without publishing."""

import argparse
import hashlib
import pathlib
import re
import shutil
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
RELEASED_FRAMEWORK = "1.0.0-beta.5"


def release_version(value, label):
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?", value) or "SNAPSHOT" in value.upper():
        raise SystemExit(f"{label} must be a non-SNAPSHOT Maven version: {value}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-version", required=True)
    parser.add_argument("--loomspan-version", required=True)
    parser.add_argument("--tag")
    parser.add_argument("--validate-only", action="store_true")
    parser.add_argument("--jar")
    parser.add_argument("--output-dir", default="target/release")
    args = parser.parse_args()
    release_version(args.project_version, "project version")
    release_version(args.loomspan_version, "Loomspan version")
    if args.loomspan_version != RELEASED_FRAMEWORK:
        raise SystemExit(f"Loomspan version must be exactly {RELEASED_FRAMEWORK}")
    if args.tag and args.tag != "v" + args.project_version:
        raise SystemExit(f"tag {args.tag} does not match v{args.project_version}")

    pom = (ROOT / "pom.xml").read_text()
    if "<loomspan.version>1.0.0-beta.5-SNAPSHOT</loomspan.version>" not in pom and \
            "<loomspan.version>1.0.0-beta.5</loomspan.version>" not in pom:
        raise SystemExit("pom.xml does not use the beta 5 framework line")
    if args.validate_only:
        print(f"Validated nonpublishing release plan for {args.project_version} with Loomspan {args.loomspan_version}")
        return

    jar = pathlib.Path(args.jar) if args.jar else ROOT / "target" / f"loomspan-sidecar-{args.project_version}.jar"
    if not jar.is_file(): raise SystemExit(f"executable JAR not found: {jar}")
    output = ROOT / args.output_dir
    output.mkdir(parents=True, exist_ok=True)
    copied = output / f"loomspan-sidecar-{args.project_version}.jar"
    shutil.copyfile(jar, copied)
    archive = output / f"loomspan-sidecar-{args.project_version}.zip"
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED) as zipped:
        info = zipfile.ZipInfo(copied.name, (1980, 1, 1, 0, 0, 0)); info.external_attr = 0o644 << 16
        zipped.writestr(info, copied.read_bytes())
    for artifact in (copied, archive):
        digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
        artifact.with_suffix(artifact.suffix + ".sha256").write_text(f"{digest}  {artifact.name}\n")
        print(artifact)


if __name__ == "__main__":
    main()
