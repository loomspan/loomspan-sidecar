# Run your first Sidecar skill

Publish and run `default:quickstart-planner`. The example uses a local test JWT
issuer, a deterministic model response and two HTTP callbacks. Both callbacks
verify the caller's credential and record the input they received. The walkthrough
runs on loopback and needs no provider account.

## Prepare the local environment

You need Java 21, Docker with Compose and Python 3.9+. Follow the
[source build setup](setup.md#obtain-a-runtime) to build `loomspan-sidecar:sc5-local`.
Run commands below from the repository root. The default loopback ports 8080 and
8081 must be free. Prepare the shared public-key volume using the
[fixture instructions](../examples/quickstart/README.md) before starting Compose.

For a fresh evaluation database, set the image and generate the credential
encryption key.

```powershell
$env:SIDECAR_IMAGE = 'loomspan-sidecar:sc5-local'
$env:LOOMSPAN_SIDECAR_CREDENTIAL_KEY = python -c "import base64,secrets; print(base64.b64encode(secrets.token_bytes(32)).decode())"
```

```sh
export SIDECAR_IMAGE=loomspan-sidecar:sc5-local
export LOOMSPAN_SIDECAR_CREDENTIAL_KEY="$(python3 -c 'import base64,secrets; print(base64.b64encode(secrets.token_bytes(32)).decode())')"
```

Store this key securely. To reuse an existing evaluation database, use its
original encryption key and review its current publication before editing.
The Compose volume persists across `down` and `up`.

Follow [local administrator setup](admin-access.md#local-quickstart-loopback-http)
in the same shell. Generate the setup credential, start Compose, create the
administrator, sign in, and remove the setup credential by recreating Sidecar.
Keep `LOOMSPAN_SIDECAR_CREDENTIAL_KEY` set during that recreation.

```sh
docker compose -f examples/quickstart/compose.yaml ps
```

Sidecar and the fixture host should become healthy. The
[management sign-in](http://localhost:8080/management/login) is on port 8080.
The fixture host on port 8081 issues test JWTs and serves model responses.

## Publish the planner and HTTP leaves

1. Open **Edit configuration** and choose **Start editing or resume draft**.
2. Use **Add skill document** for each file. Use the filename as its source
   label and paste the complete YAML.

   - [planner.yaml](../examples/quickstart/sidecar/skills/planner.yaml)
   - [identity-leaf.yaml](../examples/quickstart/sidecar/skills/identity-leaf.yaml)
   - [detail-leaf.yaml](../examples/quickstart/sidecar/skills/detail-leaf.yaml)

3. Set **Framework execution settings YAML** to the complete contents of
   [execution.yaml](../examples/quickstart/sidecar/execution.yaml).
   The publisher authorizes the `identity-host` and `detail-host` loopback
   targets to receive the caller's execution credential. The local model
   connection uses a synthetic API key.
4. Wait for the draft to save, then choose **Retry validation**. When validation
   succeeds, choose **Publish validated draft**. If the lease expires, reacquire
   it and validate the saved revision again.
5. Confirm **Current configuration** shows the planner and both leaves.

Publication activates the complete saved configuration in `default`. See the
[editing contract](../agent-skills/loomspan-sidecar-authoring/references/management-api.md)
for conflicts, handoff and ambiguous outcomes.

## Invoke and poll

```sh
echo '{"message":"Check the local callbacks"}' | python examples/quickstart/invoke.py default quickstart-planner
```

Use `python3` if that is the Python 3 command on your system. The
[script](../examples/quickstart/invoke.py) gets a fixture token, lists the
`default` namespace and uses the returned namespace and local name for detail
and submission. It sends the UTF-8 JSON object as business input and polls the
returned execution ID for up to three minutes. A polling timeout leaves the
execution running. Inspect the accepted ID before submitting again.

The final result is `quickstart complete`. Open
[fixture status](http://localhost:8081/status) and look for two callbacks verified
as `quickstart-user` with role `QUICKSTART_USER`. The planner sends message
`verify` to `/callbacks/identity`, then `detail` to `/callbacks/detail`.
Both leaves use the HTTP executor.

For manual calls or another language, see the
[execution API reference](../agent-skills/loomspan-sidecar-authoring/references/integration.md#execution-api).
Production callers obtain tokens from their own trusted issuer. Restarting the
fixture replaces its ephemeral key and previous tokens stop validating as
Sidecar adopts the replacement.

## Optional real-provider triage

To turn the [README's support report](../README.md#from-customer-report-to-engineering-triage)
into an engineering brief, use a separate evaluation or add the skill and model
settings to the current draft. This example sends the report to OpenAI and
incurs normal provider usage. Use synthetic data.

1. Add [triage-support-request.yaml](../examples/quickstart/triage-support-request.yaml)
   as a skill document. Use `triage-support-request.yaml` as its source label.
2. Configure the `assistant` model and an OpenAI connection under **Framework
   execution settings YAML**. Add these entries under `loomspan`, preserving
   the rest of the draft's execution settings.

   ```yaml
   loomspan:
     connections:
       primary:
         driver: openai
         api-key: { $var: provider.primary.key }
     models:
       assistant:
         connection: primary
         provider-model: YOUR_PROVIDER_MODEL_ID
   ```

3. Under **Managed variables**, enter `provider.primary.key` as **Identifier**,
   paste the provider key as **New value**, and choose **Save or replace credential**.
   Validate and publish the saved revision.
4. Run the same client with the triage skill's business input.

   ```sh
   echo '{"customerMessage":"Since this morning\u0027s deployment, checkout times out after payment. Three customers were charged without an order confirmation. Retrying sometimes creates two orders."}' | python examples/quickstart/invoke.py default triage-support-request
   ```

   The response assesses the reported impact and suggests investigation steps.

## Troubleshoot and stop

- **Unhealthy service**. Inspect `docker compose -f examples/quickstart/compose.yaml logs sidecar`.
  Check ports, database permissions and the original encryption key.
- **Skill missing**. Check **Current configuration** for the requested identity
  and confirm the draft was published.
- **401**. Obtain a fresh fixture token and keep the same issuer and subject when polling.
- **Model or callback failure**. Check the terminal snapshot and use
  [Console setup](console-setup.md) for execution evidence.
  For the OpenAI flow, check the configured model ID, saved credential and provider response.

Stop the evaluation while preserving its database.

```sh
docker compose -f examples/quickstart/compose.yaml down
```

Keep the encryption key for the next startup. To add application REST calls,
use the [authoring skill](../agent-skills/loomspan-sidecar-authoring/SKILL.md).
Before enabling remote access, follow the [production setup](../examples/production/README.md).
