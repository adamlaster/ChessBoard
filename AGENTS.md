# Chess 4 Board — Workspace Rules & Conventions

## 1. Canonical Root Files (No Duplicates)
The following project-wide files must exist **exclusively** in the repository root (`/Users/adamlaster/Development/BoardGames/Chess4Board/`):
- [`README.md`](./README.md) — Project overview, quick-start guide, controls reference, and app catalog.
- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — System architecture, component specifications, view hierarchy, and Mermaid flowcharts.
- [`BUILD_SEQUENCE.md`](./BUILD_SEQUENCE.md) — Detailed 4-step Gradle AAR -> Godot CLI export -> `board-connect` deployment pipeline.
- [`PROJECT_HISTORY.md`](./PROJECT_HISTORY.md) — Complete chronological engineering log, architectural decisions, and feature roadmap.
- [`build_and_deploy.sh`](./build_and_deploy.sh) — Single automated CLI build and deployment script.
- [`.gitignore`](./.gitignore) — Single root git ignore configuration for macOS, Godot 4+, Gradle/Android, and IDEs.

**Do NOT** create or sync duplicate copies of these files inside `chess-4-board/`, `godot-webview/`, or `~/Documents/AI_Markdown/`.

## 2. Keep Root Documentation Up to Date
Whenever you add or modify features, web apps/categories, menu controls, or build/deployment behavior:
1. Update [`PROJECT_HISTORY.md`](./PROJECT_HISTORY.md) with the new phase/changes.
2. Update [`README.md`](./README.md) and [`BUILD_SEQUENCE.md`](./BUILD_SEQUENCE.md) if user-facing features, app lists, controls, or build steps changed.
3. Update [`ARCHITECTURE.md`](./ARCHITECTURE.md) if components, view hierarchies, or system interactions changed.

## 3. Build & Output Hygiene
- **Out-of-Tree APK Output:** Always export `Chess4Board.apk` to `/Users/adamlaster/Development/BoardGames/output/Chess4Board.apk` (never inside the git repository).
- **Clean Godot Resource Tree:** Keep `chess-4-board/` free of unused test scripts, duplicate icons, or unused `.tflite` models (`Chess 4 Board` uses native touch passthrough via `GodotWebView.prepareBoardTouchPassthrough()`).
