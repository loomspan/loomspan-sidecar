#!/usr/bin/env python3
"""Invoke the published triage skill against the local evaluation stack."""
import json
import time
import urllib.error
import urllib.parse
import urllib.request


def main():
    origin = "http://localhost:8080"
    with urllib.request.urlopen("http://localhost:8081/token", timeout=10) as response:
        token = json.load(response)["access_token"]
    headers = {"Authorization": "Bearer " + token, "Content-Type": "application/json"}
    payload = {"customerMessage": "Since this morning's deployment, checkout times out after payment. "
               "Three customers were charged without an order confirmation. "
               "Retrying sometimes creates two orders."}
    request = urllib.request.Request(origin + "/v1/skills/triageSupportRequest/executions",
                                     data=json.dumps(payload).encode(), headers=headers, method="POST")
    with urllib.request.urlopen(request, timeout=30) as response:
        if response.status != 202:
            raise RuntimeError("Expected 202 Accepted")
        location = response.headers["Location"]
        accepted = json.load(response)
    print("Accepted execution:", accepted["id"], flush=True)
    location = urllib.parse.urljoin(origin, location)
    if urllib.parse.urlsplit(location).netloc != urllib.parse.urlsplit(origin).netloc:
        raise RuntimeError("Unexpected polling host")
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        request = urllib.request.Request(location, headers=headers)
        with urllib.request.urlopen(request, timeout=10) as response:
            execution = json.load(response)
        if execution["status"] == "COMPLETED":
            print(execution["result"])
            return
        if execution["status"] == "FAILED":
            raise RuntimeError(json.dumps(execution["failure"]))
        time.sleep(1)
    raise RuntimeError("Polling deadline reached; inspect the accepted execution before retrying.")


if __name__ == "__main__":
    try:
        main()
    except (urllib.error.URLError, RuntimeError) as error:
        raise SystemExit(str(error))
