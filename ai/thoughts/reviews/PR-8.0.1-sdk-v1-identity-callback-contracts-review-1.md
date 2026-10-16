# PR-8.0.1 Code Review — Cycle 1

## Scope and Repository State

Pipeline Step 5, Full 5-Step Pipeline explicitly approved, review 1. Reviewed the
current working-tree change against HEAD `be693c9f65dcbda6e15cf93fbffe5f832bc0fb40`;
no staged changes or branch comparison were supplied/needed. Reconstructed six
tracked dirty Markdown paths and five untracked Markdown paths, including the full
new normative reference, scenario matrix, research and both plans. Read the supplied
ticket, research, implementation plan and testing plan completely. No prior review
artifact was read.

The original developer edits are the PR-number heading prefixes in PR-8.0.1/.2/.3.
Independently compared .2/.3 against HEAD after only that heading substitution;
their bodies remain identical. Reviewed all .1 changes, the design-lens addition,
roadmap refinements and Phase 0 handoff. Product, test, build, dependency, version
and workflow files are unchanged. No commits, pushes, releases or live services.

Independent security/correctness review preceded acceptance/plan comparison and any
write. Traced the current decoder, exact owner pair, queued authentication handoff,
unchanged REST bearer forwarding and redirect/retry/cookie suppression beyond the
diff. Inspected the current ArchUnit boundary and matching Framework beta.8 public
handoff/SPI. Primary-source checks covered tagged Keycloak exchange and purpose
semantics, Spring chain/persistence/concurrency mechanisms and JWT substitution
guidance. Those checks establish documentation consistency, not interoperability.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. This context changed only this review artifact.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| AC1: four strategies, shared issuance, explicit Java/properties, trusted adapters, named Exchange | Reference credential signatures, equivalent construction and Keycloak 26.4.0 registration/request/output/failure rules; no product code is claimed | STRATEGY-01–06 and EXCHANGE-01–04 are concrete, owned and explicitly PLANNED | implemented as documentation |
| AC2: independent token validation, purpose, overlapping ID audiences, sender constraints | Separate admission/callback tables; fixed profiles, algorithm/key/time/claim rules; any cnf presence and DPoP rejected independently of workload certificates | TOKEN-01–09; transport expansion requires wrong-purpose/bound-token negatives at both validators, bypassing SDK preflight | implemented as documentation |
| AC3: exact pair, roles, native principal, resolution, freshness and ownership | Pair-preserving wrapper; post-validation fail-closed resolver; issuer maps; snapshot default/live intersection; logout/disable/expiry/renewal rules | ISSUER-01–03, IDENTITY-01–06, FRESHNESS-01–04 | implemented as documentation |
| AC4: both chains, session isolation, URL/method/record checks, CSRF, async cleanup | Reserved all-method namespace; dedicated and application-owned recipes; callback-null repositories before context loading; gated browser mechanisms; fail-closed diagnostics; explicit supported MVC scopes | CALLBACK-01–13 each apply to both arrangements, including session-access instrumentation and error/async cleanup | implemented as documentation |
| AC5: unchanged reusable passthrough, approved destinations, audience/peer/lifetime/leakage limits | Reference lifetime/passthrough section preserves captured JWT, approved reuse and independent recipient/record/Sidecar-peer checks, no sanitization or replay-prevention claim | PASSTHROUGH-01–07 distinguish infrastructure canaries from deliberately supplied business data | implemented as documentation |
| AC6: roadmap obligations, owners, Sidecar gaps, Framework boundary and handoffs | Scoped reference/matrix plus narrow roadmap/Phase 0/lens reconciliation; single-issuer and missing SDK behavior clearly identified; trust/runtime/deployment remain .2/.3 duties | Traceability table and TRANSPORT-01–05 preserve all four strategies/both mTLS paths/primary/mixed/no-callback obligations and cross-repository owners | implemented as documentation |
| Preserve developer work and documentation-only scope | All changed/untracked paths inventoried; .2/.3 full-content comparison and .1 prefix assertion | Independent inline verifier and Git checks below | implemented |
| Full-profile review and proportionate verification | Security and external-protocol design retain Full eligibility; no reduced-profile reassessment needed | Independent semantic review plus structural/scope checks; no invented runtime result | implemented |

