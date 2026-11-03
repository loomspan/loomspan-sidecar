# HTTP skill executor

The `http` executor implements application HTTP operations. YAML skills use the
Framework's executor contract; Sidecar validates HTTP configuration and actual
mapped input without duplicating Framework schema or role validation.

**Current activation limitation:** the startup loader and management editor do
not assemble this executor configuration. They still expect separate route data
and cannot activate these skills. The runtime can be composed using its public
configuration validators and Framework namespace publication APIs. The current
checkout's complete application build is blocked by those integration callers;
the isolated HTTP runtime verification is not an application deployment check.

```yaml
name: updateOrder
description: Update an authorized order
executor: http
config:
  target: orders
  method: PATCH
  path: /orders/{id}
  pathParams: {id: /order/id}
  queryParams: {tag: /tags}
  body: {source: field, field: /order/changes}
  recoverableStatuses: [409, 422]
input_schema:
  type: object
  properties:
    order: {type: object}
    tags: {type: array, items: {type: string}}
  required: [order]
```

The annotated bean is `@SkillExecutor(name="http", namespace="default", config="http")`.
Its captured executor settings are the selected map under
`loomspan.executor-config.default.http`:

```yaml
loomspan:
  executor-config:
    default:
      http:
        targets:
          orders:
            base-url: https://orders.example/api/v1
            headers: {Accept: application/json, X-Application: support}
            auth: {mode: caller-passthrough}
            connect-timeout: 5s
            read-timeout: 60s
            max-response-size: 1MB
```

The settings map accepts `targets` and optional `credentials`. A target accepts
exactly the fields shown above. Authentication defaults to `none`, timeouts to
5s/60s, response size to 1MB (1,048,576 bytes), and ordinary headers to empty.
Timeouts are duration strings of 1 through 2,147,483,647 milliseconds; sizes are
size strings of 1 through 2,147,483,647 bytes. Limits cannot be overridden by a
skill. Read timeout measures socket inactivity, not a total execution deadline.
Unknown fields, malformed shapes and invalid settings fail without network calls.
`HttpExecutorConfiguration.validate` and `validateOperation` are reusable for
startup/management consumers; invocation calls those same validators.

## Request mapping

Operation keys are `target`, `method`, `path`, optional `pathParams`, `queryParams`,
`body` and `recoverableStatuses`. Target/method/path are required. Methods are
uppercase GET, POST, PUT, PATCH and DELETE. GET rejects a body declaration. Other
methods send no body when `body` is omitted. `body: {source: input}` serializes the
complete input map, including path/query fields. `body: {source: field, field:
/order/changes}` serializes the selected object, array, scalar or explicit null.
A missing selected body fails; JSON null is different from an absent body.

Every reference is a nonempty JSON Pointer starting with `/`. Standard `~0`/`~1`
escaping, nesting and array indices work. There is no shorthand, expression or
wildcard expansion. A property literally named `*` remains an ordinary property.

Path placeholders occupy complete segments; every placeholder has a mapping and
every mapping is used. Repeated placeholders may reuse the same mapping. Selected
values must be nonempty strings, numbers or booleans. Missing/null/object/array
values, separators, dot/dot-dot and recursively encoded separator/traversal forms
fail before dispatch. Spaces, Unicode and punctuation are encoded from original
values. Do not pre-encode input values. Static skill segments are authored text
and encoded as segments; base URL path escapes are preserved after validation.

The target owns scheme, host, port and optional base prefix. Skill paths begin
with one slash and append to that prefix with one boundary slash. Absolute URLs,
authority replacement, traversal, queries and fragments are prohibited in paths;
base URLs also reject user-info, queries and fragments.

Query mappings omit missing/null/out-of-range values and empty arrays. Empty
strings produce empty values, and zero/false are preserved. Scalar arrays produce
repeated names in array order. Objects, nested arrays and null array elements fail.
Required input belongs in the skill's Framework input schema.

## Headers and credentials

Target headers are fixed ordinary metadata. Names use HTTP token syntax, names
cannot conflict case-insensitively, and values cannot contain invalid controls.
Host, Content-Length and Transfer-Encoding belong to transport. Authorization,
Proxy-Authorization and Cookie cannot be ordinary headers; cookies are not an
authentication mode. JSON requests default to `Content-Type: application/json`.
An explicit body Content-Type must be application/json or application/*+json
using UTF-8. Accept and other ordinary metadata may be set explicitly.

The three authentication modes are:

- `none`: sends no authentication credential.
- `fixed`: `auth: {mode: fixed, header-refs: {Authorization: serviceToken}}` uses
  a captured encrypted credential for each service header. Semantically secret
  custom headers belong here. It cannot supply framing, cookie or payload metadata.
- `caller-passthrough`: sends the original captured Spring Security JWT as a bearer
  token, checking strict expiry immediately before dispatch. Missing, unauthenticated
  or expired JWTs fail. There is no refresh, exchange, fallback or input identity.

For fixed auth, `credentials.serviceToken` contains `version` and `ciphertext`
from an existing `EncryptedCredential`, not plaintext or a Framework provider
reference. `HttpExecutorConfiguration.withCredentials(selectedMap, records)`
attaches retained encrypted records before preparation. Invocation decrypts them
using `ProviderCredentialCipher` and the external `LOOMSPAN_SIDECAR_CREDENTIAL_KEY`,
in either configuration mode. Missing references/key and invalid ciphertext fail.
This helper does not provision or persist credentials; current startup/management
does not yet attach them. The external key never belongs in an export or payload.

Targets must be approved by the configuration's authorized publisher. The
destination independently validates JWT audience, trust, roles and record access.
Fixed credentials identify the service; they do not establish a user's authority.
Infrastructure credentials stay out of generated errors and diagnostics.
Application request/response content is never redacted or rewritten.

## Transport, results and failure

One application-owned Apache pool applies captured target limits and headers per
request. There are no retries, redirects or cookie storage/replay. A timeout or
lost response does not prove that a remote action had no effect. HTTP and HTTPS
use normal JVM trust and hostname validation. There is no executor SSL bundle,
client-certificate setting or validation bypass. Caddy owns mTLS; standard Boot
inbound `server.ssl.*` and `spring.ssl.bundle.*` facilities are independent.

All 2xx statuses return unchanged decoded text; empty bodies return `""` regardless
of media type. JSON (`application/json` and `application/*+json`) requires UTF-8.
Text uses its declared charset or UTF-8. Missing Content-Type uses UTF-8 without
sniffing. Other media types, unsupported charsets, invalid bytes and non-UTF-8
JSON fail. There is no trimming, newline normalization, JSON parsing or envelope.

Success and error bodies are bounded, including decompressed content. Exceeding
the limit fails without a truncated result or draining an unbounded remainder.
Transport, configuration, credential, timeout, size and decoding failures are
terminal `SkillException`s. HTTP errors use `HTTP <status>` followed by a newline
and the complete unchanged decoded body when nonempty. Empty bodies use status only.
Only explicitly listed 4xx statuses other than 401/403 produce
`RecoverableSkillException`, after size/decoding checks. Omission means none.
Redirects, 5xx, 401 and 403 remain terminal. Recoverable feedback is available to
standard model tool calling; direct calls propagate failures and step-plan tool
failure terminates execution. There is no response-envelope convention.

Framework owns captured generations and the shutdown budget. The client stays
alive while admitted work drains or reaches cutoff, then closes immediately
without a second drain period or generation-specific client resources.
