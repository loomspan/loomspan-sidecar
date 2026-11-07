"""Build and exercise the installed SDK from fresh, external environments."""

import json
import os
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
assert len(dist.requires) == 1
assert dist.requires[0].split("<", 1)[0] == "h11"
assert set(dist.requires[0][3:].split(",")) == {"<0.17", ">=0.16"}
assert installed.with_name("py.typed").is_file()
assert installed.with_name("py.typed").is_relative_to(pathlib.Path(sys.prefix).resolve())
for name in ("fastapi", "starlette", "uvicorn", "cryptography", "jwt"):
    assert importlib.util.find_spec(name) is None, name
assert importlib.util.find_spec("loomspan_sdk._signing") is None
assert "CredentialSnapshot" in loomspan_sdk.__all__
print(platform.python_implementation(), platform.python_version(), platform.system(),
      platform.machine(), "h11", metadata.version("h11"))
"""


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
        example = external / "existing_credential.py"
        shutil.copyfile(PROJECT / "examples" / example.name, example)
        for kind, artifact in (("wheel", wheel), ("sdist", sdist)):
            environment = external / kind
            run("uv", "venv", "--python", sys.executable, str(environment))
            python = str(environment / ("Scripts/python.exe" if os.name == "nt" else "bin/python"))
            run("uv", "pip", "install", "--python", python, str(artifact))
            print(f"{kind}: {run(python, '-I', '-c', CHECK_INSTALL)}")
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
        print(run(python, "-I", "-m", "mypy", "--strict", str(example)))


if __name__ == "__main__":
    verify()
