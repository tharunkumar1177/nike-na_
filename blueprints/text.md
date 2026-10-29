# Text

## Outcome or responsibility

Own titles and captions typed by the user: text clips on text lanes, their words, style, and entrance and exit animations, how they are drawn in preview and export, and the text sheet and tools that edit them.

## Current verified status

**Status:** Partial

The text model rules, the commands `AddTextClip`, `RemoveTextClip`, and `SetTextContent`, animation handling in trim, split, and merge, and lane placement in `TextPlacement` are verified by `TextCommandsTest` and `TextPlacementTest`. The schema 4 upgrade is verified by `ProjectStoreTest`. Text span planning is verified by `PlaybackPlannerTest` and the animation curves by `TextCurvesTest`. The Text, Edit, and Animate tools are verified by `EditorScreenTest`, the sheet by `TextEditSheetTest`, the text box size by `OverlayGeometryTest`, and text lane selection by `TimelineStripTest`. How text actually looks in preview and export needs a device and is unverified.

## Architecture dependencies

- [Project document model](../architecture.md#project-document-model) — text lanes are `TEXT` tracks that are never magnetic; every text clip points at the one shared text asset and carries its `TextContent`.
- [Anchoring and ripple](../architecture.md#anchoring-and-ripple) — new text is anchored to the base clip under the playhead and follows it through moves, ripples, and splits like an overlay.
- [Edit commands and history](../architecture.md#edit-commands-and-history) — each sheet session is one command whose inverse restores the exact previous content, including animations shortened by a trim.
- [Engine translation](../architecture.md#engine-translation) — text becomes bitmap overlays over the composed picture, shared by preview and export.
- [Threading](../architecture.md#threading) — Media3 draws text bitmaps on its GL thread; the sheet measures text on the main thread.

## Local rules and implications

### Model

- `TextContent` holds the words (1 to 500 characters, never blank), a `TextStyle`, and optional `animationIn` and `animationOut`. `TextStyle` has a system font (sans, bold, serif, mono), a size from 2% to 30% of the canvas height (8% by default), a color, an optional outline and shadow sized as fractions of the text size, an optional box behind each line, and an alignment.
- `TEXT_ASSET` (id `text-asset`) has no URI, duration, or sound and is 1 by 1. `AddTextClip` adds it when missing and `RemoveTextClip` drops it with the last text clip.
- Each animation lasts more than zero and no longer than its clip; the two may overlap. Text clips keep the default volume and fades.

### Placement and commands

- `TextPlacement.propose` places new text at the frame-snapped playhead inside the base clip under it, lasting 3 seconds or up to that clip's end, anchored to it, centered at natural size. It uses the first text lane free for that span, else a new lane after the other text lanes; a project has at most 8 text lanes (`MAX_TEXT_LANES`). `canPlace` tells the tool row whether that would succeed.
- `SetTextContent` replaces a clip's whole content, fitting its animations to the clip, and its inverse restores the old content.
- `TrimClip` shortens animations that no longer fit and its inverse restores them. `SplitClip` keeps the entrance on the left part and the exit on the right; `MergeClips` joins only parts with the same words and style whose inner animations are absent, and its inverse from a split restores both.
- Overlay placement commands (`SetOverlayTransform`, `MoveOverlay`) and the timeline's move, nudge, and trim gestures treat text like overlays.

### Rendering

- `PlaybackPlanner` turns each text clip into a `TextSpan`, ordered from the bottom text lane up so upper lanes draw on top, and pads base sequences to the end of the latest text.
- `CompositionFactory` puts one `OverlayEffect` of `TextOverlay`s first in the composition's video effects, so export resizing happens after the text is drawn.
- `TextLayouter` lays out the words with `StaticLayout` at the canvas size, wrapping at 90% of its width, and draws line boxes, then the outline with the shadow, then the fill. A typing animation hides characters with a span that changes neither the layout nor the bitmap size.
- `TextCurves` evaluates animations: fade ramps opacity, slide rises a quarter of the half-canvas while fading, pop grows past full size and settles, and typewriter reveals characters. The exit plays the entrance curve backwards; entrance and exit multiply opacity and scale, add offsets, and take the fewer revealed characters.
- `TextOverlay` redraws its bitmap only when the canvas size or the revealed count changes and is fully transparent outside its span.

### Editing

- The Text tool shows with no selection or a base clip selected, and is dimmed while picks import or when `canPlace` fails. It adds "Text", selects it, and opens the sheet on the Style tab with the words selected and the keyboard up.
- Selected text shows Edit, Animate, Opacity, Split, and Delete. Edit and a double tap on the preview box open the Style tab; Animate opens the Animation tab.
- The sheet keeps every change as a draft and dispatches one `SetTextContent` when it closes, by Done or by dismissal. Blank words disable Done; closing with blank words removes text added for that sheet and keeps an existing clip's words.
- The preview box of selected text is sized by `TextLayouter.measure` on the project canvas, so it matches the drawn text.
- Text lanes draw as secondary-container blocks labelled with their words; they request no thumbnails.

## Related blueprints

### Required

- [Timeline model](timeline-model.md) — text tracks, the text asset, and anchors are model rules.
- [Edit history](edit-history.md) — the text commands follow its command and inverse rules.

### Impact checks

- [Media engine](media-engine.md) — when the composition's effect order or text spans change.
- [Editor UI](editor-ui.md) — when tools, the sheet, preview handles, or text-lane drawing change.
- [Project persistence](project-persistence.md) — when text fields or command types change.

## Relevant implementation and tests

- `domain/src/main/kotlin/com/framecraft/editor/domain/model/` — `TextStyle`, `TextContent`, and the text rules in `MediaAsset`, `Track`, `Clip`, and `Project`.
- `domain/src/main/kotlin/com/framecraft/editor/domain/command/` — `AddTextClip`, `RemoveTextClip`, `SetTextContent`, `TextFit`, and the animation handling in `TrimClip`, `SplitClip`, and `MergeClips`.
- `domain/src/main/kotlin/com/framecraft/editor/domain/edit/TextPlacement.kt` — text placement.
- `data/project/src/main/kotlin/com/framecraft/editor/data/project/Migration3To4.kt` — the schema 4 upgrade.
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/text/` — `TextCurves`, `TextLayouter`, `TextOverlay`; `plan/PlaybackPlan.kt` (`TextSpan`); `CompositionFactory`; `placementSettings` in `OverlayCompositorSettings`.
- `app/src/main/kotlin/com/framecraft/editor/editor/` — `TextEditing`, `TextEditSheet`, `TextStyleControls`, `TextAnimationControls`, the text branches of `EditorTools` and `EditorToolsZone`, and `OverlayTransformLayer`.
- `app/src/main/kotlin/com/framecraft/editor/timeline/TimelineTextDrawing.kt` — text lane drawing.
- Tests: `domain/src/test/kotlin/com/framecraft/editor/domain/command/TextCommandsTest.kt`, `domain/src/test/kotlin/com/framecraft/editor/domain/edit/TextPlacementTest.kt`, `data/project/src/test/kotlin/com/framecraft/editor/data/project/ProjectStoreTest.kt`, `engine/media3/src/test/kotlin/com/framecraft/editor/engine/text/TextCurvesTest.kt`, `engine/media3/src/test/kotlin/com/framecraft/editor/engine/plan/PlaybackPlannerTest.kt`, `app/src/test/kotlin/com/framecraft/editor/editor/EditorScreenTest.kt`, `app/src/test/kotlin/com/framecraft/editor/editor/TextEditSheetTest.kt`, `app/src/test/kotlin/com/framecraft/editor/editor/OverlayGeometryTest.kt`, `app/src/test/kotlin/com/framecraft/editor/timeline/TimelineStripTest.kt`.

## Acceptance or verification criteria

- [x] Adding text opens a lane and adds the shared asset; removing the last text drops both, and undo restores them (`TextCommandsTest`).
- [x] Content changes, trims, splits, and merges keep animations within the clip with exact inverses (`TextCommandsTest`).
- [x] Text clips are rejected off text lanes, with sound changes, or with animations longer than the clip (`TextCommandsTest`).
- [x] Every text command round-trips through JSON, and a schema 3 project opens and saves as schema 4 (`TextCommandsTest`, `ProjectStoreTest`).
- [x] New text goes at the playhead inside its base clip, reuses free lanes, and stops at 8 lanes (`TextPlacementTest`).
- [x] Text plans as spans from the bottom lane up and pads the base (`PlaybackPlannerTest`); each animation and their composition follow their curves (`TextCurvesTest`).
- [x] The tools follow `assets/wireframes/text-slice9.md`: Text without a selection and dimmed when full, Edit and Animate for selected text (`EditorScreenTest`); the sheet returns one edited content, sets presets, and refuses blank words (`TextEditSheetTest`); text lanes select on tap without thumbnails (`TimelineStripTest`).
- [ ] Text, its style, and its animations look the same in preview and export (needs a device).

## Remaining gaps and unknowns

- Text is drawn by Media3 composition effects in preview, which Media3 marks as still settling; preview and export have not been compared on a device.
- Right-to-left text, emoji, and very long words have no automated rendering test; `TextLayouter` needs a device or screenshot tests.
- Only system fonts are offered; custom fonts, per-character styling, and text templates are not planned yet.
- Captions generated from speech arrive in slice 12.
