# Editor, preview and tools (slices 2 to 4)

Opened after picking media or opening a saved project. Every edit is saved as it happens, so leaving never asks for confirmation.

```text
+-------------------------------+
| [<-]  Summer Vacation [↶] [↷] |   TopAppBar; back saves and returns home;
|                               |   undo and redo disabled when unavailable
+-------------------------------+
|                               |
|       [ Video Preview ]       |   ~45% height, black, letterboxed to clip aspect
|                               |
+-------------------------------+
| [Play]            0:05 / 1:23 |   48dp; M:SS, or H:MM:SS when duration >= 1h
+-------------------------------+
|                               |
|        (timeline zone)        |   ~40% height; tap a clip to select it
|                               |   (2dp primary outline), tap empty space to clear
+-------------------------------+
|  [ic_delete]                  |   ~15% height, surfaceVariant; 64dp tool,
|   Delete                      |   shown only while a clip is selected
+-------------------------------+
```

States and copy:

- Picker cancelled: stay on home.
- Loading: centered progress, "Preparing media…".
- Some files unreadable: snackbar "Some files couldn't be read and were skipped."
- All files unreadable: dialog "Couldn't load media" with "Go back".
- Playback error: snackbar "Playback failed. Try restarting the app."
- Back: returns home; nothing to confirm.
- Crash recovery: snackbar "Recovered project. 2 unsaved edits couldn't be restored."
- Save error: snackbar "Couldn't save changes."
- Newer version: dialog "App update required", "This project was created in a newer version of Framecraft. Please update the app to open it.", "Go back".
- Corrupt project: dialog "Couldn't open project", "This project file is corrupt and can't be opened.", "Go back".
- Storage unavailable when creating: dialog "Couldn't save project", "Storage is full or unavailable. Free up some space and try again.", "Go back".

Accessibility: play/pause labelled "Play video" / "Pause video"; back "Navigate up"; undo "Undo"; redo "Redo"; the delete tool "Delete clip"; the timecode is read as, for example, "5 seconds of 1 minute 23 seconds".
