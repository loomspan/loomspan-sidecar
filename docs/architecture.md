# Architecture and tradeoffs

Sidecar runs the Loomspan Framework behind an authenticated HTTP service. Your
application owns user authentication, business data, and the REST operations it
exposes. Sidecar owns HTTP admission, polling records, outbound REST routing, and
configuration management. Framework owns skill validation, model execution,
planning, authorization checks, and execution lifetime.

```text
Application -- execution JWT + JSON --> Sidecar --> model provider
Application <-- ID, then polled result -- Sidecar --> application REST endpoint
Developer/operator -- management UI or API --> configuration drafts/publication
Assistant -- Console MCP --> Loomspan Console --> read-only observability API
```

## Choose the boundary

Use Sidecar for a language-neutral service boundary and independently managed
skills. Use [embedded Framework](https://github.com/loomspan/loomspan-framework)
for direct Java service integration. Sidecar hosts model-backed YAML and REST
skills, not Java application methods. HTTP adds authentication, network failure,
deployment, and polling responsibilities that in-process callers do not have.

Loomspan uses hierarchical task planning: a model-backed parent works within its
declared child capabilities. Start with one skill when that is enough; add a tree
when the task needs decomposition and application operations. See the matching
[Framework mental model](https://github.com/loomspan/loomspan-framework/blob/v1.0.0-beta.7/agent-skills/loomspan-docs/references/skill-authoring/mental-model.md).
Validation constrains accepted inputs and outputs, but cannot prove a model's
factual correctness or undo external effects.

## State and failure tradeoffs

SQLite persists accounts, configuration history, and private drafts. File mode
still uses local storage for management, while deployment files own runtime
configuration. Database mode publishes complete validated snapshots without
restart. See [configuration choices](setup.md#choose-configuration-authority).

The supported deployment is one instance per local SQLite volume with reliable
locking. It is not a shared-database replica architecture. Execution records are
in memory, bounded by count and retention time; results and diagnostics can still
consume substantial memory. A restart loses execution records. An HTTP disconnect
does not cancel accepted work, and losing a result does not establish that an
external action never occurred. Design business operations to tolerate ambiguous
outcomes before adding retries. See the [execution contract](../agent-skills/loomspan-sidecar-authoring/references/integration.md#execution-api).

Publication preserves the captured configuration for already-admitted work.
Shutdown discards waiting work and lets Framework govern admitted work through
its shutdown budget. Polling history is available diagnostics, not a durable job
queue or a guaranteed complete trace. See [capacity and diagnostics](../agent-skills/loomspan-sidecar-authoring/references/integration.md#capacity-retention-and-diagnostics).

## Trust boundaries

Execution JWTs, local management credentials, and observability API keys have
different purposes. Management tokens cannot execute skills; execution JWTs cannot
publish configuration. Console MCP uses another credential to reach the local
Console. [Setup](setup.md) explains how these fit together.

A REST endpoint must authenticate the request and authorize each requested
record independently. A model's input is not trusted identity. Caller passthrough
forwards the original execution JWT without refreshing it; queue and model time
can leave it expired when the callback begins. Static credentials require an
application-specific authorization design. See [REST authoring](../agent-skills/loomspan-sidecar-authoring/references/rest-configuration.md).

Sidecar's built-in management UI edits configuration. The separate **Loomspan
Console** investigates runtime behavior and exposes read-only MCP tools. Neither
installing an Agent Skill nor connecting Console publishes application skills.
