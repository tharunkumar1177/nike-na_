# Roadmap

Ordered vertical slices. Each slice is user-visible, shipped on its own branch, and verified green in GitHub Actions before the next starts. A fresh session resumes from the first unchecked slice.

**Phase 0 status:** approved. App name Framecraft (`com.framecraft.editor`); fully free.

## Foundation

- [x] 1. **Walking skeleton** — Gradle modules `:app` and `:domain`, version catalog, CI workflow (ktlint, detekt, Android Lint, unit tests, assemble), app opens to an empty home screen. UI: yes.
- [x] 2. **Import and play** — pick videos and photos (partial media access handled), build a project, preview it. UI: yes.
- [x] 3. **See the timeline** — pinch-zoom timeline showing tracks, clips, and gaps with a playhead synced to preview. UI: yes.
- [x] 4. **Never lose work** — project list, autosave via snapshot plus journal, crash recovery, persistent undo/redo buttons. UI: yes.
- [x] 5. **Basic edits** — trim handles, split at playhead, delete, reorder, frame-step nudge, snap toggle with haptics. UI: yes.
- [x] 6. **Export** — resolution and frame-rate settings, foreground-service export, progress, success and failure states, share. UI: yes.

## Core editing

- [x] 7. **Overlays that stay in sync** — picture-in-picture clips anchored to base clips; trimming the base ripples anchored children. UI: yes.
- [x] 8. **Audio basics** — extract audio, import music, per-clip volume and fades. UI: yes.
- [x] 9. **Text** — text clips with styles and in/out animations. UI: yes.
- [x] 10. **Canvas** — project aspect ratio, clip fit/fill, position, scale, rotation. UI: yes.
- [x] 11. **Speed** — constant speed and speed curves. UI: yes.
- [x] 12. **Transitions** — handle-consuming crossfades and a small transition set. UI: yes.
- [ ] 13. **Look** — filters and color adjustments. UI: yes.

## Differentiators

- [ ] 14. **Voiceover and mixer** — record voiceover, controllable ducking. UI: yes.
- [ ] 15. **Masks and chroma key**. UI: yes.
- [ ] 16. **On-device auto-captions** — word-timed captions anchored to source clips so they survive trims. UI: yes.

## Open decisions

None.
