# Transitions

## Outcome or responsibility

Own transitions between touching clips on a video track: which cuts can take one and for how long, how a transition is stored and kept exact through edits and undo, how the engine plays it from the clips' hidden source, and the cut badges and Transition sheet that edit it.

## Current verified status

**Status:** Partial

Where a cut can take a transition, the transition commands, and how split, merge, and trim treat a stored transition are verified by `TransitionCommandsTest`. The schema 7 upgrade is verified by `ProjectStoreTest`. The engine lanes are verified by `TransitionPlanTest` and the motion of each kind by `TransitionMotionTest`. Cut badges and their touch zone are verified by `TimelineCutsTest`, and the sheet by `TransitionSheetTest`. How transitions actually look and export needs a device and is unverified.

## Architecture dependencies

- [Project document model](../architecture.md#project-document-model) — a transition is an optional field of the clip before the cut, so older projects read as plain cuts.
- [Clip speed and source mapping](../architecture.md#clip-speed-and-source-mapping) — the hidden source a sped clip can lend is converted through `SpeedMap`.
- [Edit commands and history](../architecture.md#edit-commands-and-history) — every transition edit has an exact inverse, and no other command needs to change a transition to stay exact.
- [Engine translation](../architecture.md#engine-translation) — transitions consume clip handles and never freeze frames.

## Local rules and implications

### Model

- `ClipTransition(kind, durationMicros)` sits on the left clip as `outTransition`. `TransitionKind` serializes as `dissolve`, `fadeBlack`, `fadeWhite`, `slideLeft`, `pushLeft`, and `zoom`.
- A stored transition plays only while its clip ends exactly where the next clip on the same video track starts and the cut allows at least the shortest transition; otherwise it is kept but inactive, so moving, trimming, or deleting a neighbor and undoing brings it back unchanged.
- The window is centered on the cut and never shortens the timeline: half the length, rounded down, before the cut and the rest after it.
- `TransitionGeometry.capMicros` is the smallest of half the shorter clip, twice the smaller hidden source on either side, and 4 s. Stills have unlimited hidden source, speed-curve clips none, and constant-speed clips convert theirs through `SpeedMap`. The shortest transition is 100 ms or two frames, whichever is longer.

### Commands

- `SetTransition(clipId, transition)` fits the length to the cut and frames, and refuses clips off a video track (`TransitionNotSupported`), clips with no touching next clip (`NotButted`), and cuts too short (`TransitionTooShort`). Clearing is always allowed.
- `ApplyTransitionToAll(trackId, transition)` gives every cut on the track that fits the transition, each fitted, as one undo step; cuts too short keep what they have.
- Both invert through `SetTransitions`, which writes each clip's previous value back verbatim.
- `SplitClip` hands the transition to the right part; `MergeClips` keeps the right part's and refuses a left part that still carries one; extracted sound carries none.

### Engine

- `TransitionLanes` keeps the base track playing the outgoing clip on through each window into its hidden source, with its sound fading out over the window, and adds an incoming lane that plays the next clip's hidden lead-in, fading in. Fades to black or white add a backdrop lane of a solid PNG. Per base track the lanes register incoming first, then the base, then the backdrop. Both clip lanes carry their clip's look ([look](look.md)).
- `TransitionMotion` gives each side its opacity, horizontal shift, and zoom from the window's progress. Off-canvas shifts combine Media3's background and overlay anchors so neither leaves the range -1 to 1.

### Editing

- The sheet follows `assets/wireframes/transitions-slice12.md`. A badge centered on each cut between touching clips on a video track shows a bar for a plain cut, overlapping squares for a transition, and a dimmed bar when the cut has no hidden source; a 48dp touch zone opens the sheet, and the trim handles of a selected clip win over it. TalkBack has one action per cut.
- The sheet has chips for None and each kind, a frame-stepped duration slider up to the cut's limit, and Apply to all, which then says how many cuts it reached. Each finished change dispatches one command and moves the preview to just before the window.

## Related blueprints

### Required

- [Timeline model](timeline-model.md) — the transition field is a clip field under its validation.
- [Edit history](edit-history.md) — the transition commands and the split and merge rules follow its inverse rules.

### Impact checks

- [Media engine](media-engine.md) — when sequence order, compositor inputs, or item building change.
- [Editor UI](editor-ui.md) — when timeline drawing, gestures, or sheets change.
- [Project persistence](project-persistence.md) — when transition fields or command types change.
- [Clip speed](clip-speed.md) — when how a sped clip lends hidden source changes.

## Relevant implementation and tests

- `domain/src/main/kotlin/com/framecraft/editor/domain/model/` — `ClipTransition`, `TransitionGeometry`, and the `outTransition` field of `Clip`.
- `domain/src/main/kotlin/com/framecraft/editor/domain/command/` — `SetTransition`, `SetTransitions`, `ApplyTransitionToAll`, and the transition rules in `SplitClip` and `MergeClips`.
- `data/project/src/main/kotlin/com/framecraft/editor/data/project/Migration6To7.kt` — the schema 7 upgrade.
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/` — `plan/TransitionLanes`, `plan/PlaybackPlan.kt`, `plan/CanvasLayout.kt`, `TransitionMotion`, and `CanvasCompositorSettings`.
- `app/src/main/kotlin/com/framecraft/editor/timeline/TimelineCuts.kt` — badges, their touch zone, and TalkBack actions.
- `app/src/main/kotlin/com/framecraft/editor/editor/TransitionSheet.kt` — `TransitionEditing` and the sheet.
- Tests: `domain/src/test/kotlin/com/framecraft/editor/domain/command/TransitionCommandsTest.kt`, `data/project/src/test/kotlin/com/framecraft/editor/data/project/ProjectStoreTest.kt`, `engine/media3/src/test/kotlin/com/framecraft/editor/engine/plan/TransitionPlanTest.kt`, `engine/media3/src/test/kotlin/com/framecraft/editor/engine/TransitionMotionTest.kt`, `app/src/test/kotlin/com/framecraft/editor/timeline/TimelineCutsTest.kt`, `app/src/test/kotlin/com/framecraft/editor/editor/TransitionSheetTest.kt`.

## Acceptance or verification criteria

- [x] The limit follows clip lengths, hidden source, stills, and speed; windows center on the cut (`TransitionCommandsTest`).
- [x] Setting, clearing, and applying to all fit, refuse, and undo exactly; split, merge, and trim keep stored transitions exact (`TransitionCommandsTest`).
- [x] Transition commands round-trip through JSON and a schema 6 project opens and saves as schema 7 (`TransitionCommandsTest`, `ProjectStoreTest`).
- [x] The plan plays through each window, leads into the incoming clip, and adds a backdrop only for fades (`TransitionPlanTest`); each kind moves as designed (`TransitionMotionTest`).
- [x] Badges show each cut's state and a touch reaches the nearest one (`TimelineCutsTest`); the sheet sends the expected edits (`TransitionSheetTest`).
- [ ] Transitions play and export as previewed, including HDR sources (needs a device).

## Remaining gaps and unknowns

- Clips with a speed curve cannot lend hidden source, so their cuts cannot take a transition.
- The blurred background follows the outgoing clip through a window.
- Transitions have no easing, play only on video tracks, and extracted sound does not crossfade.
- The preview seeks before the window rather than looping it; chips have text labels without icons.
- Off-canvas anchors, decoder load from the extra lane, and HDR tone mapping during a window are unverified on hardware.
