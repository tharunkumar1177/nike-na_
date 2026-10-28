# Editor, timeline (slice 3)

The timeline fills the middle zone of the editor. The playhead stays fixed at the horizontal center; the content moves under it. Read-only in this slice; editing arrives in slice 5.

```text
+---------------------------------------------+
| 0:00    |    0:01    |    0:02  ▼  |  0:03  |   32dp ruler; labelled ticks at whole seconds
|                                  |           |   playhead knob sits on the ruler
| [thumb][thumb][thumb]  2.5s][thumb][thumb]   |   64dp clip row; thumbnails tile at the
|                                  |           |   clip's aspect ratio; duration bottom-right
|                                  |           |
|                                  |   [⤢]    |   48dp zoom-to-fit, bottom end
+---------------------------------------------+
                                   ^ fixed 2dp playhead, primary color
```

Gestures:

- Touch down pauses playback and enters scrub mode.
- One-finger drag moves time under the playhead; seeks snap to frame boundaries.
- Two-finger pinch zooms around the playhead (4 to 240 dp per second; default 48).
- Zoom to fit sets the zoom so the whole project fills 90% of the width.

States:

- Thumbnail loading: the clip shows solid surfaceVariant.
- Thumbnail unreadable: a broken-image icon centered on the first failed tile.
- Gaps on non-magnetic tracks show as empty surface.

Accessibility: the timeline is exposed as a progress control ("Timeline. Playhead at 5 seconds. Track 1 has 3 clips. Swipe up or down to seek.") and supports set-progress seeking; zoom to fit is labelled "Zoom to fit timeline".
