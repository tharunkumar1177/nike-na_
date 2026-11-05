<!--
  Remediation plan for the architectural issues found in the October 2026 review, issues 2 to 10.
-->

# Architecture fixes: issues 2 to 10

This document is the working plan for the nine open issues from the October 2026 architecture review. Issue 1 (undo quietly failing after a caption-silencing trim) is fixed and merged in PR #17; it is listed only for completeness.

Every claim below was checked against `main` at commit `6105867`. File paths are relative to the repository root, and line numbers are as of that commit; they will drift as code changes, so the function or symbol name is always given too.

Shared contracts stay authoritative in [architecture.md](architecture.md) and the feature rules in [blueprints/](blueprints/README.md). When a fix lands, update the owning blueprint and tick the checkbox here; when a fix changes a shared contract, update `architecture.md` first.

## How to read each issue

Each issue has the same sections:

- **What the user sees**: the symptom, in plain language.
- **Evidence**: where it lives in code, with short quotes.
- **Root cause**: why it happens.
- **Fix**: the design, step by step, with the files to change.
- **Tests**: what to add, and what each test asserts.
- **Done when**: acceptance criteria that CI or a reviewer can check.
- **Risks**: what could go wrong, and how to contain it.
- **Docs to update**: blueprints and architecture headings that must change with the fix.
- **Size and approval**: S (under a day), M (one to three days), L (a week or more), and whether a shared contract changes, which needs approval first.

## Overview

| # | Issue | Size | Contract change | Status |
|---|---|---|---|---|
| 1 | Undo can quietly fail | M | Yes (approved) | Done, PR #17 |
| 2 | The app slows down as projects get bigger | M | Yes | Open |
| 3 | Playback stutters and drains the battery | M | No | Open |
| 4 | The screen freezes briefly after an edit | M | No | Open |
| 5 | Effects might export in the wrong place | L | Yes | Open |
| 6 | A future change could scramble all the layers | M | No | Open |
| 7 | Cancelling an export at the wrong moment leaves junk | M | No | Open |
| 8 | Preview keeps playing in the background | S | No | Open |
| 9 | Memory use grows with many source videos | S | No | Open |
| 10 | Hidden quality risks (docs, build, model shape, error handling) | L in total | Yes, for some parts | Open |

## Recommended order

1. **Issue 5** first, because it is the safety net for issues 4 and 6, which both touch rendering. Its first step (a device-test job with one golden frame) is useful on its own.
2. **Issue 6** next, while issue 5's golden test can catch a mistake.
3. **Issues 2 and 3** together, since both are about edit and playback speed and both need a benchmark harness.
4. **Issue 7**, independent and contained in the export package.
5. **Issues 8 and 9**, small and independent; they can ship any time.
6. **Issue 4**, after the `PreviewPlayer` seam from issue 10.5 exists, so the rebuild pipeline can be unit-tested.
7. **Issue 10** parts, in the order given inside that section. Parts 10.1 to 10.4 are small and can be slipped in between the larger fixes.

The unmerged branch `fix/preview-replay-overlays` changes `ProjectPreview`. Merge or close it before starting issues 4 or 8, which edit the same file.

---

## Issue 2: The app slows down as projects get bigger

- [ ] Done

### What the user sees

Every tap, trim, or drag gets slower the longer the video is, and much slower when clips use a speed curve. Opening a project with many edits since its last compaction takes a noticeably long time, and on low-memory phones it can run out of memory while opening.

### Evidence

**Every new project state re-checks everything.** `Project` is a data class whose `init` block validates the whole document, so every `copy()` made by every command runs it again:

```24:44:domain/src/main/kotlin/com/framecraft/editor/domain/model/Project.kt
    init {
        require(id.isNotBlank()) { "Project id must not be blank" }
        val clipIds = tracks.flatMap { track -> track.clips.map { it.id } }
        val allIds = listOf(id) + assets.map { it.id } + tracks.map { it.id } + clipIds
        val duplicates = allIds.groupBy { it }.filterValues { it.size > 1 }.keys
        // ... per-clip requireFits, look/mask/key and voiceover checks ...
        AnchorResolver.validate(tracks)
        CaptionResolver.validate(tracks, assets, frameRate)
    }
```

**Speed maps are rebuilt from scratch every time they are needed.** `requireFits` (called for every clip, `Project.kt:61-66`) builds a whole `SpeedMap` for each sped clip only to read its duration:

```61:66:domain/src/main/kotlin/com/framecraft/editor/domain/model/Project.kt
        clip.speed?.let { speed ->
            require(asset.isTimed) { "Clip ${clip.id} has a speed but no timed source" }
            val expected = SpeedMap.durationFor(speed, clip.sourceSpanMicros, frameRate)
            require(abs(expected - clip.durationMicros) < frameRate.startOfFrame(1)) {
```

`SpeedMap` splits a curve into steps of at most one frame of source (`SpeedMap.kt`, around lines 120-141). A 10-minute curve at 30 fps is about 18,000 steps. Nothing caches the result: `Clip.speedMap()` (`SpeedMap.kt:147`) builds a new map on every call, and `CaptionResolver.spanOf` calls `timelineAt` twice per caption, and each call builds a map (`CaptionResolver.kt:35`, `:46-47`).

**One edit pays this several times.** `ProjectSession.dispatch` calls the command, whose `replacingTracks` (`EditCommand.kt:176-190`) runs `AnchorResolver.materialize`, then `CaptionResolver.materialize`, then `Project.copy`, which runs `init`. With S sped clips and P captions on sped clips, one edit builds roughly S + 4P speed maps of up to 18,000 steps each, plus several full walks over every clip.

**Opening a project repeats it for every journal record, and keeps every intermediate state.** `replay` applies each record in turn and stores every resulting project:

```171:185:data/project/src/main/kotlin/com/framecraft/editor/data/project/ProjectLoader.kt
private fun replay(
    start: Project,
    records: List<JournalRecord>,
): Replay {
    val applied = mutableListOf<JournalRecord>()
    val entries = mutableListOf<HistoryEntry>()
    val projects = mutableListOf(start)
    for (record in records) {
        val outcome = record.command.applyTo(projects.last()) as? CommandOutcome.Applied ?: break
        applied += record
        entries += HistoryEntry(record.command, outcome.inverse)
        projects += outcome.project
    }
    return Replay(applied, entries, projects)
}
```

