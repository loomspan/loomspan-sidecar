# Loomspan Python SDK

`loomspan-sdk` provides typed synchronous and asynchronous clients for the Sidecar
execution HTTP API. It requires CPython 3.12 or newer and an existing Sidecar
execution credential from your application. Its direct runtime dependencies are
h11 and AnyIO.

From the repository root, install into your application's virtual environment.
You can also install a wheel or source distribution of the SDK into that
environment.

```sh
python -m pip install ./sdks/python
```

```python
from loomspan_sdk import CredentialSnapshot, SidecarClient

credential = CredentialSnapshot(execution_jwt)
with SidecarClient("https://sidecar.example.com/") as client:
    catalog = client.list_skills(credential)
    skill = client.get_skill("summarize", credential)
    accepted = client.submit(skill.name, {"text": "Hello"}, credential)
    current = client.get_execution(accepted.id, credential)
    terminal = client.wait_for_completion(accepted.id, credential)
```

An async application can keep one `AsyncSidecarClient` open for its lifespan and
share it across tasks in the same runtime. Supply each caller's credential on
each operation. Use `async with` or await `client.aclose()` at shutdown to
release local connections and reject further calls.

```python
from loomspan_sdk import AsyncSidecarClient, CredentialSnapshot

async def execute(client: AsyncSidecarClient, execution_jwt: str):
    credential = CredentialSnapshot(execution_jwt)
    catalog = await client.list_skills(credential)
    skill = await client.get_skill("summarize", credential)
    accepted = await client.submit(skill.name, {"text": "Hello"}, credential)
    current = await client.get_execution(accepted.id, credential)
    terminal = await client.wait_for_completion(accepted.id, credential)
    return terminal

async def run(base_url: str, execution_jwt: str):
    async with AsyncSidecarClient(base_url) as client:
        return await execute(client, execution_jwt)
```

The runnable [sync example](examples/existing_credential.py) and
[async example](examples/existing_credential_async.py) prompt for the credential
and show discovery, submission, status and waiting.

Discovery preserves descriptor names, descriptions, kinds and schema strings.
Submission takes a Python dictionary representing a JSON object. The accepted
execution ID comes from a complete HTTP 202 body, and status URLs use the
configured base URL. Discovery and status expect HTTP 200. The SDK encodes skill
names as one path segment and rejects blank names, controls, slashes,
backslashes, percent signs and the names `.` and `..`.

Snapshots carry the execution ID, skill name, status, original timestamp text,
optional configuration snapshot ID, result text, primary failure and available
events. Result text is unchanged, including an empty string or malformed
JSON-looking text. A null result is `None`. A failed execution returns a
normal `FAILED` snapshot, with failure details and issues when available.
An absent event history is `None`, while present empty history is `()`.
Event details are returned as defensive copies. A decimal number in event or
problem details that would overflow a float is represented by `decimal.Decimal`.

## Deadlines and failures

By default, the client allows 2 seconds for connection setup and 10 seconds
for the complete HTTP exchange, including the body, with an 8 MiB response
body limit. Both clients accept `connect_timeout`, `response_timeout` and
`max_response_size` to change these limits. System hostname resolution consumes
the deadline. The synchronous client's resolver call can itself run past it.
The async client bounds its await for resolution and initial system TLS trust
loading. An OS lookup or trust load already in progress can finish later.
If a trust load outlives its caller, new HTTPS calls can fail during connection
setup while that load is pending. The client reuses a successful load.
Expired connection setup prevents request dispatch.

`WaitOptions(timeout=60.0, poll_interval=0.25)` gives the default wait in seconds.
The wait shares its remaining budget with every status read, polls only after
successful `QUEUED` or `RUNNING` reads and stops on the first failure.

| Exception | Meaning |
| --- | --- |
| `LocalFailure` | The SDK rejected the call before dispatch. |
| `HttpFailure` | The server or proxy returned a non-success status. `status_code` is available even if the body was unusable. When available, `problem` contains a complete bounded JSON object with credential occurrences redacted in keys and strings. |
| `ProtocolFailure` | Discovery or status did not return a usable response. |
| `AcceptanceUnknown` | Submission was attempted and the SDK could not establish acceptance. Server work may be running. |
| `WaitTerminated` | The local wait reached its deadline. |

SDK status and wait errors expose `execution_id`. Wait errors also expose
`last_snapshot`, which can be `None`. Native `KeyboardInterrupt` in the sync
client and native task cancellation in the async client propagate after local
cleanup. An outer `asyncio.timeout()` keeps its own `TimeoutError` behavior.
Only the SDK's wait budget produces `WaitTerminated` with execution context.

If a submission is interrupted or cancelled after possible dispatch, Sidecar
may already have accepted the work even if the caller receives no execution ID.
Cancellation carries only its native exception. Avoid replaying an uncertain
submission. Ending a wait or closing the client leaves remote work running.
If you already have an accepted execution ID, you can explicitly read status
later with an open client and the appropriate credential.

## Transport and credentials

Supply the credential explicitly for each operation. `CredentialSnapshot`
accepts nonempty printable ASCII without spaces and hides its value from
default display and serialization. Concurrent callers can share the client with
separate credentials.

The client makes one HTTP/1.1 request per operation and omits redirects, automatic
retries, authentication rechallenges, cookies and compression negotiation.
If acceptance is uncertain, avoid automatically resubmitting the operation.
Provide an absolute HTTP(S) URL without user information, query or fragment.
Any base path stays in the request URL.

HTTPS verifies certificates and hostnames by default. To use a private CA
or client identity, supply a standard verifying Python client SSL context and keep
its configuration stable while the client is in use. For responses that use
connection closure to delimit the body, the SDK requires a TLS closure alert.
Keep unexpected EOF detection enabled in the SSL context.

```python
import ssl

from loomspan_sdk import SidecarClient

context = ssl.create_default_context(cafile="private-ca.pem")
context.load_cert_chain("sdk-client.pem", "sdk-client-key.pem")
with SidecarClient("https://sidecar.example.com/", ssl_context=context) as client:
    catalog = client.list_skills(credential)
```
