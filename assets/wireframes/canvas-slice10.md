# Editor, canvas (slice 10)

Slice 10 adds a project canvas: an output shape chosen by the user, a background behind the base clips, and per-clip fit, fill, and placement for base clips.

## Canvas tool

```text
+---------------------------------------------+
|  [▣ Overlay]  [T Text]  [♪ Audio]  [⧉ Canvas]  |   shown with no selection or a base clip selected;
+---------------------------------------------+   Canvas is dimmed (alpha 0.38) when the project
                                                   is empty or a pick is still importing
```

- Canvas opens the canvas sheet. It is not offered while an overlay, text, or audio clip is selected, because the canvas belongs to the whole project.

## Canvas sheet

```text
+---------------------------------------------+
|  Canvas                                     |
|   Aspect  │  Background                     |   tabs
|  ─────────────────────────────────────────  |
|  [Original] [9:16] [16:9] [1:1] [4:5] [4:3] |   chips; the current shape is selected
+---------------------------------------------+

+---------------------------------------------+
|  Canvas                                     |
|   Aspect  │  Background                     |
|  ─────────────────────────────────────────  |
|   (●)    (◍)    (○)    (●)   (●)   (●)  (●)  |   the current background is ringed in primary
|  Black   Blur  White  Gray   Red  Blue Green |
+---------------------------------------------+
```

- A tap is saved at once as one edit, so the sheet has no Done button. Tapping the current choice saves nothing. A swipe down or a tap outside closes the sheet.
- Original follows the first base clip's displayed shape, as before slice 10. Every other shape keeps the short side of the first base clip and sets the long side from the ratio, so a 1080p clip on 9:16 gives a 1080 × 1920 canvas.
- Black is the default. Blur fills the canvas with a softened, silent copy of the base track. Each plain color is opaque.

## Tools with a base clip selected

```text
+---------------------------------------------+
|  [⛶ Fit / Fill] [✥ Adjust] [▣ Overlay] [T Text] [⧉ Canvas] [✂ Split] [🗑 Delete] →
+---------------------------------------------+
```

- Fit / Fill swaps the clip between fitting the whole picture inside the canvas (icon fit_screen) and covering the canvas (icon crop_free, tool highlighted in primaryContainer). Where the clip sits is kept.
- Adjust is a toggle. While it is on and the playhead is inside the clip, the preview shows the clip's box at its fitted or filled size. Drag moves it, pinch resizes it, and twist rotates it with snapping to quarter turns and a haptic tick. Each gesture is saved as one edit when the fingers lift. Selecting another clip turns Adjust off.
- A base clip with fit, centered, unscaled, and upright stores no placement, so it looks and saves exactly as before slice 10.

Accessibility: the tools are labelled "Canvas settings", "Toggle clip fit or fill", and "Adjust clip placement", and the two toggles report their on state. The preview box reads "Selected clip. Drag to move, pinch to resize, twist to rotate." Aspect chips read "Aspect ratio 9:16" or "Original aspect ratio", and swatches read "Black background", "Blurred background", "Red background", and so on.