The journal compacts only after 400 records or 1 MB (`ProjectSession.kt`, `COMPACT_AFTER_RECORDS` and `COMPACT_AFTER_BYTES`), so an open can replay up to 400 edits and hold up to 401 full project copies at once. Only one of them is used: `project = replay.projects[index]` (`ProjectLoader.kt:108`).

### Root cause

Validation is tied to construction, so there is no way to say "only these clips changed". Speed maps are pure functions of `(speed, sourceSpanMicros, frameRate)` but are recomputed instead of reused. Replay was written for correctness, not memory.

### Fix

Do it in three independent steps, each shippable alone.

1. **Cache speed maps.** In `SpeedMap.kt`, add a small bounded cache keyed by `(ClipSpeed, sourceSpanMicros, FrameRate)` (all three are immutable value types with structural equality). Route `SpeedMap.of`, `durationFor`, and `Clip.speedMap()` through it. Use a synchronized `LinkedHashMap` with access order and a cap of about 64 entries, so scrubbing a handful of sped clips always hits. Add a fast path in `durationFor` for a constant speed, which needs no map at all.
2. **Stop keeping every replayed project.** In `ProjectLoader.kt`, change `Replay` to hold `records`, `entries`, and only the project at the stored cursor. Replay forward, remember the project when the loop passes the cursor index, and keep applying the remaining records only to build their `HistoryEntry` (needed for redo), dropping each project as soon as the next one exists. Peak memory becomes two projects instead of up to 401.
3. **Validate only what changed.** Split `Project`'s checks into:
   - cheap whole-document checks that must always run (blank id, duplicate ids), and
   - per-clip checks (`requireFits`, look/mask/key placement, voiceover) plus the anchor and caption checks.
   Give `Project` an internal factory used by `replacingTracks`, for example `Project.revised(tracks, changedTrackIds)`, that runs per-clip checks only for clips on changed tracks and runs `AnchorResolver.validate` / `CaptionResolver.validate` only on the trees that touch them. Deserialization and the public constructor keep running the full check, so a file read from disk is always fully validated. `replacingTracks` already knows which tracks `build()` returned that differ from the originals, so it can compute `changedTrackIds` by reference comparison.

Files: `domain/.../model/SpeedMap.kt`, `domain/.../model/Project.kt`, `domain/.../model/CaptionResolver.kt`, `domain/.../command/EditCommand.kt`, `data/project/.../ProjectLoader.kt`.

### Tests

- `SpeedMapCacheTest`: the same inputs return the same instance; a different frame rate or span returns a different one; the cache never grows past its cap; cached and uncached durations are equal for constant, preset, and custom curves.
- `ProjectRevisionTest`: a `revised` project with one changed track equals the fully constructed one; an invalid clip on a changed track is still rejected; an invalid clip on an unchanged track cannot exist (it was valid before), so no check is lost.
- `ProjectLoaderReplayTest` (in `data/project`): replaying 400 synthetic rename and trim records yields the same project, history, and cursor as today, for a cursor at the start, the middle, and the end; undo and redo after reopening still work (extend the existing `ProjectStoreTest` restart cases).
- A JVM benchmark test with a time budget: one `TrimClip` on a project with a 10-minute curved clip and 50 captions builds at most one new speed map; opening a 400-record journal on that project stays under an agreed budget on the CI runner. Keep the budget generous (for example 3x the measured time) so it fails only on real regressions.

### Done when

- One edit to a project with a 10-minute speed curve builds at most one speed map.
- Opening a journal of 400 records keeps at most two projects alive at once.
- All existing `domain` and `data/project` tests pass unchanged.

### Risks

- A cache key that is not truly immutable would return a wrong map. Confirm that `ClipSpeed` (including its curve points) and `FrameRate` are immutable value types with structural equality before relying on them as keys.
- Partial validation could miss a cross-track rule. Keep the full check for every load, and add a debug-only assertion in tests that `revised(...)` equals `Project(...)` for every command in the existing command test suites.

### Docs to update

- `architecture.md`, "Project document model": state that a document is fully validated when read and incrementally after each command.
- `blueprints/timeline-model.md` and `blueprints/clip-speed.md`: the cache and its key.
- `blueprints/project-persistence.md`: replay keeps only the cursor project.

### Size and approval

M in total (S for the cache, S for replay, M for incremental validation). The validation rule is a shared contract, so step 3 needs approval; steps 1 and 2 do not.

---

## Issue 3: Playback stutters and drains the battery

- [ ] Done

### What the user sees

While a video plays, the whole editor screen is rebuilt 20 times a second, not just the moving playhead and timecode. On cheaper phones that shows up as dropped frames in the timeline, laggy taps on the tool row during playback, and faster battery drain. It also happens on every frame while scrubbing.

### Evidence

The preview publishes its position every 50 ms while playing:

```32:33:engine/media3/src/main/kotlin/com/framecraft/editor/engine/ProjectPreview.kt
/** How often the playhead position is sampled while playing. */
private const val PROGRESS_INTERVAL_MS = 50L
```

`ReadyEditor` collects that state at the top of the editor and reads the position as a plain value, so every tick recomposes the whole screen:

```85:94:app/src/main/kotlin/com/framecraft/editor/editor/EditorRoute.kt
    val previewState by preview.state.collectAsStateWithLifecycle()
    // ...
    val position = previewState.positionMicros
```

That value then flows into almost everything: `EditorSheets` (line 98), `toolAvailability` (line 106), `playbackActions` (line 118), `toolActions` (line 121), `pictureEffectLayer` and `selectionLayer` (lines 132-133). Each tick therefore:

- re-runs `toolAvailability` (`EditorTools.kt`, around lines 127-159), which scans the whole project for captions through `CaptionPlanner.sources(...)` and `CaptionPlanner.hasCaptions(project)`, and also runs `TextPlacement.canPlace`, `AudioPlacement.canRecord`, and `SplitClip.isSplittable`;
- rebuilds about 19 tool lambdas in `ToolActions` (`EditorScreen.kt`, around lines 188-209), plus `EditorNotices`, `PlaybackActions`, and the overlay layers.

The timeline was designed to avoid this: `TimelineStrip` takes `positionMicros: () -> Long` and reads it only while drawing. But `ReadyEditor` itself recomposes every tick and passes a new lambda each time (`EditorRoute.kt:127`), so the timeline cannot skip either.

### Root cause

Position is read in the parent composable, which makes the parent depend on it. In Compose, whoever reads a state value is the one that recomposes.

### Fix

