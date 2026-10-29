# Editor UI

## Outcome or responsibility

Own the editor screen: preview surface, timeline rendering, zoom and scrub, selection, precision controls (frame step, zoom to playhead, snap toggle with haptics), the contextual tool bar, and the export flow (settings sheet, progress, outcome, share, the export foreground service, and its notifications).

## Current verified status

**Status:** Partial

The home project list (open, rename, delete, unreadable rows), photo-picker import, the editor shell (preview, play and pause, frame step, timecode, undo and redo, save and recovery notices), the timeline (fixed center playhead, frame-snapped scrubbing, pinch zoom, zoom to fit, zoom to playhead, snap toggle, clip thumbnails, tap to select, trim handles, long-press reorder), the Split and Delete tools, overlays (the Overlay tool, the overlay lane with attached and free badges, overlay time drags, the opacity slider, and the preview transform handles), audio (the Audio, Extract, Volume, and Fade tools in a scrolling tool row, the volume panel with mute, the fade sheet, and audio lanes with waveforms and fade shading), text (the Text, Edit, and Animate tools, the text sheet with font, style, and animation tabs, text lanes with labels, and text placement on the preview with double tap to edit), and the export sheet (settings, progress, success with share and play, failure and cancel) are implemented and verified by CI Robolectric tests. The export service and notifications are implemented; the service lifecycle needs a device and is unverified. Later tools (canvas, speed, and the rest) follow in their slices.

## Architecture dependencies

