# Audio

## Outcome or responsibility

Own sound editing: music on audio lanes, extracting a video's sound onto its own lane, per-clip volume and fades, how that sound is processed for preview and export, and the waveforms the timeline draws.

## Current verified status

**Status:** Partial

The audio model rules, the commands `AddAudioClip`, `RemoveAudioClip`, `ExtractAudio`, and `SetClipAudio`, fade handling in trim, split, and merge, and lane placement in `AudioPlacement` are verified by `AudioCommandsTest` and `AudioPlacementTest`. The schema 3 upgrade is verified by `ProjectStoreTest`. The gain envelope, sample clamping, and waveform bucketing are verified by `AudioGainTest` and `WaveformBucketerTest`, and audio sequence planning by `PlaybackPlannerTest`. The Audio, Extract, Volume, and Fade tools, the mute toggle, and waveform requests for audio lanes are verified by `EditorScreenTest` and `TimelineStripTest`. What a user actually hears in preview and export, waveform decoding, and the music picker need a device and are unverified.

## Architecture dependencies

- [Project document model](../architecture.md#project-document-model) — audio lanes are `AUDIO` tracks that are never magnetic, so music and extracted sound never ripple-close; volume and fades live on every clip, so a video's own sound uses the same controls as music.
- [Anchoring and ripple](../architecture.md#anchoring-and-ripple) — extracted sound is anchored to its video at offset zero and follows it through moves, ripples, and splits. Trimming the video does not trim the sound; the copy keeps its own range.
- [Edit commands and history](../architecture.md#edit-commands-and-history) — every sound edit is one command whose inverse restores the exact previous volume and fades, including fades shortened by a trim.
- [Engine translation](../architecture.md#engine-translation) — audio sequences come last and carry no video; the per-clip chain below is applied to every clip with sound in both preview and export.
- [Media access](../architecture.md#media-access) — music comes from the document picker, so a music file can go offline like any other source.
- [Threading](../architecture.md#threading) — waveforms decode on the IO dispatcher, one source at a time; the cache is read and written on the main thread.

## Local rules and implications

### Lanes and placement

- `AudioPlacement.proposeMusic` places new music at the frame-snapped playhead for the whole file, on the first audio lane free for that span, else on a new lane after the others. A project has at most 8 audio lanes (`MAX_AUDIO_LANES`); with none free, no command is proposed and the editor shows the rejection snackbar.
- `AddAudioClip` adds the asset when missing and creates the lane at the requested index; `RemoveAudioClip` drops an emptied lane and an asset no other clip uses, so removing an extracted copy keeps the shared video asset.

### Extracting sound

- `canExtract` needs a video asset with sound, no copy already anchored to that clip on an audio lane, and a free lane. `proposeExtract` copies the clip's source range and fades, takes the video's volume (100 when the video was muted), and anchors the copy to the video at offset zero.
- `ExtractAudio` sets the video's volume to 0 and adds the copy; its inverse is `RemoveAudioClip` with the video's old volume, so undo brings the video's sound back. Deleting the copy later is a plain `RemoveAudioClip` and leaves the video muted.

### Volume and fades

- `SetClipAudio` sets volume, both fades, and the ducking depth on any clip except images.
- `TrimClip` shortens fades that no longer fit (fade-in first, then fade-out from what is left) and its inverse restores the original fades. `SplitClip` keeps the fade-in on the left part and the fade-out on the right; `MergeClips` joins only parts whose inner fades are zero and whose volumes match.

### Sound processing

- `AudioItemEffects` builds each item's chain: `ToInt16PcmAudioProcessor`, a stereo mixer for 1 to 6 source channels (constant gain for mono and stereo, constant power above), then `ClampingGainProcessor` unless the clip plays at unity gain with no fades.
- `GainEnvelope` multiplies the volume by linear fade ramps from the clip's start and end, and by any duck windows the plan gave the clip (lane volume and ducking are owned by [voiceover](voiceover.md)). `ClampingGainProcessor` applies it per frame on 16-bit samples, clamps to the sample range instead of wrapping, and restarts its frame position from the stream offset after a seek.
- A muted picture clip is passed with its audio removed, so silence costs no processing.

### Waveforms

- `AudioWaveforms` decodes a source's sound with `MediaExtractor` and `MediaCodec` (16-bit or float PCM) into 50 ms RMS buckets (`WaveformBucketer`), scaled so the loudest bucket is 1, and keeps them in an 8 MB LRU cache. A failed source is remembered and not retried during the session.
- The timeline requests waveforms for the sources of every audio-lane clip when the project changes and redraws when the cache version changes. Thumbnails are never requested for audio lanes.

## Related blueprints

### Required

- [Timeline model](timeline-model.md) — audio lanes, clip audio fields, and anchors are model rules.
- [Edit history](edit-history.md) — the audio commands follow its command and inverse rules.

### Impact checks

- [Media engine](media-engine.md) — when the per-item chain or sequence order changes.
- [Editor UI](editor-ui.md) — when tools, panels, or audio-lane drawing change.
- [Project persistence](project-persistence.md) — when audio fields or command types change.

## Relevant implementation and tests

- `domain/src/main/kotlin/com/framecraft/editor/domain/command/` — `AddAudioClip`, `RemoveAudioClip`, `ExtractAudio`, `SetClipAudio`, and the fade handling in `TrimClip`, `SplitClip`, and `MergeClips`.
- `domain/src/main/kotlin/com/framecraft/editor/domain/edit/AudioPlacement.kt` — music and extraction placement.
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/` — `AudioItemEffects`, `GainEnvelope`, `ClampingGainProcessor`, `WaveformBucketer`, `AudioWaveforms`, and the audio branches of `CompositionFactory`.
- `app/src/main/kotlin/com/framecraft/editor/editor/` — `AudioImport`, `EditorToolsZone` (tool row, volume panel, fade sheet), `EditorTools` (availability and actions, music picker), `TimelineMedia`.
- `app/src/main/kotlin/com/framecraft/editor/timeline/TimelineAudioDrawing.kt` — waveform bars, loading line, and fade shading.
- `app/src/main/kotlin/com/framecraft/editor/media/` — `AudioMetadataParser` and the audio branch of `AndroidMediaImporter`.
- Tests: `domain/src/test/kotlin/com/framecraft/editor/domain/command/AudioCommandsTest.kt`, `domain/src/test/kotlin/com/framecraft/editor/domain/edit/AudioPlacementTest.kt`, `engine/media3/src/test/kotlin/com/framecraft/editor/engine/AudioGainTest.kt`, `engine/media3/src/test/kotlin/com/framecraft/editor/engine/WaveformBucketerTest.kt`, `engine/media3/src/test/kotlin/com/framecraft/editor/engine/plan/PlaybackPlannerTest.kt`, `app/src/test/kotlin/com/framecraft/editor/media/AudioMetadataParserTest.kt`, `app/src/test/kotlin/com/framecraft/editor/editor/EditorScreenTest.kt`, `app/src/test/kotlin/com/framecraft/editor/timeline/TimelineStripTest.kt`.

## Acceptance or verification criteria

- [x] Extracting mutes the video, adds an attached copy, and undo and redo restore both (`AudioCommandsTest`).
- [x] Silent videos and photos cannot be extracted, and a clip's sound can be extracted only once (`AudioCommandsTest`, `AudioPlacementTest`).
- [x] Music opens a lane at the playhead, reuses the first free lane, and stops at 8 lanes (`AudioPlacementTest`).
- [x] Volume and fades are validated, trims shorten fades with an exact inverse, and split and merge divide and rejoin fades (`AudioCommandsTest`).
- [x] Every audio command round-trips through JSON, and a schema 2 project opens and saves as schema 3 (`AudioCommandsTest`, `ProjectStoreTest`).
- [x] Gain follows volume and fade ramps and clamps instead of wrapping (`AudioGainTest`); waveforms bucket and normalize (`WaveformBucketerTest`).
- [x] Audio sequences plan last and base sequences pad to the end of the latest track (`PlaybackPlannerTest`).
- [x] The tools follow `assets/wireframes/audio-slice8.md`: Audio without a selection, Extract, Volume, and Fade for a video with sound, none for silent clips, and the mute toggle returns to the last audible volume (`EditorScreenTest`); audio lanes request their waveforms and select on tap (`TimelineStripTest`).
- [ ] Volume, fades, and extracted sound are heard as set in preview and export (needs a device).

## Remaining gaps and unknowns

- Audio clips cannot be dragged in time yet; they are placed by the tools and trimmed with handles.
- Sources with more than 6 channels fail to configure the stereo mixer; no such source has been tested.
- Waveform decoding, the music picker, and the gain chain inside Media3 have no automated test because they need a device.
- The fade sheet's sliders have no automated UI test; fade rules are tested in `AudioCommandsTest`.
- Ducking, voiceover, and a mixer view arrive in slice 14.
