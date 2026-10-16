# PR #8.0.1 — Settle SDK v1 identity and callback security contracts

## Development constraints

**Sidecar is still in development. Changes must destructively replace superseded
behavior; no compatibility shims, legacy adapters, parallel legacy APIs, or
migration machinery solely to preserve obsolete development contracts.**

Apply the [design lens](../design-lens.md). Choose the simplest solution that
fully meets the requirements and minimize technical debt. Keep implementation,
tests and documentation proportional. Document required development-data resets
and their impact; this requirement does not authorize deleting deployed data.

## Outcome

Produce a concrete reference contract for SDK-managed user credentials and
callback authentication so subsequent implementation can preserve customer login,
business authorization and issuer-aware execution ownership without inventing
security behavior. This is Phase 0 design work, not product implementation or
evidence that the planned integrations already work.

## Requirements

- Define explicit configuration and equivalent Java initialization for Relay,
  Exchange, Spring-issued and Custom identity. Customers must not implement JWT
  construction, signing, attachment or callback authentication filters. Spring-issued
  and Custom identity share issuance; trusted custom sending/receiving adapters are
  concrete contracts, not merely illustrative bean names. Never switch strategies
  after failure, replace customer login, or substitute service identity.
- Select a named locally runnable authorization server and version, registration,
  exchange flow and resulting token profile. Specify supported inputs, failure
  behavior and client authentication without implying all OAuth providers support
  exchange. Relay selects a compatible access token, never a login ID token.
- Specify signature, allowed algorithms, time and required claims, issuer, audience
  and purpose independently at Sidecar admission and callbacks. Define reliable
  access-token purpose rules per supported profile, including overlapping ID-token
  audiences. Reject wrong-purpose tokens, DPoP authentication and all unsupported
  sender constraints, including malformed/unknown `cnf`, even with valid workload
  certificates. Never strip bindings or silently reissue incompatible credentials.
- Preserve verified `(iss, sub)` identity and execution ownership. Define default
  principal representation, explicit issuer-specific multiple-role mappings and an
  optional trusted native-account resolver invoked only after validation. Retain the
  verified pair with the native principal. Resolution failure rejects access; no
  implicit username/email linking, account creation, fallback or ownership merging.
  Specify authority-source precedence, snapshot versus live permissions, disabled
  accounts, logout, expiry and supported async context propagation. Same-pair renewal
  may retain ownership eligibility; changed-pair credentials do not inherit it.
- Specify the group issuer/audience/authority model against Sidecar's single-issuer
  baseline. Workload membership confers no issuer authority. Identify demonstrated
  Sidecar gaps and reconcile standing plans/lens constraints; do not infer supported
  multi-issuer behavior from a proposed configuration. The trust ticket owns storage,
  administration and lifecycle mechanisms for this model.
- Define both explicitly selected callback arrangements: an SDK-owned dedicated
  chain and an SDK configurer for an application-owned chain. Settle matchers,
  ordering, authorization hooks, overlapping-route diagnostics and supported
  servlet/reactive and asynchronous boundaries. Apply URL rules in the selected
  chain; preserve endpoint, method and record checks rather than assuming chains
  merge or automatically support arbitrary customer security configurations.
- Callbacks authenticate only the bearer token and ignore session cookies. Never
  load or merge session authority, fall back to sessions, persist callback identity
  to a session, mutate the accompanying session or redirect authentication failures
  to login. Limit CSRF exceptions to designated callback requests. Preserve ordinary
  browser security and guarantee context isolation/cleanup on success, rejection,
  exceptions and supported async paths. Keep optional JWKS route policy separate.
- Preserve unchanged-token passthrough. Define trusted recipients, limited audiences
  and permissions, lifetime/queue/callback policy and Sidecar-specific callback peer
  requirements. An approved recipient can reuse a valid credential when admission
  and authorization permit; tokens are not single-use or proof that Sidecar sent a
  body. Sidecar never mints, refreshes or exchanges captured credentials; later SDK
  renewal does not update admitted work. Require approved destinations, credential
  redirect protection and no infrastructure credential leakage. Do not add business
  content sanitization or promise isolation between mutually untrusted recipients.
- Deliver concrete interfaces/configuration, decision rationale and named planned
  positive/negative acceptance scenarios for these contracts. Preserve the roadmap's
  full acceptance obligations; identify component/repository owners and Phase 1/2/3
  implementation dependencies for consolidation in the final Phase 0 matrix.

## Acceptance criteria

- [x] The reference design specifies all four strategies, shared issuance, Java and
  Spring integration, trusted adapter contracts and a named/versioned local Exchange
  profile with explicit failures and no fallback or replacement login service.
- [x] Admission and callback validation tables define accepted profiles and rejection
  behavior for invalid signature/algorithm/time/claims, audience/purpose substitution,
  ID tokens, DPoP and malformed/unknown sender constraints across transports.
