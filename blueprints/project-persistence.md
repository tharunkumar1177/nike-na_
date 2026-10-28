# Project Persistence

## Outcome or responsibility

Own project storage: the project list, the versioned JSON snapshot, the append-only command journal, autosave, crash recovery, and schema migration.

## Current verified status

**Status:** Planned

## Architecture dependencies

- [Project persistence and recovery](../architecture.md#project-persistence-and-recovery)
- [Project document model](../architecture.md#project-document-model)
- [Error and recovery](../architecture.md#error-and-recovery)

## Local rules and implications

### Project persistence and recovery

Projects live in app-private storage, one directory per project ID. Journal writes are flushed before an edit is reported complete; compaction runs off the main thread.

### Project document model

Migrations are pure functions from schema version N to N+1, each covered by a test with a fixture of the older format.

### Error and recovery

A corrupt snapshot or unreadable journal shows the user a recovery message and keeps the original files untouched for diagnosis.

## Related blueprints

### Required

- [Timeline model](timeline-model.md) — defines what is serialized.
- [Edit history](edit-history.md) — defines journaled commands.

### Impact checks

None

## Relevant implementation and tests

Planned: `data/project/src/main/kotlin/`, `data/project/src/test/kotlin/`.

## Acceptance or verification criteria

- [ ] Killing the process mid-edit loses at most the in-flight gesture.
- [ ] A torn final journal line is discarded on recovery.
- [ ] Snapshot writes are atomic.
- [ ] Older schema versions open; newer ones show a clear error.

## Remaining gaps and unknowns

- Whether to copy imported media into the project (self-contained) or reference it with relink is open; decided in the import slice.