1. In `ReadyEditor`, stop reading `previewState.positionMicros`. Keep `isPlaying`, `durationMicros`, and `errorCode` as values (they change rarely), and pass position down as `() -> Long` (for example `val position = remember(preview) { { preview.state.value.positionMicros } }`).
2. Make the playback bar and timecode the only composables that read position as state. Wrap the timecode in `derivedStateOf` that rounds to the current frame, so it recomposes at most once per frame, not once per tick.
3. Split `toolAvailability` into:
   - a part that depends only on `project`, `selected`, `kind`, and `importing`, computed with `remember(project, selected, kind, importing)`; this holds the caption scan;
   - a position-dependent part (`canSplit`, `canRecord`, `canAddText`) computed in a `derivedStateOf { ... position() ... }`, so it recomposes only when one of those booleans actually flips.
4. Make tool actions read the position when tapped (`position()` inside the lambda) instead of capturing a value. Then `ToolActions` can be built once with `remember(viewModel, selected, kind, pickers)`.
5. Do the same for `EditorSheets`, `pictureEffectLayer`, and `selectionLayer`: pass the lambda, and read it at commit time or inside `derivedStateOf` where the layer must react to the playhead (for example, the overlay handles show only while the playhead is inside the clip).
6. Keep a stable lambda for the timeline (`remember`), so `TimelineStrip` skips recomposition and only redraws.

Files: `app/.../editor/EditorRoute.kt`, `EditorScreen.kt`, `EditorTools.kt`, `EditorSheets` and the overlay-layer files it calls, and `timeline/TimelineStrip.kt` (check only).

### Tests

- A Robolectric Compose test that plays a fake preview for 1 second and counts recompositions of `EditorScreen` and `ToolRow` (with a `SideEffect` counter or the Compose recomposition-count test tools). Assert at most a handful, not about 20.
- A unit test that `toolAvailability`'s project scan runs once per project change, not once per position change (inject a counting `CaptionPlanner` wrapper or test the split functions directly).
- Behavior tests that Split, Record, and Add Text still use the playhead position at the moment of the tap (existing `EditorScreenTest` patterns).

These tests need a fakeable preview, so land issue 10.5 (`PreviewPlayer` interface) first, or in the same change.

### Done when

- During playback, only the timecode, play button, and timeline redraw; the tool row and sheets do not recompose.
- Split, Record, and Add Text still act at the current playhead.
- A macrobenchmark or manual trace on a low-end device shows no dropped frames in the timeline during 1080p playback. This is a manual check until a device job exists (issue 5).

### Risks

- An action that captured `position` could silently start using a stale value. Reading `position()` at tap time avoids that; the behavior tests above guard it.
- Over-using `derivedStateOf` adds bookkeeping. Use it only for values derived from position.

### Docs to update

- `blueprints/editor-ui.md`: rule that only the playback bar, timecode, and timeline read the playhead, and everything else samples it on demand.

### Size and approval

M. No shared contract changes.

---

## Issue 4: The screen freezes briefly after an edit

- [ ] Done

### What the user sees

After an edit (a trim, an opacity change, a look tweak), the app can hang for a moment before the screen responds again. Several quick edits in a row queue several full rebuilds back to back.

### Evidence

After every applied edit, the ViewModel hands the new project to the preview on the main thread (`EditorViewModel.edit`, `target.preview.setProject(next.project)`, around line 295). `ProjectPreview` is main-thread only:

```56:59:engine/media3/src/main/kotlin/com/framecraft/editor/engine/ProjectPreview.kt
/**
 * Plays a project snapshot. Must be created, used, and released on the main thread. It holds audio focus while
 * playing, so a call or another app's sound pauses it.
 */
```

and `setProject` does all the work synchronously:

```104:118:engine/media3/src/main/kotlin/com/framecraft/editor/engine/ProjectPreview.kt
    fun setProject(project: Project) {
        val plan = PlaybackPlanner.plan(project)
        // ...
        load(CompositionFactory.create(plan), position)
    }
```

`load` then calls `player.setComposition(...)` and `player.prepare()` (lines 179-186). `OpenProject.change` serializes edits with a `Mutex`, but nothing merges a burst of edits into one rebuild.

The media-engine blueprint claims otherwise:

> Rebuilding the composition after an edit is debounced so scrubbing stays responsive. (`blueprints/media-engine.md:45`)

No debounce exists anywhere in the code.

### Root cause

Planning (`PlaybackPlanner.plan`) and building the Media3 `Composition` (`CompositionFactory.create`) are pure, but they run on the main thread because the same function also talks to the player, which must stay on the main thread.

### Fix

1. Split `setProject` into a pure build step and a main-thread apply step:
   - `build(project): Built` runs `PlaybackPlanner.plan` and `CompositionFactory.create` and returns the plan, frame rate, duration, and composition (or "empty").
   - `apply(built)` updates state and calls `load` or `player.stop()`.
2. Inside `ProjectPreview`, keep a `MutableStateFlow<Project?>` of the latest requested project. Collect it with `collectLatest` on the preview's scope: map with `withContext(Dispatchers.Default) { build(it) }`, then apply on `Dispatchers.Main.immediate`. `collectLatest` cancels a build that is superseded by a newer edit, so a burst of edits produces one rebuild. `setProject` becomes a non-blocking `requested.value = project`.
3. Keep the duration and clamped position update immediate (cheap and needed by the timeline), and apply the composition when it is ready.
4. Make seeks requested while a build is in flight apply after the new composition loads (store the latest requested position and use it in `apply`).
5. Check that `CompositionFactory.create` and everything it calls are safe off the main thread (they build immutable Media3 objects; `SolidPng.dataUri` and effect construction do not touch GL). If any effect needs the main thread, construct it lazily in `apply`.
6. Correct the blueprint sentence to describe the new conflated rebuild.

Files: `engine/media3/.../ProjectPreview.kt`, possibly `CompositionFactory.kt`; no app changes beyond what issue 10.5 introduces.

### Tests

- With the `PreviewPlayer` seam (issue 10.5) and an injectable build dispatcher, a JVM test that 10 rapid `setProject` calls result in at most 2 `setComposition` calls, and that the last one matches the last project.
- A test that a seek requested during a build lands at the requested position after the build applies.
- A Robolectric test with `StrictMode` or a main-thread check that `PlaybackPlanner.plan` is never called on the main thread.

### Done when

- No planning or composition building runs on the main thread.
- A burst of edits causes one rebuild, using the latest project.
- The preview always ends up showing the latest project at the requested position.

### Risks

