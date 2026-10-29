# Export sheet (slice 6)

The export icon at the end of the editor top bar opens a Material 3 modal bottom sheet. Editing can continue while a video exports. Closing the sheet mid-export leaves the export running: the top-bar icon becomes a 24dp progress ring, and tapping it reopens the sheet. The sheet opens by itself when an export finishes or fails.

## Settings (idle)

```text
+---------------------------------------------------+
|                      [ — ]                        |
| Export video                                  [X] |   titleLarge; X = "Close export sheet"
|                                                   |
| Resolution                                        |   labelLarge, 8dp above the row
| [   720p    |   1080p   |    4K    ]              |   segmented, single choice; only sizes
|                                                   |   up to the largest source appear
| Frame rate                                        |
| [ Match (30) |    25     |    24    ]             |   project rate plus slower standard rates
|                                                   |
| Quality                                           |
| [  Standard  |   High   ]                         |
|                                                   |
|             Estimated size: 45 MB                 |   bodyMedium, centered
| [                  Export                       ] |   full width, 48dp
+---------------------------------------------------+
```

- A project with no clips shows "Add a clip before exporting." and a disabled Export button.
- Tapping Export asks for notification permission on Android 13 and later; the export starts either way.
- The settings are remembered per project.

## Preparing and running

```text
+---------------------------------------------------+
| Exporting…                                        |   "Preparing export…" before encoding,
|                                                   |   "Saving to gallery…" at 100%
|                    .-~~~-.                        |
|                  /         \                      |   120dp ring, 8dp stroke; spins while
|                 |    45%    |                     |   preparing; percent is a polite live region
|                  \         /                      |
|                    '-___-'                       |
|                                                   |
| [                  Cancel                       ] |   outlined, full width, 48dp
+---------------------------------------------------+
```

## Succeeded

```text
+---------------------------------------------------+
| Export complete                               [X] |
| (✓) Saved to Movies/Framecraft.                   |   check in primary
| [   Share   ] [   Play    ]  Done                 |   filled, tonal, text buttons
+---------------------------------------------------+
```

## Failed or cancelled

```text
+---------------------------------------------------+
| Export failed                                 [X] |   or "Export cancelled"
| (!) Not enough storage space. Free up some space  |   error color; one message per reason,
|     and try again.                                |   "Nothing was saved." when cancelled
|                                           Close   |
+---------------------------------------------------+
```

## Notification

- Ongoing while exporting: "Exporting <project>", a progress bar, and a Cancel action; tapping opens the app.
- On success: "Video saved", "<project> is in Movies/Framecraft. Tap to play."; tapping plays the video.
- On failure: "Export failed" with the project name; tapping opens the app, where the sheet explains why.
- Nothing is posted when notifications are not allowed; the sheet and the top-bar ring still show progress.
