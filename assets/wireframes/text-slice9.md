# Editor, text (slice 9)

Slice 9 adds titles and captions typed by the user: text clips on text lanes, a text sheet for words, font, style, and animation, and placement on the preview.

## Adding text

```text
+---------------------------------------------+
|  [▣ Overlay]  [♪ Audio]  [T Text]            |   shown with no selection or a base clip selected;
+---------------------------------------------+   Text is dimmed (alpha 0.38) when it cannot add
```

- Text adds the word "Text" at the playhead, snapped to a frame, inside the base clip under it. It lasts 3 seconds or up to that clip's end and is attached to that clip.
- It goes on the first text lane that is free for that span, or on a new lane after the other text lanes. A project has at most 8 text lanes; with all of them busy, or with no base clip under the playhead, Text is dimmed.
- The new clip is selected and the sheet opens on the Style tab with the words selected and the keyboard up.

## Text sheet

```text
+---------------------------------------------+
|  ┌ Text ──────────────────────────────────┐ |   up to 3 lines, 500 characters; blank words show
|  │ Summer trip                             │ |   "Text cannot be empty" and disable Done
|  └─────────────────────────────────────────┘ |
|   Font  │  Style  │  Animation              |   tabs
|  ─────────────────────────────────────────  |
|  Size 100%                                  |   relative to the default size
|  ──────●───────────                         |
|  (○)(●)(●)(●)(●)(●)(●)(●)                    |   white, black, red, yellow, green, cyan, blue, pink
|  [Outline] [Shadow] [Background]            |   toggles
|  [ Left | Center | Right ]                  |   alignment
|                                    [Done]   |
+---------------------------------------------+
```

- The Font tab offers Sans, Bold, Serif, and Mono as chips.
- Every change stays in the sheet as a draft. One edit is saved when the sheet closes, by Done, a swipe down, or a tap outside; nothing is saved if nothing changed.
- Closing a newly added clip with blank words removes it. Closing an existing clip with blank words keeps its old words.

## Animation tab

```text
+---------------------------------------------+
|  [ In | Out ]                               |   which end of the clip is being set
|  [None] [Fade] [Slide] [Pop] [Typewriter]   |
|  Duration: 0.5 s                            |   0.1 s up to 3 s or the clip's length
|  ───●──────────────                         |
+---------------------------------------------+
```

- A new preset lasts 0.5 seconds or the whole clip if it is shorter. The keyboard stays down on this tab.
- Fade ramps opacity, Slide rises into place while fading, Pop grows past full size and settles, and Typewriter reveals one character at a time. The exit plays the same curve backwards.
- Trimming a clip shorter than its animations shortens them; splitting keeps the entrance on the left part and the exit on the right.

## Timeline and tools with text selected

```text
| 0:00    |    0:01    |    0:02  ▼  |  0:03  |
|      [🔗 Summer trip…        ]               |   text lane: secondaryContainer, one-line label
|      [▣ photo   ]                            |   overlay lane
|  [A][A][A][A][A][A]│[B][B][B][B][B]          |   base track

+---------------------------------------------+
|  [✎ Edit] [◎ Animate] [◐ Opacity] [✂ Split] [🗑 Delete] →
+---------------------------------------------+
```

- Text lanes are drawn above the overlay lanes at the shared row height and request no thumbnails. A tap selects a text clip; trims, time drags, and nudges work as on overlays.
- Edit opens the Style tab and Animate opens the Animation tab.
- On the preview, selected text shows the same box as an overlay, sized to the drawn words. Drag moves it, pinch resizes it, twist rotates it, and a double tap opens the Style tab.

Accessibility: the tools are labelled "Add text at playhead", "Edit text", and "Animate text". The preview box reads "Selected text. Drag to move, pinch to resize, twist to rotate, double-tap to edit." and offers "Edit text" as its first custom action. The sheet's field is labelled "Text" and its sliders "Size" and "Duration".
