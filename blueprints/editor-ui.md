# Editor UI

## Outcome or responsibility

Own the editor screen: preview surface, timeline rendering, zoom and scrub, selection, precision controls (frame step, zoom to playhead, snap toggle with haptics), and the contextual tool bar.

## Current verified status

**Status:** Planned

## Architecture dependencies

- [Module structure and dependency direction](../architecture.md#module-structure-and-dependency-direction)
- [Edit commands and history](../architecture.md#edit-commands-and-history)
- [Threading](../architecture.md#threading)

## Local rules and implications

### Module structure and dependency direction

Screens live in `:feature:*` modules and reach the engine only through its interfaces.

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

Planned: `feature/timeline/`, `feature/import/`.

## Acceptance or verification criteria

- [ ] Each screen follows its approved design plan in `assets/wireframes/`.

## Remaining gaps and unknowns

- Visual design, layout, and gesture map are pending design plans per slice.
