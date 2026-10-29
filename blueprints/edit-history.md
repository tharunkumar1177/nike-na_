# Edit History

## Outcome or responsibility

Own edit commands and linear undo/redo: every user edit becomes a validated, invertible command, and history survives restarts.

## Current verified status

**Status:** Implemented

The commands `RenameProject`, `DeleteClip`, and `AddClip` and the `EditHistory` stack are implemented in `:domain` and verified by `EditCommandTest` and `EditHistoryTest`. Persistent undo is verified by `ProjectStoreTest`.

## Architecture dependencies

- [Edit commands and history](../architecture.md#edit-commands-and-history)
- [Project persistence and recovery](../architecture.md#project-persistence-and-recovery)

## Local rules and implications

### Edit commands and history

- `EditCommand` is a sealed, `@Serializable` interface; each command has a `@SerialName` stored under the `type` discriminator. Command types and fields are additive only, so old journals keep replaying.
- `applyTo(project)` returns `Applied(project, inverse)` or `Rejected(reason)`. Model invariant failures thrown while building a track become `Rejected(ModelInvariant)`.
- A command that creates objects carries their generated ids, so replay is deterministic.
- On a magnetic track, `DeleteClip` shifts later clips left and `AddClip` shifts clips at or after the new start right.
- A new command after undo discards the redo branch.

### Project persistence and recovery

- The undo position is saved in `history.cursor` next to the journal, so reopening restores undo and redo availability.
- History depth: compaction keeps at least the latest 100 records behind the cursor; older edits fold into the snapshot and can no longer be undone.

## Related blueprints

### Required

- [Timeline model](timeline-model.md) — commands transform and validate the model.

### Impact checks

- [Project persistence](project-persistence.md) — when a command type is added or its fields change.

## Relevant implementation and tests

- `domain/src/main/kotlin/com/framecraft/editor/domain/command/` — `EditCommand`, `RenameProject`, `DeleteClip`, `AddClip`, `EditHistory`.
- `domain/src/test/kotlin/com/framecraft/editor/domain/command/` — `EditCommandTest`, `EditHistoryTest`.
- `data/project/src/test/kotlin/com/framecraft/editor/data/project/ProjectStoreTest.kt` — undo after restart.

## Acceptance or verification criteria

- [x] Applying a command then its inverse yields the original project.
- [x] Invalid commands return a typed failure and leave the project unchanged.
- [x] Undo and redo are available after reopening a project.

## Remaining gaps and unknowns

- Drag gestures that coalesce into one command arrive with trim and reorder in slice 5.
