# Media Engine

## Outcome or responsibility

Own translation of project snapshots into Media3 compositions for real-time preview and for export, including gap padding, transitions, export encoding, failure mapping, and publishing exported files.

## Current verified status

**Status:** Partial

`PlaybackPlanner` maps a project to a Media3-free plan (media segments and gaps, with overlay sequences before the base, audio sequences last, each segment's volume and fades, base placement spans, the canvas shape, and the background), `CompositionFactory` turns the plan into a `Composition` with `CanvasCompositorSettings` when the plan is composited and each clip's sound chain from `AudioItemEffects` (see [audio](audio.md)), with text drawn by one `OverlayEffect` of `TextOverlay`s ahead of any export effects (see [text](text.md)), and `ProjectPreview` drives a `CompositionPlayer` built on a multiple-input video graph and shown through `PreviewSurface`. `ProjectExporter` runs the same `CompositionFactory` output through a Media3 `Transformer` and publishes the result with `GallerySaver`. The planner and the export failure mapping are verified by CI unit tests; the exporter, saver, and precheck need a device and are unverified.

## Architecture dependencies

- [Engine translation](../architecture.md#engine-translation)
- [Time representation](../architecture.md#time-representation)
- [Threading](../architecture.md#threading)
- [Media access](../architecture.md#media-access)
- [Error and recovery](../architecture.md#error-and-recovery)

## Local rules and implications

### Engine translation

One mapper produces the composition for both preview and export: export calls `CompositionFactory.create(plan, videoEffects)` with the effects from `ExportEffects` (a frame-drop effect to the chosen rate, then a scale-to-fit `Presentation` at the output size). `CompositionPlayer` is an experimental Media3 API; its use is isolated behind an engine interface so it can be swapped. With the original aspect, the output canvas follows the first base clip's displayed size (rotation aware), matching the preview; a chosen aspect keeps that clip's short side and sets the long side from the ratio (`CanvasLayout.canvasSize`), and `ExportPlanner` sizes the export to the same ratio. Export encodes H.264 video and AAC audio only. Audio sequences are built with the audio track type only; a sound-only item drops its video and a muted picture item drops its audio. The output canvas never comes from an audio lane.

Media3's compositor draws the first registered picture sequence on top and times each composed frame by it, waits for a frame from every picture input, and feeds sequence gaps as small opaque black frames. So the planner orders overlays (top lane first) before the base and pads every picture sequence to the timeline end. A plan is composited when it has overlays, a base clip with a placement, or a canvas other than the default; then `CompositionFactory` registers a transparent still at the project frame rate first as the timing clock, then the picture sequences, then a background sequence when the background is not black, then the sound sequences. `CanvasLayout.inputsOf` gives the same order as `CompositorInput` values, and `CanvasCompositorSettings` hides the clock and every frame that no clip covers, and sizes the canvas from the first base input. Projects with the default canvas and no overlays or placements keep the single-sequence path.

Backgrounds: a solid color is a still of a 1 x 1 PNG data URI built by `SolidPng` and stretched over the canvas. Blur is a silent copy of the base track's segments, each reduced to 360 pixels high by a `Presentation` and softened by `GaussianBlur` (sigma 12) so the blur strength does not depend on the source resolution, then drawn filling the canvas.

Base clips are placed per frame from their `BaseSpan`s: fit or fill picks the min or max factor between canvas and input size (`CanvasLayout.fitFactor`), then the clip's own center, scale, and turn apply as for overlays. `placementSettings` in `CanvasCompositorSettings` holds that shared mapping and is also used by text.

Overlay sequences are placed per frame by `CanvasCompositorSettings`: the span covering the frame time gives the placement, the domain's y-down clockwise placement is flipped to Media3's y-up counter-clockwise anchors, and the scale is multiplied by a fit factor so scale 1 fits the canvas whatever the overlay's pixel size. Media3 draws an overlay at its pixel size relative to the background, and the input sizes it reports for each frame supply that factor.

### Time representation

Media3 APIs take microseconds or milliseconds depending on the call; conversions happen only inside this module.

### Threading

The preview player is created and driven on its application looper. Rebuilding the composition after an edit is debounced so scrubbing stays responsive. `ProjectExporter` is created and driven on the main thread; Transformer progress is polled every 500 ms and capped at 99 percent until the file is saved. Copying into `MediaStore` runs on the IO dispatcher.

### Media access

The exporter writes `cache/exports/export.mp4`, then `GallerySaver` inserts a pending row in `Movies/Framecraft`, copies the file, and clears `IS_PENDING`. A failed copy deletes the row. Because of the temporary file, `ExportPrecheck` requires free cache space of 2.2 times the size estimate. File names are `<project name> yyyy-MM-dd HH.mm.ss.mp4`, with unsafe characters removed and `Framecraft` as the fallback.

### Error and recovery

`ExportFailureMapper` maps Media3 error codes and causes to `ExportFailure`: a no-space cause anywhere in the chain is `STORAGE_FULL`; codes 3003 and 4003 are `UNSUPPORTED_CODEC`; the 2xxx family is `SOURCE_MISSING`; the remaining 3xxx codes are `DECODE_FAILED`; 4000 to 7999 is `ENCODE_FAILED`; anything else is `UNKNOWN`. Before starting, `ExportPrecheck` reports `NOTHING_TO_EXPORT` for an empty project, `UNSUPPORTED_CODEC` when no AVC encoder supports the size, and `STORAGE_FULL` when space is short. Cancelling after the copy deletes the published entry.

### Speed

- `PlanSegment.Media` carries `speed`, `sourceSpanMicros`, and `maintainPitch`. `CompositionFactory` clips each item to its source span and, for a sped clip, sets `SpeedParameters` from `SpeedProviders` and caps a sped-up picture at the project frame rate (`SpeedProvidersTest`, `PlaybackPlannerTest`). Details belong to [clip-speed](clip-speed.md).

### Transitions

- A base track with playing transitions becomes up to three sequences from `TransitionLanes`, registered as incoming lane, base, then backdrop; `CompositorInput.Incoming` and `CompositorInput.Backdrop` are shown only during their windows and moved by `TransitionMotion` (`TransitionPlanTest`, `TransitionMotionTest`). Details belong to [transitions](transitions.md).
- Media segments carry the clip's look, and every item that draws a clip (base, incoming lane, blurred background, muted or not) gets `LookEffects.of(look)`: color table, then color matrix, then saturation. A project with any filter (`PlaybackPlan.hasLutLook`) tone-maps HDR to SDR in OpenGL even when not composited (`LookPlanTest`, `LookEffectsTest`). Details belong to [look](look.md).

## Related blueprints

### Required

- [Timeline model](timeline-model.md) — the engine resolves absolute positions through the model.

### Impact checks

- [Editor UI](editor-ui.md) — when `ExportState` or `ExportFailure` values change, because the export sheet and notifications render them.

## Relevant implementation and tests

- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/plan/PlaybackPlan.kt` — plan types and `PlaybackPlanner`.
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/CompositionFactory.kt` — plan to `Composition` (clipping for video, image durations, `addGap` padding, optional video effects, the clock sequence, overlay compositor settings, HDR tone mapping when compositing).
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/CanvasCompositorSettings.kt` — per-frame overlay, base, and background placement and output size.
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/plan/CanvasLayout.kt` — compositor input order, canvas size, and fit and fill factors.
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/SolidPng.kt` — 1 x 1 PNG data URIs for solid backgrounds.
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/ProjectPreview.kt`, `PreviewSurface.kt` — preview player (play, pause, seek, scrubbing mode) and its SurfaceView.
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/AudioItemEffects.kt`, `GainEnvelope.kt`, `ClampingGainProcessor.kt`, `AudioWaveforms.kt`, `WaveformBucketer.kt` — per-clip sound chain and timeline waveforms; rules in [audio](audio.md).
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/plan/AudioMix.kt` — lane volume and voiceover ducking folded into each segment when the plan is built; rules in [voiceover](voiceover.md).
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/ClipThumbnails.kt` — timeline thumbnails via Media3 `FrameExtractor` (video) and `ImageDecoder` (images), cached in memory.
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/export/` — `ProjectExporter`, `ExportState`, `ExportFailureMapper`, `ExportEffects`, `ExportPrecheck`, `GallerySaver`.
- `domain/src/main/kotlin/com/framecraft/editor/domain/export/` — `ExportSettings`, `ExportPlanner` (offered resolutions and frame rates, defaults, output size, bitrate, size estimate).
- Tests: `engine/media3/src/test/kotlin/com/framecraft/editor/engine/plan/PlaybackPlannerTest.kt`, `plan/CanvasLayoutTest.kt`, `SolidPngTest.kt`, `engine/media3/src/test/kotlin/com/framecraft/editor/engine/export/ExportFailureMapperTest.kt`, `engine/media3/src/test/kotlin/com/framecraft/editor/engine/AudioGainTest.kt`, `WaveformBucketerTest.kt`, `domain/src/test/kotlin/com/framecraft/editor/domain/export/ExportPlannerTest.kt`.

## Acceptance or verification criteria

- [x] A project with gaps maps to sequences whose durations match the timeline.
- [x] Overlay tracks plan as sequences before the base video from the top lane down, padded to the end, each with the placement spans of its clips (`PlaybackPlannerTest`).
- [ ] Overlays render at their placement in preview and export (needs a device).
- [x] A canvas choice or a base placement turns on compositing, and backgrounds plan as solid or blurred base copies (`PlaybackPlannerTest`).
- [x] Compositor inputs follow registration order, the canvas keeps the base short side with even sides, and fit and fill pick the min and max factor (`CanvasLayoutTest`).
- [x] Solid background PNGs decode to the requested color (`SolidPngTest`).
- [x] A chosen aspect sets the export size, such as 1080 x 1920 for 9:16 (`ExportPlannerTest`).
- [ ] Canvas shapes, backgrounds, and base placement render as planned in preview and export (needs a device).
- [x] Audio lanes plan as sequences after the base with each clip's volume and fades, and picture sequences pad to the end of the latest track (`PlaybackPlannerTest`).
- [x] Preview and export use the same mapper (`ProjectExporter` builds its composition with `CompositionFactory`; verified by code inspection).
- [x] Export settings never offer a resolution above the largest source or a frame rate above the project rate; bitrate and size estimates match `ExportPlannerTest`.
- [x] Media3 error codes and full-disk causes map to the documented failure causes (`ExportFailureMapperTest`).
- [ ] Export continues with the screen off and reports completion or failure (implemented in `ExportService`; needs a device to verify).

## Remaining gaps and unknowns

- `CompositionFactory`, `ProjectPreview`, `ProjectExporter`, `GallerySaver`, and `ExportPrecheck` have no automated tests; they need a device, and CI runs JVM tests only.
- Pending `MediaStore` rows left by a killed process are not swept; Android removes stale pending rows after about a week.
- The frame-drop effect is always applied, so sources faster than the chosen rate are capped; HEVC and other codecs are not offered.
- Overlay preview and export depend on `MultipleInputVideoGraph` and `VideoCompositorSettings`, which research flagged as still evolving. The fit factor assumes the compositor asks for the output size before placing each frame's inputs; if a Media3 update changes that, overlays fall back to Media3's pixel-relative size. The overlay order, the clock sequence (a data-URI PNG), and hidden gaps follow the Media3 1.11.1 compositor source; slices 7 and 8 drew overlays under the base, and the fix has not yet been seen on a device.
- The blurred background decodes the base media a second time, which costs decoder capacity on low-end phones; this has not been measured on a device.
- Per-clip volume and fades are applied by audio processors inside Media3; whether preview and export sound identical needs a device to verify.
