"""Verify real Java SDK issuance using a freshly installed Python SDK."""

import os
import shutil
import subprocess
from importlib.metadata import distribution
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

import anyio
import loomspan_sdk
from loomspan_sdk import (
    CallbackPrincipal,
    CallbackVerifier,
    ColocatedSigning,
    ExecutionTokenIssuer,
    InvalidCallbackCredential,
    _callback,
)

ROOT = Path(__file__).resolve().parents[3]
NOW = 1792324800_123_456_789
SKEW_EDGE = NOW + 1 + 60_000_000_000


async def require_rejection(verifier: CallbackVerifier, token: str) -> None:
    try:
        await verifier.verify(token)
    except InvalidCallbackCredential:
        return
    raise AssertionError("Java credential should have been rejected")


async def verify_fixtures(directory: Path) -> None:
    own = directory / "python"
    own.mkdir()
    issuer = ExecutionTokenIssuer(ColocatedSigning(own))
    overlap = directory / "jwks.json"
    regular = (directory / "regular.jwt").read_text(encoding="ascii")
    nanosecond = (directory / "nanosecond.jwt").read_text(encoding="ascii")
    expected = CallbackPrincipal("loomspan-app", "java:alice", ("ROLE_ORDER_EDITOR", "ORDER_EDITOR"))

    with patch.object(_callback, "time_ns", return_value=NOW) as clock:
        async with CallbackVerifier.open(issuer) as verifier:
            await require_rejection(verifier, regular)
        async with CallbackVerifier.open(issuer, overlap_jwks=overlap) as verifier:
            assert await verifier.verify(regular) == expected
        async with CallbackVerifier.open(issuer, overlap_jwks=overlap, audience="callback-receiver") as verifier:
            assert await verifier.verify(regular) == expected
            clock.return_value = SKEW_EDGE
            assert await verifier.verify(nanosecond) == expected
            clock.return_value += 1
            await require_rejection(verifier, nanosecond)
        async with CallbackVerifier.open(issuer, overlap_jwks=overlap, audience="other-receiver") as verifier:
            await require_rejection(verifier, regular)


def verify() -> None:
    installed = Path(loomspan_sdk.__file__).resolve()
    packaged = Path(distribution("loomspan-sdk").locate_file("loomspan_sdk/__init__.py")).resolve()
    if installed.is_relative_to(ROOT) or installed != packaged:
        raise RuntimeError("Install the Python SDK in the runner environment before checking interoperability")
    maven = shutil.which("mvn")
    if maven is None:
        raise RuntimeError("Maven is required for the Java callback interoperability check")
    with TemporaryDirectory(prefix="loomspan-java-callbacks-") as folder:
        directory = Path(folder)
        subprocess.run(
            [maven, "-o", "-f", "sdks/java/pom.xml", "-pl", "core", "-Dtest=JavaCallbackIssuanceFixtureTest", "test"],
            cwd=ROOT, env=dict(os.environ, LOOMSPAN_JAVA_CALLBACK_FIXTURES=str(directory)), timeout=180, check=True,
        )
        anyio.run(verify_fixtures, directory)
    print("Installed Python verifier accepted Java-issued credentials with the Java public JWKS.")


if __name__ == "__main__":
    verify()
