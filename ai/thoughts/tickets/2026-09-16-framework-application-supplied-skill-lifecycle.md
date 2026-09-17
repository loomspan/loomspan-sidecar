# Initialize and reload skills from application-supplied content

## Outcome

An application can supply a complete set of YAML skill documents from its own
storage, validate and freeze that candidate, stage matching external REST resources,
and activate it. The same supported lifecycle works at initial startup, after a
process restart, and for subsequent reloads without replacing a live skill directory.

## Requirements

- Extend the supported public surface to accept a complete, explicitly supplied
  collection of named YAML skill documents. Callers must be able to supply content
  already held in memory, without temporary files, symlinks, custom filesystem
  providers, internal imports, or replacement of framework beans.
- Preserve framework authority over parsing, validation, child references, model
  references and skill authorization. Support the existing model-backed and REST
  YAML declaration surface. Existing Java declarations and model/handler bindings
  remain fixed; this is not Java hot swap or model-connection reload.
- Preparation must freeze the supplied content and return a candidate catalog and
  generation ID without activating it. Subsequent caller mutations or storage
  changes cannot alter that candidate. Publication must not reread source content.
- The input is a complete replacement YAML set, not a merge or incremental patch.
  Preserve existing empty-set semantics and validation of duplicates and invalid
  declarations. Named sources must produce useful diagnostics without requiring
  physical paths; establish supported source naming and duplicate-source handling.
- Provide an opt-in application-controlled initial activation path. Applications
  must be able to start their management infrastructure without automatic skill
  directory activation, prepare initial content, stage REST resources under the
  candidate ID, and publish before allowing skill execution.
- Before successful initial activation, execution must not silently use an empty,
  default or partially initialized generation. Expose an unambiguous lifecycle
  state/failure so applications can gate traffic and distinguish not-initialized
  from an intentionally published empty set. Catalog/snapshot behavior in that
  state must be documented.
- Failed initial preparation leaves activation pending and allows a corrected
  candidate to be prepared; failed later preparation/publication preserves the
  active generation. Keep existing stale/foreign/repeated candidate and shutdown
  rejection guarantees, including competing initial candidates.
- Preserve existing directory-based startup and no-argument reload for applications
  that do not opt into this mode. A directory convenience API may share the new
  preparation implementation, but a directory-only API is insufficient for this
  ticket's application-supplied content requirement.
- Restart loading uses the same public input contract. The application selects its
  durable active snapshot and resubmits its content; the framework assigns a fresh
  process-local generation ID. Do not require preservation of old framework IDs.
- Keep database access, durable active-pointer recovery, route/client storage and
  application readiness policy outside the framework. Do not introduce SQLite,
  Sidecar schema knowledge, a generic storage plugin system or a distributed
  transaction with application persistence.

## Acceptance criteria

- [ ] A public-surface application boots in controlled activation mode, prepares
  in-memory model-backed/REST YAML, stages generation-keyed REST resources, publishes
  and executes without skill files on disk.
- [ ] Execution before initial activation is rejected deterministically; failed
  preparation is recoverable through the supported API without process restart.
- [ ] Initial and replacement candidates freeze complete input content, provide
  useful source diagnostics, and preserve full validation and empty-set semantics.
- [ ] Publication activates the prepared content only; old admitted/running trees
  keep their generation while new roots use the replacement generation.
- [ ] Foreign, stale, repeated, competing-initial and shutdown-time publication
  cases obey the documented lifecycle without partial activation.
- [ ] A restart example resubmits the same application-owned durable snapshot and
  stages its REST configuration against the newly assigned framework generation ID.
- [ ] Existing directory-based applications retain their startup/reload behavior.
- [ ] Documentation explains both initial activation and reload, source identity,
  pre-activation API behavior, and the boundary between framework publication and
  application persistence/readiness. Examples use only supported public contracts.

## Context

Destination repository: loomspan-framework. This ticket was authored in Sidecar
for the developer to move into the framework repository; it must stand alone there.

Sidecar is planning an embedded console with session-owned edits and publication
of complete skill/REST snapshots. Database storage, potentially SQLite for the
beta, is being evaluated but is not selected. The framework contract must work
independently of that choice: an application reads storage and supplies content.

Current SkillReloader.prepare() discovers configured skill locations and offers
no source argument. A directory argument was the first proposed enhancement;
discussion expanded the requirement to content input to avoid filesystem staging.
The initial startup path must also support that input: adding a reload overload
alone does not solve database-backed restart bootstrap.

Exact API names and representation of supplied documents are implementation-plan
decisions. The proposed Sidecar startup flow is: read durable active snapshot,
prepare its skills, stage matching REST configuration/clients under the fresh
generation ID, publish, then open execution traffic. A fresh installation may
explicitly publish an empty set; whether Sidecar does so is its own product policy.
This ticket does not implement Sidecar authentication, persistence, UI or imports.

Coordinate with the companion generation-retirement notification ticket so
application-controlled initial and replacement generations share safe cleanup
semantics. Neither ticket permits Sidecar to depend on framework internals.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Extends public API and changes initialization, validation,
  generation publication and execution admission behavior across the framework.
- **Reassessment triggers:** None that justify reducing rigor; re-evaluate scope
  if source inspection finds new existing public capabilities.
