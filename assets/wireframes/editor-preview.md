# Editor, preview (slice 2)

Opened after picking media from the system photo picker. Clips play back to back. The lower zones are reserved for the timeline (slice 3) and tools (slice 5).

```text
+-------------------------+
| [<-]   New project      |   TopAppBar; back asks to discard
+-------------------------+
|                         |
|     [ Video Preview ]   |   ~45% height, black, letterboxed to clip aspect
|                         |
+-------------------------+
| [Play]      0:05 / 1:23 |   48dp; M:SS, or H:MM:SS when duration >= 1h
+-------------------------+
|                         |
|   (timeline zone)       |   ~40% height, surface
|                         |
+-------------------------+
|   (tools zone)          |   ~15% height, surfaceVariant
+-------------------------+
```

States and copy:

- Picker cancelled: stay on home.
- Loading: centered progress, "Preparing media…".
- Some files unreadable: snackbar "Some files couldn't be read and were skipped."
- All files unreadable: dialog "Couldn't load media" with "Go back".
- Playback error: snackbar "Playback failed. Try restarting the app."
- Back: dialog "Discard project?" (Cancel / Discard) while projects are not yet saved.

Accessibility: play/pause labelled "Play video" / "Pause video"; back "Navigate up"; the timecode is read as, for example, "5 seconds of 1 minute 23 seconds".
