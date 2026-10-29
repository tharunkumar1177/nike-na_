# Look

## Outcome or responsibility

Own a picture clip's look: the filter catalog, the six color adjustments, how a look is stored and kept exact through edits and undo, how the engine draws it on every lane that shows the clip, and the Look tool and sheet that edit it.

## Current verified status

**Status:** Partial

The look model, its commands, and how split and merge treat a look are verified by `LookCommandsTest`. The schema 8 upgrade is verified by `ProjectStoreTest`. That every lane carries the clip's look and that filters force SDR are verified by `LookPlanTest`; the color tables and the adjustment matrix by `LookEffectsTest`. The sheet is verified by `LookSheetTest` and the tool's visibility by `EditorScreenTest`. How looks actually render and export needs a device and is unverified.

## Architecture dependencies

- [Project document model](../architecture.md#project-document-model) — a look is an optional clip field on video and overlay tracks, so older projects read as unchanged pictures.
- [Edit commands and history](../architecture.md#edit-commands-and-history) — every look edit has an exact inverse.
- [Engine translation](../architecture.md#engine-translation) — the effect order, the lanes that carry a look, and the SDR rule for filters.

## Local rules and implications

### Model

- `ClipLook(filterId, filterIntensity, brightness, contrast, saturation, exposure, temperature, tint)` sits on the clip as `look`. Intensity runs 0 to 100 and defaults to 100; each adjustment runs -100 to 100. Only `VIDEO` and `OVERLAY` tracks (`TrackKind.takesLook`) may hold one.
- `LookFilterCatalog` lists 18 filters by stable id in five categories (portrait, scenery, food, vintage, mono). Ids are stored in projects and never reused; display names live in the app.
- A look that changes nothing (`isNeutral`) is stored as none.

### Commands

- `SetClipLook(clipId, look)` refuses a clip off a picture track (`LookNotSupported`) and an unknown filter id (`UnknownLookFilter`); clearing is always allowed.
- `ApplyLookToAll(look)` gives every clip on every video and overlay track the same look as one undo step, and refuses a project with no picture clip (`NoLookTargets`).
- Both invert through `SetLooks`, which writes each clip's previous value back verbatim.
- `SplitClip` gives both parts the look; `MergeClips` requires both parts to share it; extracted sound carries none.

### Engine

- `LookEffects.of(look)` returns the filter's color table, then one `RgbMatrix`, then one `HslAdjustment` for saturation, each only when it changes something.
- `LookLuts` builds a 33-point table from `LookFilterTransforms` blended toward identity by intensity, caching the last 8.
- The matrix scales by 2^(exposure/50) and 2^(contrast/50) around middle gray, offsets by half the brightness, and moves red and blue by up to 20% for temperature and green for tint.
- The base, transition, and blurred background lanes all carry the clip's look; a muted clip keeps its look. `PlaybackPlan.hasLutLook` turns on HDR to SDR tone mapping.

### Editing

- The sheet follows `assets/wireframes/look-slice13.md`. The Look tool shows for any clip on a video or overlay track.
- The Filters tab has category chips, filter chips with None first, and an intensity slider shown only while a filter is chosen; picking a filter resets intensity to 100.
- The Adjust tab has six sliders that settle on 0 within 3 with a haptic tick, and a reset button per slider while it is not 0.
- Apply to all and Reset all wait until the look changes something; Apply to all then says how many clips it reached. Each finished change dispatches one command.

## Related blueprints

### Required

- [Timeline model](timeline-model.md) — the look field is a clip field under its validation.
- [Edit history](edit-history.md) — the look commands and the split and merge rules follow its inverse rules.

### Impact checks

- [Media engine](media-engine.md) — when item building, effect order, or tone mapping change.
- [Editor UI](editor-ui.md) — when the tool row or sheets change.
- [Project persistence](project-persistence.md) — when look fields or command types change.
- [Transitions](transitions.md) — when transition lanes change.

## Relevant implementation and tests

- `domain/src/main/kotlin/com/framecraft/editor/domain/model/` — `ClipLook`, `LookFilter`, and the `look` field of `Clip`.
- `domain/src/main/kotlin/com/framecraft/editor/domain/command/` — `SetClipLook`, `SetLooks`, `ApplyLookToAll`, and the look rule in `MergeClips`.
- `data/project/src/main/kotlin/com/framecraft/editor/data/project/Migration7To8.kt` — the schema 8 upgrade.
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/` — `ColorOps`, `LookFilterTransforms`, `LookLuts`, `LookEffects`, `CompositionFactory`, `plan/PlaybackPlan.kt`, and `plan/TransitionLanes.kt`.
- `app/src/main/kotlin/com/framecraft/editor/editor/` — `LookSheet`, `LookTabs`, `LookLabels`, and the Look tool in `EditorToolsZone` and `EditorTools`.
- Tests: `domain/src/test/kotlin/com/framecraft/editor/domain/command/LookCommandsTest.kt`, `data/project/src/test/kotlin/com/framecraft/editor/data/project/ProjectStoreTest.kt`, `engine/media3/src/test/kotlin/com/framecraft/editor/engine/plan/LookPlanTest.kt`, `engine/media3/src/test/kotlin/com/framecraft/editor/engine/LookEffectsTest.kt`, `app/src/test/kotlin/com/framecraft/editor/editor/LookSheetTest.kt`, `app/src/test/kotlin/com/framecraft/editor/editor/EditorScreenTest.kt`.

## Acceptance or verification criteria

- [x] Looks validate, setting and applying to all store and undo exactly, and split and merge keep looks (`LookCommandsTest`).
- [x] Look commands round-trip through JSON and a schema 7 project opens and saves as schema 8 (`LookCommandsTest`, `ProjectStoreTest`).
- [x] Every lane carries the clip's look and filters force SDR (`LookPlanTest`); tables blend by intensity and the matrix follows the adjustments (`LookEffectsTest`).
- [x] The sheet sends the expected edits (`LookSheetTest`) and the tool shows only where it applies (`EditorScreenTest`).
- [ ] Looks render and export as previewed, including HDR sources and photos (needs a device).

## Remaining gaps and unknowns

- One look per clip; no look layers, keyframed looks, or `.cube` import.
- No sharpen, vignette, grain, highlights, shadows, vibrance, or per-color HSL.
- Thumbnails and the timeline do not show looks; there is no hold-to-compare.
- Output color space, HDR sources with matrix-only looks, and color tables on photos are unverified on hardware.
