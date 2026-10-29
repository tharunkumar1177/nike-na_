# Edit History

## Outcome or responsibility

Own edit commands and linear undo/redo: every user edit becomes a validated, invertible command, and history survives restarts.

## Current verified status

**Status:** Implemented

The commands `RenameProject`, `DeleteClip`, `AddClip`, `TrimClip`, `SplitClip`, `MergeClips`, and `MoveClip` and the `EditHistory` stack are implemented in `:domain` and verified by `EditCommandTest`, `ClipEditCommandsTest`, and `EditHistoryTest`. The overlay commands `AddOverlayClip`, `RemoveOverlayClip`, `MoveOverlay`, `SetOverlayTransform`, and `ReanchorOverlay`, and the anchor ripple of the clip commands, are verified by `OverlayCommandsTest`. The drag rules in `domain/edit` (`TrimController`, `SnapPoints`, `ReorderSlots`, `OverlayPlacement`) are verified by `EditGesturesTest` and `OverlayPlacementTest`. Persistent undo is verified by `ProjectStoreTest`.

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
- A drag produces exactly one command when the finger lifts; `TrimController` turns a drag delta into a frame-aligned, clamped, optionally snapped `TrimClip`, `ReorderSlots` lists where a lifted clip can land, and `OverlayPlacement.drag` turns an overlay drag into a snapped `MoveOverlay` attached to the base clip under its new start.
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

- `domain/src/main/kotlin/com/framecraft/editor/domain/command/` — `EditCommand`, `RenameProject`, `DeleteClip`, `AddClip`, `TrimClip`, `SplitClip`, `MergeClips`, `MoveClip`, `AddOverlayClip`, `RemoveOverlayClip`, `MoveOverlay`, `SetOverlayTransform`, `ReanchorOverlay`, `AnchorRestoration`, `EditHistory`.
- `domain/src/main/kotlin/com/framecraft/editor/domain/edit/` — `TrimController`, `SnapPoints`, `ReorderSlots`, `OverlayPlacement`.
- `domain/src/test/kotlin/com/framecraft/editor/domain/command/` — `EditCommandTest`, `ClipEditCommandsTest`, `EditHistoryTest`, `OverlayCommandsTest`.
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

## Remaining gaps and unknowns

- `ReanchorOverlay` has no UI yet; overlays re-attach through drags.