- A short window where the timeline shows the new project and the preview still shows the old one. Acceptable (a few tens of milliseconds) and better than a freeze.
- Media3 objects built on one thread and used on another: Composition and EditedMediaItem are immutable builders' output, which is safe; verify with the device job from issue 5.

### Docs to update

- `blueprints/media-engine.md`, threading paragraph: replace the "debounced" claim with the actual conflated background rebuild.
- `architecture.md`, "Threading": note that preview plans build on `Dispatchers.Default` and apply on the main thread.

### Size and approval

M. "Threading" is a shared heading, but this matches its intent (heavy work off the main thread), so it is a clarification rather than a contract change. Mention it in the PR.

---

## Issue 5: Effects might export in the wrong place

- [ ] Done

### What the user sees

Nothing checks that the rendered or exported video looks right. Overlays once appeared behind the main video, and the fix has never been confirmed on a phone. An overlay, mask, green-screen key, text, or blurred background could be misplaced or missing, and nobody would know until a user noticed.

### Evidence

- There are no instrumented tests anywhere: zero files under any `androidTest` folder, and no `managedDevices` or `androidTest` configuration in any Gradle file.
- CI runs only JVM steps (`.github/workflows/ci.yml`, around lines 31-45: ktlint, detekt, Android Lint, unit tests, assemble).
- `architecture.md`, "Verification boundaries", promises them: ":engine:media3: unit tests for snapshot-to-composition mapping; instrumented tests where a device is required."
- `blueprints/media-engine.md` admits the gap: the exporter, saver, and precheck "need a device and are unverified"; "slices 7 and 8 drew overlays under the base, and the fix has not yet been seen on a device"; and the criterion "Overlays render at their placement in preview and export (needs a device)" is unchecked.
- `blueprints/build-and-ci.md`: "Instrumented (emulator) tests in CI are not planned yet."

### Root cause

The rendering path (Media3 compositor, GL shaders for masks and keys, text overlays) only runs on a device or emulator, and no CI job provides one.

### Fix

Build it in four steps.

1. **Device job.** In `engine/media3/build.gradle.kts`, add a Gradle Managed Device on an Automated Test Device image, which runs headless and supports GLES through SwiftShader:

   ```kotlin
   testOptions.managedDevices.allDevices {
       create<ManagedVirtualDevice>("atdApi34") {
           device = "Pixel 7"
           apiLevel = 34
           systemImageSource = "aosp-atd"
       }
   }
   ```

   Add `androidTestImplementation` dependencies for AndroidX Test (runner, rules, JUnit extension) to the version catalog, with versions checked against the registry. Add a CI job `device-tests` that enables KVM on the GitHub runner and runs `gradle :engine:media3:atdApi34DebugAndroidTest`. Run it on every pull request; if it is too slow, run it on pull requests that touch `engine/` or `domain/` and nightly on `main`.
2. **Test fixtures.** Add small, license-free source media under `engine/media3/src/androidTest/assets/`: a 2-second 720p base video with a known color pattern, a 2-second overlay video of solid colored blocks, a green-screen clip, and a PNG. Generate them once with a script committed beside them, so they are reproducible.
3. **Golden-frame test.** `CompositedRenderTest` builds one project in code: base video, overlay at a known placement and opacity, a circle mask, a chroma key over the green clip, a text clip, a 9:16 canvas, and a blurred background. It runs `PlaybackPlanner.plan`, then `CompositionFactory.create`, then exports with `Transformer` to a temp file, then extracts frames at fixed times (0.5 s, 1.0 s, 1.5 s) with the `FrameExtractor` already used by the engine. Compare each frame with a golden PNG in `engine/media3/src/androidTest/assets/goldens/` using mean absolute difference per channel (fail above about 2/255) and a per-region check (the overlay's rectangle must match the overlay's color, the background corners must match the blur, and so on). Region checks make failures readable: "overlay region is base-colored" means a z-order bug.
4. **Golden bootstrap and updates.** Add a test argument `-Pgoldens.record=true` that writes the extracted frames to the build outputs instead of comparing. The first run records; a person reviews the PNGs and commits them. Goldens are tied to the pinned API level and image; changing either means re-recording in a reviewed PR.

Later, add a preview test with the same project through `CompositionPlayer` and a `SurfaceTexture` reader, and an export-to-gallery test for `GallerySaver` (it needs MediaStore, so only a device can check it).

### Tests

- `CompositedRenderTest` (above), with one test per effect so failures point at the effect.
- A deliberate-failure check when introducing the job: temporarily reverse the overlay order in a throwaway commit and confirm the job fails, then drop that commit.
- `GallerySaverDeviceTest`: saving creates exactly one entry in `Movies/<folder>`, cancelled saves leave none (this also verifies issue 7 on a real device).

### Done when

- A `device-tests` CI job runs and passes on `main`.
- Reversing the overlay or background order makes it fail with a region message.
- The media-engine blueprint criterion "Overlays render at their placement in preview and export" is ticked with the test name as evidence.

### Risks

- Emulator GL output can differ slightly between runs or images: use tolerances and region checks, not exact pixels, and pin the image.
- Job time: an ATD boot plus one export is a few minutes; caching the system image with the Gradle action keeps it manageable.
- `:app` ships ARM-only ABIs (`app/build.gradle.kts`, around lines 25-28), so app-level device tests would need an x86_64 build variant. Keeping these tests in `:engine:media3` avoids that.

### Docs to update

- `architecture.md`, "Verification boundaries": the device job and what it covers.
- `blueprints/build-and-ci.md`: the new job, its trigger, and how to re-record goldens.
- `blueprints/media-engine.md`: tick the device criteria it now covers, and remove the "not yet seen on a device" gap.

### Size and approval

L. Adding a CI job and test dependencies changes "Stack and versions" and "Verification boundaries", so it needs approval.

---

## Issue 6: A future change could scramble all the layers

- [ ] Done

### What the user sees

Nothing today. The risk is that one routine change could make every overlay draw in the wrong place or with another layer's settings, with no error, and (until issue 5 lands) no test would notice.

### Evidence

Two functions independently work out the order of the compositor's picture inputs, and Media3 pairs them only by position.

`CompositionFactory.create` builds the sequence list:

```81:100:engine/media3/src/main/kotlin/com/framecraft/editor/engine/CompositionFactory.kt
        val (sound, pictures) = plan.sequences.partition { it.kind == TrackKind.AUDIO }
        // ...
                listOf(clock) + picture + listOfNotNull(background)
        // ...
        val sequences = pictureSequences + sound.map { sequenceOf(it, frameRate, stillGaps = false) }
```