- [x] Identity/authority contracts and planned scenarios cover default/resolved
  principals, multiple roles, issuer collisions, invalid-token resolver exclusion,
  failed lookup, no implicit linking, same-pair renewal, changed-pair ownership,
  live/snapshot policy, logout, disabled accounts and queue/callback expiry.
- [x] Both callback composition recipes specify matcher/order/hooks/diagnostics and
  planned evidence for bearer Alice with session Bob, missing/invalid bearer with
  valid cookies, cookie-only/bearer-only requests, URL/method/record authorization,
  browser CSRF/session preservation, JWKS separation and context cleanup under
  sequential, concurrent, error and supported asynchronous execution.
- [x] Passthrough requirements and scenarios cover approved-recipient reuse, limited
  audiences, callback peer restrictions, redirect/leakage rejection and unchanged
  captured credentials without sanitization or replay-prevention claims.
- [x] Relevant roadmap obligations have named planned scenarios and owners; Sidecar
  gaps and public Framework dependencies are explicit, lens/plan conflicts are
  reconciled, and unresolved developer decisions prevent affected contract sign-off.

## Context

Roadmap work item: GitHub PR #8. Ticket identifier: `PR-8.0.1`.
SDK v1 tickets use `PR-8.[phase].[ticket]` identifiers.

