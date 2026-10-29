# Editor, look (slice 13)

Slice 13 gives a picture clip on the base track or an overlay lane a look: one of 18 filters at an intensity, and six color adjustments. The Look sheet saves every finished change at once as one undo step.

## Tool row

```text
 [Fit] [Adjust] [Overlay] [Canvas] [Speed] [Look] [Extract] [Volume] [Fade] [Split] [Delete]  →
                                           ^
                                           videos and photos on the base track or an overlay lane
```

- Look sits after Speed. It shows for any clip on the base track or an overlay lane, video or photo; text and audio selections have no Look tool.

## Filters tab

```text
+---------------------------------------------+
|  Look                                       |
|  [ Filters ]   Adjust                       |
|  Intensity 60                               |   only while a filter is chosen
|  ○━━━━━━━━━━━━━━━━●──────────────────────── |   0 to 100
|  [All] [Portrait] [Scenery] [Food] [Vintage] [Mono]
|  [None] [Porcelain] [Peach] [Rosé] [Alpine] [Emerald] ...  →
|  Apply to all   Reset all                   |
|  Applied to 4 clips                         |   after Apply to all
+---------------------------------------------+
```

- A category chip narrows the filter chips; All shows the whole catalog in order. Chips have text labels.
- A filter chip saves that filter at full intensity and keeps the adjustments. None removes the filter.
- The intensity slider follows the thumb live and saves once when the finger lifts.

## Adjust tab

```text
+---------------------------------------------+
|  Look                                       |
|    Filters   [ Adjust ]                     |
|  Brightness 10                          ↺   |   reset shows while the value is not 0
|  ○━━━━━━━━━━━━━━━━━━━━━●─────────────────── |   -100 to 100, settles on 0 with a tick
|  Contrast 0                                 |
|  ○━━━━━━━━━━━━━━━━━━━━●──────────────────── |
|  Saturation, Exposure, Temperature, Tint    |   the same, in this order
|  Apply to all   Reset all                   |
+---------------------------------------------+
```

- Values within 3 of 0 settle on 0 with a haptic tick, so neutral is easy to find. Each slider saves once when the finger lifts.
- The reset button beside a value puts that adjustment back to 0. Reset all removes the filter and every adjustment.
- Apply to all gives every clip on the base track and overlay lanes the same look, as one undo. Both buttons wait until the look changes something.
