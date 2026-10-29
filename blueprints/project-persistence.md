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
- Files per project: `snapshot.json` (base sequence number, creation time, project), `journal.jsonl` (one record per line: sequence number, time, command), `history.cursor` (the undo position), `meta.json` (the list summary), and `export-settings.json` (the last export settings, written atomically outside the journal so they never enter undo history; a missing or corrupt file reads as none). A damaged journal is copied to `journal.corrupt-<millis>` before it is rewritten.
- `ProjectSession` holds the in-memory history. A write that fails leaves the in-memory project and history unchanged; the next write rewrites the whole journal so disk and memory agree again.
- Loading ignores journal records at or before the snapshot's base sequence number, so a journal left behind by an interrupted compaction never applies twice.
- A torn or garbled line, a sequence gap, or an unknown command type drops that record and every later one; the session reports how many were dropped and the editor tells the user.
- `ProjectStore.list` reads `meta.json` and rebuilds a missing one from the project; projects that cannot be read are listed by id so the user can delete them.

### Project document model

Older schemas are upgraded through `SchemaMigrations`, which rewrites the project and every journaled command in one pass and then saves the upgraded files. The current schema is 9 and `SchemaMigrations.SHIPPED` holds `Migration1To2` through `Migration8To9`, all of which leave documents unchanged. Schema 9 adds track `volumePercent`, clip `voiceover` and `autoDuckPercent`, `autoDuckPercent` on `setClipAudio`, `trackVolumePercent` on `addAudioClip`, `extractAudio`, and `addOverlayClip`, and the `setTrackVolumes` command type, all defaulting to unity lanes and no ducking when absent. Schema 8 adds clip `look` and the look command types (`setClipLook`, `setLooks`, `applyLookToAll`), defaulting to no look when absent. Schema 7 adds clip `outTransition` and the transition command types (`setTransition`, `setTransitions`, `applyTransitionToAll`), defaulting to plain cuts when absent. Schema 6 adds clip `speed`, `sourceDurationMicros`, and `maintainPitch`, optional `sourceDurationMicros` on `trimClip`, `leftSourceMicros` on `splitClip`, and `speed` on `mergeClips`, and the `setClipSpeed` command type, all defaulting to normal speed when absent. Schema 5 adds the project `canvas` (aspect and background), clip `canvasPlacement`, and the canvas command types (`setCanvasAspect`, `setCanvasBackground`, `setBaseClipPlacement`), all defaulting to schema 4 behavior when absent. Schema 4 adds the `TEXT` track and asset kinds, clip `text`, optional animation fields on `trimClip` and `mergeClips`, and the text command types (`addTextClip`, `removeTextClip`, `setTextContent`), all defaulting to schema 3 behavior when absent. Schema 3 adds the `AUDIO` track and asset kinds, clip `volumePercent`, `fadeInMicros`, and `fadeOutMicros`, optional fade fields on `trimClip` and `mergeClips`, and the audio command types, all defaulting to schema 2 behavior when absent. schema 2 only adds optional fields (track kind `OVERLAY`, clip `anchor` and `overlayTransform`, `anchorRestorations` on `addClip`) and the overlay command types, all of which default to schema 1 behavior when absent. Each bump exists so older apps refuse projects that contain the new content instead of dropping it. The slice 5 command types (`trimClip`, `splitClip`, `mergeClips`, `moveClip`) are additive and needed no schema change.

Voiceover takes live in the project directory's `recordings/` folder. Opening a project runs `RecordingSweep`, which deletes every take whose file name appears in neither the snapshot nor the journal, so takes that undo or redo can still reach are kept.

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

- `data/project/src/main/kotlin/com/framecraft/editor/data/project/` — `ProjectStore`, `ProjectSession`, `ProjectLoader`, `ProjectDirectory`, `ProjectDocuments`, `DurableFiles`, `SchemaMigrations`, `Migration1To2`, `Migration2To3`, `Migration3To4`, `Migration4To5`, `Migration5To6`, `Migration6To7`, `Migration7To8`, `Migration8To9`, `RecordingSweep`, `ExportSettingsFile`.
- `data/project/src/test/kotlin/com/framecraft/editor/data/project/RecordingSweepTest.kt` — referenced takes kept, unreferenced ones deleted, missing folder tolerated.
- `data/project/src/test/kotlin/com/framecraft/editor/data/project/ExportSettingsStoreTest.kt` — export settings round trip outside history, missing and corrupt files, unknown project.
- `data/project/src/test/kotlin/com/framecraft/editor/data/project/ProjectStoreTest.kt` — reopen after edits, undo across restart, redo truncation, torn tail, sequence gap, compaction floor, newer and older schemas, list rebuild and unreadable projects, change and delete, unsafe ids.

## Acceptance or verification criteria

- [x] Killing the process mid-edit loses at most the in-flight gesture: every applied edit is appended and flushed before it is reported.
- [x] A torn final journal line is discarded on recovery, and the original journal is kept for diagnosis.
- [x] Snapshot, journal rewrite, cursor, and summary writes are atomic (temporary file, flush, atomic rename).
- [x] Older schema versions open after an upgrade; newer ones are refused with their files untouched.
- [x] A verbatim schema 1 snapshot and journal open, replay, and are saved as the current schema (`schemaOneFixtureOpens`).
- [x] A verbatim schema 2 snapshot and journal with an overlay open, undo, and are saved as the current schema with default clip audio (`schemaTwoFixtureOpens`).
- [x] A verbatim schema 3 snapshot and journal with an audio lane open, undo, and are saved as the current schema with no clip text (`schemaThreeFixtureOpens`).
- [x] A verbatim schema 4 snapshot and journal with a text clip open, undo, and are saved as the current schema with the default canvas (`schemaFourFixtureOpens`).
- [x] A verbatim schema 5 snapshot and journal on a portrait canvas open, undo, and are saved as the current schema with every clip at normal speed (`schemaFiveFixtureOpens`).
- [x] A verbatim schema 6 snapshot and journal with a sped clip open, undo, and are saved as the current schema with no transitions (`schemaSixFixtureOpens`).
- [x] A verbatim schema 7 snapshot and journal with a transition open, undo, and are saved as the current schema with no looks (`schemaSevenFixtureOpens`).
- [x] A verbatim schema 8 snapshot and journal with a look and an audio lane open, undo, and are saved as schema 9 with unity lanes and no ducking (`schemaEightFixtureOpens`).
- [x] Opening a project deletes voiceover takes nothing references (`RecordingSweepTest`).

## Remaining gaps and unknowns

- Whether to copy imported media into the project (self-contained) or reference it with relink is still open.
