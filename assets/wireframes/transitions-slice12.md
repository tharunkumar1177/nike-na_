# Editor, transitions (slice 12)

Slice 12 puts a transition on a cut between two touching clips on a video track: a dissolve, a fade through black or white, a slide or push to the left, or a zoom, from 100 ms up to 4 s, made from the clips' hidden source so the timeline keeps its length.

## Cut badges

```text
 0:00        0:04        0:08        0:10        0:11
 |-----------|-----------|-----------|-----------|
 [ Clip A    ▣ Clip B    ▯ Clip C    ░ Clip D    ]      base video track
             ^           ^           ^
             transition  plain cut   no hidden source (dimmed)
```

- A 16dp badge sits centered on each cut between touching clips on a video track. Overlapping squares mean a transition plays; a bar means a plain cut that can take one; a dimmed bar means the clips have no trimmed frames to overlap.
- A 48dp touch zone around each badge opens the Transition sheet. While a clip is selected, its trim handles win over a badge under them.
- Cuts on overlay, audio, and text lanes and clips separated by a gap have no badge.
- TalkBack offers "Edit transition at 4 seconds, Dissolve" on the timeline, one action per cut.

## Transition sheet

```text
+---------------------------------------------+
|  Transition                                 |
|  [None] [Dissolve] [Fade Black] [Fade White] [Slide Left] [Push Left] [Zoom] →
|  Duration: 0.5s                             |
|  ○━━━━━━●───────────────────────────────── |   100 ms to the cut's limit, steps by frames
|  Apply to all                               |   with a haptic tick each
|  Applied to 3 cuts                          |   after Apply to all
+---------------------------------------------+
```

- A kind chip saves that kind at once, keeping the current length or 0.5s for a plain cut, fitted to the cut. None clears the transition.
- The slider follows the thumb live and saves once when the finger lifts. Apply to all gives every cut on the track that can take it the same kind and length, as one undo.
- After each change the preview moves to just before the transition so play shows it.

## Cut without hidden source

```text
+---------------------------------------------+
|  Transition                                 |
|  More media needed. Both clips need extra   |
|  trimmed frames to overlap.                 |
|  [None] [Dissolve] [Fade Black] ...  (dim)  |
+---------------------------------------------+
```

- Only None is offered, so a transition kept from an earlier edit can still be cleared.
