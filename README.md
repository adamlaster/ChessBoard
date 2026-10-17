# ♟️ Chess 4 Board

**Chess 4 Board** (`com.lastersoft.chess4board`) is a custom-engineered web gaming, video streaming, and tabletop application hub built for the **Board** digital tabletop hardware device.

Because the Board OS validates all installed APKs against its native SDK (`libboard.so` / `libnativeBoardSDK.so`), **Chess 4 Board** pairs a lightweight **Godot 4.7** host application (`chess-4-board`) with a custom native Android plugin (`com.lastersoft.godotwebview.GodotWebView`) that renders a hardware-accelerated Chromium `WebView` with full multi-touch passthrough. For deep architectural details and component diagrams, see [`ARCHITECTURE.md`](./ARCHITECTURE.md).

---

## ✨ Key Features

* **Board OS System Menu Integration:**
  * Pressing the Board hardware/system menu button opens the native Board OS overlay with three actions:
    * **Resume** — Return to the active web app.
    * **Chess 4 Board Menu** — Open the centered **Chess 4 Board** launcher and control card.
    * **Exit to Library** — Immediately terminate the app via `SessionManagerBridge` and return to the Board OS Library.
* **100% Native Multi-Touch Passthrough:**
  * Configures `BoardNativePlugin` before SDK initialization so `SystemOverlayService` binds cleanly without swallowing raw Android touch events—preserving smooth dragging, scrolling, and multi-touch gestures in the `WebView`.
* **2x Tabletop-Scaled Centered Menu (`menuCard`):**
  * Designed for a 24-inch tabletop display with high-contrast dark styling, outside-tap scrim dismissal, and a top loading progress bar.
* **Desktop & Mobile User-Agent Switching (`🖥` / `📱`):**
  * Defaults to **Desktop Mode (`🖥`)** for large-screen layouts.
  * **Short tap** toggles Desktop/Mobile mode for the current site only.
  * **3-second hold** toggles and saves the global default User-Agent in `SharedPreferences` (with a confirmation dialog).
* **4-Way Tabletop Rotation (`90°`):**
  * **Short tap** rotates clockwise through all 4 tabletop orientations (`Landscape` → `Portrait` → `Reverse Landscape` → `Reverse Portrait`).
  * **Long-press** immediately resets the display to default `Landscape`.
* **Isolated Secondary Tab & Floating `✕` Dismissal:**
  * Links that request a new window (`target="_blank"`, `window.open()`, or external links clicked during a game) open in a dedicated secondary `WebView` layered on top, keeping your active Chess.com game alive underneath.
  * A draggable floating **`✕`** button with idle auto-fade allows you to close the secondary tab at any time and instantly return to your active game.
* **Chess.com Desktop Touch Ergonomics:**
  * Injects a client-side bridge into Chess.com to resolve desktop mouse-hover vs. touchscreen tap mismatches. Prevents category taps from immediately hotlinking away from your active board, opening flyout submenus ("Play Online", "Computer", etc.) on first tap, with clean outside-tap dismissal.
* **Widevine DRM & HTML5 Fullscreen Video:**
  * Automatically grants `RESOURCE_PROTECTED_MEDIA_ID` permissions for protected streaming playback and supports native HTML5 fullscreen video surfaces.

---

## 📁 Repository Structure

```text
Chess4Board/
├── .gitignore                # Unified root git ignore (macOS, Godot, Gradle, IDEs)
├── README.md                 # Project overview & quick reference
├── ARCHITECTURE.md           # System architecture, component specifications & diagrams
├── AGENTS.md                 # Always-on workspace rules & conventions
├── BUILD_SEQUENCE.md         # Detailed 4-step build & deployment guide
├── PROJECT_HISTORY.md        # Chronological engineering log & roadmap
├── build_and_deploy.sh       # One-step CLI build, export & deploy script
├── chess-4-board/            # Godot 4.7 host project & Board SDK integration
│   ├── project.godot         # Godot configuration & 3s splash setup
│   ├── chess.tscn            # Root scene (splash screen & WebView host)
│   ├── root.gd               # Board SDK lifecycle, pause menu & WebView bridge
│   ├── export_presets.cfg    # Android APK export preset (-> ../../output/Chess4Board.apk)
│   └── addons/
│       ├── board_sdk/        # Official Board OS Godot SDK & native AARs
│       └── webview/          # Compiled GodotWebView debug/release AARs
└── godot-webview/            # Native Android WebView plugin source (Gradle/Java)
    └── GodotWebView/
        └── GodotWebView/src/main/java/com/lastersoft/godotwebview/
            ├── GodotWebView.java          # Godot plugin coordinator & facade
            ├── AppCatalog.java            # Chess platforms & categorized apps
            ├── BoardBridge.java           # Native Board SDK reflection bridge
            ├── UserAgentManager.java      # Desktop/Mobile UA manager & prefs
            ├── DisplayController.java     # Step zoom & 4-way orientation cycling
            ├── FloatingDismissButton.java # Draggable floating dismiss button (✕)
            ├── SecondaryTabManager.java   # Isolated multi-window popup tab manager
            ├── ChessTouchHelper.java      # Chess.com desktop sidebar touch bridge
            └── MenuOverlayView.java       # 2x scaled centered menu overlay card
```

