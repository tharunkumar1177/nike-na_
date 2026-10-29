# Timeline Model

## Outcome or responsibility

Own the pure Kotlin project model: tracks, clips, implicit gaps, magnetic tracks, anchoring, and ripple behavior, plus the time math that every other module relies on.

## Current verified status

**Status:** Partial

Time math (`FrameRate`, `Timecode`), the validated document model (`MediaAsset`, `Clip`, `Track`, `Project`, `ProjectFactory`), track kinds, overlay placement (`OverlayTransform`), and anchoring (`ClipAnchor`, `AnchorResolver`) are implemented and verified by CI unit tests. Speed and transitions are not built yet.

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

`AnchorResolver` is the single place that walks the anchor forest. Commands call `AnchorResolver.materialize` after editing tracks, so every stored start is already resolved and the timeline UI and the engine read `startMicros` directly; they cannot disagree. `Project` runs `AnchorResolver.validate` on construction, which rejects anchored clips on magnetic tracks, placements on non-overlay tracks, missing parents, cycles, negative starts, and stored starts that differ from their anchor.

## Related blueprints

### Required

None

### Impact checks

- [Edit history](edit-history.md) — when model invariants change, commands that enforce them must change too.
- [Project persistence](project-persistence.md) — when serialized fields change, bump `schemaVersion` and add a migration.

## Relevant implementation and tests

- `domain/src/main/kotlin/com/framecraft/editor/domain/time/` — `FrameRate`, `Timecode`.
- `domain/src/main/kotlin/com/framecraft/editor/domain/model/` — `MediaAsset`, `Clip`, `Track`, `Project`, `ProjectFactory`, `IdGenerator`, `ClipAnchor`, `OverlayTransform`, `AnchorResolver`.
- Tests: `domain/src/test/kotlin/com/framecraft/editor/domain/time/FrameRateTest.kt`, `TimecodeTest.kt`, `domain/src/test/kotlin/com/framecraft/editor/domain/model/ProjectTest.kt`, `domain/src/test/kotlin/com/framecraft/editor/domain/command/OverlayCommandsTest.kt`.

## Acceptance or verification criteria

- [x] Frame math round-trips at fractional rates (29.97) and never returns negative time.
- [x] Overlapping clips on one track are rejected.
- [x] Magnetic tracks reject gaps; duplicate IDs and unknown asset references are rejected.
- [x] Anchored children resolve to parent start plus offset.
- [x] Anchor cycles are rejected.
- [x] Ripple on a magnetic track shifts later clips and their anchored children.

## Remaining gaps and unknowns

- Speed curves and transitions will extend clip fields; their shape is decided in their slices.
