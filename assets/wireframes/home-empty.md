# Home screen, empty state

Project list with no projects. Dark theme default. The primary action sits bottom-center for one-handed reach.

```text
+-------------------------+
| [System Status Bar]     |
|       Framecraft        |   CenterAlignedTopAppBar, titleLarge
|                         |
|          [movie]        |   64dp icon, primary @ 50%, decorative
|     No projects yet     |   headlineSmall, onSurface
|  Create your first      |   bodyLarge, onSurfaceVariant
|  project to start       |
|  editing.               |
|                         |
|   ( +  New project )    |   56dp tall, 28dp radius, FAB slot centered
| [System Navigation Bar] |
+-------------------------+
```

States: before media import exists, "New project" is visible but disabled, with the TalkBack label "New project, disabled. Feature arriving in next update."
