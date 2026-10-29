# Voiceover and mixer

## Outcome or responsibility

Own recording voiceovers from the microphone into a project, ducking other clips under them, and the per-lane volumes of the mixer: the model fields, the commands, how the engine folds lane volume and ducking into each clip's gain, the take files and their cleanup, and the Voiceover, Mixer, and Duck tools.

## Current verified status

**Status:** Partial

Lane volume, voiceover and ducking fields, their commands, and placing a take are verified by `MixerCommandsTest`. The schema 9 upgrade and the cleanup of unused takes are verified by `ProjectStoreTest` and `RecordingSweepTest`. Lane volume scaling and duck windows are verified by `AudioMixTest`, and the gain curve under a duck by `AudioGainTest`. The recorder's states are verified by `VoiceoverRecordingTest`; the mixer, ducking sheet, and recorder pill by `MixerSheetTest`; and tool visibility by `EditorScreenTest`. Recording on a real microphone, the permission flow, and how ducking sounds need a device and are unverified.

## Architecture dependencies

- [Project document model](../architecture.md#project-document-model) — lane volume is a track field and voiceover and ducking are clip fields, all defaulting so older projects read unchanged.
- [Edit commands and history](../architecture.md#edit-commands-and-history) — every mixer and ducking edit has an exact inverse, and a take is added as one ordinary audio clip edit.
- [Engine translation](../architecture.md#engine-translation) — lane volume and ducking are applied when the plan is built, so preview and export match.
- [Media access](../architecture.md#media-access) — takes are app-private files the project owns, not picked media.

## Local rules and implications

### Model

- `Track.volumePercent` runs 0 to 200 and defaults to 100. Only tracks that play sound (`TrackKind.takesVolume`, every kind but text) may move off 100.
- `Clip.voiceover` marks a recorded take and is only allowed on sound assets. `Clip.autoDuckPercent` runs 0 to 100 and says how far a clip drops while a voiceover plays; 0 means it does not duck.
- A clip with ducking is not "default audio"; split parts keep both fields, and merge needs both parts to agree.

### Commands

- `SetTrackVolumes(values)` sets lane volumes as one undo step and inverts to the previous values. It refuses a missing track (`TrackNotFound`) and a text track (`TrackVolumeNotSupported`).
- `SetClipAudio` carries `autoDuckPercent`; its inverse restores it with the volume and fades.
- `AddAudioClip`, `ExtractAudio`, and `AddOverlayClip` carry `trackVolumePercent`, so undoing the removal of a lane's last clip brings the lane back at its volume.
- `AudioPlacement.proposeTake` places a take like music at the playhead, marked as a voiceover; `canRecord` says whether a lane is free there.

### Engine

- `AudioMix.atTrackVolume` multiplies each clip's volume by its lane's, so a muted lane is silent through the existing mute rule.
- `AudioMix.ducked` collects the audible voiceover spans, merges spans less than 500 ms apart, and gives every other clip with ducking a `DuckWindow` in clip time at floor `(100 - autoDuckPercent) / 100`.
- `GainEnvelope` ramps into and out of each window over 250 ms and takes the lowest duck that applies.
- The preview player handles audio focus.

### Recording

- `MediaRecorderVoiceoverCapture` records mono AAC at 128 kbps and 44.1 kHz into `recordings/take-<id>.m4a` in the project directory. It refuses to start with less than 50 MB free.
- `VoiceoverRecording` runs Closed, Armed, a 3-2-1 countdown, Recording, and Saving. Takes under 500 ms are deleted. A take with no free lane is deleted and reported. Leaving the screen (`ON_STOP`) stops the take, keeps it, and says so.
- Playback runs while a take records, and the take lands where recording started.
- Opening a project deletes takes no snapshot or journal names (`RecordingSweep`), so takes reachable through undo stay.

### Editing

- The UI follows `assets/wireframes/voiceover-slice14.md`.
- With nothing selected the row offers Voiceover, which is dimmed while no lane is free, and Mixer.
- Voiceover asks for the microphone first. A refusal explains why and links to the app's settings.
- The recorder is a pill over the preview with close, a headphones hint, and record. Then a countdown announced to TalkBack, then the dot, elapsed time, level meter, and Stop.
- The Mixer sheet lists "Main video", "Overlay N", and "Audio N" lanes that hold clips. Each has a 0 to 200% slider and a mute toggle that remembers the last volume. Each finished change is one edit.
- A sound clip that is not a voiceover offers Duck. Its sheet turns ducking on at 60% and sets the depth from 10 to 100%.

## Related blueprints

### Required

- [Audio](audio.md) — takes are audio lane clips under its placement and sound chain rules.
- [Edit history](edit-history.md) — the mixer commands follow its inverse rules.

### Impact checks

- [Media engine](media-engine.md) — when sequence order or the per-item sound chain change.
- [Editor UI](editor-ui.md) — when the tool row, preview overlay, or notices change.
- [Project persistence](project-persistence.md) — when mixer fields, command types, or the recordings folder change.

## Relevant implementation and tests

- `domain/src/main/kotlin/com/framecraft/editor/domain/` — `Track.volumePercent`, `Clip.voiceover` and `autoDuckPercent`, `command/SetTrackVolumes.kt`, `SetClipAudio`, and `edit/AudioPlacement`.
- `data/project/src/main/kotlin/com/framecraft/editor/data/project/` — `Migration8To9`, `RecordingSweep`, and `ProjectDirectory.recordingsDir`.
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/` — `plan/AudioMix.kt`, `plan/PlaybackPlan.kt`, `GainEnvelope`, and `AudioItemEffects`.
- `app/src/main/kotlin/com/framecraft/editor/` — `media/VoiceoverCapture`, `media/MediaRecorderVoiceoverCapture`, and in `editor/`: `VoiceoverRecording`, `VoiceoverBar`, `MixerSheet`, `DuckingSheet`, and the tools in `EditorToolsZone` and `EditorTools`.
- Tests:
    - `domain/src/test/kotlin/com/framecraft/editor/domain/command/MixerCommandsTest.kt`
    - `data/project/src/test/kotlin/com/framecraft/editor/data/project/ProjectStoreTest.kt` and `RecordingSweepTest.kt`
    - `engine/media3/src/test/kotlin/com/framecraft/editor/engine/plan/AudioMixTest.kt` and `AudioGainTest`
    - `app/src/test/kotlin/com/framecraft/editor/editor/VoiceoverRecordingTest.kt`, `MixerSheetTest.kt`, and `EditorScreenTest.kt`

## Acceptance or verification criteria

- [x] Mixer fields validate, lane volumes and ducking store and undo exactly, and lane volume survives removing a lane's last clip (`MixerCommandsTest`).
- [x] Takes land as voiceovers and recording waits for a free lane (`MixerCommandsTest`).
- [x] Mixer commands round-trip through JSON, and a schema 8 project opens and saves as schema 9 (`MixerCommandsTest`, `ProjectStoreTest`).
- [x] Unused takes are deleted on open (`RecordingSweepTest`).
- [x] Lane volume scales clips, and voiceovers give ducking clips merged windows in clip time (`AudioMixTest`); gain dips and recovers around a window (`AudioGainTest`).
- [x] The recorder counts down, keeps, discards, and cuts short takes as specified (`VoiceoverRecordingTest`).
- [x] The mixer, ducking sheet, and pill send the expected actions (`MixerSheetTest`), and the tools show only where they apply (`EditorScreenTest`).
- [ ] Recording, the permission flow, and ducking sound right on a device.

## Remaining gaps and unknowns

- There is no review playback or retake of a take before it is kept, and no punch-in over an existing take.
- No noise reduction, voice effects, or input gain control.
- Ducking follows voiceovers only, not music beats or other speech; the ramp length is fixed.
- The mixer has no per-lane solo, pan, or meters.
- Bluetooth microphones, audio focus loss while recording, and echo from the speaker are unverified on hardware.
