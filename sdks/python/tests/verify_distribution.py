"""Build and exercise the installed SDK from fresh, external environments."""

import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path
from tempfile import TemporaryDirectory
from uuid import UUID

from wire_fixture import WirePeer, reply_json

PROJECT = Path(__file__).resolve().parents[1]
EXECUTION_ID = UUID("6a0c950c-d172-4151-b629-6a5c15c7ae37")
TOKEN = "synthetic-distribution-test"
DESCRIPTOR = {"name": "echo", "kind": "HTTP", "inputSchema": "{}"}
SYNTHETIC_PEM = PROJECT.parent / "java/core/src/test/resources/signing/synthetic-rsa.pem"

CHECK_INSTALL = """
import importlib.metadata as metadata
import importlib.util
import pathlib
import platform
import sys

import loomspan_sdk

installed = pathlib.Path(loomspan_sdk.__file__).resolve()
assert installed.is_relative_to(pathlib.Path(sys.prefix).resolve()), installed
dist = metadata.distribution("loomspan-sdk")
assert dist.version == "0.1.0.dev0"
assert dist.metadata["Requires-Python"] == ">=3.12"
requirements = {
    name: set(bounds.split(","))
    for name, bounds in (requirement.split("<", 1) for requirement in dist.requires)
}
assert requirements == {
    "anyio": {"5", ">=4.16"}, "asn1": {"4", ">=3.3"}, "cryptography": {"49", ">=48"}, "h11": {"0.17", ">=0.16"},
    "pyasn1": {"0.7", ">=0.6.5"}, "pyasn1-modules": {"0.5", ">=0.4.2"}, "simplejson": {"5", ">=4.2"},
}
assert installed.with_name("py.typed").is_file()
assert installed.with_name("py.typed").is_relative_to(pathlib.Path(sys.prefix).resolve())
for name in ("fastapi", "starlette", "uvicorn", "jwt"):
    assert importlib.util.find_spec(name) is None, name
assert {"CredentialSnapshot", "AsyncSidecarClient", "AuthenticatedIdentity", "ExecutionTokenIssuer",
        "IssuanceSettings", "ColocatedSigning", "ProvidedSigning"}.issubset(loomspan_sdk.__all__)
print(platform.python_implementation(), platform.python_version(), platform.system(),
      platform.machine(), *(f"{name} {metadata.version(name)}" for name in sorted(requirements)))
"""


def write_issuance_example(external: Path) -> Path:
    readme = (PROJECT / "README.md").read_text(encoding="utf-8")
    issuance = readme.split("## Issue execution credentials\n", 1)[1].split("\n## ", 1)[0]
    colocated, provided, capture = re.findall(r"```python\n(.*?)\n```", issuance, re.DOTALL)
    script = external / "documented_issuance.py"
    script.write_text("\n\n".join((capture, colocated, """
colocated_credential = credential_for_authenticated_caller(issuer, "alice", ["ORDER_EDITOR"])
assert repr(colocated_credential) == "CredentialSnapshot([redacted])"
""", provided, """
provided_credential = credential_for_authenticated_caller(issuer, "bob", ["ORDER_EDITOR"])
assert repr(provided_credential) == "CredentialSnapshot([redacted])"
""")), encoding="utf-8")
    (external / "runtime/loomspan-trust").mkdir(parents=True)
    (external / "secrets").mkdir()
    shutil.copyfile(SYNTHETIC_PEM, external / "secrets/loomspan-signing.pem")
    return script


