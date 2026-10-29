# Clip speed

## Outcome or responsibility

Own how fast a timed clip plays its source: one constant speed or a speed curve, whether its sound keeps its pitch, how trims, splits, and merges keep sped clips exact, how the engine plays them, and the Speed tool and sheet that edit them.

## Current verified status

**Status:** Partial

The speed model, `SpeedMap`, `SetClipSpeed`, and the speed handling in split, merge, and the trim controller are verified by `SpeedCommandsTest`; curve editing and the log scale by `SpeedCurveEditTest`. The schema 6 upgrade is verified by `ProjectStoreTest`. Agreement between the domain's durations and Media3's is verified by `SpeedProvidersTest`, and speed in the plan by `PlaybackPlannerTest`. The Speed tool is verified by `EditorScreenTest`, the sheet by `SpeedSheetTest`, and thumbnail mapping and badges by `TimelineGeometryTest`. How sped clips actually play and export needs a device and is unverified.

## Architecture dependencies

- [Clip speed and source mapping](../architecture.md#clip-speed-and-source-mapping) — `ClipSpeed`, `sourceDurationMicros`, and `SpeedMap` are the only way source and timeline time relate for a sped clip; every rule below applies it.
- [Time representation](../architecture.md#time-representation) — `durationMicros` stays the timeline length; speeds and positions are stored as integers.
- [Project document model](../architecture.md#project-document-model) — speed fields are clip fields with defaults, so older projects read as normal speed.
- [Edit commands and history](../architecture.md#edit-commands-and-history) — every speed change, trim, split, and merge of a sped clip has an exact inverse.
- [Engine translation](../architecture.md#engine-translation) — sped items carry a speed provider built from the same steps as the domain.

## Local rules and implications

### Model

- `ClipSpeed.Constant(speedMilli)` and `ClipSpeed.Curve(points)` serialize as `constant` and `curve`. `SpeedPreset` holds Montage, Hero, Bullet, Jump Cut, Flash In, and Flash Out, and `customStart` is a flat 1x curve of five points.
- `Clip.sourceSpanMicros` is `sourceDurationMicros` when sped and `durationMicros` otherwise; `Project` checks `trimStartMicros + sourceSpanMicros` against the source and the stored duration against `SpeedMap`.
- `ClipSpeed.slice` cuts a curve at two source offsets, adding a point at each cut with the speed there and moving the points between to the part.

### Commands

- `SetClipSpeed(clipId, speed, maintainPitch, fadeInMicros, fadeOutMicros)` keeps the clip's start and source, sets its duration from `SpeedMap`, ripples a magnetic track, and fits the fades. It refuses clips without a timed source (`SpeedNotSupported`) and results under one frame (`ClipTooShort`); its inverse restores speed, pitch, and fades exactly.
- `TrimClip` carries `sourceDurationMicros`, required exactly when the clip is sped. `TrimController` trims the source and gives the duration the speed plays it for; its limits come from the asset and the neighbors, converted through `SpeedMap`.
- `SplitClip` cuts the source where the speed plays the cut, or after `leftSourceMicros` when given, and slices the speed for each part. Its inverse `MergeClips` carries the original speed so undo restores the whole curve.
- `MergeClips` reads source continuity through each part's source span and joins only parts with the same constant speed and pitch setting unless it is given the merged speed.
- `ExtractAudio` copies the video clip's speed and pitch to its sound.

### Engine

- `PlanSegment.Media` carries the speed, the source span, and the pitch setting. `CompositionFactory` clips the item to the source span, sets `SpeedParameters` from `SpeedProviders`, and caps a sped-up picture at the project frame rate; sound-only items get speed without the cap. Fades are applied after speed, in timeline time.

### Editing

- The Speed tool shows for a video clip on the base or an overlay lane. The sheet follows `assets/wireframes/speed-slice11.md`: a Normal tab with a log slider from 0.1x to 100x and a Curve tab with preset chips, the curve editor, Add or Delete point at the playhead, and Reset; both show the duration and the pitch switch. Each finished gesture dispatches one `SetClipSpeed`.
- Points drag vertically only and nudge by 0.1x through accessibility actions; the editor allows up to 10 points.
- Timeline labels start with the speed, or the slowest to fastest speed of a curve, and thumbnails and waveforms map through `SpeedMap`.

## Related blueprints

### Required

- [Timeline model](timeline-model.md) — speed fields are clip fields under its validation.
- [Edit history](edit-history.md) — the speed command and the speed-aware trim, split, and merge follow its inverse rules.

### Impact checks

- [Media engine](media-engine.md) — when segment fields or how items are built change.
- [Editor UI](editor-ui.md) — when the tool row, sheet, or timeline drawing change.
- [Project persistence](project-persistence.md) — when speed fields or command types change.
- [Audio](audio.md) — when extracted sound or the per-item sound chain changes.

## Relevant implementation and tests

- `domain/src/main/kotlin/com/framecraft/editor/domain/model/` — `ClipSpeed`, `SpeedMap`, `SpeedPreset`, and the speed rules in `Clip` and `Project`.
- `domain/src/main/kotlin/com/framecraft/editor/domain/command/` — `SetClipSpeed` and the speed handling in `TrimClip`, `SplitClip`, and `MergeClips`.
- `domain/src/main/kotlin/com/framecraft/editor/domain/edit/` — `TrimController`, `SpeedCurveEdit`, and `SpeedScale`.
- `data/project/src/main/kotlin/com/framecraft/editor/data/project/Migration5To6.kt` — the schema 6 upgrade.
- `engine/media3/src/main/kotlin/com/framecraft/editor/engine/` — `SpeedProviders`, `CompositionFactory`, and `plan/PlaybackPlan.kt`.
- `app/src/main/kotlin/com/framecraft/editor/editor/` — `SpeedEditing`, `SpeedSheet`, `SpeedCurveEditor`, and the speed branches of `EditorTools` and `EditorToolsZone`.
- `app/src/main/kotlin/com/framecraft/editor/timeline/` — `SpeedLabel`, the speed mapping in `TimelineGeometry`, `TimelineThumbnails`, and `TimelineAudioDrawing`, and the badge in `TimelineDrawing`.
- Tests: `domain/src/test/kotlin/com/framecraft/editor/domain/command/SpeedCommandsTest.kt`, `domain/src/test/kotlin/com/framecraft/editor/domain/edit/SpeedCurveEditTest.kt`, `data/project/src/test/kotlin/com/framecraft/editor/data/project/ProjectStoreTest.kt`, `engine/media3/src/test/kotlin/com/framecraft/editor/engine/SpeedProvidersTest.kt`, `engine/media3/src/test/kotlin/com/framecraft/editor/engine/plan/PlaybackPlannerTest.kt`, `app/src/test/kotlin/com/framecraft/editor/editor/SpeedSheetTest.kt`, `app/src/test/kotlin/com/framecraft/editor/editor/EditorScreenTest.kt`, `app/src/test/kotlin/com/framecraft/editor/timeline/TimelineGeometryTest.kt`.

## Acceptance or verification criteria

- [x] Speeds and curves validate their ranges; constants and curves map time both ways and slice at cuts (`SpeedCommandsTest`).
- [x] A speed change ripples and undoes exactly; too short a result is refused (`SpeedCommandsTest`).
- [x] Splitting a curve and undoing restores it exactly; equal constant parts merge; trims follow the speed (`SpeedCommandsTest`).
- [x] Speed commands round-trip through JSON and a schema 5 project opens and saves as schema 6 (`SpeedCommandsTest`, `ProjectStoreTest`).
- [x] Media3's duration after speed equals the domain's for constants and every preset at 24, 30, and 60 fps (`SpeedProvidersTest`).
- [x] The tool, sheet, presets, points, and pitch switch send the expected edits (`EditorScreenTest`, `SpeedSheetTest`); curve editing rules hold (`SpeedCurveEditTest`).
- [ ] Sped clips play, keep pitch, and export at the shown duration (needs a device).

## Remaining gaps and unknowns

- Preview of sped secondary sequences under the compositor and decode load at high speeds are unverified on hardware.
- There is no effective frame rate hint, because assets do not record their source frame rate.
- Refused speeds show the generic rejection snackbar rather than a speed-specific message.
- Audio-lane clips have no Speed tool; their speed comes only from the video they were extracted from.
