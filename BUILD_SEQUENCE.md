# ChessBoard — Build & Deployment Pipeline

This document details the complete build, packaging, and deployment pipeline for **ChessBoard** (`com.lastersoft.chessboard`) running on the **Board** digital tabletop device.

For a high-level overview and controls reference, see [`README.md`](./README.md). For detailed component design, view hierarchy, and Mermaid flowcharts, see [`ARCHITECTURE.md`](./ARCHITECTURE.md).

---

## ⚡ Quick Start: One-Step Automated Pipeline

A unified build script handles the entire 4-step sequence automatically:

```bash
./build_and_deploy.sh
```

### Script Options & Environment Diagnosis:
* **Verify prerequisites & local tool configuration:**
  ```bash
  ./build_and_deploy.sh --doctor
  ```
* **Full pipeline (default):** Recompiles Android library, copies AARs, exports Godot APK to `../output/ChessBoard.apk`, and installs/launches on Board:
  ```bash
  ./build_and_deploy.sh
  ```
* **Deploy to a specific Board device/IP:**
  ```bash
  ./build_and_deploy.sh -d 10.0.0.65
  ```
* **Godot-only updates (skip Java/AAR compilation):**
  ```bash
  ./build_and_deploy.sh --skip-aar
  ```
* **Build without deploying:**
  ```bash
  ./build_and_deploy.sh --no-deploy
  ```
* **Enable remote navigation daemon for prototype tooling & screenshot automation:**
  ```bash
  ./build_and_deploy.sh --enable-remote-nav
  ```
  *(Strictly disabled by default during normal builds and runtime to ensure zero CPU/memory/thread overhead on Board hardware).*

---

## ⚙️ Environment Setup & Customization

The build pipeline works out of the box with auto-detected standard paths on macOS/Homebrew. When sharing or cloning this repository to a new machine, configure local paths using **`.env`**:

1. Copy the provided template:
   ```bash
   cp .env.example .env
   ```
2. Adjust any paths in `.env` if your tools or Android SDK are in non-standard locations:
   * `ANDROID_HOME` — Path to Android SDK (default: `~/Library/Android/sdk`)
   * `JAVA_HOME` — Path to JDK 17+ (default: auto-detected Homebrew OpenJDK 17 or Android Studio JBR)
   * `GODOT_BIN` — Path to Godot 4 executable (default: `/Applications/Godot.app/Contents/MacOS/Godot` or `godot` on PATH)
   * `OUTPUT_DIR` — Path to export output folder (default: `../output`, keeping large binaries out of git)
   * `BOARD_CONNECT_BIN` — Path to Board Connect CLI (default: `~/.local/bin/board-connect`)
3. Validate your environment:
   ```bash
   ./build_and_deploy.sh --doctor
   ```

---

## 📋 Detailed 4-Step Pipeline Breakdown

### Step 1: Build Android WebView Plugin (AAR)
Compiles both debug and release Android library binaries containing our custom `GodotWebView` Java implementation (`com.lastersoft.godotwebview`) and Android dependencies.

* **Working Directory:** `godot-webview/GodotWebView`
* **Command:**
  ```bash
  ./gradlew --no-build-cache clean assemble
  ```
* **Outputs:**
  * `godot-webview/GodotWebView/GodotWebView/build/outputs/aar/GodotWebView-debug.aar`
  * `godot-webview/GodotWebView/GodotWebView/build/outputs/aar/GodotWebView-release.aar`

---

### Step 2: Copy AARs to ChessBoard Project
Copies the updated native AAR binaries directly into the game's `addons/webview` directory so Godot packages them into the Android export template.

* **Source Directory:** `godot-webview/GodotWebView/GodotWebView/build/outputs/aar`
* **Destination Directory:** `chessboard/addons/webview`
* **Command:**
  ```bash
  cp godot-webview/GodotWebView/GodotWebView/build/outputs/aar/GodotWebView-debug.aar chessboard/addons/webview/
  cp godot-webview/GodotWebView/GodotWebView/build/outputs/aar/GodotWebView-release.aar chessboard/addons/webview/
  ```

---

### Step 3: Export Android APK via Godot CLI
Performs a headless compilation and packaging of `chessboard` into an Android APK using the Android Gradle build template configured in `export_presets.cfg`.

* **Project Directory:** `chessboard`
* **Command:**
  ```bash
  mkdir -p "${OUTPUT_DIR:-../output}"
  "${GODOT_BIN:-godot}" --headless --path chessboard --export-debug "Android" "${OUTPUT_DIR:-../output}/ChessBoard.apk"
  ```
* **Output:** `ChessBoard.apk` placed in `OUTPUT_DIR` (default: `../output/ChessBoard.apk`, stored out-of-tree so large APK binaries stay outside the git repository).

---

### Step 4: Install and Launch APK on Board Device
Uploads and installs `ChessBoard.apk` to the target Board device over LAN, then immediately launches the app.

* **Working Directory:** `OUTPUT_DIR` (e.g. `../output`)
* **Command:**
  ```bash
  board-connect install "${OUTPUT_DIR:-../output}/ChessBoard.apk" --launch
  ```
* **Output:** Real-time upload progress bar and confirmation from the `board-connect` CLI tool.

---

## 🛠️ Environment & Tool Requirements

| Tool | Configurable Variable / Env | Default Auto-Detected Path (macOS) | Purpose |
|---|---|---|---|
| **JDK 17+** | `JAVA_HOME` | `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` | Compiles Gradle AAR and Android APK |
| **Android SDK** | `ANDROID_HOME` | `$HOME/Library/Android/sdk` | Build tools, platform tools (`adb`), target API 34+ |
| **Godot Engine** | `GODOT_BIN` | `/Applications/Godot.app/Contents/MacOS/Godot` | Headless game engine exporter (v4.2+) |
| **Board Connect** | `BOARD_CONNECT_BIN` | `$HOME/.local/bin/board-connect` | Board device deployment & launch tool |
| **Output Directory** | `OUTPUT_DIR` | `../output` | Out-of-tree directory for exported APK binaries |

---

## 📚 Related Documentation

* **[`README.md`](./README.md)** — Project overview, menu controls quick reference, and complete catalog of primary chess suites and extra web applications.
* **[`ARCHITECTURE.md`](./ARCHITECTURE.md)** — Comprehensive system architecture, modular component specifications, view hierarchy, and Mermaid flowcharts.
* **[`PROJECT_HISTORY.md`](./PROJECT_HISTORY.md)** — Complete chronological engineering log, phase-by-phase development history, and future roadmap.
* **[`AGENTS.md`](./AGENTS.md)** — Workspace conventions, canonical root file rules, and build/output hygiene.

