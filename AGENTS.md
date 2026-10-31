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

**Do NOT** create or sync duplicate copies of these files inside `chessboard/`, `godot-webview/`, or `~/Documents/AI_Markdown/`.

## 2. Keep Documentation & Web Prototype Up to Date (Root Docs, In-App Help & Web Prototype)
Whenever you add or modify features, web apps/categories, menu controls, gestures, duos, or build/deployment behavior:
1. Update in-app documentation in [`HelpDialogHelper.java`](./godot-webview/GodotWebView/GodotWebView/src/main/java/com/lastersoft/godotwebview/HelpDialogHelper.java) ("Controls & Gestures Guide" modal) whenever user-facing controls, shortcuts, gestures, themes, or multitasking features change.
2. Update [`PROJECT_HISTORY.md`](./PROJECT_HISTORY.md) with the new phase/changes.
3. Update [`README.md`](./README.md) and [`BUILD_SEQUENCE.md`](./BUILD_SEQUENCE.md) if user-facing features, app lists, controls, or build steps changed.
4. Update [`ARCHITECTURE.md`](./ARCHITECTURE.md) if components, view hierarchies, or system interactions changed.
5. **Synchronize Interactive Web Prototype:** Update and verify the standalone web prototype in `docs/` (`index.html`, `style.css`, `app.js`, and screenshots in `docs/screenshots/`) so the browser simulator faithfully mirrors all current features, controls, theme palettes, and split-screen capabilities.

## 3. Build & Output Hygiene
- **Out-of-Tree APK Output:** Always export `ChessBoard.apk` to `/Users/adamlaster/Development/BoardGames/output/ChessBoard.apk` (never inside the git repository).
- **Clean Godot Resource Tree:** Keep `chessboard/` free of unused test scripts, duplicate icons, or unused `.tflite` models (`ChessBoard` uses native touch passthrough via `GodotWebView.prepareBoardTouchPassthrough()`).
- **Resource Hygiene for Prototype Tooling:** Any developer/diagnostic components added to support prototype creation, screenshot automation, or headless inspection (such as `RemoteNavigationServer`) MUST be strictly disabled by default during standard builds and normal runtime, consuming zero sockets, threads, or memory on the Board device. They should only be activated conditionally during automated prototype generation.

## 4. Git Commit & Push Confirmation Rule
- **Documentation & Prototype Prerequisite:** Part of merging and pushing is making sure both canonical documentation AND the interactive web prototype in `docs/` are fully up to date *before* committing or pushing. "Updating the docs" explicitly includes updating the in-app help/controls guide ([`HelpDialogHelper.java`](./godot-webview/GodotWebView/GodotWebView/src/main/java/com/lastersoft/godotwebview/HelpDialogHelper.java)) as well as root canonical documentation ([`PROJECT_HISTORY.md`](./PROJECT_HISTORY.md), [`README.md`](./README.md), [`ARCHITECTURE.md`](./ARCHITECTURE.md), and [`BUILD_SEQUENCE.md`](./BUILD_SEQUENCE.md)), and verifying that the `docs/` prototype is functional and current. All documentation and prototype assets must contemplate current changes before staging.
- **Explicit Confirmation Required:** NEVER run `git commit` or `git push` autonomously.
- Always summarize the modified/staged files, ensure the user has verified and tested the changes on device, and wait for the user's explicit confirmation before committing and pushing.
- **Secondary Branch Target:** When working on a secondary/experimental branch, all confirmed commits and pushes must remain strictly on that branch (`git push origin <branch-name>`). NEVER push to or merge into `main` unless the user explicitly instructs to merge into `main`.

## 5. Sandboxed Experiments & Exploratory Prototyping ("Sandbox Rule")
Whenever trying something new, prototyping exploratory UX/architectural changes, or testing alternative workflows (e.g., migrating UI elements to system menus, restructuring layouts, swapping core systems):
- **Isolate in a Dedicated Sandbox Branch:** NEVER experiment directly on `main` or the current stable working tree. Ensure the current baseline is clean, then create and switch to a dedicated branch (e.g., `git checkout -b experiment/<feature-name>`).
- **Deploy & Test from Sandbox:** Run `./build_and_deploy.sh` directly from the sandbox branch so the user can test the prototype on the physical Board hardware.
- **Frictionless Rollback ("Throw Away"):** If the user dislikes the experiment or asks to revert ("let's go back", "revert back", "I don't like that"):
  1. Switch back to the stable baseline branch: `git checkout main`.
  2. Delete or archive the experiment branch: `git branch -D experiment/<feature-name>`.
  3. Rebuild and deploy: `./build_and_deploy.sh -d <device_ip>`.
  This guarantees an instant return to the verified baseline in seconds with zero manual code surgery, no residual bugs, and no risk of regressions.
- **Explicit Merge Instruction Required for Promotion:** Do NOT merge or fast-forward sandbox branches into `main` on general approval ("push", "commit", "good work"). Only merge into `main` when the user explicitly commands to merge (e.g., "merge into main", "promote branch to main"). Without that explicit command, commits and pushes must target the secondary experiment branch exclusively.