The matrix/checker supplements semantic review; keyword presence and unique IDs do
not establish token security. Reviewed normal, malformed and boundary token paths,
issuer collision, authority precedence, resolver outages, context/session isolation,
queue expiry, callback failures, recipient reuse and infrastructure credential paths.
No new storage/concurrency/product-performance behavior is implemented in this unit;
trust publication/withdrawal and TLS lifecycle mechanisms remain explicit dependencies.

## Active Project Guardrails

- Development policy and design-lens references are prominent; no compatibility
  layer, migration or data reset is introduced or authorized.
- Exact issuer/subject ownership and captured JWT remain outside model inputs.
  Sidecar never mints, exchanges or refreshes execution credentials.
- Framework owns execution authorization/lifetime. Inspected tagged beta.8
  `SkillInvocationHandoff` and `RestSkillHandler`; no new Framework SPI, internal
  import, reflection, bean replacement or Sidecar Java skill is introduced.
  Existing ArchUnit bans on internal/autoconfigure dependencies remain untouched.
- Management/execution authority, browser sessions/CSRF, callback bearer authority
  and workload membership/issuer trust remain separate.
- SDK settings are explicitly planned under `loomspan-sdk.*`; Framework/Sidecar/
  Boot configuration ownership and all pinned versions remain intact.
- Diagnostic/business-content fidelity is preserved; infrastructure must avoid
  credential leakage without claiming business sanitization.
- No production/state/shutdown/publication changes and no deployed-data deletion.

## Open Questions and Assumptions

No unresolved choice blocks this ticket's documentation result. The final SDK
runtime and actual feasibility of application-owned filter/repository composition
remain PR-8.0.3 reconciliation and implementation evidence gates. The reference
explicitly forbids weakening session isolation if composition cannot be proven;
this is an honest dependency, not current compatibility evidence or permission to
drop the required arrangement. Trust mechanisms remain PR-8.0.2 work.

## Verification Results

- PASS — `git diff --check` — no whitespace errors; configured LF/CRLF notices only.
- PASS — `git diff --cached --stat` — no staged changes.
- PASS — `git status --short` — documentation-only tracked/untracked inventory.
- PASS — independent inline command below — 11 documentation paths, 75 local link
  targets, 57 unique scenarios in nine families, both callback arrangements named,
  planned status, original headings and untouched .2/.3 bodies; whitespace/fences clean.

```powershell
@'
from pathlib import Path
import re, subprocess
tracked = subprocess.check_output(['git','diff','--name-only'], text=True).splitlines()
new = subprocess.check_output(['git','ls-files','--others','--exclude-standard'], text=True).splitlines()
files = sorted(set(tracked + new))
assert all(p.startswith('ai/thoughts/') and p.endswith('.md') for p in files), files
for ticket in ['PR-8.0.2-sdk-v1-trust-bootstrap-lifecycle-contracts','PR-8.0.3-sdk-v1-sdk-deployment-evidence-contracts']:
    name = 'ai/thoughts/tickets/' + ticket + '.md'
    old = subprocess.check_output(['git','show','HEAD:' + name], text=True)
    expected = old.replace('# ', '# PR #' + ticket.split('-sdk')[0][3:] + ' — ', 1)
    assert Path(name).read_text(encoding='utf-8') == expected, name
assert Path('ai/thoughts/tickets/PR-8.0.1-sdk-v1-identity-callback-contracts.md').read_text(encoding='utf-8').startswith('# PR #8.0.1 — ')
links = 0
for name in files:
    p = Path(name); prose = []; fenced = False
    for n, line in enumerate(p.read_text(encoding='utf-8').splitlines(), 1):
        assert line == line.rstrip(), (name, n, 'whitespace')
        if line.lstrip().startswith('```'): fenced = not fenced
        elif not fenced: prose.append(line)
    assert not fenced, (name, 'fence')
    for target in re.findall(r'!?\[[^\]]*\]\(([^\n)]+)\)', '\n'.join(prose)):
        target = target.strip().split(' "',1)[0].strip('<>')
        if re.match(r'^[A-Za-z][\w+.-]*:', target) or target.startswith('#'): continue
        target = target.split('#',1)[0]
        if target:
            links += 1
            assert (p.parent / target).exists(), (name, target)
