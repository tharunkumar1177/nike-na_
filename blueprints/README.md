# Blueprint Manifest

## Loading protocol

1. Match the task by intent and synonyms in the router below.
2. Load one primary blueprint and only its linked architecture headings.
3. Inspect the listed implementation and tests.
4. Load an impact-check blueprint only if its stated trigger applies.
5. For a shared-contract or ambiguous change, load the complete [architecture](../architecture.md) and every entry below that links the affected heading.
6. Do not load unrelated blueprints.

## Router

| Concepts and synonyms | Responsibility type | Primary blueprint | Required architecture contracts | Required blueprints | Impact checks | Principal implementation and tests |
|---|---|---|---|---|---|---|
| build, Gradle, CI, workflow, lint, detekt, ktlint, versions, scaffold | Workflow | [build-and-ci](build-and-ci.md) | [Stack and versions](../architecture.md#stack-and-versions), [Module structure](../architecture.md#module-structure-and-dependency-direction), [Verification boundaries](../architecture.md#verification-boundaries) | None | None | `.github/workflows/`, `gradle/`, root build files |
| track, clip, gap, magnetic, anchor, ripple, time math, microseconds | Domain | [timeline-model](timeline-model.md) | [Time representation](../architecture.md#time-representation), [Identity and ownership](../architecture.md#identity-and-ownership), [Project document model](../architecture.md#project-document-model), [Anchoring and ripple](../architecture.md#anchoring-and-ripple) | None | [edit-history](edit-history.md) when invariants change; [project-persistence](project-persistence.md) when serialized fields change | `domain/` model |
| undo, redo, command, history, edit operation, rename, delete clip, trim, split, merge, move, reorder, snap | Domain | [edit-history](edit-history.md) | [Edit commands and history](../architecture.md#edit-commands-and-history), [Project persistence and recovery](../architecture.md#project-persistence-and-recovery) | [timeline-model](timeline-model.md) | [project-persistence](project-persistence.md) when command types change | `domain/src/*/kotlin/com/framecraft/editor/domain/command/`, `domain/src/*/kotlin/com/framecraft/editor/domain/edit/` |
| save, autosave, draft, journal, compaction, recovery, migration, schema version, project list storage | Component | [project-persistence](project-persistence.md) | [Project persistence and recovery](../architecture.md#project-persistence-and-recovery), [Project document model](../architecture.md#project-document-model), [Error and recovery](../architecture.md#error-and-recovery), [Threading](../architecture.md#threading) | [timeline-model](timeline-model.md), [edit-history](edit-history.md) | None | `data/project/` |
| preview, playback, export, render, Media3, composition, transition, codec | Integration | [media-engine](media-engine.md) | [Engine translation](../architecture.md#engine-translation), [Time representation](../architecture.md#time-representation), [Threading](../architecture.md#threading), [Error and recovery](../architecture.md#error-and-recovery) | [timeline-model](timeline-model.md) | None | `engine/media3/` |
| editor screen, home screen, project list screen, timeline view, zoom, scrub, selection, gestures, toolbar, import screen | Component | [editor-ui](editor-ui.md) | [Module structure](../architecture.md#module-structure-and-dependency-direction), [Edit commands and history](../architecture.md#edit-commands-and-history), [Threading](../architecture.md#threading) | [timeline-model](timeline-model.md) | [media-engine](media-engine.md) when preview or playhead sync changes; [project-persistence](project-persistence.md) when open, save, or list behavior changes | `app/src/*/kotlin/com/framecraft/editor/` (`home`, `editor`, `timeline`, `media`) |

Responsibilities for later slices (audio, text, effects, captions) get a blueprint when their slice starts; see [ROADMAP](../ROADMAP.md).

## Status vocabulary

- `Implemented`: verified in current code and tests.
- `Partial`: some acceptance criteria are verified; gaps are named.
- `Planned`: intended behavior is not yet verified in code.
- `Unknown`: evidence is insufficient or conflicting.
- `Deprecated`: retained only for compatibility or migration.
- `Superseded`: replaced by a linked blueprint.
