# Editor, speed (slice 11)

Slice 11 lets a video clip play at one constant speed from 0.1x to 100x or follow a speed curve from 0.1x to 10x, with its sound keeping its pitch by default.

## Speed tool

```text
+------------------------------------------------------------------+
|  [⛶ Fit / Fill] [✥ Adjust] [▣ Overlay] [T Text] [⧉ Canvas]         |
|  [⏲ Speed] [♪ Extract] [🔊 Volume] [◢ Fade] [✂ Split] [🗑 Delete] →   |   Speed shows for a video clip on the
+------------------------------------------------------------------+   base or an overlay lane
```

- Speed opens the speed sheet. It is not offered for photos, text, or clips on audio lanes; extracted sound keeps the speed of the video it came from.

## Speed sheet, Normal tab

```text
+---------------------------------------------+
|  Speed                                      |
|   Normal  │  Curve                          |   tabs; a curve opens on Curve
|  ─────────────────────────────────────────  |
|  2.0x                                       |
|  ●━━━━━━━━━━━━━━━━━━━━━━○──────────────────  |   log slider: 0.1x left, 1x a third in, 100x right;
|  Duration: 5.0s → 2.5s                      |   settles on 1x with a haptic tick
|  Maintain audio pitch                 [ ●] |   on by default
+---------------------------------------------+
```

- The value above the slider follows the thumb live; the edit is saved once when the finger lifts. 1x saves normal speed.
- The duration readout compares the clip at normal speed with the clip at the shown speed.

## Speed sheet, Curve tab

```text
+---------------------------------------------+
|  Speed                                      |
|   Normal  │  Curve                          |
|  ─────────────────────────────────────────  |
|  [Custom] [Montage] [Hero] [Bullet] [Jump Cut] [Flash In] [Flash Out] →
|  +---------------------------------------+  |
|  |10x            │                       |  |   240dp, surfaceVariant, speed on a log scale
|  |        ●      │            ●          |  |   from 0.1x (bottom) to 10x (top) across the
|  | 1x ───/─\─────│───────────/─\──────── |  |   clip's source; the line is the playhead
|  |  ●──/   \─────●──────────/   \──●     |  |
|  |0.1x           │                       |  |
|  +---------------------------------------+  |
|  [Add point]  [Reset]                       |   Add point becomes Delete point near a point
|  Duration: 5.0s → 3.1s                      |
|  Maintain audio pitch                 [ ●] |
+---------------------------------------------+
```

- A preset chip saves its curve at once. Custom starts a flat 1x curve with five points, or shows the clip's own curve.
- Points drag up and down only; each drag is saved once when the finger lifts. The first and last points stay at the clip's ends.
- Add point puts a point on the line at the playhead, up to ten points. Within 2% of a point the button deletes it instead; the first and last points cannot be deleted.
- Reset sets every point to 1x.

## Timeline

```text
+---------------------------------------------+
| [thumb][thumb][thumb]        [2.0x  2.5s]   |   a sped clip's label starts with its speed;
| [thumb][thumb]          [0.4–4.0x  3.1s]    |   a curve shows its slowest to fastest speed
+---------------------------------------------+
```

- Thumbnails and waveforms show the source playing at each moment of the clip, so a fast clip shows more of its source under each tile.

Accessibility: the tool reads "Adjust clip speed", the slider "Constant speed" with its value as state, and the switch "Maintain audio pitch". The editor reads "Speed curve. Drag a point up to speed up or down to slow down." Each point reads "Curve point 2 of 5, speed 1.0x" with the actions "Nudge speed up" and "Nudge speed down", 0.1x each. A refused speed shows the existing "edit rejected" snackbar.
