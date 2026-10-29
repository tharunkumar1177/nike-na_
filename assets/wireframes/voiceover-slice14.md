# Voiceover and mixer wireframes (slice 14)

## Tool row, nothing selected

```
+------------------------------------------------------------------+
| [Overlay] [Text] [Audio] [Voiceover] [Mixer] [Canvas]            |
+------------------------------------------------------------------+
```

Voiceover is dimmed when every audio lane is busy at the playhead.

## Recorder over the preview

```
Armed                               Countdown
+------------------------------+    +------------------------------+
|                              |    |                              |
|          (preview)           |    |          (preview)           |
|                              |    |                              |
| ( X  🎧 Use headphones  (●) ) |    |      (   3     Cancel   )     |
+------------------------------+    +------------------------------+

Recording                           Saving
+------------------------------+    +------------------------------+
|        (preview plays)       |    |          (preview)           |
|                              |    |                              |
| ( ● 0:07  [|||||    ]  [■] ) |    |   ( ◌  Saving take )         |
+------------------------------+    +------------------------------+
```

- The countdown is a live region that TalkBack announces assertively.
- Back closes the armed recorder, or stops the countdown or the take.
- Outcomes show as snackbars: take too short, not enough storage, microphone failed, no free lane, and recording saved after leaving the app.

## Microphone refused

```
+--------------------------------------+
|  🎤  Microphone needed               |
|  Framecraft records voiceovers with  |
|  the microphone. Allow it in         |
|  Settings to record.                 |
|                 [Not now] [Settings] |
+--------------------------------------+
```

## Mixer sheet

```
+--------------------------------------+
| Mixer                                |
| Main video 100%                      |
| [=========o----------]          [🔊] |
| Overlay 1 60%                        |
| [=====o--------------]          [🔊] |
| Audio 1 0%                           |
| [o-------------------]          [🔇] |
+--------------------------------------+
```

Only lanes that hold clips are listed. Each slider runs 0 to 200%, and mute remembers the last volume.

## Duck sheet (sound clip that is not a voiceover)

```
+--------------------------------------+
| Lower under voiceovers         [on ] |
| This clip gets quieter while a       |
| voiceover plays, then comes back.    |
| Lower by 60%                         |
| [======o------]                      |
+--------------------------------------+
```

The depth slider runs 10 to 100% in steps of 10 and is dimmed while ducking is off.
