# Chess 4 Board — Build & Deployment Pipeline

This document details the complete build, packaging, and deployment pipeline for **Chess 4 Board** (`com.lastersoft.chess4board`) running on the **Board** digital tabletop device.

For a high-level overview and controls reference, see [`README.md`](./README.md). For detailed component design, view hierarchy, and Mermaid flowcharts, see [`ARCHITECTURE.md`](./ARCHITECTURE.md).

---

## ⚡ Quick Start: One-Step Automated Pipeline

A unified build script handles the entire 4-step sequence automatically:

```bash
cd /Users/adamlaster/Development/BoardGames/Chess4Board
./build_and_deploy.sh
```

### Script Options:
* **Full pipeline (default):** Recompiles Android library, copies AARs, exports Godot APK to `/Users/adamlaster/Development/BoardGames/output/Chess4Board.apk`, and installs/launches on Board:
  ```bash
  ./build_and_deploy.sh
  ```
* **Godot-only updates (skip Java/AAR compilation):**
  ```bash
  ./build_and_deploy.sh --skip-aar
  ```
* **Build without deploying:**
  ```bash
  ./build_and_deploy.sh --no-deploy
  ```

---

## 📋 Detailed 4-Step Pipeline Breakdown

### Step 1: Build Android WebView Plugin (AAR)
Compiles both debug and release Android library binaries containing our custom `GodotWebView` Java implementation (`com.lastersoft.godotwebview`) and Android dependencies.

* **Working Directory:** `/Users/adamlaster/Development/BoardGames/Chess4Board/godot-webview/GodotWebView`
* **Command:**
  ```bash
  export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
  export PATH="$JAVA_HOME/bin:$PATH"
  ./gradlew --no-build-cache clean assemble
  ```
* **Outputs:**
  * `GodotWebView/GodotWebView/build/outputs/aar/GodotWebView-debug.aar`
  * `GodotWebView/GodotWebView/build/outputs/aar/GodotWebView-release.aar`

---

### Step 2: Copy AARs to Chess 4 Board Project
Copies the updated native AAR binaries directly into the game's `addons/webview` directory so Godot packages them into the Android export template.

* **Source Directory:** `/Users/adamlaster/Development/BoardGames/Chess4Board/godot-webview/GodotWebView/GodotWebView/build/outputs/aar`
* **Destination Directory:** `/Users/adamlaster/Development/BoardGames/Chess4Board/chess-4-board/addons/webview`
* **Command:**
  ```bash
  cp /Users/adamlaster/Development/BoardGames/Chess4Board/godot-webview/GodotWebView/GodotWebView/build/outputs/aar/GodotWebView-debug.aar /Users/adamlaster/Development/BoardGames/Chess4Board/chess-4-board/addons/webview/
  cp /Users/adamlaster/Development/BoardGames/Chess4Board/godot-webview/GodotWebView/GodotWebView/build/outputs/aar/GodotWebView-release.aar /Users/adamlaster/Development/BoardGames/Chess4Board/chess-4-board/addons/webview/
  ```

---

### Step 3: Export Android APK via Godot CLI
Performs a headless compilation and packaging of `chess-4-board` into an Android APK using the Android Gradle build template configured in `export_presets.cfg`.

* **Project Directory:** `/Users/adamlaster/Development/BoardGames/Chess4Board/chess-4-board`
* **Command:**
  ```bash
  export ANDROID_HOME="/Users/adamlaster/Library/Android/sdk"
  export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
  export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
  mkdir -p /Users/adamlaster/Development/BoardGames/output
  /Applications/Godot.app/Contents/MacOS/Godot --headless --path /Users/adamlaster/Development/BoardGames/Chess4Board/chess-4-board --export-debug "Android" /Users/adamlaster/Development/BoardGames/output/Chess4Board.apk
  ```
* **Output:** `/Users/adamlaster/Development/BoardGames/output/Chess4Board.apk` (stored out-of-tree so large APK binaries stay outside the git repository)

---

### Step 4: Install and Launch APK on Board Device
Uploads and installs `Chess4Board.apk` to the target Board device over LAN, then immediately launches the app.

* **Working Directory:** `/Users/adamlaster/Development/BoardGames/output`
* **Command:**
  ```bash
  export PATH="$HOME/.local/bin:$PATH"
  board-connect install /Users/adamlaster/Development/BoardGames/output/Chess4Board.apk --launch
  ```
* **Output:** Real-time upload progress bar and confirmation from the `board-connect` CLI tool.

---

## 🛠️ Environment & Tool Requirements

| Tool | Resolved Path | Purpose |
|---|---|---|
| **JDK 17** | `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` | Compiles Gradle AAR and Android APK |
| **Android SDK** | `/Users/adamlaster/Library/Android/sdk` | Build tools, platform tools (`adb`), target API 34 |
| **Godot Engine** | `/Applications/Godot.app/Contents/MacOS/Godot` | Headless game engine exporter |
| **Board Connect** | `~/.local/bin/board-connect` | Board device deployment & launch tool |
| **Board Device Config** | `~/.config/board-connect/config.json` | IP: `10.0.0.65:8843` |

---

## 📚 Related Documentation

* **[`README.md`](./README.md)** — Project overview, menu controls quick reference, and complete catalog of primary chess suites and extra web applications.
* **[`ARCHITECTURE.md`](./ARCHITECTURE.md)** — Comprehensive system architecture, modular component specifications, view hierarchy, and Mermaid flowcharts.
* **[`PROJECT_HISTORY.md`](./PROJECT_HISTORY.md)** — Complete chronological engineering log, phase-by-phase development history, and future roadmap.
* **[`AGENTS.md`](./AGENTS.md)** — Workspace conventions, canonical root file rules, and build/output hygiene.

