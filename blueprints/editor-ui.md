# Editor UI

## Outcome or responsibility

Own the editor screen: preview surface, timeline rendering, zoom and scrub, selection, precision controls (frame step, zoom to playhead, snap toggle with haptics), and the contextual tool bar.

## Current verified status

**Status:** Partial

The home project list (open, rename, delete, unreadable rows), photo-picker import, the editor shell (preview, play and pause, timecode, undo and redo, save and recovery notices), the timeline (fixed center playhead, frame-snapped scrubbing, pinch zoom, zoom to fit, clip thumbnails, tap to select), and the Delete tool are implemented and verified by CI Robolectric tests. Precision controls and the remaining tools follow in later slices.

## Architecture dependencies

- [Module structure and dependency direction](../architecture.md#module-structure-and-dependency-direction)
- [Edit commands and history](../architecture.md#edit-commands-and-history)
- [Threading](../architecture.md#threading)

## Local rules and implications

### Module structure and dependency direction

Screens live in `:app` under feature packages (`home`, `editor`, `timeline`) because the design tokens live in `:app`; they move to `:feature:*` modules if the app module grows too large. Screens reach the engine only through its public classes.

### Edit commands and history

Every gesture that changes the project dispatches a command; the UI never edits model objects directly. `EditorViewModel` applies commands through `OpenProject`, which runs one change at a time and shows a result only while that project is still open. Leaving the editor needs no confirmation because every edit is already saved; a failed save raises a notice instead.

### Threading

Timeline drawing reads derived state so scrubbing does not recompose the whole screen.

## Related blueprints

### Required

- [Timeline model](timeline-model.md) — positions come from the model's resolver.

### Impact checks

- [Media engine](media-engine.md) — when preview controls or playhead sync change.

## Relevant implementation and tests

- `app/src/main/kotlin/com/framecraft/editor/home/` — `HomeScreen` (project list, row menu, rename and delete dialogs, empty state), `HomeViewModel`, `EditedTime`.
- `app/src/main/kotlin/com/framecraft/editor/media/` — `AndroidMediaImporter`, `VideoMetadataParser`.
- `app/src/main/kotlin/com/framecraft/editor/editor/` — `EditorViewModel`, `OpenProject`, `EditorRoute`, `EditorScreen`.
- `app/src/main/kotlin/com/framecraft/editor/timeline/` — `TimelineStrip` (gestures, tap selection, semantics), `TimelineGeometry` (time/pixel math, tick ladder, thumbnail tiling, hit testing), `TimelineDrawing`, `TimelineThumbnails`, `TimelineState`.
- Tests: `app/src/test/kotlin/com/framecraft/editor/MainActivityTest.kt`, `home/HomeScreenTest.kt`, `home/EditedTimeTest.kt`, `editor/EditorScreenTest.kt`, `media/VideoMetadataParserTest.kt`, `timeline/TimelineGeometryTest.kt`, `timeline/TimelineStripTest.kt`.

## Acceptance or verification criteria

- [x] The home screen and editor preview follow `assets/wireframes/home-empty.md` and `assets/wireframes/editor-preview.md`.
- [x] Picked files that cannot be read are skipped and reported; if none can be read, the user is told and returned home.
- [x] The timeline follows `assets/wireframes/timeline-slice3.md`: dragging pauses and seeks only to frame boundaries, and pinching zooms around the playhead.
- [x] The home list follows `assets/wireframes/home-projects.md`: rows open projects, rename rejects blank names, delete asks first, and unreadable projects offer delete only.
- [x] Undo and redo are enabled only when available; tapping a clip selects it, tapping empty space clears the selection, and the Delete tool needs a selection.

## Remaining gaps and unknowns

- Thumbnails show solid surfaceVariant while loading; a loading shimmer is deferred.
- Thumbnail extraction (`ClipThumbnails`) needs a device and has no automated test; tiling and requests are tested.
- `EditorViewModel` has no unit test because its preview player cannot run under Robolectric; save and restore are covered by `ProjectStoreTest`.
- The save-failure and recovered-edits notices have no automated test.
- The timeline does not scroll to bring a selected clip into view.
