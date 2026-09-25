# Record endpoint walkthrough fixture

The example manifest and route describe `GET /records/{recordId}`. The local integration fixture uses a loopback endpoint and replaces `${RECORDS_BASE_URL}` with its runtime port. In a real application, add this endpoint to the application's repository and tests; it must verify the passed execution JWT itself and reject a record whose owner differs from the token subject. Sidecar validates the route but does not test endpoint connectivity or data authorization.

The local Java walkthrough test exercises these same YAML assets with a controlled callback. It intentionally saves a malformed `rest: false` candidate first, reads structured validation issues, repairs the saved candidate, and then publishes and executes. The application callback is a test fixture, not an API for accessing application source.

The management token is used only to author. Execution uses an issuer-signed JWT with `RECORD_READER`; the target verifies that JWT and record ownership independently. A foreign record must yield a failed execution and a callback denial. The bundled [integration guide](../../references/integration.md) describes URL variables, execution polling, and optional bundle transfer.

Use [record.yaml](record.yaml) and [rest-routes.yaml](rest-routes.yaml) together.
