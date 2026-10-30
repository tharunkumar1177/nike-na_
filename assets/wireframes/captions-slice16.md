# Auto-captions wireframes (slice 16)

## Tool row, nothing or a video clip selected

```
+------------------------------------------------------------------+
| [Overlay] [Text] [Captions] [Audio] [Voiceover] [Mixer] [Canvas] |
+------------------------------------------------------------------+
```

Captions is dimmed while a pick is importing, and when no clip has sound and no captions exist.

## Caption sheet, settings

```
+--------------------------------------+
| Auto captions                        |
| Caption                              |
| (o) All clips with sound             |
| ( ) Voiceovers only                  |
| ( ) Selected clip                    |
| Include muted clips            [off] |
| Language: English. Speech is         |
| recognized on this phone and never   |
| uploaded.                            |
| Captions need a one-time 41 MB       |
| speech download.                     |
|                  [Generate captions] |
+--------------------------------------+
```

- Selected clip shows only when a clip is selected.
- The download note shows only while the speech model is missing.
- When captions exist the button reads Regenerate captions, a note says regenerating replaces every caption in the project, and Remove all captions sits below it.

## Caption sheet, working

```
Downloading                           Generating
+----------------------------------+  +----------------------------------+
| Downloading speech model... 42%  |  | Making captions... 63%           |
| [==========-----------]          |  | [==============-------]          |
| Captions are added when this     |  | Captions are added when this     |
| finishes.                        |  | finishes.                        |
|                         [Cancel] |  |                         [Cancel] |
+----------------------------------+  +----------------------------------+
```

Progress is a live region. Back or Cancel stops the run and adds nothing.

## Caption sheet, result

```
Done                                  Failed
+----------------------------------+  +----------------------------------+
| 12 captions added                |  | Couldn't download the speech     |
|                                  |  | model. Check your connection and |
|                           [Done] |  | try again.                       |
+----------------------------------+  |                      [Try again] |
                                      +----------------------------------+
```

- No speech found reads "No speech was found in the selected clips." and changes nothing.
- Failures name no space, network, a damaged download, or speech recognition being unavailable.

## Preview and timeline

```
+------------------------------+
|          (preview)           |
|                              |
|    So we [went] down to      |
|        the river             |
+------------------------------+
| T  [So we went..][the river]  |
| V  [========= clip =========] |
+------------------------------+
```

- The word being spoken is drawn in the highlight color (shown here in brackets).
- Caption lanes sit at the top, one per source track. Tapping a caption selects it for restyling, placement, or delete, but it offers no drag, trim handles, nudges, or split.
