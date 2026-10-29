# Media Engine

## Outcome or responsibility

Own translation of project snapshots into Media3 compositions for real-time preview and for export, including gap padding, transitions, export encoding, failure mapping, and publishing exported files.

## Current verified status

**Status:** Partial

`PlaybackPlanner` maps a project to a Media3-free plan (media segments and gaps), `CompositionFactory` turns the plan into a `Composition`, and `ProjectPreview` drives a `CompositionPlayer` shown through `PreviewSurface`. `ProjectExporter` runs the same `CompositionFactory` output through a Media3 `Transformer` and publishes the result with `GallerySaver`. The planner and the export failure mapping are verified by CI unit tests; the exporter, saver, and precheck need a device and are unverified.

## Architecture dependencies

- [Engine translation](../architecture.md#engine-translation)
- [Time representation](../architecture.md#time-representation)
- [Threading](../architecture.md#threading)
- [Media access](../architecture.md#media-access)
- [Error and recovery](../architecture.md#error-and-recovery)

## Local rules and implications

### Engine translation

One mapper produces the composition for both preview and export: export calls `CompositionFactory.create(plan, videoEffects)` with the effects from `ExportEffects` (a frame-drop effect to the chosen rate, then a scale-to-fit `Presentation` at the output size). `CompositionPlayer` is an experimental Media3 API; its use is isolated behind an engine interface so it can be swapped. The output canvas follows the first clip's displayed size (rotation aware), matching the preview. Export encodes H.264 video and AAC audio only.

### Time representation

Media3 APIs take microseconds or milliseconds depending on the call; conversions happen only inside this module.

### Threading

The preview player is created and driven on its application looper. Rebuilding the composition after an edit is debounced so scrubbing stays responsive. `ProjectExporter` is created and driven on the main thread; Transformer progress is polled every 500 ms and capped at 99 percent until the file is saved. Copying into `MediaStore` runs on the IO dispatcher.

### Media access

The exporter writes `cache/exports/export.mp4`, then `GallerySaver` inserts a pending row in `Movies/Framecraft`, copies the file, and clears `IS_PENDING`. A failed copy deletes the row. Because of the temporary file, `ExportPrecheck` requires free cache space of 2.2 times the size estimate. File names are `<project name> yyyy-MM-dd HH.mm.ss.mp4`, with unsafe characters removed and `Framecraft` as the fallback.

### Error and recovery

`ExportFailureMapper` maps Media3 error codes and causes to `ExportFailure`: a no-space cause anywhere in the chain is `STORAGE_FULL`; codes 3003 and 4003 are `UNSUPPORTED_CODEC`; the 2xxx family is `SOURCE_MISSING`; the remaining 3xxx codes are `DECODE_FAILED`; 4000 to 7999 is `ENCODE_FAILED`; anything else is `UNKNOWN`. Before starting, `ExportPrecheck` reports `NOTHING_TO_EXPORT` for an empty project, `UNSUPPORTED_CODEC` when no AVC encoder supports the size, and `STORAGE_FULL` when space is short. Cancelling after the copy deletes the published entry.

## Related blueprints

### Required

- [Timeline model](timeline-model.md) — the engine resolves absolute positions through the model.

### Impact checks

- [Editor UI](editor-ui.md) — when `ExportState` or `ExportFailure` values change, because the export sheet and notifications render them.

## Relevant implementation and tests

- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/plan/PlaybackPlan.kt` — plan types and `PlaybackPlanner`.
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/CompositionFactory.kt` — plan to `Composition` (clipping for video, image durations, `addGap` padding, optional video effects).
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/ProjectPreview.kt`, `PreviewSurface.kt` — preview player (play, pause, seek, scrubbing mode) and its SurfaceView.
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/ClipThumbnails.kt` — timeline thumbnails via Media3 `FrameExtractor` (video) and `ImageDecoder` (images), cached in memory.
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/export/` — `ProjectExporter`, `ExportState`, `ExportFailureMapper`, `ExportEffects`, `ExportPrecheck`, `GallerySaver`.
- `domain/src/main/kotlin/com/framecraft/editor/domain/export/` — `ExportSettings`, `ExportPlanner` (offered resolutions and frame rates, defaults, output size, bitrate, size estimate).
- Tests: `engine/media3/src/test/kotlin/com/framecraft/editor/engine/plan/PlaybackPlannerTest.kt`, `engine/media3/src/test/kotlin/com/framecraft/editor/engine/export/ExportFailureMapperTest.kt`, `domain/src/test/kotlin/com/framecraft/editor/domain/export/ExportPlannerTest.kt`.

## Acceptance or verification criteria

- [x] A project with gaps maps to sequences whose durations match the timeline.
- [x] Preview and export use the same mapper (`ProjectExporter` builds its composition with `CompositionFactory`; verified by code inspection).
- [x] Export settings never offer a resolution above the largest source or a frame rate above the project rate; bitrate and size estimates match `ExportPlannerTest`.
- [x] Media3 error codes and full-disk causes map to the documented failure causes (`ExportFailureMapperTest`).
- [ ] Export continues with the screen off and reports completion or failure (implemented in `ExportService`; needs a device to verify).

## Remaining gaps and unknowns

- `CompositionFactory`, `ProjectPreview`, `ProjectExporter`, `GallerySaver`, and `ExportPrecheck` have no automated tests; they need a device, and CI runs JVM tests only.
- Pending `MediaStore` rows left by a killed process are not swept; Android removes stale pending rows after about a week.
- The frame-drop effect is always applied, so sources faster than the chosen rate are capped; HEVC and other codecs are not offered.
- Multi-video preview (picture in picture) depends on `MultipleInputVideoGraph` behavior that research flagged as still evolving.
