# Editor UI

## Outcome or responsibility

Own the editor screen: preview surface, timeline rendering, zoom and scrub, selection, precision controls (frame step, zoom to playhead, snap toggle with haptics), and the contextual tool bar.

## Current verified status

**Status:** Partial

The home screen, photo-picker import, and the editor shell (preview, play and pause, timecode, discard) are implemented and verified by CI Robolectric tests. Timeline, selection, and tools follow in later slices.

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
- Tests: `app/src/test/kotlin/com/framecraft/editor/MainActivityTest.kt`, `editor/EditorScreenTest.kt`, `media/VideoMetadataParserTest.kt`.

## Acceptance or verification criteria

- [x] The home screen and editor preview follow `assets/wireframes/home-empty.md` and `assets/wireframes/editor-preview.md`.
- [x] Picked files that cannot be read are skipped and reported; if none can be read, the user is told and returned home.

## Remaining gaps and unknowns

- Visual design, layout, and gesture map are pending design plans per slice.
