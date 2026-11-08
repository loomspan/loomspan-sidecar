# Loomspan Python SDK

`loomspan-sdk` provides typed synchronous and asynchronous clients for the Sidecar
execution HTTP API and issues execution credentials from application-authenticated
identity. It also verifies execution credentials at callback admission. It requires
CPython 3.12 or newer.

From the repository root, install into your application's virtual environment.
You can also install a wheel or source distribution of the SDK into that
environment.

```sh
python -m pip install ./sdks/python
```

## Issue execution credentials

Choose one signing mode and initialize an `ExecutionTokenIssuer` at application
startup. In colocated mode, the SDK generates a new in-memory 2048-bit RSA key
on every startup and publishes `jwks.json` to the configured directory.
Receivers adopting the new public key can no longer verify credentials signed
with the previous key unless they still trust that key.

```python
from pathlib import Path

from loomspan_sdk import ColocatedSigning, ExecutionTokenIssuer

issuer = ExecutionTokenIssuer(ColocatedSigning(Path("runtime/loomspan-trust")))
```

In provided mode, provision a file containing one unencrypted PKCS#8 `PRIVATE KEY`
PEM block for an RSA key of at least 2048 bits. The SDK loads it once at startup.
The same material keeps the same public identity across restarts. To rotate it,
replace the operator-owned private file, restart the application, and coordinate
adoption of the new public key at receivers. Missing or invalid material fails
startup.

The loader checks the complete PKCS#8 structure, including optional attribute
identifiers and nonempty value sets. It verifies that an embedded public key,
when present, matches the RSA private key. The SDK ignores optional attribute
payloads for signing and relies on its BER libraries to validate them. The
Python and Java SDKs can differ in which payload encodings they accept, such
as unusual ASN.1 time or string values.

```python
from pathlib import Path

from loomspan_sdk import ExecutionTokenIssuer, IssuanceSettings, ProvidedSigning

issuer = ExecutionTokenIssuer(
    ProvidedSigning(Path("secrets/loomspan-signing.pem"), Path("runtime/loomspan-trust/provided-jwks.json")),
    IssuanceSettings(roles=["ORDER_EDITOR"], additional_audiences=["orders-callbacks"]),
)
```

In both modes, the operator provisions the files, directories and permissions
needed by the app and its receivers. Use an existing export directory on a
filesystem that supports atomic same-directory replacement, with one process
responsible for each export path. The SDK publishes a complete public-only RSA
JWKS for receiver trust. On POSIX, each publication installs a new owner-only
file. If receivers run under a different account, arrange read access or
distribute the public JWKS after each publication. Provided input and export
paths must be distinct.
A publication error fails startup and preserves any previous export. Configure
TLS certificates and trust separately from the signing key and JWKS.

Pass the subject and authorities captured by your application's authentication
layer into `AuthenticatedIdentity`. Treat these values as security context and
keep them separate from request bodies, model arguments and skill input. Issue a
credential for that identity and pass it explicitly to either client.

```python
from collections.abc import Collection

from loomspan_sdk import AuthenticatedIdentity, CredentialSnapshot, ExecutionTokenIssuer

def credential_for_authenticated_caller(
    issuer: ExecutionTokenIssuer, subject: str, authorities: Collection[str]
) -> CredentialSnapshot:
    return issuer.issue(AuthenticatedIdentity(subject, authorities))
```

The issuer defaults to `loomspan-app` with a 15-minute lifetime. It needs only
the captured identity and uses its own lifetime independently of the login
session or original token. Each credential includes `loomspan-sidecar`, the
issuer and any configured extra audiences, with duplicates removed. Roles are
the exact names present in both the captured authorities and the configured
allowlist, which defaults to empty. The SDK preserves names such as
`ROLE_ORDER_EDITOR` and `ORDER_EDITOR` as distinct authorities. Issued roles
remain a snapshot until expiry.

Set `lifetime_ns` to a positive number of nanoseconds within the supported
timestamp range. The SDK warns at startup above one hour. It signs NumericDates
as exact JSON numbers with nanosecond precision. Current Java receivers may
round fractional values when reading them. A sub-microsecond lifetime can round
to equal issuance and expiration times and be rejected. Very large dates near
the timestamp limit can also round out of range.

Credentials and issuer key material are omitted from default display and
serialization. Treat issued credentials as bearer secrets.

## Verify execution callbacks

Open one `CallbackVerifier` from the initialized issuer in your application's
async lifecycle. Keep the verifier open while the application accepts callbacks.

```python
from collections.abc import Awaitable, Callable

from loomspan_sdk import CallbackVerifier, ExecutionTokenIssuer

async def serve_callbacks(
    issuer: ExecutionTokenIssuer,
    serve: Callable[[CallbackVerifier], Awaitable[None]],
) -> None:
    async with CallbackVerifier.open(issuer) as verifier:
        await serve(verifier)
```

At admission, pass the compact token from the bearer header to
`await verifier.verify(token)`. The result is an immutable `CallbackPrincipal`
with `issuer`, `subject` and the exact `authorities` in the credential. Use these
values for application authorization. Invalid credentials raise
`InvalidCallbackCredential` with a generic message. Treat the bearer token as
secret and keep it out of application logs and business payloads.

The verifier uses the issuance issuer and defaults its audience to that same
value. Set `audience` on `CallbackVerifier.open` to require an explicit receiver
audience. The verifier checks the RS256 signature, execution type and purpose,
audience and claims. Expiration and optional not-before receive 60 seconds of
clock skew at admission. The verifier preserves nanosecond precision and requires
expiration to follow issuance when the credential supplies an issuance time.

By default only the startup signer's public key is trusted. To also trust public
keys for the same issuer, pass `overlap_jwks=Path("runtime/callback-overlap.json")`
to `CallbackVerifier.open`. Use a readable local file with a complete, nonempty
public RSA JWKS. Relative paths use the working directory at verifier startup.
Supply unique key IDs and use RSA keys of at least 2048 bits.
If a key specifies `use` or `alg`, use `sig` and `RS256` respectively.
The file may include the startup public key under its own ID. A different key
with that ID invalidates the file. Unavailable or invalid overlap fails startup.

While overlap is configured, the verifier reads the current file at one-second
intervals and can refresh on an unknown key ID, at most once per second across
the verifier. Detected unavailability or invalidity withdraws all callback trust,
including the startup public key. A corrected file restores own-key trust and
the complete current overlap. Publish overlap changes atomically. Refresh keeps
the startup signing key in place. Closing the verifier stops refresh and waits for
any local read already in progress.
Withdrawal and expiry govern new admission. Previously admitted callbacks can
finish with the identity already verified for them.

## Use the execution clients

Use a `CredentialSnapshot` from the issuer or wrap an execution JWT issued by
your application.

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
