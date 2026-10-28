# Media Engine

## Outcome or responsibility

Own translation of project snapshots into Media3 compositions for real-time preview and for export, including gap padding, transitions, and the export foreground service.

## Current verified status

**Status:** Partial

`PlaybackPlanner` maps a project to a Media3-free plan (media segments and gaps), `CompositionFactory` turns the plan into a `Composition`, and `ProjectPreview` drives a `CompositionPlayer` shown through `PreviewSurface`. The planner is verified by CI unit tests; export is not built yet (slice 6).

## Architecture dependencies

- [Engine translation](../architecture.md#engine-translation)
- [Time representation](../architecture.md#time-representation)
- [Threading](../architecture.md#threading)
- [Error and recovery](../architecture.md#error-and-recovery)

## Local rules and implications

### Engine translation

One mapper produces the composition for both preview and export. `CompositionPlayer` is an experimental Media3 API; its use is isolated behind an engine interface so it can be swapped.

### Time representation

Media3 APIs take microseconds or milliseconds depending on the call; conversions happen only inside this module.

### Threading

The preview player is created and driven on its application looper. Rebuilding the composition after an edit is debounced so scrubbing stays responsive.

### Error and recovery

Export failures map Media3 error codes to user-facing causes (unsupported codec, storage full, source missing).

## Related blueprints

### Required

- [Timeline model](timeline-model.md) — the engine resolves absolute positions through the model.

### Impact checks

None

## Relevant implementation and tests

- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/plan/PlaybackPlan.kt` — plan types and `PlaybackPlanner`.
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/CompositionFactory.kt` — plan to `Composition` (clipping for video, image durations, `addGap` padding).
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/ProjectPreview.kt`, `PreviewSurface.kt` — preview player and its SurfaceView.
- Tests: `engine/media3/src/test/kotlin/com/framecraft/editor/engine/plan/PlaybackPlannerTest.kt`.

## Acceptance or verification criteria

- [x] A project with gaps maps to sequences whose durations match the timeline.
- [ ] Preview and export use the same mapper.
- [ ] Export continues with the screen off and reports completion or failure.

## Remaining gaps and unknowns

- `CompositionFactory` and `ProjectPreview` have no automated tests; they need a device, and CI runs JVM tests only.
- Multi-video preview (picture in picture) depends on `MultipleInputVideoGraph` behavior that research flagged as still evolving.
