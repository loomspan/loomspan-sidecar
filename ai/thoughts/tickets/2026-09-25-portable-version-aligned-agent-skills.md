# Install portable, version-aligned Loomspan skills in customer projects

## Development constraints

**Sidecar is still in development. Destructively replace superseded contracts,
code and schemas; do not add compatibility shims, legacy adapters, parallel
legacy APIs, or migration machinery solely to preserve obsolete development
behavior.**

Apply the [design lens](../design-lens.md), especially simplicity and technical
debt. Keep implementation, tests, documentation and process proportional. Record
any required development-data reset and its impact; this ticket does not
authorize deleting deployed data. No runtime data reset is expected for this
skill reorganization.

## Outcome

Customers can install and update a coherent set of Loomspan Agent Skills in
their own application projects without checking out Loomspan source. Treat
Sidecar integration as the primary onboarding path: customers may use any
language or framework, an SDK, or the Sidecar APIs directly. Also support Java
applications embedding loomspan-framework as a Maven dependency.

Loomspan determines the required skills and their versions. The host agent owns
the supported installation and management mechanics. Default to project-local
installation, honor an explicit scope choice, and explain a host limitation
rather than silently substituting another installation scope.

## Requirements

### Skill family and ownership

- Introduce a small `loomspan` entry point for ecosystem orientation and routing.
  Route concrete requests to the relevant available specialist without loading
  all specialists, duplicating their instructions, or automatically installing
  missing skills.
- Rename the current Console `loomspan` skill to `loomspan-console`. Keep its
  runtime investigation responsibility and update affected packaging,
  validation, documentation and cross-references.
- Retain `loomspan-docs` as the authority for version-aligned framework
  semantics, skill design and Java integration.
- Retain `loomspan-sidecar-authoring` for Sidecar skills, routes, drafts,
  validation and publication, including a usable direct API integration path.
- Replace the bootstrap contract with `loomspan-install`, maintained initially
  in the framework repository and supporting both customer paths.
- Use independently discoverable sibling skills as the installed logical
  organization. Keep specialist sources with their owning components: framework
  documentation with framework, Console guidance with Console, Sidecar guidance
  and its client with Sidecar, and future SDK skills with their SDKs. Do not
  centralize all sources or force independent component release versions to match.

### Installation and version selection

- For a Sidecar customer, determine the selected target Sidecar release from
  explicit project configuration or deployment metadata. Ask the developer when
  absent or ambiguous. A live Sidecar connection is optional, not an installation
  prerequisite. The customer's project need not contain a POM.
- Resolve framework guidance from the framework dependency in the POM of that
  exact Sidecar release. Select Sidecar guidance from the Sidecar release and
  framework documentation and Console guidance from the resolved framework
  version. Include the common entry point in the installed set.
- For an embedded Java customer, resolve the framework dependency from the
  application's POM and install matching framework documentation, Console
  guidance and the common entry point. Unresolved dependencies require a clear
  question or limitation; never guess a version or fall back to latest.
- SDK selection is independent: use the SDK dependency selected in the
  customer's project to select matching SDK guidance. Do not infer the server
  version from the SDK version or infer compatibility from equal version numbers.
- Establish minimal component version metadata and routing conventions for
  these skills. Consume explicitly published compatibility facts; report when
  compatibility cannot be established. Do not invent supported ranges.
- An update refreshes skills to match the customer's selected dependencies and
  target runtime. It must not upgrade dependencies, change the target runtime,
  edit deployment configuration, or initiate publication or execution.
- Use the host's supported skill installation and management capabilities.
  Avoid client-specific installers, directory assumptions, or a new package
  manager. Prefer existing project and skill metadata over introducing a new
  installation-record format; add machinery only for a demonstrated need.
- Resolve and validate requested sources before modifying installations. Report
  selected component versions, installation scope, outcomes and limitations.
  Preserve ordinary host authorization and explicit user choices for updates.

### Portable Sidecar guidance and SDK integration

- Bundle the Sidecar-specific operational guidance, client and required examples
  inside the Sidecar skill folder. Required file references must work after
  installing that folder alone, without sibling source repositories or links
  escaping into repository documentation and examples.
- Route deeper framework questions to the separately installed, matching
  `loomspan-docs` skill. This is an explicit skill dependency, not an assumption
  about repository layout. Explain unavailable guidance rather than inventing
  semantics. Do not copy the entire framework documentation into Sidecar.
- SDKs are first-class future components, with skills named
  `loomspan-sdk-<language>` and independently selected versions. SDK skills own
  language-specific setup, security integration, callbacks, request context,
  application lifecycle and application-side changes. Sidecar guidance owns the
  corresponding server-side configuration and coordinates with SDK guidance when
  an SDK is used.
