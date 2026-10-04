# First Sidecar application skill

Turn a customer support report into an engineering triage brief using the same
`triageSupportRequest` skill shown in the [README](../README.md). This walkthrough
uses database mode, a local test JWT issuer, and a **real OpenAI model connection**.
It sends the example report to your provider and incurs normal provider usage.
Use synthetic data while evaluating it.

## Prepare the local environment

You need Java 21, Docker with Compose, Python 3.9+, and an OpenAI API key with
access to a model of your choice. Follow [source build setup](setup.md#obtain-a-runtime)
to build `loomspan-sidecar:sc5-local`. Run commands below from the repository root.
The default loopback ports 8080, 8081 and 9091 must be free.

Use a new evaluation database, or inspect existing content before editing it.
The Compose volume persists across `down` and `up`; these instructions do not
reset an existing installation. If this volume already has encrypted credentials,
reuse its original encryption key instead of generating a new one.

For a **new** evaluation, generate the credential encryption key in your shell:

```powershell
$env:SIDECAR_IMAGE = 'loomspan-sidecar:sc5-local'
$env:LOOMSPAN_SIDECAR_CREDENTIAL_KEY = python -c "import base64,secrets; print(base64.b64encode(secrets.token_bytes(32)).decode())"
```

POSIX shell:

```sh
export SIDECAR_IMAGE=loomspan-sidecar:sc5-local
export LOOMSPAN_SIDECAR_CREDENTIAL_KEY="$(python3 -c 'import base64,secrets; print(base64.b64encode(secrets.token_bytes(32)).decode())')"
```

Preserve this key using your protected secret-handling mechanism for subsequent
container recreation. Losing it prevents recovery of saved provider credentials.
It is separate from your provider API key and administrator setup credential.

Follow [local administrator setup](admin-access.md#local-quickstart-loopback-http)
in this same shell: generate the setup credential, start Compose, create the
administrator, sign in, and remove the setup credential by recreating Sidecar.
Keep `LOOMSPAN_SIDECAR_CREDENTIAL_KEY` set during that recreation. If Docker has
not built the fixture host yet, Compose builds it on first startup.

Check readiness before continuing:

```sh
docker compose -f examples/quickstart/compose.yaml ps
```

Both services should become healthy. The browser management UI is at
[localhost:8080/management/login](http://localhost:8080/management/login).
The host on 8081 issues local fixture JWTs. Although it also implements a
deterministic model stub for repository tests, this walkthrough does not use that
stub or the planner/leaf fixtures under `sidecar/`. Those files are not loaded
automatically. See the [fixture notes](../examples/quickstart/README.md).

## Author and publish the skill

1. Open **Edit configuration** and choose **Start editing or resume draft**.
   These steps assume a fresh empty configuration. If a saved draft exists,
   inspect it first; retain any content you need.
2. Choose **Add skill document**, use `triage-support-request.yaml` as the source
   label, and paste the complete contents of
   [triage-support-request.yaml](../examples/quickstart/triage-support-request.yaml).
3. Set **REST targets and routes YAML** to the following (the first skill has no
   application callbacks):

   ```yaml
   targets: {}
   routes: {}
   ```

4. Set **Framework execution settings YAML** to the following. Replace
   `YOUR_PROVIDER_MODEL_ID` with an OpenAI model ID available to your account;
   `assistant` is the Loomspan alias used by the skill.

   ```yaml
   loomspan:
     connections:
       primary:
         driver: openai
         api-key-ref: provider.primary.key
     models:
       assistant:
         connection: primary
         provider-model: YOUR_PROVIDER_MODEL_ID
   ```

5. Wait until the draft is saved. Under **Managed provider credentials**, enter
   `provider.primary.key` as **Identifier**, paste your provider key into **New
   value**, and choose **Save or replace credential**. Do not place the key in
   the YAML, skill input, or an assistant prompt.
6. Wait for the saved revision and its automatic validation. Resolve any errors
   (use **Retry validation** if needed), then choose **Publish validated draft**.
   If the lease expires, reacquire editing and wait for fresh validation.
   Confirm **Current configuration** contains `triageSupportRequest`.

Saving and validation do not activate or execute a skill. Validation checks the
configuration without contacting the model; invocation below checks provider
connectivity. Publication activates the complete saved configuration. See the
[editing contract](../agent-skills/loomspan-sidecar-authoring/references/management-api.md)
for conflicts, handoff, and ambiguous outcomes.

## Invoke and poll

Run the standard-library HTTP example:

```sh
python examples/quickstart/invoke-triage.py
```

Use `python3` on systems where that is the Python 3 command. The
[script](../examples/quickstart/invoke-triage.py) obtains a short-lived test token,
posts the README's support report, follows the returned location, and polls for
up to three minutes. It prints the completed result or reports the execution
failure. It never resubmits an execution automatically. A polling timeout does
not cancel work; use the printed execution ID to inspect the accepted request.

A useful response identifies reported charges without confirmations and duplicate
orders as customer impact, treats the deployment as a possible cause, and suggests
correlating payment/order records and examining retry behavior. Wording and
accuracy vary by model. The skill has not queried those records or changed orders.

For manual calls or an application in another language, use the
[execution API reference](../agent-skills/loomspan-sidecar-authoring/references/integration.md#execution-api).
Production callers obtain tokens from their own trusted issuer; the fixture token
endpoint and checked-in signing key are for local testing only.

## Troubleshoot and stop

- Unhealthy service: inspect `docker compose -f examples/quickstart/compose.yaml logs sidecar`.
  Check ports, database permissions, and the original encryption key.
- Empty catalog or unknown skill: confirm successful publication, not just a saved draft.
- Provider failure: check the configured model ID, saved credential, network access,
  and provider response. Configuration validation does not prove provider access.
- `401`: obtain a fresh fixture token; keep the same issuer and subject when polling.
- For model behavior and execution evidence, follow [Console setup](console-setup.md).

Stop without deleting the database:

```sh
docker compose -f examples/quickstart/compose.yaml down
```

Preserve the encryption key for the next startup. Do not use `down -v` unless
deliberately discarding this evaluation installation. To develop further, add
authorized order or payment REST lookups with the [authoring skill](../agent-skills/loomspan-sidecar-authoring/SKILL.md),
and use [production setup](../examples/production/README.md) before remote access.
