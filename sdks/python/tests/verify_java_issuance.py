"""Exercise both Java receivers with synthetic credentials from the installed SDK."""

import os
import shutil
import subprocess
from pathlib import Path
from tempfile import TemporaryDirectory

from loomspan_sdk import (
    AuthenticatedIdentity,
    ColocatedSigning,
    CredentialSnapshot,
    ExecutionTokenIssuer,
    IssuanceSettings,
    ProvidedSigning,
    _issuance,
)

ROOT = Path(__file__).resolve().parents[3]
NOW = 1792324800_000_000_000
FIXTURE_ENV = "LOOMSPAN_PYTHON_ISSUANCE_FIXTURES"


def write_token(path: Path, credential: CredentialSnapshot) -> None:
    path.write_bytes(credential._authorization().removeprefix(b"Bearer "))


def write_fixtures(directory: Path) -> None:
    # Fix the clock only in this standalone fixture process.
    _issuance.time_ns = lambda: NOW
    colocated = ExecutionTokenIssuer(ColocatedSigning(directory))
    write_token(directory / "default.jwt", colocated.issue(AuthenticatedIdentity("bob")))

    private = directory / "synthetic-rsa.pem"
    shutil.copyfile(ROOT / "sdks/java/core/src/test/resources/signing" / private.name, private)
    provided = ProvidedSigning(private, directory / "provided.json")
    identity = AuthenticatedIdentity("alice", ["ROLE_ORDER_EDITOR", "admin", "ORDER_EDITOR", "ROLE_ORDER_EDITOR"])
    for case, duration in {
        "eighth": 125_000_000,
        "nanos": 123_456_789,
        "rounds-up": 999_999_999,
        "collapses": 1,
        "rounds-out-of-range": 31556889864403199_999_999_999 - NOW,
    }.items():
        issuer = ExecutionTokenIssuer(provided, IssuanceSettings(
            lifetime_ns=duration, additional_audiences=["callbacks", "loomspan-app", "callbacks"],
            roles=["ORDER_EDITOR", "ROLE_ORDER_EDITOR"],
        ))
        write_token(directory / f"{case}.jwt", issuer.issue(identity))


def verify() -> None:
    maven = shutil.which("mvn")
    if maven is None:
        raise RuntimeError("Maven is required for the Java issuance interop checks")
    with TemporaryDirectory(prefix="loomspan-sdk-interop-") as folder:
        directory = Path(folder)
        write_fixtures(directory)
        env = dict(os.environ, **{FIXTURE_ENV: str(directory)})
        subprocess.run([
            maven, "-o", "-f", "sdks/java/pom.xml",
            "-Dtest=PythonIssuanceInteropTest,SigningKeysTest,ExecutionTokenIssuerTest,CallbackJwtDecoderTest",
            "-Dsurefire.failIfNoSpecifiedTests=false", "test",
        ], cwd=ROOT, env=env, timeout=180, check=True)
        subprocess.run([
            maven, "-o", "-Dtest=PythonIssuanceInteropTest,JwtSecurityConfigurationTest,CurrentFileJwtTrustTest", "test",
        ], cwd=ROOT, env=env, timeout=180, check=True)


if __name__ == "__main__":
    verify()