`CanvasLayout.inputsOf` builds the matching list of what each input is:

```59:77:engine/media3/src/main/kotlin/com/framecraft/editor/engine/plan/CanvasLayout.kt
    fun inputsOf(plan: PlaybackPlan): List<CompositorInput> {
        val pictures =
            plan.sequences
                .filter { it.kind != TrackKind.AUDIO }
                .map { sequence ->
                    when {
                        sequence.role == SequenceRole.INCOMING -> CompositorInput.Incoming(sequence.transitionSpans)
                        // ... Backdrop, Overlay, Base ...
                    }
                }
        // ...
        return listOf(CompositorInput.Clock) + pictures + listOfNotNull(background)
    }
```

They are joined at `CompositionFactory.kt:105`: `CanvasCompositorSettings(CanvasLayout.inputsOf(plan), plan.aspectRatio)`. Input number N in the compositor gets the placement, opacity, and spans of entry N in `inputsOf`, purely because both lists happen to be built the same way. `CompositionFactory` has no tests.

### Root cause

The order is a convention repeated in two places instead of data produced in one.

### Fix

1. Add a single ordered model, for example in `engine/media3/.../plan/PictureLanes.kt`:

   ```kotlin
   /** One compositor input: what it is, and the sequence that feeds it, or null for generated inputs. */
   internal data class PictureLane(val input: CompositorInput, val sequence: PlannedSequence?)
   ```

   and a function `CanvasLayout.lanesOf(plan): List<PictureLane>` that produces the clock, every picture sequence, and the background in their one true order.
2. Make `CompositionFactory.create` build its picture sequences by walking `lanesOf(plan)` (clock lane becomes the clock still, sequence lanes become `sequenceOf(...)`, background lane becomes `backgroundSequence(...)`), and pass `lanesOf(plan).map { it.input }` to `CanvasCompositorSettings`. Sound sequences are appended after, as today.
3. Delete the separate `inputsOf` (or make it a one-line `lanesOf(plan).map { it.input }` for existing callers) so no second ordering exists.
4. Move the `canvasSequence` and `stillGaps` choice (`CompositionFactory.kt:84-90`) to a property of the lane, so it cannot drift from the order either.

### Tests

- `CompositionFactoryTest` (new, JVM): for generated plans (base only; base and overlay; two overlays; base with transition incoming and backdrop; with and without solid or blurred background; with sound lanes), assert that the composition's picture sequence count equals the input count, that index 0 is the clock, that the last input is the background when present, and that each sequence's item URIs match the lane's source.
- A property-style test that `lanesOf(plan).map { it.input }` equals what the compositor receives, for every plan in the existing `PlaybackPlannerTest` fixtures.
- Issue 5's golden test is the end-to-end check.

### Done when

- There is exactly one function that decides compositor input order.
- `CompositionFactoryTest` exists and fails if either side is reordered alone (check by temporarily swapping two entries).

### Risks

- The change touches the composited path that has never been seen on a device. Land it after issue 5's first golden test, or at least behind its tests in the same PR.

### Docs to update

- `blueprints/media-engine.md`: the single lane list and its order rule; remove "CompositionFactory has no tests" from gaps.

### Size and approval

M. No shared contract changes ("Engine translation" already says layers follow the track order).

---

## Issue 7: Cancelling an export at the wrong moment leaves junk

- [ ] Done

### What the user sees

If you tap Cancel while the finished video is being copied into the gallery, the copy keeps running anyway, and for a moment the cancelled video becomes visible in the gallery before it is removed. If the app is killed in that moment, the cancelled video stays in `Movies` for good. A new export started right after can also delete files the previous copy is still reading.

### Evidence

`cancel()` goes straight to `finish`, which cancels the job and deletes the work file:

```122:125:engine/media3/src/main/kotlin/com/framecraft/editor/engine/export/ProjectExporter.kt
    /** Stops the current export and discards its output; does nothing when no export is in progress. */
    fun cancel() {
        if (mutableState.value.isInProgress) finish(ExportState.Cancelled)
    }
```

```213:222:engine/media3/src/main/kotlin/com/framecraft/editor/engine/export/ProjectExporter.kt
    private fun finish(state: ExportState) {
        job?.cancel()
        job = null
        transformer?.cancel()
        transformer = null
        pending?.file?.delete()
        pending = null
        mutableState.value = state
    }
```

The save runs a blocking copy on the IO dispatcher and checks for cancellation only after the copy has finished and the entry has been published:

```191:191:engine/media3/src/main/kotlin/com/framecraft/editor/engine/export/ProjectExporter.kt
                val result = withContext(Dispatchers.IO) { runCatching { saveTo(output) } }
```

```203:211:engine/media3/src/main/kotlin/com/framecraft/editor/engine/export/ProjectExporter.kt
    private suspend fun saveTo(output: PendingOutput): Pair<Uri, Long> {
        val bytes = output.file.length()
        val uri = saver.save(output.file, output.displayName)
        if (!currentCoroutineContext().isActive) {
            saver.delete(uri)
            throw IOException("Export cancelled while saving")
        }
        return uri to bytes
    }
```

`GallerySaver.save` copies with a single blocking `copyTo` (`GallerySaver.kt:81`) and clears `IS_PENDING`, which makes the entry visible, before returning (`GallerySaver.kt:50`). Starting a new export wipes the work folder (`ProjectExporter.kt:139`, `deleteRecursively()`), even if the old copy is still reading from it.

What does not go wrong: the cancelled coroutine cannot overwrite `Cancelled` with `Failed`, because `withContext` re-throws the cancellation when it resumes, so the `finish(...)` after it never runs. Deleting the source file mid-copy does not corrupt the copy on Android, because an open file stays readable after it is unlinked. Both are safe by accident rather than by design.

### Root cause

Coroutine cancellation cannot interrupt a blocking stream copy, the gallery entry is published before the cancel check, and ownership of the work file is split between `finish` and the save coroutine.

### Fix

