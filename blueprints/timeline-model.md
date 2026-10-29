# Timeline Model

## Outcome or responsibility

Own the pure Kotlin project model: tracks, clips, implicit gaps, magnetic tracks, anchoring, and ripple behavior, plus the time math that every other module relies on.

## Current verified status

**Status:** Partial

Time math (`FrameRate`, `Timecode`), the validated document model (`MediaAsset`, `Clip`, `Track`, `Project`, `ProjectFactory`), track kinds (`VIDEO`, `OVERLAY`, `AUDIO`, `TEXT`), clip volume and fades, clip text (see [text](text.md)), overlay placement (`OverlayTransform`), the project canvas (`CanvasSettings` with `CanvasAspect` and `CanvasBackground`) and base clip placement (`CanvasPlacement`, VIDEO clips only), and anchoring (`ClipAnchor`, `AnchorResolver`) are implemented and verified by CI unit tests. Clip speed (see [clip-speed](clip-speed.md)) and outgoing transitions (see [transitions](transitions.md)) are clip fields under the same validation, as are clip voiceover and ducking and track lane volume (see [voiceover](voiceover.md)).

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

`Project` checks that each clip fits its track: picture tracks accept only `VIDEO` and `IMAGE` assets, audio tracks reject images and text, text tracks accept only the shared `TEXT` asset, a clip carries text exactly when it is on a text track, and image and text clips keep the default volume and no fades. Placements are allowed on overlay and text clips (`TrackKind.isPlaced`); only video and audio assets are timed (`MediaAsset.isTimed`), so only they have a source offset to trim. A canvas placement keeps centers within ±2 canvas half-widths and a scale above 0 and at most 4, like overlays, and a solid canvas background must be opaque. `Clip` rejects a volume outside 0 to 200 percent, negative fades, and fades that together exceed the clip. Audio assets are stored as 1 by 1 with no rotation so the shared size checks still hold; nothing reads their size.

### Anchoring and ripple

`AnchorResolver` is the single place that walks the anchor forest. Commands call `AnchorResolver.materialize` after editing tracks, so every stored start is already resolved and the timeline UI and the engine read `startMicros` directly; they cannot disagree. `Project` runs `AnchorResolver.validate` on construction, which rejects anchored clips on magnetic tracks, magnetic tracks that are not `VIDEO`, placements on non-overlay tracks, canvas placements off the `VIDEO` track, missing parents, cycles, negative starts, and stored starts that differ from their anchor.

### Clip speed

- A clip of a timed asset may carry `speed`, `sourceDurationMicros`, and `maintainPitch` (on by default); `sourceSpanMicros` is the source it reads. `Project` checks the source bounds against that span and the duration against `SpeedMap`, as [Clip speed and source mapping](../architecture.md#clip-speed-and-source-mapping) requires. The rules and commands belong to [clip-speed](clip-speed.md).

### Clip transitions

- A clip may carry `outTransition` (`ClipTransition`: kind and a positive length) into the next clip. Whether it plays is computed from the track by `TransitionGeometry`, never stored, so the field survives edits that separate the clips. The rules and commands belong to [transitions](transitions.md).
- A clip on a video or overlay track may carry `look` (`ClipLook`: an optional catalog filter at an intensity and six color adjustments); the project refuses a look on any other track. The rules and commands belong to [look](look.md).

## Related blueprints

### Required

None

### Impact checks

- [Edit history](edit-history.md) — when model invariants change, commands that enforce them must change too.
- [Project persistence](project-persistence.md) — when serialized fields change, bump `schemaVersion` and add a migration.

## Relevant implementation and tests

- `domain/src/main/kotlin/com/framecraft/editor/domain/time/` — `FrameRate`, `Timecode`.
- `domain/src/main/kotlin/com/framecraft/editor/domain/model/` — `MediaAsset`, `Clip`, `Track`, `Project`, `ProjectFactory`, `IdGenerator`, `ClipAnchor`, `OverlayTransform`, `Canvas` (aspect, background, fit, base placement), `AnchorResolver`.
- Tests: `domain/src/test/kotlin/com/framecraft/editor/domain/time/FrameRateTest.kt`, `TimecodeTest.kt`, `domain/src/test/kotlin/com/framecraft/editor/domain/model/ProjectTest.kt`, `domain/src/test/kotlin/com/framecraft/editor/domain/command/OverlayCommandsTest.kt`, `AudioCommandsTest.kt`, `CanvasCommandsTest.kt`.

## Acceptance or verification criteria

- [x] Frame math round-trips at fractional rates (29.97) and never returns negative time.
- [x] Overlapping clips on one track are rejected.
- [x] Magnetic tracks reject gaps; duplicate IDs and unknown asset references are rejected.
- [x] Anchored children resolve to parent start plus offset.
- [x] Anchor cycles are rejected.
- [x] Ripple on a magnetic track shifts later clips and their anchored children.
- [x] Invalid volume, fades, and clip-to-track pairings are rejected (`AudioCommandsTest`).
- [x] A new project has the original aspect and a black background, and canvas placements off the base are rejected (`CanvasCommandsTest`).

## Remaining gaps and unknowns