matrix = Path('ai/thoughts/phases/2026-10-15-sdk-v1-identity-callback-scenarios.md').read_text(encoding='utf-8')
ids = re.findall(r'^\| ((?:STRATEGY|EXCHANGE|TOKEN|ISSUER|IDENTITY|FRESHNESS|CALLBACK|PASSTHROUGH|TRANSPORT)-\d+) ', matrix, re.M)
assert len(ids) == len(set(ids)) == 57
assert len({i.split('-')[0] for i in ids}) == 9
assert 'PLANNED, NOT RUN' in matrix
assert 'Every CALLBACK row runs for both' in matrix
print(f'PASS: {len(files)} documentation paths; {links} local link targets; 57 unique scenarios/9 families; headings and untouched .2/.3 bodies preserved; whitespace/fences clean')
'@ | python -
```

Read-only source checks included these exact commands:

```powershell
Get-Content src/main/java/ai/loomspan/sidecar/security/JwtSecurityConfiguration.java
Get-Content src/main/java/ai/loomspan/sidecar/rest/GenericRestSkillHandler.java
Get-Content src/main/java/ai/loomspan/sidecar/security/ExecutionOwner.java
Get-Content src/main/java/ai/loomspan/sidecar/rest/RestTargetClients.java | Select-Object -First 115
Get-Content src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java | Select-Object -Skip 100 -First 95
Get-Content src/test/java/ai/loomspan/sidecar/architecture/SupportedLoomspanApiArchitectureTest.java
git -C C:/opendev/code/loomspan-framework show v1.0.0-beta.8:src/main/java/ai/loomspan/api/SkillInvocationHandoff.java
git -C C:/opendev/code/loomspan-framework show v1.0.0-beta.8:src/main/java/ai/loomspan/api/RestSkillHandler.java
```

Primary sources independently opened through the web tool:
[tagged Keycloak exchange guide](https://raw.githubusercontent.com/keycloak/keycloak/26.4.0/docs/guides/securing-apps/token-exchange.adoc),
[tagged TokenManager](https://raw.githubusercontent.com/keycloak/keycloak/26.4.0/services/src/main/java/org/keycloak/protocol/oidc/TokenManager.java),
[tagged TokenVerifier](https://raw.githubusercontent.com/keycloak/keycloak/26.4.0/core/src/main/java/org/keycloak/TokenVerifier.java),
[Spring chains](https://docs.spring.io/spring-security/reference/servlet/configuration/java.html),
[persistence](https://docs.spring.io/spring-security/reference/servlet/authentication/persistence.html),
[concurrency](https://docs.spring.io/spring-security/reference/servlet/integrations/concurrency.html)
and [RFC 8725](https://www.rfc-editor.org/rfc/rfc8725.html#section-3).
Provider request/type/audience claims agree with tagged sources. Spring mechanisms
are supporting design evidence; the reference correctly leaves final-baseline
composition proof outstanding.

- NOT RUN — `.\mvnw.cmd -B -ntp verify` — product/build/test tree unchanged;
  structural and independent semantic review establish this documentation scope.
- NOT RUN — future Keycloak/SDK/Sidecar/proxy integration commands — no SDK
  implementation or final runner exists here; all matrix rows remain PLANNED.
- An initial read attempted `ai/commands/automation-protocol.md`, which does not
  exist; immediately corrected to `ai/commands/shared/automation-protocol.md`
  and read the shared protocol completely. No required verification was skipped.

## Residual Risks and Optional Developer Checks

No optional developer checks for this documentation ticket. Runtime provider,
filter/session isolation, async cleanup, multi-issuer, transport and lifecycle
guarantees require future owned implementation evidence. Phase 0 remains open for
.2/.3 and full matrix consolidation. Documentation verification establishes none
of those runtime passes; existing Sidecar/Framework tests were inspected, not run
or substituted for SDK integration evidence.

## Disposition

`clean`: complete independent review, no actionable findings, sufficient
documentation-scope verification, no implementation-artifact modifications.