1. **Make the copy cancellable.** In `GallerySaver.copy`, copy in chunks (for example 256 KB) and call `ensureActive()` between chunks. Make `save` a `suspend` function. `SpeechModelStore.copyChunks` (`engine/captions/.../SpeechModelStore.kt`) already does this and can be the model.
2. **Publish last.** Split `save` into `insertPending`, `copy`, and `publish(uri)`. `ProjectExporter.saveTo` publishes only after a final `ensureActive()`. On any failure or cancellation, delete the still-pending row inside `withContext(NonCancellable)`.
3. **One owner for the work file.** Let the save coroutine delete the work file in its own `finally` (inside `NonCancellable`). `finish` no longer deletes `pending.file` while a save is running; instead `cancel()` cancels the job and `join`s it before setting `Cancelled` (`cancel` becomes a launch on the exporter scope, or `finish` awaits the job).
4. **Guard terminal states.** Make `finish` do nothing if the state is already `Succeeded`, `Failed`, or `Cancelled`, so the order of events cannot overwrite a final state.
5. **Do not wipe a folder in use.** In `start`, refuse to start while a previous job is still completing (the exporter must enforce this itself, whatever the UI does), or give each export its own subfolder of the work directory.
6. Keep the existing one-week sweep of stale pending rows for crashes.

Files: `engine/media3/.../export/ProjectExporter.kt`, `engine/media3/.../export/GallerySaver.kt`.

### Tests

Put `GallerySaver` behind a small interface (`GalleryWriter`: `insertPending`, `write(chunk)`, `publish`, `delete`) so the exporter's save flow can be tested on the JVM with `kotlinx-coroutines-test`:

- cancel during the copy: the copy stops within one chunk, the pending row is deleted, nothing is published, the work file is deleted after the job ends, and the final state is `Cancelled`;
- cancel after publish: the state stays `Succeeded`;
- a copy failure: the row is deleted and the state is `Failed` with the mapped cause;
- `start` while a save is finishing: refused or isolated, never deleting the in-use file.

The real `MediaStore` behavior is covered by `GallerySaverDeviceTest` from issue 5.

### Done when

- Cancelling at any moment leaves no entry in the gallery and no file in the work folder.
- The final state after a cancel is always `Cancelled`, and a finished state is never overwritten.

### Risks

- Chunked copying is slightly slower than `copyTo`; with 256 KB chunks the difference is negligible.
- `join`ing in `cancel` must not block the main thread; do it in a coroutine on the exporter scope and update state when it completes.

### Docs to update

- `blueprints/media-engine.md`: the save order (pending, copy, check, publish) and the cancel guarantee.
- `blueprints/editor-ui.md`: the export sheet's "Cancelled" state is final.

### Size and approval

M. No shared contract changes ("Error and recovery" already requires typed, final outcomes).

---

## Issue 8: Preview keeps playing in the background

- [ ] Done

### What the user sees

If you switch to another app or lock the screen while the preview is playing, the sound keeps playing, and the decoder keeps using battery.

### Evidence

There is no lifecycle handling for the preview. The only lifecycle hook in `app/src/main` is for voiceover recording:

```132:132:app/src/main/kotlin/com/framecraft/editor/editor/VoiceoverBar.kt
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { starter.recording.interrupt() }
```

`ProjectPreview` pauses only when another app takes audio focus (`player.setAudioAttributes(AudioAttributes.DEFAULT, true)`, line 101). `collectAsStateWithLifecycle` stops the UI from collecting position in the background, but the player itself keeps playing.

### Root cause

Nothing ties the player to the screen's lifecycle.

### Fix

1. In `ReadyEditor` (`EditorRoute.kt`), add `LifecycleEventEffect(Lifecycle.Event.ON_STOP) { preview.pause() }`. `ON_STOP` fires when the editor is no longer visible (home, app switch, lock screen), not for dialogs or the share sheet.
2. Do not resume automatically on return: the user taps Play again. This matches common editor behavior and avoids surprise sound.
3. Configuration changes (rotation) recreate the activity, which also passes through `ON_STOP`; pausing then is acceptable because the ViewModel keeps the position.
4. Exports are unaffected: they run in `ProjectExporter` under the foreground service, not in the preview.

Files: `app/.../editor/EditorRoute.kt`.

### Tests

- Robolectric: with a fake `PreviewPlayer` (issue 10.5), move the activity's lifecycle to `STOPPED` and assert `pause()` was called once; move it back to `RESUMED` and assert `play` was not called.

### Done when

- Leaving the app while playing stops the sound immediately; returning shows the preview paused at the same position.

### Risks

- None significant.

### Docs to update

- `blueprints/editor-ui.md`: preview pauses when the editor stops.

### Size and approval

S. No shared contract changes.

---

## Issue 9: Memory use grows with many source videos

- [ ] Done

### What the user sees

In projects built from many different videos, memory use keeps climbing during a session, which can slow the app or crash it on low-end phones.

### Evidence

`ClipThumbnails` keeps one decoder (`FrameExtractor`) per source video for as long as the project is open:

```77:77:engine/media3/src/main/kotlin/com/framecraft/editor/engine/ClipThumbnails.kt
    private val extractors = mutableMapOf<String, FrameExtractor>()
```

```143:151:engine/media3/src/main/kotlin/com/framecraft/editor/engine/ClipThumbnails.kt
    /** Returns the frame extractor for [uri], creating it on first use. */
    private fun extractorFor(uri: String): FrameExtractor =
        extractors.getOrPut(uri) {
            FrameExtractor
                .Builder(appContext, MediaItem.fromUri(uri))
                // ...
                .build()
        }
```

They are released only in `close()` (lines 109-114). The bitmaps themselves are bounded by a 32 MB `LruCache` (lines 80-87), and waveforms are bounded too (`AudioWaveforms.kt`, `LruCache` with size weighing), so the growth comes from the decoders, each of which holds a codec and native buffers.

### Root cause

The decoder map has no size limit and no eviction.

### Fix

1. Replace the map with an `LruCache<String, FrameExtractor>` sized by count (6 is a good start: enough for the clips visible in one timeline viewport, small enough that decoder memory stays bounded).
2. Override `entryRemoved` to call `close()` on evicted extractors.
3. Because extraction runs under `extractionLock`, eviction can close an extractor only when it is not in use: take the lock in `extractorFor` (it already runs inside it) and never hand out an extractor outside the lock.
4. Keep `close()` as is (`evictAll()` now also closes extractors).

Files: `engine/media3/.../ClipThumbnails.kt`.

### Tests

- Add a factory seam (`(uri) -> FrameExtractor`-like interface) so a JVM test can count opens and closes: request thumbnails for 10 distinct sources and assert at most 6 are open and every evicted one was closed exactly once; `close()` closes the rest.

### Done when

- No more than the configured number of thumbnail decoders exist at once, regardless of how many source videos the project uses.

### Risks

