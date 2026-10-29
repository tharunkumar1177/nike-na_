# Editor, audio (slice 8)

Slice 8 adds sound editing: music on audio lanes, extracting a video's sound onto its own lane, and per-clip volume and fades.

## Adding music

```text
+---------------------------------------------+
|  [▣ Overlay]  [♪ Audio]                     |   64dp tools, shown with no selection; Audio is
+---------------------------------------------+   disabled (alpha 0.38) while a picked file is read
```

- Audio opens the system file picker for one audio file.
- The music starts at the playhead, snapped to a frame, and runs for the whole file. It goes on the first audio lane that is free for that span, or on a new lane below the others, and is selected.
- A project has at most 8 audio lanes. When all of them are busy for that span, nothing is added and the rejection snackbar explains it.

## Timeline

```text
| 0:00    |    0:01    |    0:02  ▼  |  0:03  |
|  [A][A][A][A][A][A]│[B][B][B][B][B]          |   base track
|  [🔗 ▂▅▇▅▃▂▅▇█▆▃▂  6.0s]                     |   extracted sound: link badge, attached to A
|              [◤▃▅▇▆▅▃▂▃▅▆▇▅▃◥  4.0s]          |   music lane: no badge; fade ramps shaded
```

- Audio lanes are drawn below the picture tracks at the shared 64dp row height, in tertiaryContainer with the waveform in onTertiaryContainer.
- The waveform is one 2dp bar per 2dp gap, as tall as the sound's loudness there, at half strength, or full strength when the clip is selected. While the sound is being read, or if it cannot be read, a flat line is shown instead.
- Fades are shaded triangles in black at 40% over the start and end of the clip: the clear part traces the volume rising and falling.
- A tap selects an audio clip and trim handles work on it as on other clips. Audio clips are not dragged in time in this slice.

## Tools with a video selected

```text
+---------------------------------------------+
|  [▣ Overlay] [⇩ Extract] [🔊 Volume] [◢ Fade] [✂ Split] [🗑 Delete] →   row scrolls sideways
+---------------------------------------------+
```

- Extract is shown for a selected video that has sound. It is disabled once that clip's sound is already extracted or no lane is free.
- Extract mutes the video and puts a copy of its sound, with the same source range, volume, and fades, on an audio lane attached to the video at offset zero. Undo brings back the video's sound and removes the copy. Deleting the copy alone keeps the video muted; Volume brings it back.
- Volume and Fade are shown for any selected clip that plays sound: a video with sound or an audio clip. Photos get neither.
- With an audio clip selected, the row shows Volume, Fade, Split, and Delete; Overlay and Extract are hidden.

## Volume panel

```text
+---------------------------------------------+
|  [←]  Volume 80%                     [🔊]    |   replaces the tool row; Back returns to it
|       ──────────●─────────                  |   0 to 200%; saved once, when the drag ends
+---------------------------------------------+
```

- The speaker button mutes; pressed again, it returns to the last volume above zero (100% if there was none).
- The panel closes when the selection changes.

## Fade sheet

```text
+---------------------------------------------+
|  Fades                                      |
|  Fade in: 0.5 s                             |
|  ───●──────────────                         |   each fade goes up to 10 s or what the other fade
|  Fade out: 1.0 s                            |   leaves of the clip; a slider with no room is
|  ──────●───────────                         |   disabled
+---------------------------------------------+
```

- Each slider saves once, when its drag ends. The sheet closes on a swipe down, a tap outside, or a new selection.
- Trimming a clip shorter than its fades shortens the fades to fit; splitting keeps the fade-in on the left part and the fade-out on the right.

Accessibility: the tools are labelled "Add audio at playhead", "Extract audio", "Adjust volume", and "Adjust fades". The panel offers "Clip volume" for the slider, "Mute audio" or "Unmute audio" for the speaker, and "Back to tools". The sheet's sliders are labelled "Fade in" and "Fade out".
