# Media Engine

## Outcome or responsibility

Own translation of project snapshots into Media3 compositions for real-time preview and for export, including gap padding, transitions, and the export foreground service.

## Current verified status

**Status:** Planned

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

Planned: `engine/media3/src/main/kotlin/`, `engine/media3/src/test/kotlin/`.

## Acceptance or verification criteria

- [ ] A project with gaps maps to sequences whose durations match the timeline.
- [ ] Preview and export use the same mapper.
- [ ] Export continues with the screen off and reports completion or failure.

## Remaining gaps and unknowns

- Multi-video preview (picture in picture) depends on `MultipleInputVideoGraph` behavior that research flagged as still evolving.