- A timeline showing more distinct sources than the cap would re-open decoders as the user scrolls. Size the cap to at least the maximum clips visible at the widest zoom, or make it adapt to the viewport.

### Docs to update

- `blueprints/editor-ui.md` (thumbnails): the decoder cap.

### Size and approval

S. No shared contract changes.

---

## Issue 10: Hidden quality risks

- [ ] Done (all parts)

Users don't see these directly, but each one makes a future bug more likely or harder to find. They are independent; do them in this order.

### 10.1 Real bugs get reported as user mistakes

- [ ] Done

**Evidence.** `replacingTracks` wraps command building in `runCatching`, which catches every `Throwable`:

```180:190:domain/src/main/kotlin/com/framecraft/editor/domain/command/EditCommand.kt
): CommandOutcome {
    val result =
        runCatching {
            val anchored = AnchorResolver.materialize(build())
            copy(assets = assets, tracks = CaptionResolver.materialize(anchored, frameRate))
        }
    return result.fold(
        onSuccess = { CommandOutcome.Applied(it, inverse) },
        onFailure = { CommandOutcome.Rejected(CommandRejection.ModelInvariant(it.message.orEmpty())) },
    )
}
```

A null-pointer error or an out-of-memory error is shown to the user as "that edit would break another clip". `ReplaceCaptions.kt` (around lines 42-55) has the same pattern.

**Fix.** Catch only `IllegalArgumentException`, which is what every `require` and `requireNotNull` throws; let everything else crash and reach crash reporting. All validation in the model uses `require`, so no legitimate rejection is lost.

**Tests.** `ReplacingTracksTest`: a `build` that throws `IllegalArgumentException` gives `ModelInvariant`; one that throws `NullPointerException` or `IllegalStateException` propagates.

**Size.** S. No contract change ("Error and recovery" already separates user errors from bugs).

### 10.2 A file with no schema version is read as the newest version

- [ ] Done

**Evidence.**

```120:121:data/project/src/main/kotlin/com/framecraft/editor/data/project/ProjectLoader.kt
    val version =
        project[SchemaMigrations.SCHEMA_VERSION_KEY]?.jsonPrimitive?.intOrNull ?: CURRENT_SCHEMA_VERSION
```

The app always writes the version (`encodeDefaults = true`), so a missing version means a damaged or foreign file. Today it is decoded as schema 11 with every newer field defaulted, instead of showing the "project is damaged" screen.

**Fix.** Treat a missing or non-integer version as unreadable: throw, which `load` already turns into `LoadResult.Corrupt`. Keep the default only for constructing projects in code.

**Tests.** In `ProjectStoreTest`: a snapshot without `schemaVersion` opens as `Corrupt` and the files are left untouched; a string version also opens as `Corrupt`; every existing schema 1 to 10 fixture still opens.

**Size.** S. No contract change ("Project persistence and recovery" already says unreadable documents are reported, not guessed).

### 10.3 The "no Media3 in UI code" rule is not enforced

- [ ] Done

**Evidence.** `architecture.md` says screens "never import Media3 types directly". Today there are no `androidx.media3` imports under `app/src/main`, but `config/detekt/detekt.yml` has no rule that would stop one being added.

**Fix.** Add detekt's `ForbiddenImport` rule for `androidx.media3.*`, applied to the `:app` module (via a module-level detekt config or `excludes` for the other modules). Optionally also forbid `androidx.compose.*` in `:engine:media3` except the file that defines `PreviewSurface`.

**Tests.** The rule is the test; prove it once by adding a forbidden import in a throwaway commit and seeing detekt fail.

**Size.** S. No contract change; it enforces an existing one.

### 10.4 Correct the architecture document

- [ ] Done

**Evidence.** `architecture.md` describes modules and a dependency injection setup that do not exist, and contradicts itself:

- line 56: "`:feature:*` | Compose screens and ViewModels (for example `:feature:timeline`, `:feature:import`)";
- line 57: "`:app` | Application, Hilt graph, navigation, foreground services";
- line 37: "Dependency injection | Manual app container (Hilt deferred until the graph needs it)".

`settings.gradle.kts:23-27` includes only `:app`, `:domain`, `:data:project`, `:engine:media3`, and `:engine:captions`. All 77 UI source files live in `:app`, and dependencies are wired by hand in `app/.../di/AppContainer.kt`.

`architecture.md` also repeats numbers that the blueprints own, for example compaction at "400 records or 1 MB" keeping "the latest 100 edits" (owned by `blueprints/edit-history.md`), the voiceover merge and ramp timings (owned by `blueprints/voiceover.md`), and "schemaVersion (currently 11)". Two copies of a number drift.

**Fix.**

1. Rewrite the module table to match `settings.gradle.kts`. Keep `:feature:*` as a row marked **Planned**, with the trigger for splitting (for example, when `:app` passes a size or build-time threshold).
2. Replace "Hilt graph" with "manual app container (`AppContainer`)".
3. Replace repeated numbers with a link to the owning blueprint.
4. Re-check every manifest entry that links "Module structure and dependency direction".

**Size.** S. This is a shared-contract document, so get approval for the wording; no code changes.

### 10.5 The editor's core logic has no tests, because the UI depends on the concrete player

- [ ] Done

**Evidence.** `EditorResources.createPreview` returns the concrete `ProjectPreview` (`EditorViewModel.kt`, around lines 88-95), and `EditorRoute.kt` imports `ProjectPreview` directly. `ProjectPreview` builds a real Media3 `CompositionPlayer`, which cannot run under Robolectric, so `EditorViewModel` (dispatch, undo and redo, notices, selection) has no unit test. `blueprints/editor-ui.md` says so in its gaps. There are 23 test files in `app/src/test`, none for `EditorViewModel` or `ExportViewModel`.

**Fix.** Add `interface PreviewPlayer` in `:engine:media3` with exactly what the app uses today: `state: StateFlow<PreviewState>`, `setProject`, `togglePlayPause`, `pause`, `beginScrub`, `endScrub`, `seekTo`, `release`. Make `ProjectPreview` implement it and change `EditorResources.createPreview` to return it. `PreviewSurface` needs the real Media3 player, so either give the interface a narrow `attach(surface)` hook or let `PreviewSurface` accept the interface and downcast inside the engine module. Do the same for the exporter if `ExportViewModel` tests are wanted.

