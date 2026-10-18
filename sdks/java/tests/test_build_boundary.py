"""Exercise the inherited SDK boundary with real, disposable Maven reactors."""

from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


SDK = Path(__file__).resolve().parents[1]
MAVEN = shutil.which("mvn")
VERSION = "0.0.0-sdk-boundary-test"


def dependency(group, artifact):
    return (
        f"<dependency><groupId>{group}</groupId><artifactId>{artifact}</artifactId>"
        f"<version>{VERSION}</version></dependency>"
    )


def fixture(root, directory, group, artifact, dependencies=""):
    path = root / directory
    path.mkdir()
    (path / "pom.xml").write_text(
        '<project xmlns="http://maven.apache.org/POM/4.0.0">'
        "<modelVersion>4.0.0</modelVersion>"
        f"<groupId>{group}</groupId><artifactId>{artifact}</artifactId>"
        f"<version>{VERSION}</version><dependencies>{dependencies}</dependencies>"
        "</project>",
        encoding="utf-8",
    )


class BuildBoundaryTest(unittest.TestCase):
    def build(self, module=None, artifact=None, transitive=False):
        with tempfile.TemporaryDirectory(prefix="sdk-boundary-") as directory:
            root = Path(directory) / "sdk"
            shutil.copytree(
                SDK, root, ignore=shutil.ignore_patterns("target", "__pycache__")
            )
            if artifact:
                fixture(root, "forbidden", "ai.loomspan", artifact)
                modules = "<module>forbidden</module>"
                injected = dependency("ai.loomspan", artifact)
                if transitive:
                    fixture(root, "bridge", "test.sdk.boundary", "bridge", injected)
                    modules += "<module>bridge</module>"
                    injected = dependency("test.sdk.boundary", "bridge")
                parent = root / "pom.xml"
                parent.write_text(
                    parent.read_text().replace("<modules>", "<modules>" + modules)
                )
                child = root / module / "pom.xml"
                text = child.read_text()
                if module == "spring-boot-starter":
                    # Skip the starter's dependency-management import block.
                    position = text.index(
                        "<dependencies>", text.index("</dependencyManagement>")
                    )
                    text = text[:position] + text[position:].replace(
                        "<dependencies>", "<dependencies>" + injected, 1
                    )
                else:
                    text = text.replace(
                        "</project>",
                        f"<dependencies>{injected}</dependencies></project>",
                    )
                child.write_text(text)
            result = subprocess.run(
                [MAVEN, "-B", "-ntp", "-f", str(root / "pom.xml"), "verify"],
                capture_output=True,
                text=True,
                timeout=300,
            )
            if artifact:
                self.assertNotEqual(result.returncode, 0, result.stdout)
                self.assertIn("sdk-dependency-boundary", result.stdout)
                self.assertIn(f"ai.loomspan:{artifact}:jar:{VERSION}", result.stdout)
                self.assertIn("banned via the exclude/include list", result.stdout)
                self.assertIn(
                    f"on project loomspan-sdk-{'core' if module == 'core' else 'spring-boot-starter'}",
                    result.stdout,
                )
            else:
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_clean_control(self):
        self.build()

    def test_direct_and_transitive_dependencies_are_rejected_in_both_modules(self):
        for module in ("core", "spring-boot-starter"):
            for artifact in ("loomspan-spring-boot-starter", "loomspan-sidecar"):
                for transitive in (False, True):
                    with self.subTest(
                        module=module, artifact=artifact, transitive=transitive
                    ):
                        self.build(module, artifact, transitive)


if __name__ == "__main__":
    unittest.main(verbosity=2)
