# Captions

## Outcome or responsibility

Own auto-captions: recognizing speech in clip sound on the device, the one-time speech model download, chunking recognized words into readable captions, binding each caption to the clip whose sound it transcribes so trims and speed changes keep it on its words, highlighting the spoken word, and the Captions tool and sheet that generate, regenerate, and remove them.

## Current verified status

**Status:** Partial

Caption content, its binding to the parent clip, the timing locks, and how trim, split, delete, retyping, and `ReplaceCaptions` treat captions are verified by `CaptionCommandsTest`, including undoing a trim that removed a caption. Chunking, source selection, and planning are verified by `CaptionPlanningTest`; result parsing by `VoskResultParserTest`; resampling and the transcription timeline by `CaptionTranscriberTest`; the spoken-word timing and highlight range by `KaraokeTest`. The schema 11 upgrade is verified by `ProjectStoreTest`. The model download, decoding real clip sound, recognition quality, and how the sheet and highlight look need a device and are unverified; the caption sheet has no UI test yet.

## Architecture dependencies

- [Project document model](../architecture.md#project-document-model) — a caption is a text clip with optional word timing and highlight fields, so older projects read as plain text.
- [Anchoring and ripple](../architecture.md#anchoring-and-ripple) — captions are bound to their parent clip and derived from its source span after every command, which is why their timing is never edited directly.
- [Clip speed and source mapping](../architecture.md#clip-speed-and-source-mapping) — word times are source times, mapped onto the timeline through the parent's `SpeedMap`.
- [Edit commands and history](../architecture.md#edit-commands-and-history) — generating, regenerating, and removing captions are one command each, with an exact inverse.
- [Engine translation](../architecture.md#engine-translation) — captions draw as text overlays; the highlight redraws as words start.
- [Media access](../architecture.md#media-access) — clip sound is read on the device and the model download is the only network use.
- [Threading](../architecture.md#threading) — decoding and recognition run off the main thread and can be cancelled.

## Local rules and implications

### Model

- `CaptionContent(words, sourceEndMicros)` sits on `TextContent` as `caption`. It holds 1 to 12 `CaptionWord`s (text without whitespace and its source start and end) with strictly increasing starts, and `sourceEndMicros` after the first start ends the hold.
- `TextStyle.highlightArgb` is the spoken-word color; captions are made with a yellow highlight on white words with the default black outline, at 6% of the canvas height and 70% down the canvas.
- `CaptionResolver` derives a bound caption (`isBoundCaption`: has caption content and an anchor) from its parent: the words whose start lies in the parent's source span show, the clip starts at the first shown word and ends where the parent plays `sourceEndMicros`, and the text is the shown words joined by spaces. `materialize` applies this after every command; `validate` refuses a project whose captions are off a sound clip, on a text lane parent, or out of step with their speech.

### Commands

- Generated captions are added by `ReplaceCaptions`, a list of `CaptionLaneChange`s (lane id and index, removed caption ids, added captions). A lane is created at its index when missing and removed when emptied, and the shared text asset is added or removed with the first or last text clip. Only captions may be removed or added. Its inverse is the reverse list of reverse changes.
- `MoveClip`, `MoveOverlay`, `ReanchorOverlay`, `TrimClip`, and `SplitClip` refuse a caption with `CaptionLocked`, including one whose parent was deleted.
- Trimming a parent removes the captions it no longer plays any word of and carries them in its inverse (`restoredCaptions`) so undo can put them back. `TrimClip` re-places the surviving captions before it puts the restored ones back, because until then they would overlap.
- Splitting a parent hands each caption to the part that plays its first word; words past the cut stay stored but hidden, and undo shows them again.
- `SetTextContent` that changes a caption's words removes its caption content, leaving plain text anchored where it was. Style changes keep it a caption.
- `TextPlacement` never places typed text on a lane that holds captions.

### Planning

- `CaptionPlanner.sources` picks the clips to caption by `CaptionScope` (`ALL`, `VOICEOVERS`, `SELECTED`): clips off text lanes whose asset is timed and has sound, skipping silent ones (clip or lane volume 0) unless muted clips are included.
- `CaptionChunker` drops noise markers and words without a letter or digit, then breaks captions at 6 words, 42 characters, 3.5 seconds, or a 600 ms pause. Each caption starts with a capital and the pronoun "i" is capitalized. A caption is held at least 1 second, ending 80 ms before the next one.
- `CaptionPlanner.plan` replaces every caption in the project: it removes all existing captions and adds one new lane per source track at the top, so captions of one track never overlap. A run that hears no speech changes nothing.

### Recognition

- `SpeechModelStore` installs `vosk-model-small-en-us-0.15` into app-private storage: it needs 150 MB free, downloads with timeouts while hashing, checks the pinned size and SHA-256, unpacks to a staging folder with a path guard and a completion marker, and renames it into place. Partial files are deleted on failure or cancellation, which report no space, network, or a damaged download.
- `ClipSoundDecoder` decodes only the clip's source span with `MediaExtractor` and `MediaCodec`; `SpeechResampler` downmixes it to 16 kHz mono. `CaptionTranscriber` opens one recognizer per run, feeds each span, and times words in source time; a span that cannot be read gives no words.
- Vosk recognizes English only and returns lowercase words without punctuation. Its x86_64 library is not 16 KB aligned, so the app ships ARM ABIs only.

### Engine

- `Karaoke.wordStarts` maps a caption's shown word starts onto the timeline and `PlaybackPlan` puts them on the caption's `TextSpan` when it has a highlight. `TextOverlay` redraws when the spoken word changes, and `TextLayouter` colors that word's fill, leaving outline and shadow unchanged.

### Editing

- The sheet follows `assets/wireframes/captions-slice16.md`. Captions sits after Text when nothing or a video clip is selected, and is enabled when a clip has sound or captions exist.
- The sheet picks the scope (Selected clip only when one is selected) and whether muted clips count, names the language and that speech stays on the phone, and shows the one-time download size when the model is missing. While it downloads or generates it shows progress and Cancel; it ends with the number of captions added, no speech found, or a failure with Try again.
- Captions select on the timeline but offer no drag, trim handles, nudges, or split; they move with their clip and can still be restyled, repositioned on the canvas, or deleted.

## Related blueprints

### Required

- [Text](text.md) — captions are text clips with its style, drawing, and sheet.
- [Timeline model](timeline-model.md) — caption fields and binding are under its validation.
- [Edit history](edit-history.md) — the caption commands and the trim and split rules follow its inverse rules.

### Impact checks

- [Media engine](media-engine.md) — when text spans or text drawing change.
- [Editor UI](editor-ui.md) — when the tool row, sheets, or timeline gestures change.
- [Project persistence](project-persistence.md) — when caption fields or command types change.
- [Clip speed](clip-speed.md) — when `SpeedMap` or source spans change.
- [Voiceover](voiceover.md) — when voiceover flags or lane volumes change.

## Relevant implementation and tests

- `domain/src/main/kotlin/com/framecraft/editor/domain/model/` — `CaptionContent`, `CaptionResolver`, and the `caption` and `highlightArgb` fields of `TextContent` and `TextStyle`.
- `domain/src/main/kotlin/com/framecraft/editor/domain/command/` — `ReplaceCaptions`, `LaneClip`, the caption lock in `EditCommand`, and the caption rules in `TrimClip`, `SplitClip`, and `SetTextContent`.
- `domain/src/main/kotlin/com/framecraft/editor/domain/edit/` — `CaptionChunker`, `CaptionPlanner`, and the caption-lane rule in `TextPlacement`.
- `data/project/src/main/kotlin/com/framecraft/editor/data/project/Migration10To11.kt` — the schema 11 upgrade.
- `engine/captions/src/main/kotlin/com/framecraft/editor/captions/` — `SpeechEngine`, `VoskSpeechEngine`, `VoskResultParser`, `SpeechModelStore`, `ClipSoundDecoder`, `SpeechResampler`, `CaptionTranscriber`.
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/` — `plan/Karaoke.kt`, `plan/PlaybackPlan.kt`, `text/TextOverlay.kt`, `text/TextLayouter.kt`.
- `app/src/main/kotlin/com/framecraft/editor/` — `editor/Captioning`, `editor/CaptionSheet`, the tool in `EditorToolsZone` and `EditorTools`, `di/AppContainer`, and the caption guards in `timeline/TimelineGestures` and `timeline/TimelineOverlayGestures`.
- Tests: `domain/src/test/kotlin/com/framecraft/editor/domain/command/CaptionCommandsTest.kt`, `domain/src/test/kotlin/com/framecraft/editor/domain/edit/CaptionPlanningTest.kt`, `data/project/src/test/kotlin/com/framecraft/editor/data/project/ProjectStoreTest.kt`, `engine/captions/src/test/kotlin/com/framecraft/editor/captions/VoskResultParserTest.kt`, `engine/captions/src/test/kotlin/com/framecraft/editor/captions/CaptionTranscriberTest.kt`, `engine/media3/src/test/kotlin/com/framecraft/editor/engine/plan/KaraokeTest.kt`.

## Acceptance or verification criteria

- [x] Captions validate against their speech, follow trims, go to the part that plays them on split, refuse timing edits, turn into text when retyped, and detach when their clip is deleted (`CaptionCommandsTest`).
- [x] `ReplaceCaptions` adds and removes lanes and the text asset, undoes exactly, and round-trips through JSON (`CaptionCommandsTest`).
- [x] Chunks break, clean, case, and hold as specified; sources follow scope and sound; plans place captions on per-track lanes and replace old ones (`CaptionPlanningTest`).
- [x] Recognizer results become source-timed words, bad results give none, and sound is resampled to 16 kHz (`VoskResultParserTest`, `CaptionTranscriberTest`).
- [x] Word starts follow the clip and the spoken word and its characters are found (`KaraokeTest`).
- [x] Projects from schema 10 open and save as schema 11 (`ProjectStoreTest`).
- [x] A trim that leaves a caption no words removes it, its undo puts it back exactly, and redo removes it again (`CaptionCommandsTest`).
- [ ] The model downloads and installs, real clip sound is recognized, and the highlight renders and exports as previewed (needs a device).

## Remaining gaps and unknowns

- English only; recognized words are lowercase without punctuation beyond the casing rules above.
- Regenerating replaces every caption, including restyled ones; there is no per-clip regenerate or caption style preset.
- Editing a caption's words drops its word highlight without a notice.
- Words of a split caption past the cut stay hidden until undo or regenerate.
- No caption sheet UI test, and recognition speed on long projects is unmeasured.
