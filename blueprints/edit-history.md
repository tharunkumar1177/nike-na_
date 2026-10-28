# Edit History

## Outcome or responsibility

Own edit commands and linear undo/redo: every user edit becomes a validated, invertible command, and history survives restarts.

## Current verified status

**Status:** Planned

## Architecture dependencies

- [Edit commands and history](../architecture.md#edit-commands-and-history)
- [Project persistence and recovery](../architecture.md#project-persistence-and-recovery)

## Local rules and implications

### Edit commands and history

Commands are serializable data (kotlinx.serialization) so they can be journaled. Drag gestures emit a preview state while moving and commit one command on release. A new command after undo discards the redo branch.

### Project persistence and recovery

The history position (undo cursor) is persisted with the journal so reopening a project restores both content and undo/redo availability.

## Related blueprints

### Required

- [Timeline model](timeline-model.md) — commands transform and validate the model.

### Impact checks

- [Project persistence](project-persistence.md) — when a command type is added or its fields change.

## Relevant implementation and tests

Planned: `domain/src/main/kotlin/.../command/`, `domain/src/test/kotlin/.../command/`.

## Acceptance or verification criteria

- [ ] Applying a command then its inverse yields the original project.
- [ ] Invalid commands return a typed failure and leave the project unchanged.
- [ ] Undo and redo are available after reopening a project.

## Remaining gaps and unknowns

- Maximum history depth and compaction of very long histories are undecided.
