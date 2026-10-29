# Masks and chroma key

## Outcome or responsibility

Own a picture clip's mask and chroma key: the six mask shapes and their placement, feather, rounding, and inversion, the key color and its strength, softness, and spill removal, how both are stored and kept exact through edits and undo, how the engine turns them into alpha on every lane that shows the clip, and the Mask and Chroma key tools, docked panels, preview handles, and eyedropper that edit them.

## Current verified status

**Status:** Partial

The mask and key models, their commands, and how split, merge, and extract treat them are verified by `MaskKeyCommandsTest`. The schema 10 upgrade is verified by `ProjectStoreTest`. The mask distance and alpha math, the shader uniforms, the outline tracing, and the effect order are verified by `MaskMathTest`; the key math by `ChromaKeyMathTest`; which lanes carry them and that they force the composited path by `MaskKeyPlanTest`. The panels are verified by `MaskPanelTest` and `ChromaPanelTest`, the picture and gesture math by `PictureGeometryTest`, and the tools by `EditorScreenTest`. How masks and keys actually render, export, and pick colors needs a device and is unverified.

## Architecture dependencies

- [Project document model](../architecture.md#project-document-model) — mask and chroma key are optional clip fields on video and overlay tracks, so older projects read as unmasked, unkeyed pictures.
- [Edit commands and history](../architecture.md#edit-commands-and-history) — every mask and key edit has an exact inverse.
- [Engine translation](../architecture.md#engine-translation) — the effect order, the lanes that carry them, and the composited rule for clips with alpha.

## Local rules and implications

### Model

- `ClipMask(shape, centerX, centerY, halfWidth, halfHeight, rotationDegrees, featherPercent, cornerPercent, invert)` sits on the clip as `mask`. Shapes are `LINEAR`, `MIRROR`, `CIRCLE`, `RECTANGLE`, `HEART`, and `STAR`.
- The center runs -1 to 1 across the picture's edges, x right and y down. Half-sizes are in picture half-heights, so a shape keeps its proportions on any aspect, and run up to 2. Rotation is clockwise in degrees. Feather and corner rounding run 0 to 100.
- `ClipMask.defaultFor(shape)` is where a newly chosen shape starts; switching shape keeps the center, turn, and inversion.
- `ChromaKey(keyColorArgb, tolerancePercent, softnessPercent, spillPercent)` sits on the clip as `chromaKey`. The color must be opaque; strength defaults to 50, softness to 20, and spill removal to 50. A key with no strength (`isNeutral`) is stored as none.
- Only `VIDEO` and `OVERLAY` tracks (`TrackKind.takesLook`) may hold either.

### Commands

- `SetClipMask(clipId, mask)` and `SetClipChromaKey(clipId, key)` refuse a clip off a picture track (`MaskNotSupported`, `ChromaKeyNotSupported`); clearing is always allowed. Each is its own inverse, writing the previous value back verbatim.
- `SplitClip` gives both parts the mask and key; `MergeClips` requires both parts to share look, mask, and key; extracted sound carries none.

### Engine

- `SegmentVideoEffects.of(segment)` orders a clip's effects as chroma key, then mask, then look, so the key reads the source colors and the look never tints what was keyed out.
- `ChromaKeyEffect` measures BT.601 chroma distance to the key color. Strength sets the cut (up to 0.4 of the chroma range), softness the ramp below it, and spill removal pulls the key's dominant channel toward the larger of the other two across the whole picture.
- `ClipMaskEffect` evaluates a signed distance per shape in aspect-corrected, rotated picture space: linear keeps the left side of its edge, mirror the band between two edges, and the closed shapes their inside. Feather widens a smoothstep up to a quarter of the picture height, and invert flips the result.
- Base, transition, and overlay lanes carry both; the blurred background keeps only the look. Any masked or keyed clip forces the composited path, which blends straight alpha over the lanes below.
- `MaskOutline` traces closed shapes for the preview handles from the same distance math, and `FrameExtractorKeyColorProbe` reads one source frame (video through a one-shot `FrameExtractor`, photos through `ImageDecoder`) for the eyedropper.

### Editing

- The panels follow `assets/wireframes/masks-slice15.md`. Mask and Chroma key sit after Look and show for any clip on a video or overlay track, highlighted while the clip has one.
- Both panels dock below the preview without blocking it, so handles and the eyedropper work while settings stay open; opening one closes the other. Done and back close them.
- The mask panel has a chip per shape with None first, an invert switch, a feather slider, and a roundness slider for rectangles. While the playhead is on the clip the preview shows the outline or edge lines with move, pinch, and twist gestures and TalkBack actions; each finished gesture or change dispatches one command.
- The chroma key panel has the key swatch, Pick color, and green and blue screen presets, then strength, softness, and spill removal sliders and Remove. Pick color waits for a tap on the clip's picture, averages a 5 by 5 patch of the source frame under the playhead, and saves it; a frame that cannot be read says so.

## Related blueprints

### Required

- [Timeline model](timeline-model.md) — mask and key are clip fields under its validation.
- [Edit history](edit-history.md) — the mask and key commands and the split and merge rules follow its inverse rules.
- [Look](look.md) — shares the picture-track rule, the effect chain, and the tool row group.

### Impact checks

- [Media engine](media-engine.md) — when item building, effect order, or the composited rule change.
- [Editor UI](editor-ui.md) — when the tool row, panels, or preview layers change.
- [Project persistence](project-persistence.md) — when mask or key fields or command types change.
- [Transitions](transitions.md) — when transition lanes change.

## Relevant implementation and tests

- `domain/src/main/kotlin/com/framecraft/editor/domain/model/` — `ClipMask`, `ChromaKey`, and the `mask` and `chromaKey` fields of `Clip`.
- `domain/src/main/kotlin/com/framecraft/editor/domain/command/` — `SetClipMask`, `SetClipChromaKey`, and the rule in `MergeClips`.
- `data/project/src/main/kotlin/com/framecraft/editor/data/project/Migration9To10.kt` — the schema 10 upgrade.
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/` — `MaskMath`, `MaskShader`, `MaskOutline`, `ChromaKeyMath`, `ChromaKeyShader`, `SegmentVideoEffects`, `KeyColorProbe`, `CompositionFactory`, `plan/PlaybackPlan.kt`, and `plan/TransitionLanes.kt`.
- `app/src/main/kotlin/com/framecraft/editor/editor/` — `MaskPanel`, `ChromaPanel`, `DockedPanel`, `PictureBox`, `MaskGeometry`, `MaskHandlesLayer`, `KeyPickLayer`, `KeyColorMath`, `PictureEffectLayers`, and the tools in `EditorToolsZone` and `EditorTools`.
- Tests: `domain/src/test/kotlin/com/framecraft/editor/domain/command/MaskKeyCommandsTest.kt`, `data/project/src/test/kotlin/com/framecraft/editor/data/project/ProjectStoreTest.kt`, `engine/media3/src/test/kotlin/com/framecraft/editor/engine/MaskMathTest.kt`, `engine/media3/src/test/kotlin/com/framecraft/editor/engine/ChromaKeyMathTest.kt`, `engine/media3/src/test/kotlin/com/framecraft/editor/engine/plan/MaskKeyPlanTest.kt`, `app/src/test/kotlin/com/framecraft/editor/editor/MaskPanelTest.kt`, `app/src/test/kotlin/com/framecraft/editor/editor/ChromaPanelTest.kt`, `app/src/test/kotlin/com/framecraft/editor/editor/PictureGeometryTest.kt`, `app/src/test/kotlin/com/framecraft/editor/editor/EditorScreenTest.kt`.

## Acceptance or verification criteria

- [x] Masks and keys validate, set, clear, and undo exactly, are refused off picture tracks, and survive split, merge, and extract as specified (`MaskKeyCommandsTest`).
- [x] Both commands round-trip through JSON and a schema 9 project opens and saves as schema 10 (`MaskKeyCommandsTest`, `ProjectStoreTest`).
- [x] Each shape's distance, feather, inversion, and aspect handling, the shader uniforms, and the key's cut, ramp, and spill follow the rules above (`MaskMathTest`, `ChromaKeyMathTest`).
- [x] Masked and keyed clips force compositing and carry their effects on every lane but the blurred background (`MaskKeyPlanTest`).
- [x] The panels send the expected edits, gestures and the eyedropper map screen points to the picture, and the tools show only where they apply (`MaskPanelTest`, `ChromaPanelTest`, `PictureGeometryTest`, `EditorScreenTest`).
- [ ] Masks and keys render and export as previewed, and the eyedropper picks the color under the tap (needs a device).

## Remaining gaps and unknowns

- One mask and one key per clip; no keyframed masks, freehand or text masks, or mask feather that varies along the edge.
- Spill removal is global, not limited to the keyed edge; there is no edge shrink or key cleanup view.
- The eyedropper shows a reticle but no magnifier, and reads the source frame, so it ignores the clip's look.
- Alpha output, composited performance with several keyed lanes, and HDR sources are unverified on hardware.
