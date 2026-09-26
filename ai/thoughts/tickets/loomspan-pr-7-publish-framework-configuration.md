# PR 7 — Publish framework configuration through the Sidecar Console

## Development constraints

Apply the [Sidecar design lens](../design-lens.md). Choose the simplest complete
solution and minimize technical debt. Destructively replace superseded
development contracts, code and schemas; do not add compatibility shims, legacy
adapters, parallel legacy APIs or migration machinery solely to retain obsolete
development behavior. Document required development-data resets and their
impact. This policy does not authorize deleting deployed data during planning
or implementation without the required authorization.

## Outcome

An operator can edit framework execution settings in the Sidecar Console and
publish them together with skill definitions and REST routes. A publication is
one complete execution configuration: new work receives the new version while
already-admitted work keeps its original skills, models, limits and tracing
policy. Model changes no longer require restarting Sidecar.

This is the Sidecar's existing browser management console and publication
workflow, not the separate Go Loomspan Console observability application.

Developer LLMs must be able to use the bundled `loomspan-sidecar-authoring`
agent skill and client to access this Console's shared management API. The
bundle must describe and exercise reading current/private draft content,
complete three-field save and reconcile, validation, publication, external
credential references, and the process-setting boundary. Its detached client
must preserve `executionConfigurationYaml` unchanged and use the same token,
lease, revision, base, and validation rules as the browser.

## Requirements

- Extend the existing complete managed configuration to contain framework
  execution settings: named connections and their provider/retry options,
  model aliases, all session settings (timeouts, depth, quotas and attachment
  limits), and `loomspan.execution-trace.persistence`. Include them throughout
  drafts, current configuration, validation, publication, retained history,
  export/import, rollback and restart recovery. Use the framework's configuration
  names and semantics; do not maintain a second model/skill validation authority.
- Make these settings editable through the same Console draft workflow and
  shared management API used for skills and REST routes. Preserve existing
  Read/Edit/Publish authorization, private durable drafts, editing lease,
  revision/base checks, validation proofs, stale-draft reconciliation and
  authorization rechecks. A saved draft or successful validation does not activate
  any settings. Do not add a separate settings publish button or bypass path.
- Validate the entire proposed configuration together. Skills may reference
  models and connections introduced in the same draft. Surface actionable field
  errors without exposing resolved secrets. Publication must prepare all required
  framework and REST resources before switching the active version, and must
  neither execute skills nor require a billable model request for validation.
- Publish skills, REST routes and framework execution settings as one version.
  Preserve the existing durable selection, failure handling and mutation-fault
  behavior so failure cannot leave a partially active combination. Failed
  publication preserves the operator's draft.
- Use framework admission/handoff as the version-selection boundary. Queued
  requests not yet handed off use the version active at handoff; already-captured
  work and all its nested/parallel calls and retries keep their original version.
  Record the durable configuration snapshot actually captured for each execution.
  Retain old REST/provider resources until the corresponding physical work ends.
- The database-selected complete configuration is restart authority; do not
  silently override published execution settings with competing startup model
  definitions. Make initial defaults and the empty-database authoring experience
  explicit and consistent with framework defaults. A new deployment must be able
  to author its first model connection and skills through the publication workflow.
- Store/export external credential references, not their resolved values, in
  the added framework settings. Resolve references during candidate preparation
  using the deployment's external configuration. Missing references fail
  preparation; changing a reference in a new publication does not alter already
  captured connections. No secret vault, secret-value editor or live secret
  rotation service is in scope. This does not redesign existing REST YAML's
  sensitive-data handling.
- Process-wide settings remain deployment-owned: observability API/authentication
  and service retention settings, framework shutdown, Sidecar listeners,
  management/execution security, storage and other Spring/server settings.
  Skill resource locations are not editable execution settings because managed
  snapshots already supply the skill documents. Clearly label this boundary;
  reject unsupported settings rather than implying they were published.
- Rollback republishes the complete retained configuration, including framework
  settings, as a new publication. Import validation resolves references in the
  destination deployment; exports must not carry resolved environment secrets.

## Acceptance criteria

- [ ] An authorized operator can edit a skill, introduce/change its model
  connection and alias, change session limits and tracing, then validate and
  publish the whole candidate in one Console workflow without restart.
- [ ] Current configuration, durable drafts, history and export/import carry the
  complete authored configuration. Rollback and restart activate a complete
  selection rather than combining old content with current startup settings.
- [ ] Concurrent work demonstrates the handoff boundary: an admitted root and
  its later children retain the old configuration; work handed off afterward
  uses the new one. Each execution identifies its actual durable snapshot, and
  resources remain usable until their captured physical work finishes.
- [ ] Invalid model references/settings, missing secrets and staging/publication
  failures never produce partially active configurations or discard a failed
  publication's draft. Validation does not execute skills or invoke a model.
- [ ] Existing management permissions, lease/revision/base checks, stale-draft
  reconciliation and authorization rechecks apply equally to the new settings
  through Console and the shared management API.
- [ ] Added configuration exports, UI responses, errors and diagnostics do not
  disclose resolved credentials. Destination imports validate their own secret
  references, and old captured connections are not silently reconfigured.
- [ ] The UI and deployment documentation distinguish publishable execution
  settings from process settings, reject unsupported fields and explain first
  publication/defaults, reference provisioning and any development-data reset.
- [ ] Sidecar integration is verified using only the supported framework API
  against the updated installed artifact. Documentation and the design lens no
  longer describe model connections as unconditionally restart-only.

## Context and sequencing

Depends on [framework PR 11](../../../../loomspan-framework/ai/thoughts/tickets/loomspan-pr-11-publish-execution-configuration.md).
Implement its supported execution-configuration publication capability first,
then integrate and verify Sidecar against the installed artifact under the
repository dependency/release policy. Do not bypass a missing public contract
with dependencies on `ai.loomspan.internal` or `ai.loomspan.autoconfigure`.

At ticket creation, `ManagedConfiguration` contains skill documents and REST
routes only. `RuntimeConfigurationService` already coordinates preparation,
durable selection, framework publication and recovery; the execution handoff
already correlates framework generations with durable snapshots. Extend these
existing responsibilities rather than adding a competing publication system.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Extends durable/serialized configuration, management workflows,
  cross-repository contracts and concurrent execution/resource lifetimes.
- **Reassessment triggers:** An unavailable framework contract or broader
  security/data impact requires planning reassessment, not an internal workaround.

## Pipeline notes

- This ticket intentionally supersedes the design lens's restart-only exception
  for model connections and its skill/route-only snapshot description. Update
  those statements to reflect complete execution configuration publication.
- Pre-release draft, snapshot and bundle schemas may be replaced coherently
  under the development policy above. Explain the upgrade/reset impact without
  adding legacy readers or silently deleting existing data.
- Proposed PR number 7 was supplied by the developer. The framework
  `write_ticket.md` command was explicitly selected for these paired tickets,
  including its PR-number filename convention.
