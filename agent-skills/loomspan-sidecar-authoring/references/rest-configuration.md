# HTTP skill executor

The `http` executor implements application HTTP operations. YAML skills use the
Framework's executor contract; Sidecar validates HTTP configuration and actual
mapped input without duplicating Framework schema or role validation.

File mode loads skill YAML and `loomspan.executor-config` from startup files and
applies changes through restart. Database mode publishes and restores complete
skill/execution configuration through the management API and browser editor.
HTTP domain validation occurs only on invocation, before dispatch. Readiness and
management validation/publication do not establish HTTP validity or reachability.
Framework declaration/reference checks and management security/integrity checks
still apply at their normal boundaries.

```yaml
name: updateOrder
description: Update an authorized order
executor: http
config:
  target: orders
  method: PATCH
  path: { $var: orders.update-path }
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
            headers:
              Accept: application/json
              X-Region: { $var: app.region }
              Authorization: { $var: orders.authorization }
            auth: {mode: fixed}
            connect-timeout: 5s
            read-timeout: 60s
            max-response-size: 1MB
```

In file mode, Framework resolves whole-node `{ $var: name }` references from
the host's Spring properties at startup. For example, set `orders.update-path`
to `/orders/{id}` and `app.region` to `west`. Set `orders.authorization`
to the complete value the service expects, including its scheme and prefix.
Another installation can use the same documents with different property values.
In database mode, supply the same names through the managed variable controls in
the draft. Publish the authored settings, skill, and values together. Fixed headers
and the route also accept literals. The authored YAML appears in authorized reads
and configuration-only exports. Separately supplied values are write-only.

At invocation, `pathParams` fills `{id}` from the skill input.

The settings map accepts `targets`. A target accepts exactly the fields shown
above. Authentication defaults to `none`, timeouts to
5s/60s, response size to 1MB (1,048,576 bytes), and ordinary headers to empty.
Timeouts are duration strings of 1 through 2,147,483,647 milliseconds; sizes are
size strings of 1 through 2,147,483,647 bytes. Limits cannot be overridden by a
skill. Read timeout measures socket inactivity, not a total execution deadline.
Unknown fields, malformed shapes and invalid settings fail without network calls.
The executor validates captured target settings and operation mappings on each
invocation; management and startup do not run these domain validators.

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

## Headers and authentication

Target headers use captured settings. Names use HTTP token syntax and must be
unique case-insensitively. Values cannot contain invalid controls. Host,
Content-Length and Transfer-Encoding belong to transport. Proxy-Authorization
and Cookie are prohibited. JSON requests default to `Content-Type: application/json`.
An explicit body Content-Type must be application/json or application/*+json
using UTF-8. Accept and other ordinary metadata may be set explicitly.

The three authentication modes are:

- `none` sends no `Authorization` and rejects that header in the target.
- `fixed` sends any configured `Authorization` as supplied, including its
  scheme and prefix. The header is optional. A target can configure a custom
  header such as `X-API-Key` with an ordinary header value.
- `caller-passthrough`: sends the original captured Spring Security JWT as a bearer
  token, checking strict expiry immediately before dispatch. The target cannot
  declare `Authorization`. Missing, unauthenticated or expired JWTs fail. The
  JWT belongs to the request and stays outside installation settings.

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