- Keep outbound application execution requests and Sidecar callbacks into the
  application distinct, including their identities and authorization rules.
  Preserve existing management-token, execution-JWT and application-data
  authorization boundaries; organizing guidance grants no additional authority.
- Keep direct API customers supported without requiring an SDK. Do not create
  empty SDK skill implementations or prescribe future SDK security/callback
  protocols in this change. Establish the conventions future concrete SDKs use.

### Proportionate verification

- Verify Loomspan-owned contracts: metadata, complete bundles, portable resource
  references, component/version selection, ambiguity handling, update scope and
  affected distribution packaging.
- Use representative local project/release fixtures for Sidecar direct API,
  SDK-based Sidecar and embedded Java selection. Future SDK selection may be
  exercised with clearly identified fixtures, without claiming a released SDK.
- Include focused behavioral validation of the installer during its rewrite;
  structural validation alone does not demonstrate correct routing or decisions.
  Do not require a recurring Codex/Claude Code/client certification matrix,
  live external services or credentials as routine pipeline prerequisites.
- Each implementation unit supplies its own relevant tests and documentation.
  Do not claim Sidecar behavior is verified solely by framework evidence.

## Acceptance criteria

- [x] Customers discover a common `loomspan` entry point and can directly use
  focused specialists; the old Console name and bootstrap contract are replaced
  consistently in affected distributions, validation and documentation.
- [x] A non-Java direct API project receives guidance selected from its target
  Sidecar release and that release's framework pin without a local Loomspan
  checkout or live server connection.
- [x] An embedded Java project receives guidance matching its framework
  dependency, and unresolved or ambiguous selections are surfaced without a
  guessed version or latest-version fallback.
- [x] An SDK-based project independently selects SDK and server guidance using
  published compatibility facts; unknown compatibility remains explicit.
  Future SDK ownership and conventions are documented without placeholder SDKs.
- [x] Skill installation defaults to the customer project through host-supported
  mechanisms; explicit scope choices and host limitations are respected without
  client-specific installation machinery.
- [x] Updates preserve application dependencies and runtime targets, perform no
  runtime mutations, and accurately report selected versions and outcomes.
- [x] The installed Sidecar bundle supports its direct API authoring workflow
  with internal resources; deeper framework guidance uses an explicit matching
  skill dependency instead of out-of-folder repository references.
- [x] Routing preserves the SDK/application, Sidecar/server and Console/evidence
  responsibilities and existing credential and authorization boundaries.
- [x] Local fixture checks, bundle/metadata validation, affected packaging checks
  and focused installer behavioral evidence substantiate these outcomes without
  a recurring multi-client certification requirement.

## Context

Current source locations:

- Framework documentation and bootstrap:
  `C:/opendev/code/loomspan-framework/agent-skills/`.
- Console skill:
  `C:/opendev/code/loomspan-framework/loomspan-console/agent-skills/loomspan/`.
- Sidecar skill: `agent-skills/loomspan-sidecar-authoring/`.

Sidecar and framework versions advance independently. The local Sidecar POM
currently pins beta 5 while the inspected framework skill metadata identifies
beta 6 development. Planning must account for exact-version source availability;
this ticket does not authorize changing either dependency to make selection pass.
Use local fixtures for unavailable release combinations and state the limits of
that evidence. Existing published releases must not be overwritten.

Initial scope covers the entry point, Console rename, installer replacement,
Sidecar portability, version/routing conventions and their verification. Future
SDK implementations, new security protocols, new runtime discovery APIs,
dependency upgrades, publishing releases and agent-client certification are out
of scope. Preserve unrelated working-tree changes in either repository.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** medium
- **Rationale:** Product intent is settled, but version-source resolution,
  cross-repository distribution and installer behavior still require material
  discovery and implementation design. Full is recommended for those concrete
  concerns, not for a multi-client testing matrix.
- **Reassessment triggers:** Reassess against the complete ticket-scoped change
  during pipeline triage; a smaller final edit does not exclude the preceding
  cross-component work from required assurance.

## Execution notes

- 2026-09-25: Developer confirmed the Full 5-Step Pipeline at the Step 0 auto gate. Selected profile: full.
- Initial checkout scope: Sidecar had only this untracked ticket; framework working tree was clean. Preserve unrelated changes that appear during execution.


- 2026-09-25 Step 4: Implemented both repository changes and recorded independent actual instruction replay plus automated evidence in `ai/thoughts/validation/2026-09-25-portable-version-aligned-agent-skills.md`. Acceptance checkboxes reflect implementation evidence; independent Step 5 review is pending. No runtime/dependency/release mutation or data reset. Exact unavailable-release and future SDK limitations remain unchanged.
