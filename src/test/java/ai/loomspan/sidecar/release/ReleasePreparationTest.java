package ai.loomspan.sidecar.release;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReleasePreparationTest {
    @Test
    void releasePreparationRejectsSnapshotsAndProducesExpectedArtifactPlan() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        String ci = Files.readString(Path.of(".github/workflows/ci.yml"));
        String release = Files.readString(Path.of(".github/workflows/release.yml"));
        String script = Files.readString(Path.of("scripts/prepare-release.py"));
        String imageVerifier = Files.readString(Path.of("scripts/verify-image.py"));

        assertThat(pom).contains("<id>release</id>", "requireReleaseVersion", "requireReleaseDeps",
                "1\\.0\\.0-beta\\.5");
        assertThat(ci).contains("docker build", "verify-image.py").doesNotContain("push: true", "docker/login-action");
        assertThat(release).contains("tags: [\"v*\"]", "--tag \"${GITHUB_REF_NAME}\"", "if: startsWith(github.ref",
                "permissions:\n  contents: read", "permissions:\n      contents: write\n      packages: write",
                "group: release-${{ github.ref }}", "cancel-in-progress: false",
                "api.github.com/repos/${GITHUB_REPOSITORY}/releases/tags/${GITHUB_REF_NAME}",
                "Could not establish whether release", "docker manifest inspect",
                "manifest unknown|no such manifest", "Could not establish whether image tag", "refusing to overwrite",
                "push: true", "docker/login-action", "prepare-release.py", "softprops/action-gh-release");
        assertThat(script).contains("SNAPSHOT", "1.0.0-beta.5", ".sha256", "zipfile.ZipFile");
        assertThat(imageVerifier).contains("DEFAULT_COMMAND_TIMEOUT_SECONDS", "CLEANUP_TIMEOUT_SECONDS",
                "timeout=DEFAULT_COMMAND_TIMEOUT_SECONDS", "except subprocess.TimeoutExpired",
                "if result.returncode", "require_cleanup(\"docker\", \"rm\"",
                "require_cleanup(\"docker\", \"compose\"");

        assertThat(run("1.0.0-beta.5", "1.0.0-beta.5", "v1.0.0-beta.5")).isZero();
        assertThat(run("1.0.0-beta.5-SNAPSHOT", "1.0.0-beta.5", "v1.0.0-beta.5-SNAPSHOT")).isNotZero();
        assertThat(run("1.0.0-beta.5", "1.0.0-beta.3", "v1.0.0-beta.5")).isNotZero();
        assertThat(run("1.0.0-beta.5", "1.0.0-beta.5", "vwrong")).isNotZero();
    }

    private static int run(String project, String loomspan, String tag) throws Exception {
        Process process = new ProcessBuilder("python", "scripts/prepare-release.py", "--validate-only",
                "--project-version", project, "--loomspan-version", loomspan, "--tag", tag)
                .redirectErrorStream(true).start();
        process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
        return process.waitFor();
    }
}