- [Module structure and dependency direction](../architecture.md#module-structure-and-dependency-direction)
- [Edit commands and history](../architecture.md#edit-commands-and-history)
- [Threading](../architecture.md#threading)

## Local rules and implications

### Module structure and dependency direction

Screens live in `:app` under feature packages (`home`, `editor`, `timeline`) because the design tokens live in `:app`; they move to `:feature:*` modules if the app module grows too large. Screens reach the engine only through its public classes.

### Edit commands and history

Every gesture that changes the project dispatches a command; the UI never edits model objects directly. `EditorViewModel` applies commands through `OpenProject`, which runs one change at a time and shows a result only while that project is still open. Leaving the editor needs no confirmation because every edit is already saved; a failed save raises a notice instead.

Trim and reorder drags preview locally: `TimelineGestures` applies the in-progress command to the shown project in memory and stores it in `TimelineState.preview`, without touching the view model or the player. When the finger lifts, one command is dispatched, and the preview stays (settling) until the saved project arrives or one second passes. The playhead does not follow a trimmed edge, because with a fixed center playhead the clip would scroll away from the finger. Snap and zoom to playhead are editing-session state in `TimelineState` and are not saved. Trim handles appear only on the selected clip; reorder works on magnetic tracks only. Frame step pauses and seeks one frame through `ProjectPreview.pause()` and `FrameRate.stepFrames`.

The Overlay tool opens the photo picker for one item; `OverlayImport` (owned by `EditorViewModel`) probes it, asks `OverlayPlacement.propose` where it goes, dispatches `AddOverlayClip`, and selects the new clip once saved. Overlay tracks are drawn above the base at the same row height. A touch on an overlay selects it on lift or drags it in time past the touch slop; `OverlayPlacement.drag` gives the snapped, re-attached `MoveOverlay` and the drag previews like a trim. With an overlay selected and the playhead inside it, `OverlayTransformLayer` covers the preview: drag, pinch, and twist update a local placement (geometry in `OverlayGeometry`) and one `SetOverlayTransform` is dispatched when the last finger lifts. Opacity commits when the slider drag ends, so the preview shows it after saving. Any rejected command raises the rejection snackbar.

The tool row scrolls sideways. The Audio tool opens the document picker for one audio file; `AudioImport` (owned by `EditorViewModel`) probes it, asks `AudioPlacement.proposeMusic` where it goes, dispatches `AddAudioClip`, and selects the new clip once saved. Extract dispatches the proposed `ExtractAudio` and selects the copy. Volume swaps the row for an inline panel and Fade opens a bottom sheet; both commit one `SetClipAudio` when a drag ends or mute is pressed, and both close when the selection changes. Audio lanes are drawn below the picture tracks at the shared row height; `TimelineStrip` requests waveforms from `WaveformSource` and `TimelineAudioDrawing` draws bars, a flat line while loading or failed, fade shading, and the link badge on extracted sound. `TimelineMedia` owns the thumbnail and waveform caches of the open project.

The Text tool shows with no selection or a base clip selected and is dimmed when `TextPlacement.canPlace` fails. `TextEditing` dispatches the proposed `AddTextClip`, selects the clip, and opens `TextEditSheet` on the Style tab once the clip is saved; closing the sheet commits one `SetTextContent` if anything changed, and closing a new clip with blank words removes it. A selected text clip swaps the row's first tools for Edit and Animate. Text lanes are drawn above the overlays with a one-line label. On the preview, `OverlayTransformLayer` moves text like an overlay, sizes its box from `TextLayouter.measure` at a 1080-pixel reference height, and opens the Style tab on double tap (also offered as the first TalkBack action). See [text](text.md).

The Canvas tool shows with no selection or a base clip selected and is dimmed while the project is empty or a pick is importing. It opens `CanvasSheet`, whose Aspect and Background tabs dispatch one `SetCanvasAspect` or `SetCanvasBackground` per tap, skipping the current choice; the preview's aspect follows the chosen shape (`canvasAspectRatio`). A selected base clip leads the row with Fit / Fill, which dispatches `SetBaseClipPlacement` with fit and fill swapped, and Adjust, a toggle held by `CanvasEditing` for that clip only. While Adjust is on and the playhead is inside the clip, `OverlayTransformLayer` shows the clip's box at its fitted or filled size (`OverlayGeometry.fillBoxSize`) and commits one `SetBaseClipPlacement` when the last finger lifts. Placements equal to fit, centered, and upright are stored as none. `CanvasTools` holds this wiring.

The export button in the top bar opens a bottom sheet with resolution, frame rate, and quality choices and a size estimate. Starting an export saves the chosen settings and hands an `ExportRequest` to `ExportCoordinator`, which starts `ExportService`. While an export runs, the top-bar button shows a progress ring and the sheet can be closed; the sheet opens again by itself on success or failure. Success offers Share (system chooser with a read grant), Play, and Done. On Android 13 and later the app asks for notification permission before the first export; refusing it does not block the export.

### Threading

Timeline drawing reads derived state so scrubbing does not recompose the whole screen. `ExportCoordinator` is an app-scoped holder created in `AppContainer`; the service and the view model share export state through it on the main thread.

## Related blueprints

### Required

- [Timeline model](timeline-model.md) — positions come from the model's resolver.

### Impact checks

- [Media engine](media-engine.md) — when preview controls, playhead sync, or the export request and state change.
- [Project persistence](project-persistence.md) — when saved export settings change.

## Relevant implementation and tests

- `app/src/main/kotlin/com/framecraft/editor/home/` — `HomeScreen` (project list, row menu, rename and delete dialogs, empty state), `HomeViewModel`, `EditedTime`.
- `app/src/main/kotlin/com/framecraft/editor/media/` — `AndroidMediaImporter`, `VideoMetadataParser`, `AudioMetadataParser`.
- `app/src/main/kotlin/com/framecraft/editor/editor/` — `EditorViewModel`, `OpenProject`, `EditorRoute`, `EditorScreen`, `EditorTools` (tool availability and actions, overlay and music pickers), `EditorToolsZone` (tool row, opacity and volume panels, fade sheet), `OverlayImport`, `AudioImport`, `TextEditing` (add, open, and close the text sheet), `TextEditSheet`, `TextStyleControls`, `TextAnimationControls`, `CanvasSheet`, `CanvasTools` (canvas tool state and actions, the sheet host, base placement handles), `TimelineMedia`, `OverlayTransformLayer`, `OverlayGeometry`.
- `app/src/main/kotlin/com/framecraft/editor/timeline/` — `TimelineStrip` (canvas, controls, semantics), `TimelineGestures` (scrub, zoom, tap, trim, reorder, haptics), `TimelineOverlayGestures` (overlay select, drag, and nudge actions), `TimelineGeometry` (time/pixel math, tick ladder, thumbnail tiling, hit testing), `TimelineDrawing`, `TimelineAudioDrawing` (waveforms and fades), `TimelineTextDrawing` (text lane labels), `TimelineEditDrawing` (handles, floating clip, snap guide), `TimelineThumbnails`, `TimelineState`.
- `app/src/main/kotlin/com/framecraft/editor/export/` — `ExportSheet`, `ExportSheetActions`, `ExportResultContent`, `ExportViewModel`, `ExportCoordinator`, `ExportService`, `ExportNotifications`, `ExportIntents`; `editor/ExportControls.kt` wires the sheet into the editor.
- Tests: `app/src/test/kotlin/com/framecraft/editor/MainActivityTest.kt`, `home/HomeScreenTest.kt`, `home/EditedTimeTest.kt`, `editor/EditorScreenTest.kt`, `editor/TextEditSheetTest.kt`, `editor/CanvasSheetTest.kt`, `editor/CanvasToolsTest.kt`, `editor/OverlayGeometryTest.kt`, `media/VideoMetadataParserTest.kt`, `media/AudioMetadataParserTest.kt`, `timeline/TimelineGeometryTest.kt`, `timeline/TimelineStripTest.kt`, `export/ExportSheetContentTest.kt`, `export/ExportCoordinatorTest.kt`.

## Acceptance or verification criteria

- [x] The home screen and editor preview follow `assets/wireframes/home-empty.md` and `assets/wireframes/editor-preview.md`.
- [x] Picked files that cannot be read are skipped and reported; if none can be read, the user is told and returned home.
- [x] The timeline follows `assets/wireframes/timeline-slice3.md`: dragging pauses and seeks only to frame boundaries, and pinching zooms around the playhead.
- [x] The home list follows `assets/wireframes/home-projects.md`: rows open projects, rename rejects blank names, delete asks first, and unreadable projects offer delete only.
- [x] Undo and redo are enabled only when available; tapping a clip selects it, tapping empty space clears the selection, and the Delete tool needs a selection.
- [x] Basic edits follow `assets/wireframes/timeline-slice5.md`: dragging a trim handle saves exactly one trim, Split is enabled only when the playhead is inside the selected clip, frame-step buttons are disabled at the timeline bounds, and the snap and zoom-to-playhead toggles switch state.
- [x] Export follows `assets/wireframes/export.md`: choices update the request, an empty project cannot export, progress and saving states show, success offers Share and Play, failures explain the cause, and the top-bar button shows progress while exporting.
- [x] Overlays follow `assets/wireframes/overlays-slice7.md`: the Overlay tool is offered without a selection and dimmed when it cannot add, a selected overlay shows its opacity instead, and the preview handle math fits, centers, clamps, and snaps as specified (`EditorScreenTest`, `OverlayGeometryTest`).
- [x] Audio follows `assets/wireframes/audio-slice8.md`: the tools appear for the right selections, the mute toggle returns to the last audible volume, and audio lanes request waveforms and select on tap (`EditorScreenTest`, `TimelineStripTest`).
- [x] Text follows `assets/wireframes/text-slice9.md`: the Text tool appears without a selection and is dimmed when no lane is free, a selected text clip shows Edit and Animate, the sheet returns the typed words and style, sets an entrance animation, disables Done for blank words, and text lanes select on tap (`EditorScreenTest`, `TextEditSheetTest`, `TimelineStripTest`, `OverlayGeometryTest`).
- [x] The canvas follows `assets/wireframes/canvas-slice10.md`: the Canvas tool appears without a selection and with a base clip and is dimmed when it cannot open, a base clip leads with Fit / Fill and Adjust showing their on state, other selections have no canvas tools, the sheet sends the tapped aspect and background, fit and fill swap while keeping the position, and the fill box covers the canvas (`EditorScreenTest`, `CanvasSheetTest`, `CanvasToolsTest`, `OverlayGeometryTest`).
- [x] Export requests start the service once, cancelling before the service starts leaves nothing to run, and share and play intents carry the read grant (`ExportCoordinatorTest`).

## Remaining gaps and unknowns

- Long-press reorder, the snap guide, and haptics have no automated UI test; reorder slots and snapping are tested in `EditGesturesTest`.
- Reorder does not auto-scroll when the lifted clip reaches the strip edge.
- Trim and reorder are touch-only; there is no accessibility action for them yet. A selected overlay can be nudged one frame either way through TalkBack actions.
- Overlay rows use the shared 64dp row height rather than the 48dp in the design plan.
- The preview corner dots and rotation handle are not separate drag targets; gestures anywhere on the preview move, resize, and rotate the selected overlay. Opacity has no live preview while the slider moves.
- Overlay timeline drags, the transform layer gestures, and the add-overlay flow have no automated UI test; the placement and drag rules are tested in `OverlayPlacementTest` and the handle math in `OverlayGeometryTest`.

- The add-text flow, double tap to edit, and text-lane trims have no automated UI test; placement is tested in `TextPlacementTest` and the sheet in `TextEditSheetTest`.
- The base placement gestures and the sheet-to-preview flow have no automated UI test; the mapping is tested in `CanvasToolsTest` and the box math in `OverlayGeometryTest`. The canvas sheet has no live preview of a shape before it is tapped.
- Thumbnails show solid surfaceVariant while loading; a loading shimmer is deferred.
- The fade sheet, the music picker flow, and audio-lane trims have no automated UI test; their rules are tested in `AudioCommandsTest` and `AudioPlacementTest`.
- Thumbnail extraction (`ClipThumbnails`) needs a device and has no automated test; tiling and requests are tested.
- `EditorViewModel` has no unit test because its preview player cannot run under Robolectric; save and restore are covered by `ProjectStoreTest`.
- The save-failure and recovered-edits notices have no automated test.
- The timeline does not scroll to bring a selected clip into view.
- `ExportService` and `ExportNotifications` need a device and have no automated test; `ExportViewModel` has no unit test because of Robolectric main-dispatcher limits.
