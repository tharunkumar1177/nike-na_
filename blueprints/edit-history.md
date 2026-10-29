# Edit History

## Outcome or responsibility

Own edit commands and linear undo/redo: every user edit becomes a validated, invertible command, and history survives restarts.

## Current verified status

**Status:** Implemented

The commands `RenameProject`, `DeleteClip`, `AddClip`, `TrimClip`, `SplitClip`, `MergeClips`, and `MoveClip` and the `EditHistory` stack are implemented in `:domain` and verified by `EditCommandTest`, `ClipEditCommandsTest`, and `EditHistoryTest`. The overlay commands `AddOverlayClip`, `RemoveOverlayClip`, `MoveOverlay`, `SetOverlayTransform`, and `ReanchorOverlay`, and the anchor ripple of the clip commands, are verified by `OverlayCommandsTest`. The audio commands `AddAudioClip`, `RemoveAudioClip`, `ExtractAudio`, and `SetClipAudio`, and fade handling in trim, split, and merge, are verified by `AudioCommandsTest`; their rules live in [audio](audio.md). The text commands `AddTextClip`, `RemoveTextClip`, and `SetTextContent`, and animation handling in trim, split, and merge, are verified by `TextCommandsTest`; their rules live in [text](text.md). The canvas commands `SetCanvasAspect`, `SetCanvasBackground`, and `SetBaseClipPlacement`, and placement handling in split and merge, are verified by `CanvasCommandsTest`. The drag rules in `domain/edit` (`TrimController`, `SnapPoints`, `ReorderSlots`, `OverlayPlacement`, `AudioPlacement`, `TextPlacement`) are verified by `EditGesturesTest`, `OverlayPlacementTest`, `AudioPlacementTest`, and `TextPlacementTest`. Persistent undo is verified by `ProjectStoreTest`.

## Architecture dependencies

