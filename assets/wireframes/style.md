# Wireframe style

Mid-fidelity wireframes drawn in the app's own dark Material 3 theme, so a wireframe looks like the screen that ships. Each screen's behavior and copy live in its spec beside it (`assets/wireframes/*.md`); the SVG shows layout only.

## Canvas and files

- 390 × 844, portrait, 1 unit = 1 dp. Borderless: the UI runs edge to edge, with no device frame and no annotations on the canvas.
- One self-contained SVG per screen state: a header comment naming the specs it draws, an inline `<style>`, and icons in `<defs>`. No raster images, external fonts, or links.
- Name files `NN-<screen>-<state>.svg`, numbered in flow order (home, editor, export).

## Colors

Dark scheme from `ui/theme/Color.kt`; tokens it leaves unset use the Material 3 baseline.

| Token | Hex | Used for |
|---|---|---|
| background / surface | `#111318` | Screen, timeline |
| onSurface | `#E2E2E9` | Primary text, icons |
| onSurfaceVariant | `#C4C6D0` | Secondary text, labels |
| surfaceVariant | `#44474E` | Tool row, inactive slider track |
| surfaceContainerLow | `#1D1B20` | Bottom sheets, docked panels |
| surfaceContainerHigh | `#2B2930` | Dialogs, recorder pill |
| outline | `#938F99` | Chip and field borders, ruler ticks |
| primary / onPrimary | `#AEC6FF` / `#002E69` | Playhead, selection, sliders, filled buttons |
| primaryContainer / on | `#11448F` / `#D7E2FF` | FAB, overlay clips, toggled tools |
| secondaryContainer / on | `#4A4458` / `#E8DEF8` | Text and caption clips, selected chips, tonal buttons |
| tertiaryContainer / on | `#633B48` / `#FFD8E4` | Audio clips and waveforms |
| error | `#F2B8B5` | Errors, recording dot |
| preview | `#000000` | Preview letterbox |
| media placeholder | `#2A2D34` / `#3A3E46` | Video frames and thumbnails |

Scrim behind sheets and dialogs: black at 32%.

## Type

Roboto (fallback Segoe UI, Arial). titleLarge 22 Medium, headlineSmall 24 Bold, bodyLarge 16, bodyMedium 14, labelLarge 14 Medium, labelMedium 12 Medium, ruler labels 11.

## Layout

- 16 dp screen edge, 8 dp steps, 48 dp minimum touch target.
- Status bar 0–24, top app bar 24–88, gesture handle at 832.
- Editor: preview 88–388 (black, clip letterboxed), playback bar 388–436, timeline 436–736, tool row 736–844 on surfaceVariant with 64 dp tools.
- Timeline: 32 dp ruler, 64 dp lanes with 4 dp gaps (text, overlay, base, audio from top), a fixed 2 dp playhead at x 195, 48 dp per second, controls at the bottom end.
- Modal sheets: surfaceContainerLow, 28 dp top corners, 32 × 4 handle, over a scrim. Only the editor above the sheet is drawn.
- Docked panels replace the timeline and tool row with no scrim, so the preview stays live.
- Dialogs: 312 dp wide, centered, surfaceContainerHigh, 28 dp corners.

## Components

- Buttons 40 dp tall (48 dp when full width), fully rounded: filled (primary), tonal (secondaryContainer), outlined (outline border), text (primary label).
- Chips 32 dp, 8 dp corners: outline border, or secondaryContainer fill when selected.
- Tabs 48 dp with a 3 dp primary indicator; segmented buttons 40 dp.
- Sliders: 4 dp track, 20 dp thumb, primary active part. Switches 52 × 32.
- Selected clip: 2 dp primary outline with 8 × 32 primary trim handles. Disabled: 38% opacity.
- Icons: 24 dp simplified Material glyphs in `currentColor`.

## Content

- Sample project "Summer Vacation", playhead at 0:05 of 1:23. Copy comes from the screen specs, word for word.
- Media is a flat placeholder (panel, hills, sun); thumbnails tile it at the clip's aspect.
- Not drawn: the system keyboard, and snackbars unless the state is about one.
