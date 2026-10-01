"""Exercise version changes and tag guards in disposable Git repositories."""
from pathlib import Path
import tempfile
import unittest

import sidecar_version as version

ROOT = Path(__file__).resolve().parents[1]


class VersionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for name in (Path("pom.xml"), version.SKILL):
            target = self.root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((ROOT / name).read_bytes())
        version.git(self.root, "init")
        version.git(self.root, "config", "user.name", "Version test")
        version.git(self.root, "config", "user.email", "test@example.invalid")
        self.commit()

    def commit(self):
        version.git(self.root, "add", ".")
        version.git(self.root, "commit", "-m", "fixture")

    def test_round_trip_preserves_framework_and_other_content(self):
        original = {p: (self.root / p).read_bytes() for p in (Path("pom.xml"), version.SKILL)}
        old, framework = version.check(self.root)
        version.set_version(self.root, "2.0.0-beta.1")
        self.assertEqual(version.check(self.root), ("2.0.0-beta.1", framework))
        self.commit()
        version.set_version(self.root, old)
        for path, content in original.items():
            self.assertEqual((self.root / path).read_bytes(), content)

    def test_dirty_worktree_blocks_set_and_tag(self):
        (self.root / "untracked.txt").write_text("keep")
        for action in (version.set_version, version.tag):
            with self.assertRaisesRegex(version.VersionError, "clean worktree"):
                action(self.root, "2.0.0")

    def test_mismatched_metadata_blocks_check_and_set(self):
        path = self.root / version.SKILL
        path.write_text(path.read_text().replace('loomspan-version: "', 'loomspan-version: "wrong-'))
        self.commit()
        with self.assertRaisesRegex(version.VersionError, "Skill"):
            version.check(self.root)
        before = (self.root / "pom.xml").read_bytes()
        with self.assertRaises(version.VersionError):
            version.set_version(self.root, "2.0.0")
        self.assertEqual((self.root / "pom.xml").read_bytes(), before)

    def test_annotated_tag_and_duplicate_guard(self):
        version.set_version(self.root, "2.0.0-beta.1")
        self.commit()
        name = version.tag(self.root, "2.0.0-beta.1")
        self.assertEqual(version.git(self.root, "cat-file", "-t", name), "tag")
        self.assertEqual(version.git(self.root, "rev-parse", name + "^{}"),
                         version.git(self.root, "rev-parse", "HEAD"))
        with self.assertRaisesRegex(version.VersionError, "already exists"):
            version.tag(self.root, "2.0.0-beta.1")

    def test_snapshot_and_wrong_version_cannot_be_tagged(self):
        with self.assertRaisesRegex(version.VersionError, "SNAPSHOT"):
            version.tag(self.root, "2.0.0-SNAPSHOT")
        with self.assertRaisesRegex(version.VersionError, "does not match"):
            version.tag(self.root, "9.0.0")

    def test_snapshot_framework_blocks_release(self):
        version.set_version(self.root, "2.0.0")
        _, framework = version.check(self.root)
        for relative in (Path("pom.xml"), version.SKILL):
            path = self.root / relative
            path.write_text(path.read_text().replace(framework, framework + "-SNAPSHOT"))
        self.commit()
        with self.assertRaisesRegex(version.VersionError, "SNAPSHOT framework"):
            version.tag(self.root, "2.0.0")

    def test_invalid_versions_rejected_without_edits(self):
        for value in ("", "../oops", "1.0", "-force", "1.0.0\n", "${version}"):
            with self.subTest(value=value), self.assertRaises(version.VersionError):
                version.set_version(self.root, value)
        self.assertEqual(version.git(self.root, "status", "--porcelain"), "")


if __name__ == "__main__":
    unittest.main()
