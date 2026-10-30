# Editor, masks and chroma key (slice 15)

Slice 15 lets a picture clip on the base track or an overlay lane carry a mask, one of six shapes placed on the preview, and a chroma key that removes one color. Both panels dock under the preview so it stays touchable, and every finished change saves at once as one undo step.

## Tool row

```text
 [Fit] [Adjust] [Overlay] [Canvas] [Speed] [Look] [Mask] [Chroma key] [Extract] [Volume] [Split] [Delete]  →
                                                  ^      ^
                                    highlighted while the clip has a mask or a key
```

- Mask and Chroma key sit after Look and show for the same clips: videos and photos on the base track or an overlay lane.
- Opening one panel closes the other. Done or back closes it.

## Mask panel

```text
+---------------------------------------------+
|  preview                                    |
|        .-----.                              |   outline of the mask while the playhead is on the clip
|       (   +   )                             |   drag to move, pinch to resize, twist to rotate
|        '-----'                              |
+---------------------------------------------+
|  Mask                                 Done  |
|  [None] [Linear] [Mirror] [Circle] [Rectangle] [Heart] [Star]  →
|  Invert                              (  o)  |
|  Feather 20                                 |
|  ○━━━━━━━━━━●────────────────────────────── |   0 to 100
|  Roundness 20                               |   rectangles only
|  ○━━━━━━━━━━●────────────────────────────── |
|  Drag the mask on the preview to move it... |   or: Move the playhead over this clip...
+---------------------------------------------+
```

- A shape chip saves that shape's starting mask, keeping the current center, turn, and inversion. None removes the mask.
- Linear shows one edge line and Mirror two; the closed shapes show their outline, clipped to the picture.
- Each gesture saves once when the fingers lift. TalkBack offers move, resize, and rotate actions on the outline.

## Chroma key panel

```text
+---------------------------------------------+
|  preview                                    |
|   - - - - - - - - - - - - -                 |   dashed outline of the clip while picking
|  |          ⊕             |                 |   reticle where the finger last tapped
|   - - - - - - - - - - - - -                 |
+---------------------------------------------+
|  Chroma key                           Done  |
|  (●) [✎ Pick color] [● Green] [● Blue]      |   swatch shows the key color
|  Tap the color to remove in the preview     |   while picking, or the failed message
|  Strength 50                                |
|  ○━━━━━━━━━━━━━━━━━━━━●──────────────────── |
|  Softness 20                                |
|  Spill removal 50                           |
|  Remove                                     |
+---------------------------------------------+
```

- Without a key the panel says to pick a color or a preset, and the sliders are hidden.
- Pick color waits for one tap on the clip's picture and saves the averaged color of the source frame under the playhead. TalkBack can pick the color at the center.
- A preset or pick changes only the color and keeps strength, softness, and spill. Each slider saves once when the finger lifts. Remove clears the key.
