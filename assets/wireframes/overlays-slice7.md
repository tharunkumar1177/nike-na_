# Editor, overlays (slice 7)

Slice 7 adds picture-in-picture overlays: a photo or video placed over the base video, attached to the base clip under it so it moves with that clip when the base track changes.

## Adding an overlay

```text
+---------------------------------------------+
|  [▣ Overlay]                                |   64dp tool, shown with no selection or a base clip
+---------------------------------------------+   selected; disabled (alpha 0.38) without base clips
                                                  or while a picked file is being read
```

- Overlay opens the system photo picker for one photo or video.
- The overlay starts at the playhead, attached to the base clip there (or the nearest one), centered at scale 0.4 with full opacity, and is selected.
- It lasts as long as the video, or three seconds for a photo, cut short at the end of its base clip or the next overlay.

## Timeline

```text
| 0:00    |    0:01    |    0:02  ▼  |  0:03  |
|            [🔗  logo   2.0s]                |   overlay lane on top, primaryContainer fill,
|  [A][A][A][A][A][A]│[B][B][B][B][B]          |   16dp link badge bottom-left at a 4dp inset
|                         [⛓̸  free  1.0s]      |   link-off badge in the error color when the
                                                   overlay is not attached to any base clip
```

- The overlay lane is drawn above the base track. It uses the same 64dp row height as the base track instead of the 48dp in the design plan, because the timeline rows share one height.
- A tap selects an overlay. Moving a finger past the touch slop selects it and drags it in time; it snaps by its start or end to the playhead, base clip edges, and other overlays' edges within 12dp, with a tick haptic.
- When the finger lifts, the overlay attaches to the base clip under its new start, or becomes free over a gap. One move is saved. An overlap with another overlay is refused and a snackbar explains it.
- Trim handles work on overlays as on base clips. Trimming, moving, splitting, merging, or deleting a base clip carries its overlays along.

## Preview handles

```text
+---------------------------------------------+
|                                             |
|                  o                          |   rotation handle 24dp above the top edge
|           o------|------o                   |   2dp primary outline, 6dp corner dots
|           |   overlay   |                   |
|           o-------------o                   |
|                                             |
+---------------------------------------------+
```

- Shown while an overlay is selected and the playhead is inside it.
- Anywhere on the preview: one finger moves, a pinch resizes, and a two-finger twist rotates. One placement change is saved when the last finger lifts.
- The center stays on the canvas, the scale stays at least 0.1, and rotation snaps to 0, 90, 180, and 270 degrees within 5 degrees with a tick haptic.
- The corner dots and the rotation handle show the box; they are not separate drag targets in this slice.

## Tools with an overlay selected

```text
+---------------------------------------------+
|  Opacity 80%                                |   slider takes the remaining width; the value is
|  ───────────●───   [✂ Split]  [🗑 Delete]    |   saved once, when the drag ends
+---------------------------------------------+
```

- The preview shows the new opacity once it is saved, not while the slider moves.
- Delete removes the overlay; overlays attached to it become free.

Accessibility: the tool is labelled "Add overlay at playhead" and the slider "Overlay opacity". The preview handles read "Selected overlay. Drag to move, pinch to resize, twist to rotate." and offer actions to move in four directions by 5% of the canvas, resize by 0.1, and rotate by 15 degrees either way. With an overlay selected, the timeline offers "Move one frame earlier" and "Move one frame later".