Normative inputs: [Phase 0](../phases/2026-10-15-sdk-v1-phase-0-reference-contracts.md),
[roadmap security contracts](../phases/2026-10-11-java-spring-sdk-roadmap.md#sdk-managed-jwt-and-user-security)
and [security research](../phases/2026-10-11-established-security-architectures.md).
Their detailed acceptance scenarios remain required; this ticket does not reduce them.
Proposed adapter/settings names, `skills.execute` and the illustrative five-minute
lifetime are suggestions to resolve, not mandates.

Execute this ticket first. Hand off to
[trust/bootstrap/lifecycle](PR-8.0.2-sdk-v1-trust-bootstrap-lifecycle-contracts.md),
then [SDK/deployment/evidence](PR-8.0.3-sdk-v1-sdk-deployment-evidence-contracts.md).
The latter selects the final runtime/build baseline compatible with these security
contracts; document baseline constraints here and reconcile them before Phase 0 closes.
Do not create dependent implementation tickets with unresolved material contracts.

Framework source lookups use the local `C:/opendev/code/loomspan-framework` matching
`v1.0.0-beta.8` and pinned `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.8`.
Only closed `ai.loomspan.api` types and the authorized `RestSkillHandler` SPI are
allowed; no new SPI, internal-bean replacement or Sidecar Java `@SkillMethod` skills.
Retain ArchUnit prohibitions on `ai.loomspan.internal..` and
`ai.loomspan.autoconfigure..`. A missing public contract belongs to Framework planning.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Material identity, authorization, provider and callback contract
  design remains; documentation-only output does not make these decisions mechanical.
- **Reassessment triggers:** Reassess if approved concrete reference contracts already
  settle the entire scope; wording alone does not remove security design obligations.

## Pipeline notes

- The deliverable is reviewed contract documentation and planned acceptance scenarios.
  Do not implement product behavior or count unrun scenarios as passing evidence.
  Record developer decisions still needed rather than weaken an agreed requirement.
- The developer commits per ticket and syncs after all phases. Do not commit, push,
  release or require intermediate hosted CI. No code/build breakage is intended by
  this design ticket; any later intentional breakage must name impact, remaining
  verification and the dependent restoration unit, with evidence outstanding until fixed.

## Execution notes — 2026-10-15

Full 5-Step Pipeline explicitly approved. Pre-existing developer edits are the
PR-number prefixes in this ticket and PR-8.0.2/.3 headings; preserve them, and
leave .2/.3 otherwise unchanged. No product/build/test/version changes, data reset,
commit, push, release or live-service execution belongs to this documentation unit.

Deliverables: [reference contract](../phases/2026-10-15-sdk-v1-identity-callback-reference.md)
and [57 named planned scenarios](../phases/2026-10-15-sdk-v1-identity-callback-scenarios.md),
with AC1–AC6 traceability and phase/repository owners. Checkboxes above record
documentation implementation/verification; final completion still requires independent
Step 5 review. No scenario is represented as a passing runtime test. Full Phase 0
and PR-8.0.2/.3 trust/lifecycle/runtime/deployment decisions remain outstanding.

Material selections: Keycloak 26.4.0 standard V2 same-realm exchange; explicit
application-execution-v1 and keycloak-access-v1 profiles; 300s issuance/accepted
maximum lifetime and 60s verifier skew; token snapshot default and explicit live
account intersection; pair-preserving native wrapper; servlet-first supported
callback/async boundaries and fail-closed restrictions for both chain arrangements.
No new Framework SPI is identified/authorized. An inability to safely compose the
application-owned chain on the final runtime blocks implementation sign-off rather
than permitting session loading or weaker guarantees. No unresolved material choice
within this ticket is knowingly left as an implementation assumption.

### Step 4 verification evidence

- Pre-change command: `Test-Path ai/thoughts/phases/2026-10-15-sdk-v1-identity-callback-reference.md; Test-Path ai/thoughts/phases/2026-10-15-sdk-v1-identity-callback-scenarios.md` — both False. This is document absence, not a red runtime test.
- `python --version` — Python 3.13.13.
- `git diff --check` — exit 0, no whitespace errors (Git emits configured LF/CRLF conversion notices).
- `git diff --name-only`; `git status --short`; `git diff -- ai/thoughts/tickets/PR-8.0.2-sdk-v1-trust-bootstrap-lifecycle-contracts.md ai/thoughts/tickets/PR-8.0.3-sdk-v1-sdk-deployment-evidence-contracts.md` — documentation-only scope; .2/.3 changes are solely original headings.
- Exact local checker invocation below — PASS, 9 files, 56 local links at first run and 58 after ticket evidence links, 57 unique scenarios in 9 families; untracked files included for whitespace/fence checks. This checks structure/targets, not security semantics; the scoped consistency/AC review maps semantics, and Step 5 independently reviews them.

```powershell
@'
from pathlib import Path
import re
files = [
'ai/thoughts/phases/2026-10-15-sdk-v1-identity-callback-reference.md',
'ai/thoughts/phases/2026-10-15-sdk-v1-identity-callback-scenarios.md',
'ai/thoughts/phases/2026-10-11-java-spring-sdk-roadmap.md',
'ai/thoughts/phases/2026-10-15-sdk-v1-phase-0-reference-contracts.md',
'ai/thoughts/design-lens.md',
'ai/thoughts/tickets/PR-8.0.1-sdk-v1-identity-callback-contracts.md',
'ai/thoughts/plans/PR-8.0.1-sdk-v1-identity-callback-contracts.md',
'ai/thoughts/plans/PR-8.0.1-sdk-v1-identity-callback-contracts-testing.md',
'ai/thoughts/research/PR-8.0.1-sdk-v1-identity-callback-contracts.md']
errors = []; links = 0
for name in files:
    p = Path(name); raw = p.read_text(encoding='utf-8'); fenced = False; prose = []
    for n, line in enumerate(raw.splitlines(), 1):
        if line.rstrip() != line: errors.append(f'{name}:{n}: trailing whitespace')
        if line.lstrip().startswith('```'): fenced = not fenced; continue
        if not fenced: prose.append(line)
    if fenced: errors.append(f'{name}: unclosed fence')
    for target in re.findall(r'!?\[[^\]]*\]\(([^\n)]+)\)', '\n'.join(prose)):
        target = target.strip().split(' "', 1)[0].strip('<>')
        if re.match(r'^[a-zA-Z][\w+.-]*:', target) or target.startswith('#'): continue
        dest = target.split('#', 1)[0]
        if dest:
            links += 1
            if not (p.parent / dest).exists(): errors.append(f'{name}: missing {dest}')
matrix = Path(files[1]).read_text(encoding='utf-8')
ids = re.findall(r'^\| ((?:STRATEGY|EXCHANGE|TOKEN|ISSUER|IDENTITY|FRESHNESS|CALLBACK|PASSTHROUGH|TRANSPORT)-\d+) ', matrix, re.M)
if len(ids) != len(set(ids)): errors.append('duplicate scenario IDs')
for family in ['STRATEGY','EXCHANGE','TOKEN','ISSUER','IDENTITY','FRESHNESS','CALLBACK','PASSTHROUGH','TRANSPORT']:
    if not any(x.startswith(family+'-') for x in ids): errors.append('missing family '+family)
assert not errors, '\n'.join(errors)
print(f'PASS: {len(files)} files, {links} local links, {len(ids)} unique scenarios in 9 families; whitespace/fences clean')
'@ | python -
```

Additional executed scope/preservation check: inline Python compared the full .2/.3
contents with `git show HEAD:<path>` after substituting only their original heading,
asserted the original .1 heading, and asserted every `git diff --name-only` and
`git ls-files --others --exclude-standard` path is under `ai/thoughts/` and ends
`.md`. PASS: all three headings preserved, .2/.3 otherwise match HEAD, six tracked
and five untracked documentation paths at that run. Full ticket-scoped semantic
review checked explicit strategy/Java equivalence, provider registration, independent
validation, issuer/resolver/authority policy, both callback arrangements, bounded
async/lifetime behavior and all nine scenario families against AC1–AC6.

NOT RUN: `.\mvnw.cmd -B -ntp verify`, Docker/Keycloak/SDK/transport integration and
hosted CI — no product artifacts changed; future runtime scenarios intentionally
remain PLANNED. Primary provider/security sources are linked in the reference;
provider documentation is not interoperability evidence. Optional developer checks:
none for this documentation unit; independent review is the next required gate.
