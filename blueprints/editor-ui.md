# Editor UI

## Outcome or responsibility

Own the editor screen: preview surface, timeline rendering, zoom and scrub, selection, precision controls (frame step, zoom to playhead, snap toggle with haptics), and the contextual tool bar.

## Current verified status

**Status:** Partial

The home screen, photo-picker import, the editor shell (preview, play and pause, timecode, discard), and the read-only timeline (fixed center playhead, frame-snapped scrubbing, pinch zoom, zoom to fit, clip thumbnails) are implemented and verified by CI Robolectric tests. Selection, precision controls, and tools follow in later slices.

## Architecture dependencies

- [Module structure and dependency direction](../architecture.md#module-structure-and-dependency-direction)
- [Edit commands and history](../architecture.md#edit-commands-and-history)
- [Threading](../architecture.md#threading)

## Local rules and implications

### Module structure and dependency direction

Screens live in `:app` under feature packages (`home`, `editor`, `timeline`) because the design tokens live in `:app`; they move to `:feature:*` modules if the app module grows too large. Screens reach the engine only through its public classes.

### Edit commands and history

Every gesture that changes the project dispatches a command; the UI never edits model objects directly.

### Threading

Timeline drawing reads derived state so scrubbing does not recompose the whole screen.

## Related blueprints

### Required

- [Timeline model](timeline-model.md) — positions come from the model's resolver.

### Impact checks

- [Media engine](media-engine.md) — when preview controls or playhead sync change.

## Relevant implementation and tests

- `app/src/main/kotlin/com/framecraft/editor/home/HomeScreen.kt` — empty state and new-project action.
- `app/src/main/kotlin/com/framecraft/editor/media/` — `AndroidMediaImporter`, `VideoMetadataParser`.
- `app/src/main/kotlin/com/framecraft/editor/editor/` — `EditorViewModel`, `EditorRoute`, `EditorScreen`.
- `app/src/main/kotlin/com/framecraft/editor/timeline/` — `TimelineStrip` (gestures, semantics), `TimelineGeometry` (time/pixel math, tick ladder, thumbnail tiling), `TimelineDrawing`, `TimelineThumbnails`, `TimelineState`.
- Tests: `app/src/test/kotlin/com/framecraft/editor/MainActivityTest.kt`, `editor/EditorScreenTest.kt`, `media/VideoMetadataParserTest.kt`, `timeline/TimelineGeometryTest.kt`, `timeline/TimelineStripTest.kt`.

## Acceptance or verification criteria

- [x] The home screen and editor preview follow `assets/wireframes/home-empty.md` and `assets/wireframes/editor-preview.md`.
- [x] Picked files that cannot be read are skipped and reported; if none can be read, the user is told and returned home.
- [x] The timeline follows `assets/wireframes/timeline-slice3.md`: dragging pauses and seeks only to frame boundaries, and pinching zooms around the playhead.

## Remaining gaps and unknowns

- Thumbnails show solid surfaceVariant while loading; a loading shimmer is deferred.
- Thumbnail extraction (`ClipThumbnails`) needs a device and has no automated test; tiling and requests are tested.
- Visual design, layout, and gesture map for later slices are pending their design plans.
