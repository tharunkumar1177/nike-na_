# Wireframe

Generate a UI wireframe image for the requested screen.

## Rules
- Read `assets/wireframes/style.md` first. Follow its style, colors, and composition exactly. If it is missing, stop and ask.
- Match the design system.
- Borderless: the UI fills the canvas edge to edge.
- Canvas size: Mobile 390x844 (portrait, logical points).

## Output
- Save to `assets/wireframes/`.
- Name files `<screen>-<state>.svg` in kebab-case (e.g. `01-onboarding-preferences.svg`, `homescreen.svg`).


The SVGs show layout only; style.md says so. A note like audio-slice8.md also contains:

Behavior: where added music lands, the 8-lane cap, what Undo does after Extract, how fades shrink when a clip is trimmed, when a panel closes.
Enable rules: when Extract is disabled, and when Volume and Fade appear (not for photos).
Exact copy and accessibility labels, such as "Add audio at playhead" and "Mute audio" / "Unmute audio". The SVGs reuse this copy word for word, but they don't hold the labels or the rules behind them.


