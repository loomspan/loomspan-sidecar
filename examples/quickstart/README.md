# Local evaluation and verifier fixtures

Follow the [application quickstart](../../docs/quickstart.md) to publish and invoke
[triageSupportRequest](triage-support-request.yaml). The fixture host provides a
demo ephemeral signer, deterministic planner and approved callbacks. It exports
only public JWKS atomically; private keys live in temporary container storage.
Restarting the host generates a new signing key and invalidates old tokens once
receivers reload. These fixtures are not a deployed signing product.

The deployment must prepare the named public-key volume before starting:

```sh
docker volume create loomspan-quickstart-keys
docker run --rm --user 0:0 -v loomspan-quickstart-keys:/keys --entrypoint sh loomspan-sidecar:sc5-local -c 'chown 10001:10001 /keys && chmod 700 /keys'
```

These commands also work from PowerShell. Set `SIDECAR_KEYS_VOLUME` consistently
if using a different volume name. App and Sidecar run as `10001:10001`; the app
mounts `/keys` writable and Sidecar mounts `/sidecar/keys` read-only. Sidecar, including health, binds loopback. Host and Caddy share its namespace via
`network_mode: "service:sidecar"`; the Sidecar service publishes host-loopback
ports to ingress 18080 (API, management and health) and 18081 (fixture host),
which proxy to loopback 8080 and 8081. No proxy rewrites execution credentials.

Host and ingress depend on Sidecar being started, never healthy: readiness is
waiting until the first valid export. Recreate all three together when replacing
Sidecar so namespace references remain current. SQLite persists independently.
The initially empty database does not automatically publish the deterministic
skills/routes under `sidecar/`; the verifier publishes them through management.
Their routes use literal `http://127.0.0.1:8081`.

See [trust operations](../../docs/operations.md#execution-jwt-trust) for file
validation, withdrawal/recovery and lifetime behavior. No database reset is
required by this configuration replacement. Use an application-owned signer
and independently authorized callbacks for deployment.