> **Note:** Exported APK binaries are written out-of-tree to `/Users/adamlaster/Development/BoardGames/output/Chess4Board.apk` to keep large build artifacts out of git history.

---

## 🚀 Quick Start: Build & Deploy

### Prerequisites
* **JDK 17:** `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` (or Android Studio JBR)
* **Android SDK:** `~/Library/Android/sdk` (API 34)
* **Godot Engine 4.7+:** `/Applications/Godot.app/Contents/MacOS/Godot`
* **Board CLI:** `~/.local/bin/board-connect` (configured for your Board device on LAN)

### One-Command Build & Deploy
From the repository root:

```bash
./build_and_deploy.sh
```

### Script Flags
| Command | Description |
|---|---|
| `./build_and_deploy.sh` | Full build: compiles `GodotWebView` AARs, copies to `addons/webview/`, exports `Chess4Board.apk`, and installs/launches on Board |
| `./build_and_deploy.sh --skip-aar` | Skips Gradle AAR compilation (use when only Godot files/assets changed) |
| `./build_and_deploy.sh --no-deploy` | Builds the AARs and exports `Chess4Board.apk` without deploying to the Board device |

> For manual step-by-step CLI commands (Gradle, Godot headless export, and `board-connect`), see [`BUILD_SEQUENCE.md`](./BUILD_SEQUENCE.md).

---

## 🎛️ Menu Controls Reference

| Control | Short Tap Action | Long-Press Action |
|---|---|---|
| **`⬅`** | Navigate back in `WebView` history (or exit fullscreen video) | — |
| **`－`** / **`＋`** | Step page zoom (`75%`, `100%`, `125%`, `150%`, `200%`) | — |
| **`↺`** | Reload the current page | — |
| **`🖥`** / **`📱`** | Toggle Desktop (`🖥`) vs. Mobile (`📱`) User-Agent for current site | **3s hold:** Save as new global default User-Agent |
| **`90°`** | Rotate clockwise (`0°` → `90°` → `180°` → `270°`) | Reset to default Landscape (`0°`) |
| **`⬇`** | Close the Chess 4 Board Menu overlay | — |
| **`✕`** *(Floating)* | Close the secondary tab and return to active game | Drag to reposition anywhere on the tabletop |

---

## 🎮 Included Platforms & Web Apps

### ♟️ Primary Chess Suite
* **Chess.com** (`https://www.chess.com`)
* **ChessKids.com** (`https://www.chesskids.com`)
* **Lichess** (`https://lichess.org/analysis`)
* **ChessReps** (`https://chessreps.com`)

### 🗂️ Extra Apps (18 Apps Across 3 Categories)
* **🎲 Games (6):** Colonist.io (Catan), Dots & Boxes, PBS Kids, 247 Backgammon, 247 Checkers, 247 Games (All)
* **📺 Video (6):** YouTube, Peacock, Fox One, MLB.TV, Disney+, Prime Video
* **🌐 Web (6):** Google, Google Photos, Google Maps, Reddit, Wikipedia, Weather

---

## 📚 Additional Documentation

* **[`ARCHITECTURE.md`](./ARCHITECTURE.md)** — Comprehensive system architecture, modular component specifications, view hierarchy, and Mermaid flowcharts.
* **[`BUILD_SEQUENCE.md`](./BUILD_SEQUENCE.md)** — Step-by-step breakdown of the Gradle, Godot CLI, and `board-connect` build pipeline.
* **[`PROJECT_HISTORY.md`](./PROJECT_HISTORY.md)** — Complete chronological engineering history, architectural decisions, and upcoming feature roadmap.
* **[`AGENTS.md`](./AGENTS.md)** — Workspace conventions, canonical root file rules, and build/output hygiene.
