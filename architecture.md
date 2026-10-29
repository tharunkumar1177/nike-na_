# System Architecture

## Purpose and authority

This document governs the shared technical contracts of a native Android video editor whose goal is to match or exceed CapCut mobile while being more intuitive. Every contract here applies to two or more responsibilities; responsibility-local rules live in [the blueprint manifest](blueprints/README.md).

Authority order: inspected code and tests override this prose for current behavior; this document overrides blueprints for shared rules. Every contract below is **intended** (status `Planned`) until a slice verifies it in CI.

## Product principles

These shape every design decision and come from research into CapCut's most common failures.

- **Sync never breaks silently.** Trimming, splitting, or moving a clip keeps the clips anchored to it in sync.
- **Nothing is lost.** Autosave and undo history survive app close, process death, and crashes.
- **Precision on a phone.** Frame-step nudges, zoom-to-playhead, haptic snap, and a global snap toggle. No hidden gesture zones.
- **Local-first and private.** Core editing, export, and on-device AI work offline. No mandatory account or cloud upload.
- **Honest pricing.** No feature is revealed as paid only at export time. (Currently fully free; see [Product decisions](#product-decisions).)

## System boundaries

In scope: an Android app (phones first, tablets supported) that imports local media, edits a multi-track timeline, previews in real time, and exports MP4 files.

External actors: the Android media store (source media), the device codecs (via AndroidX Media3), and the Android share sheet (exported files).

Out of scope until explicitly planned: cloud sync, accounts, online asset libraries, templates marketplace, desktop/web clients.

## Stack and versions

Versions are declared in `gradle/libs.versions.toml` and resolved in CI; the table lists the governing ones.

| Concern | Choice |
|---|---|
| Language | Kotlin 2.4.21 (AGP built-in Kotlin) |
| Build | Android Gradle Plugin 9.5.0, Gradle 9.8.2 run directly in CI on JDK 21 (no committed wrapper) |
| UI | Jetpack Compose, BOM 2026.09.00 |
| Media engine | AndroidX Media3 1.11.1 (Transformer, Composition, CompositionPlayer) |
| Dependency injection | Manual app container (Hilt deferred until the graph needs it) |
| Serialization | kotlinx.serialization (JSON) |
| Unit tests | JUnit Jupiter (JUnit 6 BOM) for pure Kotlin modules; Robolectric 4.17 with JUnit 4 for Android JVM tests |
| Static analysis | ktlint (kotlinter 5.7.0), detekt 2.0.0-alpha.6, Android Lint |
| SDK levels | minSdk 29, targetSdk 37 |

Rejected: Flutter and React Native (frame-accurate scrubbing and multi-track rendering need native media access); an FFmpeg-centric engine (`ffmpeg-kit` was retired in 2025; only community forks remain); Room for project storage (see [Project persistence and recovery](#project-persistence-and-recovery)).

## Module structure and dependency direction

Dependencies point inward toward `:domain`. `:domain` has zero Android dependencies.

| Module | Owns | May depend on |
|---|---|---|
| `:domain` | Project model, time math, anchoring and ripple rules, edit commands | Kotlin stdlib, kotlinx.serialization |
| `:engine:media3` | Translating a project snapshot into a Media3 `Composition`; preview player; export | `:domain`, Media3 |
| `:data:project` | Project document files, command journal, autosave, recovery (pure JVM) | `:domain`, kotlinx.serialization, kotlinx.coroutines |
| `:feature:*` | Compose screens and ViewModels (for example `:feature:timeline`, `:feature:import`) | `:domain`, engine and data interfaces |
| `:app` | Application, Hilt graph, navigation, foreground services | All modules |

Feature modules never import Media3 types directly; they talk to engine interfaces.

## Shared contracts

### Time representation

- All timeline and media times are `Long` **microseconds**. No floating-point time is stored or used in edit math.
- Conversions to frames use the project frame rate and round toward the nearest frame boundary at the UI edge only.
- A clip's timeline extent is `[startMicros, startMicros + durationMicros)`, half-open.
- Source trim is `trimStartMicros` into the source media; `durationMicros` is the timeline duration after speed is applied.

### Identity and ownership

- `Project`, `Track`, and `Clip` have stable string IDs (UUID v4) assigned at creation and never reused.
- A project owns its tracks; a track owns its clips. A clip belongs to exactly one track.
- Source media is referenced by content URI plus a fingerprint (size, duration, last-modified) so a missing source can be detected and relinked.

### Project document model

- A project is one immutable aggregate snapshot: `Project → Track[] → Clip[]`, plus project settings (aspect ratio, frame rate, canvas background).
- There is **no privileged main track**. Any track may set `magnetic = true`, which closes gaps when clips are removed or trimmed on that track.
- Gaps are **implicit**: a gap is any timeline span with no clip on a track. The domain never stores filler clips.
- Each track has a kind: `VIDEO` (base video, drawn full frame), `OVERLAY` (composited over the base), `AUDIO` (sound only, never drawn), or `TEXT` (words drawn over everything). Only overlay and text clips carry a placement: center in normalized canvas units with y downward, a scale, clockwise rotation, and opacity. An overlay's scale is relative to the largest size that fits inside the canvas; a text's scale is relative to its natural size. Only `VIDEO` tracks may be magnetic.
- Media assets are `VIDEO`, `IMAGE`, `AUDIO`, or `TEXT`. Picture tracks accept only video and image assets; audio tracks accept any asset with sound except images, so an extracted sound shares its video's asset. Text has no source file: every text clip points at one shared `TEXT` asset with an empty URI, and only text tracks accept it.
- A clip carries text exactly when it sits on a text track. Text content is the words (1 to 500 characters, never blank), a style whose size is a fraction of the canvas height so it looks the same at any resolution, and optional entrance and exit animations, each no longer than the clip.
- Every clip carries a volume from 0 to 200 percent (100 is unchanged) and fade-in and fade-out lengths whose sum fits within the clip. Gain is the volume times linear fades from the clip's edges. Images and text keep the defaults, and neither has a source offset to trim.
- The canvas size and aspect ratio come from the first base clip, never from an overlay, a sound, or text.
- The document carries an integer `schemaVersion` (currently 4). Readers migrate older versions forward; they refuse newer versions with a visible error.

### Anchoring and ripple

- A clip may declare an anchor: `anchor = (parentClipId, offsetMicros)`. Its absolute start is derived from the parent's start plus the offset.
- Anchors form a forest; cycles are rejected at command validation.
- Anchored clips live only on non-magnetic tracks. The stored start of an anchored clip always equals its resolved start: every command re-derives anchored starts after it edits tracks, and a project whose stored starts disagree with its anchors fails validation on load.
- When a parent clip moves or ripples, anchored children move with it. When a parent is deleted, children become unanchored at their current absolute position, and undoing the delete restores their anchors.
- Splitting a parent hands the children that start at or after the cut to the right part; merging hands the right part's children to the merged clip.
- Ripple on a magnetic track shifts following clips on that track and, through anchors, their children on other tracks. An edit whose ripple would make clips overlap is rejected as a whole.

### Edit commands and history

- Every project mutation is an `EditCommand` that is pure: `applyTo(Project)` returns the new project plus an inverse command. UI code never mutates the project directly.
- Commands validate before applying and return a typed failure instead of throwing.
- Undo/redo is a linear history over commands. Gesture-driven edits (a drag) coalesce into one command on gesture end.
- History is persisted (see below), so undo works after a restart.

### Project persistence and recovery

- Each project is a directory containing a versioned JSON snapshot, an append-only JSON-lines command journal with sequence numbers, a history cursor file, and a summary file that indexes the project list.
- Every applied command is appended and flushed before the UI reports it done; the cursor is written after the journal. Whole-file writes are atomic (write temp, fsync, rename).
- Compaction runs when the journal passes 400 records or 1 MB, and when a project closes. It folds records more than 100 behind the cursor into a new snapshot, so at least the latest 100 edits stay undoable.
- Recovery on open: load the snapshot, replay journal records newer than the snapshot's base sequence number, and drop a torn or unreadable line together with everything after it, keeping a copy of the original journal.
- Rationale versus Room: the project is a single aggregate tree; whole-document snapshots plus a journal give atomic saves, cheap duplication, and persistent undo without relational mapping.

### Engine translation

- The engine consumes immutable project snapshots only. It never writes back to the project.
- The adapter maps each track to a Media3 `EditedMediaItemSequence` and pads implicit gaps with generated silent or transparent segments at preview and export time.
- Base video sequences come first, overlay sequences after them, and audio sequences last. Every base sequence is padded to the end of the latest track so sound beyond the pictures still plays. Audio sequences carry only audio; a muted picture clip carries no audio, and every other clip's sound passes through per-clip processing (to 16-bit PCM, mixed to stereo, then the clip's gain with clamping). A compositor setting places each overlay frame from the clip covering its time; the first sequence sets the output size. Preview builds its player with a multiple-input video graph so the same composition renders on screen.
- Text tracks add no sequence. Each text clip becomes a bitmap overlay in one overlay effect applied to the composed picture, before any export resizing, so preview and export draw it the same way; upper text lanes draw on top. Text tracks still count toward the padded end. A project with only text has nothing to export.
- Transitions consume clip handles (overlapping source media); they never freeze frames.
- Preview and export use the same translation so what you see is what you export.

### Media access

- Video and photo import uses Android photo picker and media store APIs and handles partial access (`READ_MEDIA_VISUAL_USER_SELECTED`) on Android 14 and later. Music is picked through the system document picker (`audio/*`), which needs no permission; the importer keeps a persistable read grant where the provider offers one.
- Loss of access to a referenced source is a recoverable state (offline clip with relink), never a crash or silent removal.
- Exports are published to the shared `Movies/Framecraft` collection through `MediaStore` with `IS_PENDING`, so other apps never see a partial file and no storage permission is needed. Exported files are shared by content URI with a read grant.

### Threading

- Domain code is synchronous and thread-agnostic.
- Media3 players are accessed only from their application looper thread. Persistence I/O runs on a dedicated dispatcher.
- UI state is exposed as `StateFlow` from ViewModels.

### Error and recovery

- Failures are typed results surfaced to the user with a cause and a next action; nothing fails silently.
- Long-running export runs in a foreground service and reports progress, success, and failure through a notification and in-app state. The service type is `mediaProcessing` on Android 15 and later and `dataSync` before it; when the system times the service out, the export is cancelled and reported as cancelled, never dropped silently.

## Verification boundaries

- `:domain`: JUnit 5 unit tests for time math, commands, anchoring, ripple, and migrations.
- `:data:project`: JUnit unit tests for journal append, compaction, torn-write recovery, and schema migration.
- `:engine:media3`: unit tests for snapshot-to-composition mapping; instrumented tests where a device is required.
- UI: Compose UI tests for screen state and interactions.
- GitHub Actions CI is the only build authority: ktlint, detekt, Android Lint, unit tests, and assemble must pass on every push.

## Product decisions

- App name **Framecraft**, application ID and root package `com.framecraft.editor`.
- Monetization: fully free; no feature gating. Revisit only by explicit user decision.

## Feature blueprints

See [the blueprint manifest](blueprints/README.md).
