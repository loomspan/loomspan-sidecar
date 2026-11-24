#!/usr/bin/env python3
"""Discover and invoke a published skill against the local evaluation stack."""
import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from uuid import UUID


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, new_url):
        return None


def run(namespace, local_name, payload, origin, host):
    opener = urllib.request.build_opener(NoRedirect)

    def read(path, data=None):
        request = urllib.request.Request(origin + path, data=data, headers=headers)
        with opener.open(request, timeout=10 if data is None else 30) as response:
            if data is not None and response.status != 202:
                raise RuntimeError("Expected 202 Accepted")
            return json.load(response)

    with opener.open(host + "/token", timeout=10) as response:
        token = json.load(response)["access_token"]
    headers = {"Authorization": "Bearer " + token, "Content-Type": "application/json"}
    catalog_path = f"/v1/namespaces/{urllib.parse.quote(namespace, safe='')}/skills"
    target = next((skill for skill in read(catalog_path)
                   if (skill["namespace"], skill["localName"]) == (namespace, local_name)), None)
    if target is None:
        raise RuntimeError(f"Skill {namespace}:{local_name} was not found")
    namespace_segment = urllib.parse.quote(target["namespace"], safe="")
    name_segment = urllib.parse.quote(target["localName"], safe="")
    skill_path = f"/v1/namespaces/{namespace_segment}/skills/{name_segment}"
    read(skill_path)
    accepted = read(skill_path + "/executions", payload)
    execution_id = UUID(accepted["id"])
    print("Accepted execution:", execution_id, flush=True)

    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        execution = read(f"/v1/executions/{execution_id}")
        if execution["status"] == "COMPLETED":
            print(execution["result"])
            return
        if execution["status"] == "FAILED":
            raise RuntimeError(json.dumps(execution["failure"]))
        time.sleep(1)
    raise RuntimeError("Polling deadline reached; inspect the accepted execution before retrying.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("namespace")
    parser.add_argument("local_name")
    parser.add_argument("--origin", default="http://localhost:8080")
    parser.add_argument("--host", default="http://localhost:8081")
    args = parser.parse_args()
    payload = sys.stdin.buffer.read()
    if not isinstance(json.loads(payload.decode("utf-8")), dict):
        raise ValueError("Supply a UTF-8 JSON object on stdin")
    run(args.namespace, args.local_name, payload, args.origin.rstrip("/"), args.host.rstrip("/"))


if __name__ == "__main__":
    try:
        main()
    except (urllib.error.URLError, RuntimeError, ValueError) as error:
        raise SystemExit(str(error))