- [Edit commands and history](../architecture.md#edit-commands-and-history)
- [Project persistence and recovery](../architecture.md#project-persistence-and-recovery)

## Local rules and implications

### Edit commands and history

- `EditCommand` is a sealed, `@Serializable` interface; each command has a `@SerialName` stored under the `type` discriminator. Command types and fields are additive only, so old journals keep replaying.
- `applyTo(project)` returns `Applied(project, inverse)` or `Rejected(reason)`. Model invariant failures thrown while building a track become `Rejected(ModelInvariant)`.
- A command that creates objects carries their generated ids, so replay is deterministic.
- On a magnetic track, `DeleteClip` shifts later clips left and `AddClip` shifts clips at or after the new start right.
- `TrimClip` sets a clip's start, source in-point, and duration. A clip must stay at least one frame long (`ClipTooShort`), an image keeps its in-point, a magnetic clip keeps its start, and later clips on a magnetic track shift by the change in the clip's end.
- `SplitClip` keeps the clip id on the left part and gives the right part the carried id; it needs a frame on each side (`SplitClip.isSplittable`). Its inverse is `MergeClips`, which only joins adjacent parts of one source with continuous source times.
- `MoveClip` on a magnetic track removes the clip (later clips close the gap) and inserts it at the new start in the closed-up track; on a free track overlaps are rejected.
- Every command rebuilds tracks through one path that re-derives anchored starts and turns a broken invariant (overlap, cycle, start before zero) into `Rejected(ModelInvariant)`, so a ripple that would collide rejects the whole edit.
- `TrimClip` and `MoveClip` shift an anchored clip's offset by its start change. `DeleteClip` and `RemoveOverlayClip` free the children of the removed clip and their inverses carry `AnchorRestoration`s that re-attach them. `SplitClip` re-anchors children at or after the cut to the right part; `MergeClips` re-anchors them back and only joins parts with matching anchors and placements.
- `AddOverlayClip` adds the asset when missing and creates the overlay track at the front of the track list (drawn above the base); `RemoveOverlayClip` drops an emptied track and an asset no other clip uses. `MoveOverlay` sets the anchor (or none) and start together; `ReanchorOverlay` changes the parent and keeps the position; `SetOverlayTransform` changes placement only.
- `SetCanvasAspect` and `SetCanvasBackground` change only the project canvas; placements are normalized to the canvas, so no clip changes with the shape. `SetBaseClipPlacement` changes one base clip's fit and placement and rejects clips on other tracks; null means fit, centered, and upright. Split copies the placement to both parts, and merge requires equal placements.
- A drag produces exactly one command when the finger lifts; `TrimController` turns a drag delta into a frame-aligned, clamped, optionally snapped `TrimClip`, `ReorderSlots` lists where a lifted clip can land, and `OverlayPlacement.drag` turns an overlay drag into a snapped `MoveOverlay` attached to the base clip under its new start.
- `TrimClip` and `MergeClips` carry optional fade lengths, so the inverses of trims and splits restore the exact fades; records without them, including older journals, use the default rule (a trim shortens fades to fit, a merge takes the left fade-in and the right fade-out).
- A new command after undo discards the redo branch.

### Project persistence and recovery

- The undo position is saved in `history.cursor` next to the journal, so reopening restores undo and redo availability.
- History depth: compaction keeps at least the latest 100 records behind the cursor; older edits fold into the snapshot and can no longer be undone.

### Speed-aware editing

- `SetClipSpeed` changes a clip's speed with an exact inverse. `TrimClip` carries the sped clip's source length, `SplitClip` the left part's source (`leftSourceMicros`), and `MergeClips` the merged speed, so trims, splits, and merges of sped clips undo exactly; `TrimController` proposes source and duration through `SpeedMap`. Details belong to [clip-speed](clip-speed.md).

### Transition editing

- `SetTransition` and `ApplyTransitionToAll` invert through `SetTransitions`, which restores each clip's previous transition verbatim. `SplitClip` hands a transition to the right part and `MergeClips` keeps the right part's, so splits and merges undo exactly; no other command touches transitions (`TransitionCommandsTest`). Details belong to [transitions](transitions.md).
- `SetClipLook` and `ApplyLookToAll` invert through `SetLooks`, which restores each clip's previous look verbatim. `SplitClip` gives both parts the look and `MergeClips` requires both parts to share it, and extracted sound carries none (`LookCommandsTest`). Details belong to [look](look.md).

## Related blueprints

### Required

- [Timeline model](timeline-model.md) — commands transform and validate the model.

### Impact checks

- [Project persistence](project-persistence.md) — when a command type is added or its fields change.

## Relevant implementation and tests

- `domain/src/main/kotlin/com/framecraft/editor/domain/command/` — `EditCommand`, `RenameProject`, `DeleteClip`, `AddClip`, `TrimClip`, `SplitClip`, `MergeClips`, `MoveClip`, `AddOverlayClip`, `RemoveOverlayClip`, `MoveOverlay`, `SetOverlayTransform`, `ReanchorOverlay`, `AddAudioClip`, `RemoveAudioClip`, `ExtractAudio`, `SetClipAudio`, `AddTextClip`, `RemoveTextClip`, `SetTextContent`, `CanvasCommands` (`SetCanvasAspect`, `SetCanvasBackground`, `SetBaseClipPlacement`), `SetTrackVolumes` (rules in [voiceover](voiceover.md), verified by `MixerCommandsTest`), `AnchorRestoration`, `EditHistory`.
- `domain/src/main/kotlin/com/framecraft/editor/domain/edit/` — `TrimController`, `SnapPoints`, `ReorderSlots`, `OverlayPlacement`, `AudioPlacement`.
- `domain/src/test/kotlin/com/framecraft/editor/domain/command/` — `EditCommandTest`, `ClipEditCommandsTest`, `EditHistoryTest`, `OverlayCommandsTest`, `AudioCommandsTest`, `CanvasCommandsTest`.
- `domain/src/test/kotlin/com/framecraft/editor/domain/edit/EditGesturesTest.kt` — trim clamps, frame and target snapping, reorder slots.
- `domain/src/test/kotlin/com/framecraft/editor/domain/edit/OverlayPlacementTest.kt` — overlay placement at the playhead, overlay drags, and overlay snap targets.
- `data/project/src/test/kotlin/com/framecraft/editor/data/project/ProjectStoreTest.kt` — undo after restart.

## Acceptance or verification criteria

- [x] Applying a command then its inverse yields the original project.
- [x] Invalid commands return a typed failure and leave the project unchanged.
- [x] Undo and redo are available after reopening a project.
- [x] Trim, split, merge, and move round-trip through JSON and their inverses restore the project.
- [x] A trim drag yields one frame-aligned command clamped to the source and to the one-frame minimum.
- [x] Trimming, moving, splitting, merging, and deleting a base clip carry its anchored overlays, and every overlay command's inverse restores the project.
- [x] An edit whose ripple would make overlays collide is rejected and changes nothing.
- [x] Canvas aspect, background, and base placement changes undo to the previous value and round-trip through JSON; merging needs matching placements (`CanvasCommandsTest`).

## Remaining gaps and unknowns

- `ReanchorOverlay` has no UI yet; overlays re-attach through drags.
