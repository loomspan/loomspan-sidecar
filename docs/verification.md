# Local verification

Run the repository checks against the installed Framework snapshot:

```powershell
./mvnw.cmd -B -ntp verify
python -m unittest discover -s scripts -p 'test_*.py' -v
python -m unittest discover -s agent-skills/loomspan-sidecar-authoring/client -p 'test_*.py' -v
```

The Java suite uses temporary SQLite stores, loopback model/application servers,
JUnit, ArchUnit and Playwright Chromium. It covers management authorization,
ownership/revision/reconciliation, file/database modes, snapshot recovery and
portable bundles, HTTP request/response behavior, captured admission, execution
trust and shutdown. Browser checks require Playwright Chromium installed locally.
The architecture tests enforce use of the closed supported Framework API in both
application and test code.

HTTP configuration validation occurs on invocation before dispatch. Tests must
separately prove Framework validation and management integrity/security checks,
and acceptance of HTTP-invalid but Framework-valid startup/publication/restoration.
Successful readiness or publication does not certify HTTP validity or reachability.

Deployment checks require an explicitly configured non-production environment.
Caddy owns mTLS; direct executor HTTPS uses normal JVM trust/hostname verification.
Do not use live model providers or production targets as routine local tests.
