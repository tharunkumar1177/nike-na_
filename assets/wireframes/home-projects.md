# Home screen, project list (slice 4)

Saved projects, most recently edited first. Tapping a row opens the project; the trailing options button offers Rename and Delete. With no projects, `home-empty.md` applies.

```text
+------------------------------------------+
| [System Status Bar]                      |
|                Framecraft                |   CenterAlignedTopAppBar, titleLarge
|                                          |
| Summer Vacation                     [⋮]  |   72dp row, clickable; bodyLarge title
| Edited 5 min ago • 1:23 • 4 clips        |   bodyMedium subtitle, onSurfaceVariant
|                                          |
| Birthday Party                      [⋮]  |
| Edited yesterday • 0:15 • 1 clip         |
|                                          |
| Unreadable project                  [⋮]  |   project that could not be read
| Couldn't read project file               |   subtitle in error color; Delete only
|                                          |
|            ( + New project )             |   56dp, extraLarge shape, centered FAB slot
| [System Navigation Bar]                  |
+------------------------------------------+
```

Relative time: under 1 minute "Edited just now"; under 1 hour "Edited {m} min ago"; under 24 hours "Edited {h} hr ago"; under 48 hours "Edited yesterday"; same year "Edited Oct 24"; otherwise "Edited Oct 24, 2025" (month-day order follows the locale).

States and copy:

- Options menu: "Rename", "Delete".
- Rename dialog: "Rename project", field "Project name", "Cancel", "Rename" (disabled while blank).
- Delete dialog: "Delete project?", "This can't be undone.", "Cancel", "Delete".

Accessibility: each row reads "{name}. {edited}. {spoken duration}. {n} clips."; the options button reads "Project options for {name}". Test tags: `home_project_row`, `home_project_overflow`, `dialog_rename_input`, `dialog_rename_confirm`, `dialog_delete_confirm`.
