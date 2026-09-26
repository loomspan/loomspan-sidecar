"""Portable bundle contracts; no installed agent or running Sidecar is required."""
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import threading
import unittest
import xml.etree.ElementTree as ET
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = Path(__file__).resolve().parents[1]
BUNDLE = ROOT / "agent-skills/loomspan-sidecar-authoring"


def validate_bundle(root):
    errors = []
    pom = ET.parse(ROOT / "pom.xml").getroot()
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    version = pom.findtext("m:version", namespaces=ns)
    dependencies = [d for d in pom.findall("m:dependencies/m:dependency", ns)
                    if d.findtext("m:groupId", namespaces=ns) == "ai.loomspan"
                    and d.findtext("m:artifactId", namespaces=ns) == "loomspan-spring-boot-starter"]
    assert len(dependencies) == 1, "expected one direct framework dependency"
    framework = dependencies[0].findtext("m:version", namespaces=ns)
    if framework.startswith("${") and framework.endswith("}"):
        framework = pom.findtext("m:properties/m:" + framework[2:-1], namespaces=ns)
    assert framework and "${" not in framework, "unresolved framework dependency"
    manifest = (root / "SKILL.md").read_text(encoding="utf-8")
    for key, value in {"loomspan-component": "sidecar", "loomspan-version": version,
                       "loomspan-framework-version": framework}.items():
        if not re.search(r"^  " + key + r': ["\x27]?' + re.escape(value) + r'["\x27]?$', manifest, re.M):
            errors.append(f"missing/mismatched {key}: {value}")
    for path in [root, *root.rglob("*")]:
        if "__pycache__" in path.parts:
            continue
        info = path.lstat()
        if path.is_symlink() or getattr(info, "st_file_attributes", 0) & stat.FILE_ATTRIBUTE_REPARSE_POINT:
            errors.append(f"linked asset: {path}")
            continue
        if not path.is_file() or path.suffix != ".md":
            continue
        content = path.read_text(encoding="utf-8")
        links = re.findall(r"\]\(([^)]+)\)", content)
        # Resource-looking inline code is a requirement too; API/configuration paths are not files.
        links += re.findall(r"`((?:\.\.?/|agent-skills/|docs/|references/|client/|examples/)[^`\s]*\.(?:md|py|yaml))`", content)
        for link in links:
            if "://" in link or link.startswith("#"):
                continue
            target = (path.parent / link.split("#")[0]).resolve()
            if not target.is_relative_to(root.resolve()) or not target.exists():
                errors.append(f"missing/escaping resource: {path.name}: {link}")
    if errors:
        raise AssertionError("\n".join(errors))


class SidecarAgentSkillsTest(unittest.TestCase):
    def copy_bundle(self, folder):
        return Path(shutil.copytree(BUNDLE, Path(folder) / "skill with spaces", ignore=shutil.ignore_patterns("__pycache__")))

    def test_sidecar_bundle_is_self_contained(self):
        with tempfile.TemporaryDirectory() as folder:
            validate_bundle(self.copy_bundle(folder))

    def test_invalid_metadata_and_resources_are_rejected(self):
        for mutation in ("marker", "version", "framework", "absent-marker", "escape", "missing"):
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory() as folder:
                root = self.copy_bundle(folder)
                skill = root / "SKILL.md"
                text = skill.read_text(encoding="utf-8")
                if mutation == "marker":
                    text = text.replace("loomspan-component: sidecar", "loomspan-component: framework")
                elif mutation == "version":
                    text = text.replace('loomspan-version: "1.0.0-beta.1-SNAPSHOT"', 'loomspan-version: "0.0.0"')
                elif mutation == "framework":
                    text = text.replace('loomspan-framework-version: "1.0.0-beta.6-SNAPSHOT"', 'loomspan-framework-version: "0.0.0"')
                elif mutation == "absent-marker":
                    text = text.replace("  loomspan-component: sidecar\n", "")
                else:
                    text += "\n[bad](" + ("../../outside.md" if mutation == "escape" else "references/absent.md") + ")\n"
                skill.write_text(text, encoding="utf-8")
                with self.assertRaises(AssertionError):
                    validate_bundle(root)

    def test_linked_asset_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.copy_bundle(folder)
            try:
                (root / "linked.md").symlink_to(root / "SKILL.md")
            except OSError as error:
                self.skipTest(f"platform does not permit symlinks: {error}")
            with self.assertRaisesRegex(AssertionError, "linked asset"):
                validate_bundle(root)

    def test_canonical_examples(self):
        example = BUNDLE / "examples/remote-authoring"
        self.assertFalse((ROOT / "examples/remote-authoring").exists())
        self.assertIn("name: readRecord", (example / "record.yaml").read_text())
        self.assertIn("  readRecord:", (example / "rest-routes.yaml").read_text())

    def test_execution_authoring_guidance_uses_shared_console_workflow(self):
        skill = (BUNDLE / "SKILL.md").read_text(encoding="utf-8")
        client = (BUNDLE / "references/client-usage.md").read_text(encoding="utf-8")
        execution = (BUNDLE / "references/execution-configuration.md").read_text(encoding="utf-8")
        for text in (skill, client, execution):
            self.assertIn("executionConfigurationYaml", text)
        for action in ("read", "save", "validate", "publish"):
            self.assertIn(action, skill.lower())
        self.assertIn("api-key-ref", execution)
        self.assertIn("process settings", execution)
        integration = (BUNDLE / "references/integration.md").read_text(encoding="utf-8")
        self.assertIn("api-key-ref: provider.primary.key", integration)
        self.assertIn("execution settings", integration)
        self.assertNotIn("api-key: ${MODEL_API_KEY}", integration)

    def test_detached_client_from_unrelated_cwd(self):
        requests = []
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                requests.append((self.path, self.headers.get("Authorization")))
                body = b'{"publishedId":"fixture-p1"}'
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            def log_message(self, *_args):
                pass
        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with tempfile.TemporaryDirectory() as folder, tempfile.TemporaryDirectory() as cwd:
                root = self.copy_bundle(folder)
                token = "detached-fixture-token"
                result = subprocess.run([sys.executable, str(root / "client/sidecar_authoring.py"),
                    "--url", f"http://127.0.0.1:{server.server_port}", "current"], cwd=cwd,
                    env={**os.environ, "LOOMSPAN_SIDECAR_MANAGEMENT_TOKEN": token},
                    capture_output=True, text=True, timeout=15)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(json.loads(result.stdout), {"publishedId": "fixture-p1"})
                self.assertEqual(requests, [("/api/management/configuration/current", "Bearer " + token)])
                self.assertNotIn(token, result.stdout + result.stderr)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


if __name__ == "__main__":
    unittest.main()