def serve_example(conn, request, index):
    assert request.headers["authorization"] == f"Bearer {TOKEN}"
    if index == 0:
        assert (request.method, request.target) == ("GET", "/v1/skills")
        reply_json(conn, 200, [DESCRIPTOR])
    elif index == 1:
        assert (request.method, request.target) == ("GET", "/v1/skills/echo")
        reply_json(conn, 200, DESCRIPTOR)
    elif index == 2:
        assert (request.method, request.target) == ("POST", "/v1/skills/echo/executions")
        assert json.loads(request.body) == {"text": "hello"}
        reply_json(conn, 202, {"id": str(EXECUTION_ID)})
    else:
        assert index in (3, 4)
        assert (request.method, request.target) == ("GET", f"/v1/executions/{EXECUTION_ID}")
        reply_json(conn, 200, {
            "id": str(EXECUTION_ID), "skillName": "echo",
            "createdAt": "2026-11-07T10:00:00.123456789Z",
            "status": "RUNNING" if index == 3 else "COMPLETED",
            "completedAt": None if index == 3 else "2026-11-07T10:00:01Z",
        })


def verify() -> None:
    env = os.environ.copy()
    for key in ("PYTHONPATH", "PYTHONHOME", "VIRTUAL_ENV"):
        env.pop(key, None)
    with TemporaryDirectory(prefix="loomspan-sdk-dist-") as folder:
        external = Path(folder)
        assert not external.resolve().is_relative_to(PROJECT.parent.parent)

        def run(*args: str, input_text: str | None = None) -> str:
            process = subprocess.run(
                args, cwd=external, env=env, timeout=180, text=True,
                input=input_text, capture_output=True,
            )
            if process.returncode != 0:
                raise RuntimeError(f"{args[0]} failed:\n{process.stdout}\n{process.stderr}")
            assert TOKEN not in process.stdout and TOKEN not in process.stderr
            return process.stdout.strip()

        output = external / "dist"
        run("uv", "build", "--out-dir", str(output), str(PROJECT))
        (wheel,) = output.glob("*.whl")
        (sdist,) = output.glob("*.tar.gz")
        print(f"Built {wheel.name} and {sdist.name}")
        examples = [external / name for name in ("existing_credential.py", "existing_credential_async.py")]
        for example in examples:
            shutil.copyfile(PROJECT / "examples" / example.name, example)
        issuance_example = write_issuance_example(external)
        for kind, artifact in (("wheel", wheel), ("sdist", sdist)):
            environment = external / kind
            run("uv", "venv", "--python", sys.executable, str(environment))
            python = str(environment / ("Scripts/python.exe" if os.name == "nt" else "bin/python"))
            run("uv", "pip", "install", "--python", python, str(artifact))
            print(f"{kind}: {run(python, '-I', '-c', CHECK_INSTALL)}")
            assert run(python, "-I", str(issuance_example)) == ""
            keys = []
            for filename in ("jwks.json", "provided-jwks.json"):
                [key] = json.loads((external / "runtime/loomspan-trust" / filename).read_bytes())["keys"]
                assert set(key) == {"kty", "n", "e", "kid", "alg", "use"}
                keys.append(key)
            assert keys[0]["kid"] != keys[1]["kid"]
            assert keys[1] == json.loads(SYNTHETIC_PEM.with_name("public-jwks.json").read_bytes())["keys"][0]
            for example in examples:
                with WirePeer(serve_example) as peer:
                    summary = run(python, "-I", str(example), peer.url, "echo", "hello",
                                  input_text=TOKEN + "\n")
                    assert "Discovered 1 skill(s)." in summary and "RUNNING -> COMPLETED" in summary
                    assert str(EXECUTION_ID) in summary
                assert len(peer.requests) == 5

        type_environment = external / "types"
        run("uv", "venv", "--python", sys.executable, str(type_environment))
        python = str(type_environment / ("Scripts/python.exe" if os.name == "nt" else "bin/python"))
        mypy = next(
            requirement for requirement in (PROJECT / "requirements-dev.txt").read_text().splitlines()
            if requirement.startswith("mypy==")
        )
        run("uv", "pip", "install", "--python", python, str(wheel), mypy)
        print(run(python, "-I", "-m", "mypy", "--strict", *(str(example) for example in [*examples, issuance_example])))


if __name__ == "__main__":
    verify()
