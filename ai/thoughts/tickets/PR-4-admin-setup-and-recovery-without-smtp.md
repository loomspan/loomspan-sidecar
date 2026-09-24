# Set up and recover administrator access without SMTP

## Outcome

An operator can generate an installation-specific setup credential, create the
first Sidecar administrator, and recover administrator access without an SMTP
server. A README-linked guide provides a complete, copyable path from a built
image to console sign-in for local development and the Caddy deployment.
There is no shared default username/password or dependency on Python to generate
the setup credential.

## Requirements

### Initial administrator setup

- Retain `LOOMSPAN_SIDECAR_SETUP_TOKEN` as the deployment-controlled, one-time
  setup credential. It is not the administrator's login password. Missing,
  malformed, or incorrect credentials must not authorize setup.
- At `/management/setup`, accept the setup credential, administrator email,
  chosen password, and password confirmation. Retain email as the login
  username; entering it does not prove mailbox ownership.
- Create and activate the administrator and permanently close initial setup
  atomically, using the existing password policy and hashing. No email delivery
  is required. Preserve CSRF protection and attempt limits. Failed submissions
  must not leave a newly reserved account; concurrent requests must not create
  multiple initial administrators.
- Completion survives restart and remains closed even if the environment token
  remains configured. Document removing that secret after successful setup.
- Preserve existing activated accounts. Any transition for an existing pending
  initial administrator must retain its reserved identity and require the valid
  setup credential; it must not allow takeover by a different address or reopen
  completed setup. Planning should choose the simplest safe transition under
  the repository's development compatibility policy.

### Streamlined credential generation

- Provide a bundled operator command to generate the existing required format:
  unpadded base64url encoding of 32 cryptographically random bytes. Each
  invocation generates a fresh credential; no database, SMTP, model settings,
  or running server is required.
- Support capturing successful output directly into the setup environment
  variable. Keep stdout limited to the generated value; errors must fail with
  a nonzero exit status and must not contaminate captured credential output.
- Do not automatically generate and disclose setup secrets through routine
  server startup logs. Document deliberate terminal output and secret handling.

### Local operator recovery

- Provide a local host/container operator command that issues an expiring,
  single-use password-reset credential for an existing enabled administrator
  with an established password, without SMTP or an authenticated console session.
  Host/container and database access are the authority; do not introduce a
  publicly callable credential-issuance endpoint.
- Store only a digest of the reset credential, supersede older outstanding
  credentials for that account, and enforce expiry and single use atomically.
  Reuse the existing 30-minute reset lifetime unless research establishes a
  concrete reason to propose a change.
- Deliver the raw credential deliberately to the operator, excluding it from
  routine logs, exception messages, and audit records. The documented redemption
  flow must let the user enter the credential and choose their own password
  without requiring SMTP or placing the credential in a URL.
- On successful reset, invalidate existing management sessions and clear the
  account's editing state and outstanding reset credentials. Issuing a reset
  must not itself replace the current password.
- Recovery must not create accounts, enable disabled accounts, change roles,
  reopen initial setup, or initialize an unrelated empty database. Reject an
  absent or incompatible store and invalid account targets clearly.
- Protect database consistency when maintenance runs. Do not start normal HTTP,
  model, or skill execution services merely to generate or issue credentials.

### Deployment and documentation

- Make SMTP optional for first setup and operator recovery, including production
  Compose configuration. Preserve existing email invitations and email recovery
  when SMTP is configured. Explain their unavailability when it is not.
- Put the operator walkthrough in the README or a directly linked document;
  keep quickstart, operations, and production instructions consistent. Include
  token generation and capture, startup, setup URL and fields, sign-in, removal
  of the setup secret, and recovery issuance/redemption with persistent storage.
- Clearly distinguish the local quickstart's direct loopback HTTP console from
  the production Caddy HTTPS console. Explain username versus setup credential
  versus login password, required host/container privileges, any recovery
  downtime, expiration, and retry behavior. Include PowerShell commands and the
  corresponding supported POSIX workflow. No placeholder default password.
- Keep implementation and verification in Sidecar and obey the supported
  framework boundary; use the locally installed beta 5 snapshot as prescribed
  by repository policy.

## Acceptance criteria

- [ ] From a built image, documented credential generation works without Python,
  database, SMTP, model configuration, or a running server, produces valid fresh
  values suitable for shell capture, and reports errors without secret leakage.
- [ ] A fresh local installation with no SMTP can create one administrator at
  the setup page and sign in using the supplied email and chosen password.
  Wrong/missing credentials, invalid passwords, confirmation mismatches, and
  concurrent submissions cannot bypass validation or leave partial new setup.
- [ ] Setup remains closed after restart and with the token still present;
  activated accounts remain usable, and any pending-setup transition preserves
  the reserved identity without opening a takeover path.
- [ ] A documented local recovery command operates on the intended persistent
  store without SMTP, refuses invalid stores/targets, protects against unsafe
  concurrent maintenance, and starts no normal application services.
- [ ] Recovery credentials expire, cannot be replayed, supersede earlier
  credentials, and can be redeemed without a token-bearing URL. Issuance leaves
  the password unchanged; redemption changes it, invalidates prior sessions and
  tokens, and clears editing state without changing roles or bootstrap state.
- [ ] SMTP-free Compose setup succeeds. Existing email invitation and recovery
  flows still work with SMTP, and the console explains unavailable email actions.
- [ ] README-linked instructions cover the complete first-run and recovery
  workflows, distinguish local HTTP from Caddy HTTPS, and explain all three
  credential concepts, storage, secret removal, and operational prerequisites.
- [ ] Sidecar-owned regression and integration evidence verifies these behaviors,
  including the packaged command/container workflow and security failure cases.

## Context

External work item: **GitHub PR 4**, as supplied by the developer. No URL was
provided.

The current local quickstart starts a JWT execution API but leaves console setup
dependent on SMTP. The resulting confusion about console URLs and default
credentials exposed a first-run usability gap. Shared defaults such as
`admin/admin` are deliberately excluded.

Source hints, not prescribed implementation: management identity, account-token,
and session-guard code already contains password hashing, token redemption,
credential-version checks, and editing-state cleanup. Reuse those capabilities
where they fit instead of creating a second authentication system.

Suggested command names are `admin generate-setup-token` and
`admin issue-password-reset --email ...`; planning may choose consistent final
syntax. An offline stop / maintenance container / restart recovery workflow is
the recommended simple implementation, with enforced exclusion of simultaneous
server access. Exact command dispatch and locking are planning decisions.
A wrapper quickstart script is an optional convenience, not a requirement;
automatic secret cleanup is not required.

SMTP-free invitations for additional users, general account administration from
the CLI, SSO, and changes to JWT execution authentication are out of scope.
This ticket does not require publishing or changing framework dependencies.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The work changes administrator bootstrap and recovery security,
  persisted account transitions, management API behavior, command lifecycle,
  and deployment configuration. Research, implementation and test planning,
  and independent review are warranted.
- **Reassessment triggers:** Retain Full while these security and persistence
  changes remain in scope; only a developer-directed reduction to documentation
  of existing behavior would justify considering a lighter profile.
