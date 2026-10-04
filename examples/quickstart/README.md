# Local evaluation and verifier fixtures

Follow the [application quickstart](../../docs/quickstart.md) to publish and invoke
[triageSupportRequest](triage-support-request.yaml). That walkthrough uses a real
model provider and the fixture host only for local JWT issuance.

The Compose stack starts a persistent, initially empty Sidecar database. It does
not mount or publish the skill and route files under `sidecar/`. The host also
contains a deterministic planner and identity/detail callbacks used by repository
verification; these are protocol fixtures, not realistic application reasoning.

The legacy `sidecar/rest-routes.yaml` fixture contains a defaulted
`${QUICKSTART_HOST_URL:http://host:8081}` expression. Do not paste that expression
into a Sidecar draft: current base URLs allow literals or exact allowlisted
`${NAME}` references. The production verifier substitutes `${TARGET_URL}` before
validation. This audit leaves that test input unchanged to avoid changing verifier
behavior. For your own routes, follow the
[REST contract](../../agent-skills/loomspan-sidecar-authoring/references/integration.md#rest-skill-routes).

The checked-in signing key, token endpoint and callbacks are local test assets.
Use your application's identity provider and independently authorized endpoints
for deployment. Maintainer verification procedures are parked for review in
[ai/throughts](../../ai/throughts/readme-maintainer-material.md).
