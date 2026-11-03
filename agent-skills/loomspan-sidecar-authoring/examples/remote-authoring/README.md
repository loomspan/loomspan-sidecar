# Record endpoint walkthrough fixture

The example skill and execution configuration describe `GET /records/{recordId}`. The local integration fixture replaces `https://records.example.test` with its loopback endpoint before submission. In a real application, supply the destination's explicit base URL and add this endpoint to the application's repository and tests; it must verify the passed execution JWT itself and reject a record whose owner differs from the token subject. Management validates Framework contracts, without checking HTTP settings, endpoint connectivity or data authorization. The executor checks captured HTTP settings on invocation before dispatch.

The local Java walkthrough test exercises these same YAML assets with a controlled callback. It intentionally saves an unknown executor reference candidate first, reads structured validation issues, repairs the saved candidate, and then publishes and executes. The application callback is a test fixture, not an API for accessing application source.

The management token is used only to author. Execution uses an issuer-signed JWT with `RECORD_READER`; the target verifies that JWT and record ownership independently. A foreign record must yield a failed execution and a callback denial. The bundled [integration guide](../../references/integration.md) describes HTTP target configuration, execution polling, and optional bundle transfer.

Use [record.yaml](record.yaml) and [execution.yaml](execution.yaml) together.
The skill owns its HTTP operation; execution YAML supplies shared targets.
HTTP-specific validation happens at invocation before dispatch.
