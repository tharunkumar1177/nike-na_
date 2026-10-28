# Timeline Model

## Outcome or responsibility

Own the pure Kotlin project model: tracks, clips, implicit gaps, magnetic tracks, anchoring, and ripple behavior, plus the time math that every other module relies on.

## Current verified status

**Status:** Planned

## Architecture dependencies

- [Time representation](../architecture.md#time-representation)
- [Identity and ownership](../architecture.md#identity-and-ownership)
- [Project document model](../architecture.md#project-document-model)
- [Anchoring and ripple](../architecture.md#anchoring-and-ripple)

## Local rules and implications

### Time representation

All model fields and helper functions take and return microsecond `Long` values. Frame snapping helpers live here so UI and engine round identically.

### Identity and ownership

Model constructors validate ID uniqueness within a project; duplicate IDs are a model error, not a UI concern.

### Project document model

Clips on one track must not overlap. Magnetic tracks are kept gap-free by the commands that edit them, not by a background normalizer.

### Anchoring and ripple

Absolute clip positions are resolved by a single function that walks the anchor forest; both the timeline UI and the engine call it, so they cannot disagree.

## Related blueprints

### Required

None

### Impact checks

- [Edit history](edit-history.md) — when model invariants change, commands that enforce them must change too.
- [Project persistence](project-persistence.md) — when serialized fields change, bump `schemaVersion` and add a migration.

## Relevant implementation and tests

Planned: `domain/src/main/kotlin/.../model/`, `domain/src/test/kotlin/.../model/`.

## Acceptance or verification criteria

- [ ] Overlapping clips on one track are rejected.
- [ ] Anchored children resolve to parent start plus offset.
- [ ] Anchor cycles are rejected.
- [ ] Ripple on a magnetic track shifts later clips and their anchored children.

## Remaining gaps and unknowns

- Speed curves and transitions will extend clip fields; their shape is decided in their slices.
