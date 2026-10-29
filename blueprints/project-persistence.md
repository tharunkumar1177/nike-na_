# Project Persistence

## Outcome or responsibility

Own project storage: the project list, the versioned JSON snapshot, the append-only command journal, autosave, crash recovery, and schema migration.

## Current verified status

**Status:** Implemented

The `:data:project` module saves every edit to a per-project journal before reporting it, recovers from torn or garbled journals, compacts the journal while keeping the undo floor, lists projects from a per-project summary, and refuses newer schemas. CI unit tests in `ProjectStoreTest` verify each criterion below.

## Architecture dependencies

- [Project persistence and recovery](../architecture.md#project-persistence-and-recovery)
- [Project document model](../architecture.md#project-document-model)
- [Error and recovery](../architecture.md#error-and-recovery)
- [Threading](../architecture.md#threading)

## Local rules and implications

### Project persistence and recovery

- Projects live under `filesDir/projects/<id>`; ids must match `[A-Za-z0-9-]+` so an id can never escape the root.
- Files per project: `snapshot.json` (base sequence number, creation time, project), `journal.jsonl` (one record per line: sequence number, time, command), `history.cursor` (the undo position), and `meta.json` (the list summary). A damaged journal is copied to `journal.corrupt-<millis>` before it is rewritten.
- `ProjectSession` holds the in-memory history. A write that fails leaves the in-memory project and history unchanged; the next write rewrites the whole journal so disk and memory agree again.
- Loading ignores journal records at or before the snapshot's base sequence number, so a journal left behind by an interrupted compaction never applies twice.
- A torn or garbled line, a sequence gap, or an unknown command type drops that record and every later one; the session reports how many were dropped and the editor tells the user.
- `ProjectStore.list` reads `meta.json` and rebuilds a missing one from the project; projects that cannot be read are listed by id so the user can delete them.

### Project document model

Older schemas are upgraded through `SchemaMigrations`, which rewrites the project and every journaled command in one pass and then saves the upgraded files. No migration step exists yet; the registry is empty at schema version 1.

### Error and recovery

A newer schema returns `TooNew` and leaves every file untouched. An unreadable snapshot returns `Corrupt`. Both reach the editor as an explanation screen with a way home; a storage failure while creating a project does the same.

### Threading

All file work runs on a single-threaded IO dispatcher (`Dispatchers.IO.limitedParallelism(1)`) created in `AppContainer`, so writes to one project never interleave.

## Related blueprints

### Required

- [Timeline model](timeline-model.md) — defines what is serialized.
- [Edit history](edit-history.md) — defines journaled commands.

### Impact checks

None

## Relevant implementation and tests

- `data/project/src/main/kotlin/com/framecraft/editor/data/project/` — `ProjectStore`, `ProjectSession`, `ProjectLoader`, `ProjectDirectory`, `ProjectDocuments`, `DurableFiles`, `SchemaMigrations`.
- `data/project/src/test/kotlin/com/framecraft/editor/data/project/ProjectStoreTest.kt` — reopen after edits, undo across restart, redo truncation, torn tail, sequence gap, compaction floor, newer and older schemas, list rebuild and unreadable projects, change and delete, unsafe ids.

## Acceptance or verification criteria

- [x] Killing the process mid-edit loses at most the in-flight gesture: every applied edit is appended and flushed before it is reported.
- [x] A torn final journal line is discarded on recovery, and the original journal is kept for diagnosis.
- [x] Snapshot, journal rewrite, cursor, and summary writes are atomic (temporary file, flush, atomic rename).
- [x] Older schema versions open after an upgrade; newer ones are refused with their files untouched.

## Remaining gaps and unknowns

- Whether to copy imported media into the project (self-contained) or reference it with relink is still open.
- There is no migration step yet; the first schema change must add one with an old-format fixture test.
