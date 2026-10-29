# Editor, basic edits (slice 5)

Slice 5 makes the timeline editable: trim handles on the selected clip, split at the playhead, long-press reorder on magnetic tracks, frame stepping, and snapping. The playhead stays fixed at the horizontal center.

## Playback bar and tools

```text
+---------------------------------------------+
| [<] [ ▶ ] [>]                   0:05 / 1:23 |   48dp buttons: previous frame, play/pause, next frame
+---------------------------------------------+   previous is disabled at 0, next at the end
|  timeline (below)                           |
+---------------------------------------------+
|  [✂ Split]  [🗑 Delete]                      |   64dp tool buttons, shown only with a selection
+---------------------------------------------+   Split is disabled (alpha 0.38) unless the playhead
                                                  is inside the selected clip with a frame on each side
```

## Trimming the selected clip

```text
| 0:00    |    0:01    |    0:02  ▼  |  0:03  |
|            ┃[thumb][thumb][thumb]  2.5s]┃    |   8dp x 32dp primary handles inside the selection
|            ^ 48dp touch zone per edge   |    |   outline; the duration label updates live
|                                  ┆           |   1dp primary guide while an edge is snapped
```

- Dragging a handle previews the trim in the strip and saves one trim when the finger lifts.
- On magnetic tracks the clip start stays pinned: dragging the start handle cuts the beginning of the source and the later clips ripple.
- Edges always land on frame boundaries; with snapping on they also catch the playhead and other clip edges within 12dp.
- A tick haptic plays when a new snap target engages; a long haptic plays when the clip reaches its one-frame minimum.
- The playhead does not follow the trimmed edge, because with a fixed center playhead the clip would scroll away from the finger.

## Reordering on magnetic tracks

```text
|          [thumb][thumb]    ┃ [A][A][A] ┃      |   long press lifts the clip (alpha 0.8) under the finger
|  [B][B][B]│[C][C][C][C]                     |   2dp primary line marks the slot it will land in;
```

- A long press picks the clip up with a long haptic and selects it; a tick plays each time the target slot changes.
- Lifting saves one move. Auto-scroll at the strip edges is not built yet.

## Timeline controls

```text
|                         [⊟] [⊕] [⤢]         |   48dp, bottom end: snap toggle, zoom to playhead, zoom to fit
```

- Snap starts on and is remembered for the editing session only. Zoom to playhead zooms in around the playhead and a second tap restores the previous zoom; both toggles show a primary checked state.

Accessibility: Split is labelled "Split clip at playhead", the frame buttons "Previous frame" and "Next frame", and the toggles "Snap to playhead and clip edges" and "Zoom to playhead". Trimming and reordering are touch-only in this slice.
