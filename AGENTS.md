# ChessBoard — Workspace Rules & Conventions

## 1. Canonical Root Files (No Duplicates)
The following project-wide files must exist **exclusively** in the repository root (`/Users/adamlaster/Development/BoardGames/Chess4Board/`):
- [`README.md`](./README.md) — Project overview, quick-start guide, controls reference, and app catalog.
- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — System architecture, component specifications, view hierarchy, and Mermaid flowcharts.
- [`BUILD_SEQUENCE.md`](./BUILD_SEQUENCE.md) — Detailed 4-step Gradle AAR -> Godot CLI export -> `board-connect` deployment pipeline.
- [`PROJECT_HISTORY.md`](./PROJECT_HISTORY.md) — Complete chronological engineering log, architectural decisions, and feature roadmap.
- [`build_and_deploy.sh`](./build_and_deploy.sh) — Single automated CLI build and deployment script.
- [`.env.example`](./.env.example) — Template configuration file for local developer environments.
- [`.gitignore`](./.gitignore) — Single root git ignore configuration for macOS, Godot 4+, Gradle/Android, and IDEs.

**Do NOT** create or sync duplicate copies of these files inside `chess-4-board/`, `godot-webview/`, or `~/Documents/AI_Markdown/`.

## 2. Keep Root Documentation Up to Date
Whenever you add or modify features, web apps/categories, menu controls, or build/deployment behavior:
1. Update [`PROJECT_HISTORY.md`](./PROJECT_HISTORY.md) with the new phase/changes.
2. Update [`README.md`](./README.md) and [`BUILD_SEQUENCE.md`](./BUILD_SEQUENCE.md) if user-facing features, app lists, controls, or build steps changed.
3. Update [`ARCHITECTURE.md`](./ARCHITECTURE.md) if components, view hierarchies, or system interactions changed.

## 3. Build & Output Hygiene
- **Out-of-Tree APK Output:** Always export `ChessBoard.apk` to `/Users/adamlaster/Development/BoardGames/output/ChessBoard.apk` (never inside the git repository).
- **Clean Godot Resource Tree:** Keep `chess-4-board/` free of unused test scripts, duplicate icons, or unused `.tflite` models (`ChessBoard` uses native touch passthrough via `GodotWebView.prepareBoardTouchPassthrough()`).

## 4. Git Commit & Push Confirmation Rule
- **Explicit Confirmation Required:** NEVER run `git commit` or `git push` autonomously.
- Always summarize the modified/staged files, ensure the user has verified and tested the changes on device, and wait for the user's explicit confirmation before committing and pushing.

## 5. Sandboxed Experiments & Exploratory Prototyping ("Sandbox Rule")
Whenever trying something new, prototyping exploratory UX/architectural changes, or testing alternative workflows (e.g., migrating UI elements to system menus, restructuring layouts, swapping core systems):
- **Isolate in a Dedicated Sandbox Branch:** NEVER experiment directly on `main` or the current stable working tree. Ensure the current baseline is clean, then create and switch to a dedicated branch (e.g., `git checkout -b experiment/<feature-name>`).
- **Deploy & Test from Sandbox:** Run `./build_and_deploy.sh` directly from the sandbox branch so the user can test the prototype on the physical Board hardware.
- **Frictionless Rollback ("Throw Away"):** If the user dislikes the experiment or asks to revert ("let's go back", "revert back", "I don't like that"):
  1. Switch back to the stable baseline branch: `git checkout main`.
  2. Delete or archive the experiment branch: `git branch -D experiment/<feature-name>`.
  3. Rebuild and deploy: `./build_and_deploy.sh -d <device_ip>`.
  This guarantees an instant return to the verified baseline in seconds with zero manual code surgery, no residual bugs, and no risk of regressions.
- **Promote upon Explicit Approval:** Only when the user explicitly approves the prototype and wants to adopt it:
  1. Merge or fast-forward the sandbox branch into `main`.
  2. Update canonical documentation (`PROJECT_HISTORY.md`, `README.md`, `ARCHITECTURE.md`).
  3. Wait for explicit user confirmation before committing and pushing per Rule 4.