**Tests.** `EditorViewModelTest` with a fake player and a real `ProjectStore` in a temp folder: a rejected dispatch raises the edit notice; a rejected undo raises the history notice (from PR #17); undo and redo update `canUndo` and `canRedo`; deleting the selected clip clears the selection; closing releases the player.

**Size.** M. No shared contract change ("Module structure" already says the UI talks to engine interfaces). Needed by issues 3, 4, and 8 for their tests.

### 10.6 Preview errors are plain text

- [ ] Done

**Evidence.** `PreviewState.errorCode: String?` (`ProjectPreview.kt:53`) is filled with Media3's `errorCodeName` (line 95). Exports, by contrast, use a typed `ExportFailure` mapped by `ExportFailureMapper`.

**Fix.** Add `PreviewFailure` (for example `SOURCE_MISSING`, `DECODER`, `UNSUPPORTED_FORMAT`, `UNKNOWN`) mapped from `PlaybackException.errorCode`, mirroring `ExportFailureMapper`. Keep the raw code name as a diagnostic field. The designer specifies the message for each cause.

**Tests.** `PreviewFailureMapperTest` covering each mapped Media3 error code and the fallback.

**Size.** S, plus a short design pass for the messages.

### 10.7 The speech-model download has no tests

- [ ] Done

**Evidence.** `engine/captions/src/test/` has only `VoskResultParserTest` and `CaptionTranscriberTest`. `SpeechModelStore` is the only code that downloads from the network and checks integrity: it downloads a pinned model zip, checks size and SHA-256 while streaming, requires about 150 MB free, rejects zip entries that escape the target folder, unpacks into a staging folder, renames it atomically, and writes a `.complete` marker.

**Fix.** Inject the connection (a small `(url) -> InputStream`-like opener) instead of calling `URL(spec.url).openConnection()` directly, so tests can serve bytes from memory or a local `com.sun.net.httpserver` server.

**Tests.** Happy path installs and `isInstalled` is true; a short, long, or wrong-hash download fails as `CORRUPT` and leaves nothing behind; a dropped connection fails as `NETWORK`; a zip with a `../` entry is rejected; cancelling mid-download cleans up staging; too little free space fails as `NO_SPACE` before downloading.

**Size.** S to M. No contract change.

### 10.8 Builds are not reproducible

- [ ] Done

**Evidence.** There is no Gradle wrapper (`gradlew` and `gradle/wrapper/` are missing; `gradle/` contains only `libs.versions.toml`), no dependency lockfiles, and no `gradle/verification-metadata.xml`. CI installs Gradle `9.8.2` with `gradle/actions/setup-gradle` (`.github/workflows/ci.yml:29`, `release.yml:33`) without a checksum. Versions in `libs.versions.toml`: AGP 9.5.0, Kotlin 2.4.21, Compose BOM 2026.09.00, Media3 1.11.1, and detekt **2.0.0-alpha.6** (an alpha). Because CI is the only build authority, a republished or compromised dependency would silently change every build.

**Fix.** All of this is generated in CI, never hand-written, per the repository rules:

1. Add a CI step (or a one-off workflow) that runs `gradle wrapper --gradle-version 9.8.2 --gradle-distribution-sha256-sum <sum>` and commits the wrapper; switch CI steps to `./gradlew`.
2. Enable dependency locking (`dependencyLocking { lockAllConfigurations() }`) and generate lockfiles in CI with `--write-locks`, committing them through a PR.
3. Generate `gradle/verification-metadata.xml` in CI with `--write-verification-metadata sha256`, and commit it.
4. Plan to move detekt to a stable 2.x release when one is published; until then, the lockfile pins the alpha exactly.

**Tests.** CI fails if a dependency's checksum changes or a lockfile is out of date (that is the default behavior once both are enabled).

**Size.** S. Changes "Stack and versions", so needs approval.

### 10.9 `Clip` holds every feature's settings in one place

- [ ] Done

**Evidence.** `Clip` (`domain/.../model/Clip.kt:33-55`) has 21 fields: timing (`id`, `assetId`, `startMicros`, `trimStartMicros`, `durationMicros`), anchoring (`anchor`), placement (`overlayTransform`, `canvasPlacement`), sound (`volumePercent`, `fadeInMicros`, `fadeOutMicros`, `voiceover`, `autoDuckPercent`), text and captions (`text`), speed (`speed`, `sourceDurationMicros`, `maintainPitch`), and effects (`outTransition`, `look`, `mask`, `chromaKey`). Its rules are spread across `Clip.init`, `Track.init`, `Project.init` and `requireFits`, `AnchorResolver.validate`, `CaptionResolver.validate`, `MergeClips` (its "joinable" checks), and individual commands. Every new feature widens the constructor, the JSON shape, the merge-equality checks, and every `copy(...)` call.

**Fix.** Group the fields into small value objects that validate themselves, while keeping one serialized `Clip`:

- `ClipSound(volumePercent, fadeInMicros, fadeOutMicros, voiceover, autoDuckPercent)`
- `ClipRetime(speed, sourceDurationMicros, maintainPitch)`
- `ClipEffects(look, mask, chromaKey, outTransition)`
- `ClipPlacement`, holding `overlayTransform` or `canvasPlacement`

Move each feature's "which tracks may carry this" rule into that feature's file as a function `Project.init` calls, so the rule lives beside its fields. This changes the JSON shape, so bump the schema to 12 and add a migration that rewrites both snapshots and journal commands (commands such as `TrimClip.restoredCaptions` embed whole clips), following the existing `SchemaMigrations` pattern.

**Tests.** Migration round trips from every schema 1 to 11 fixture; every existing command test passes unchanged after the regrouping; a test that the top-level `Clip` has at most 8 properties, so the regrouping does not quietly erode.

**Size.** L, with high churn. It is tolerable to postpone for about two more features; schedule it before the next feature that adds clip fields. Changes "Project document model" and the schema version, so needs approval.

### 10.10 Small leftovers found while verifying

- [ ] `RemoveTextClip` is the only clip-removing command without the caption lock that `TrimClip`, `SplitClip`, `MoveClip`, `MoveOverlay`, and `ReanchorOverlay` have, so a bound caption can be deleted directly and its lane dropped. Add the lock so captions are removed only through `ReplaceCaptions`, and add a test. Undo is safe today because every lane-dropping command's inverse re-creates the lane with the same id, but a test should pin that. Size S.
- [ ] `TransitionGeometry.kt` (around line 140) and thumbnail code also rebuild speed maps; route them through issue 2's cache. Size S.

---

## Change log

- 2026-11-05: Created from the October 2026 review; every item re-verified against `main` at `6105867`.
