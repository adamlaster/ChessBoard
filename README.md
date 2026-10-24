# ♟️ ChessBoard

**ChessBoard** (`com.lastersoft.chessboard`) is a custom-engineered web gaming, video streaming, and tabletop application hub built for the **Board** digital tabletop hardware device.

Because the Board OS validates all installed APKs against its native SDK (`libboard.so` / `libnativeBoardSDK.so`), **ChessBoard** pairs a lightweight **Godot 4.7** host application (`chessboard`) with a custom native Android plugin (`com.lastersoft.godotwebview.GodotWebView`) that renders a hardware-accelerated Chromium `WebView` with full multi-touch passthrough. For deep architectural details and component diagrams, see [`ARCHITECTURE.md`](./ARCHITECTURE.md).

---

## ✨ Key Features

* **Tactical Arrow Drawing & Square Highlighting (Double-Tap Gestures):**
  * Solves the lack of right-click hardware on touchscreens by translating single-finger double-taps on the chessboard into desktop right-mouse events on Chess.com and Lichess.
  * **Double-tap & drag:** Draws live tactical arrows from origin to target square without accidentally moving pieces.
  * **Double-tap in place:** Toggles square color highlights (green).
  * **Additive markings:** Multiple arrows and highlights persist together on the board; clearing is done with a single tap or piece move.
  * **Zero piece movement latency:** Drag-and-drop moves (`> 10px`) bypass the gesture engine with 0ms latency.
* **Chess.com Bot Engine WebAssembly Memory Patching:**
  * Intercepts `explanation-engine.wasm` at the network level and dynamically patches Section 5 (Memory Section) from 386 MB down to 32 MB initial memory with local disk caching. Eliminates V8 virtual memory out-of-memory (`RangeError`) crashes on Android WebView, allowing all Chess.com bots (Vinh, Count Morphy, etc.) and Coach review to run flawlessly.
* **Board OS System Menu Integration:**
  * Pressing the Board hardware/system menu button opens the native Board OS overlay with four actions:
    * **Resume** — Return to the active web app.
    * **App Menu** — Open the centered **ChessBoard** launcher and control card.
    * **Controls Guide** — Open the 2x tabletop-scaled interactive modal detailing all chess gestures, browser navigation, desktop persistence, margin toggles, screen rotation, and secondary tabs.
    * **Exit to Library** — Immediately terminate the app via `SessionManagerBridge` and return to the Board OS Library.
* **100% Native Multi-Touch Passthrough:**
  * Configures `BoardNativePlugin` before SDK initialization so `SystemOverlayService` binds cleanly without swallowing raw Android touch events—preserving smooth dragging, scrolling, and multi-touch gestures in the `WebView`.
* **2x Tabletop-Scaled Centered Menu (`menuCard`):**
  * Designed for a 24-inch tabletop display with high-contrast dark styling, outside-tap scrim dismissal, and a top loading progress bar.
* **Desktop & Mobile User-Agent Switching (`🖥` / `📱`):**
  * Defaults to **Desktop Mode (`🖥`)** for large-screen layouts.
  * **Short tap** toggles Desktop/Mobile mode for the current session.
  * **3-second hold** saves Desktop or Mobile mode as the persistent default for just that site in `SharedPreferences` (with a confirmation dialog).
* **4-Way Tabletop Rotation (`90°`):**
  * **Short tap** rotates clockwise through all 4 tabletop orientations (`Landscape` → `Portrait` → `Reverse Landscape` → `Reverse Portrait`).
  * **Long-press** immediately resets the display to default `Landscape`.
* **On-Demand Right Margin Gutter Toggle (Board System Menu Clearance):**
  * Resolves cases where the Board OS floating system menu button overlaps right-side web UI elements (notation, clocks, controls). Applying the 106dp right margin evenly frames the Board OS floating button between the web content and display bezel.
  * **Short tap** toggles the margin for the current session.
  * **3-second hold** saves right margin ON or OFF as the persistent default for just that site.
* **In-App Interactive Controls & Gestures Guide:**
  * An interactive, 2x tabletop-scaled modal dialog accessible directly from the Board OS system pause menu. Outlines all tabletop chess gestures (double-tap arrows, highlights, additive markings), desktop sidebar flyouts, browser navigation controls, desktop mode persistence, page margin gutter toggles, screen rotation, secondary tabs, and Board OS pause menu actions.
* **Isolated Secondary Tab & Floating `✕` Dismissal:**
  * Links that request a new window (`target="_blank"`, `window.open()`, or external links clicked during a game) open in a dedicated secondary `WebView` layered on top, keeping your active Chess.com game alive underneath.
  * A draggable floating **`✕`** button with idle auto-fade allows you to close the secondary tab at any time and instantly return to your active game.
* **Chess.com Desktop Touch Ergonomics:**
  * Injects a client-side bridge into Chess.com to resolve desktop mouse-hover vs. touchscreen tap mismatches. Prevents category taps from immediately hotlinking away from your active board, opening flyout submenus ("Play Online", "Computer", etc.) on first tap, with clean outside-tap dismissal.
* **Configurable Michigan Sports & Regional UI Themes:**
  * Implements 6 tabletop brand themes tailored for high contrast and glare-free tabletop gaming:
    * **University of Michigan:** Michigan Blue (`#00274C`), vibrant Maize (`#FFCB05`), and deep obsidian navy surfaces (`#001226`).
    * **Detroit Pistons:** Royal Blue (`#1D42BA`), vibrant Red (`#ED174C`), and midnight navy surfaces (`#060C1C`).
    * **Detroit Lions:** Honolulu Blue (`#0076B6`), vibrant Honolulu Cyan (`#00A3E0`), silver accents, and dark steel surfaces (`#030E19`).
    * **Detroit Tigers:** Midnight Navy (`#0C2340`), vibrant Tiger Orange (`#FA4616`), and obsidian surfaces (`#020914`).
    * **Utica Unicorns:** USPBL Unicorn Purple (`#5A2D81`), vibrant Red (`#ED1C24`), and plum surfaces (`#12061A`).
    * **Classic Slate:** Slate Charcoal (`#121622`), Indigo (`#4C6EF5`), and Cyan (`#22B8CF`).
  * Includes a scrollable Tabletop Theme Picker modal (`ThemePickerDialogHelper`) accessible from the Launcher header allowing instant dynamic re-theming without page reloads, persisted in `SharedPreferences`.
* **Widevine DRM & HTML5 Fullscreen Video:**
  * Automatically grants `RESOURCE_PROTECTED_MEDIA_ID` permissions for protected streaming playback and supports native HTML5 fullscreen video surfaces.

---

## 📁 Repository Structure

```text
ChessBoard/
├── .gitignore                # Unified root git ignore (macOS, Godot, Gradle, IDEs)
├── README.md                 # Project overview & quick reference
├── ARCHITECTURE.md           # System architecture, component specifications & diagrams
├── AGENTS.md                 # Always-on workspace rules & conventions
├── BUILD_SEQUENCE.md         # Detailed 4-step build & deployment guide
├── PROJECT_HISTORY.md        # Chronological engineering log & roadmap
├── build_and_deploy.sh       # One-step CLI build, export & deploy script
├── chessboard/               # Godot 4.7 host project & Board SDK integration
│   ├── project.godot         # Godot configuration & 3s splash setup
│   ├── chess.tscn            # Root scene (splash screen & WebView host)
│   ├── root.gd               # Board SDK lifecycle, pause menu & WebView bridge
│   ├── export_presets.cfg    # Android APK export preset (-> ../../output/ChessBoard.apk)
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
            ├── ChessTouchHelper.java      # Sidebar navigation & double-tap arrow bridge
            ├── WasmPatchHelper.java       # Wasm binary patcher for Chess.com bot engine OOM
            ├── HelpDialogHelper.java      # 2x modal controls & gestures guide dialog
            ├── SiteSettingsDialogHelper.java # Per-site User-Agent & custom Right Margin settings dialog
            ├── SplitScreenManager.java    # Split-Screen dual-app tabletop subsystem & controls
            ├── ThemeManager.java          # Dynamic UI theme tokens & persistence (U of M / Classic)
            ├── ThemePickerDialogHelper.java # Tabletop modal theme picker dialog with preview swatches
            └── MenuOverlayView.java       # Decoupled fullscreen launcher & live browser controls rail
```

> **Note:** Exported APK binaries are written out-of-tree to `../output/ChessBoard.apk` (configurable via `OUTPUT_DIR` in `.env`) to keep large build artifacts out of git history.

---

## 🚀 Quick Start: Build & Deploy

### Environment Setup
The project uses smart auto-detection for standard macOS, Homebrew, and Android Studio paths. To customize paths for your machine or verify tools:

```bash
# Optional: customize paths for your environment
cp .env.example .env

# Verify your local environment and installed SDKs
./build_and_deploy.sh --doctor
```

### One-Command Build & Deploy
From the repository root:

```bash
./build_and_deploy.sh
```

### Script Flags
| Command | Description |
|---|---|
| `./build_and_deploy.sh` | Full build: compiles `GodotWebView` AARs, copies to `addons/webview/`, exports `ChessBoard.apk`, and installs/launches on Board |
| `./build_and_deploy.sh --doctor` | Verifies local JDK 17+, Android SDK, Godot binary, and Board CLI configuration |
| `./build_and_deploy.sh -d <target>` | Installs to a specific Board device or IP address (e.g. `./build_and_deploy.sh -d 10.0.0.65`) |
| `./build_and_deploy.sh --skip-aar` | Skips Gradle AAR compilation (use when only Godot files/assets changed) |
| `./build_and_deploy.sh --no-deploy` | Builds the AARs and exports `ChessBoard.apk` without deploying to the Board device |

> For manual step-by-step CLI commands (Gradle, Godot headless export, and `board-connect`), see [`BUILD_SEQUENCE.md`](./BUILD_SEQUENCE.md).

---

## 🎛️ Browser & Launcher Controls Reference

| Control | Short Tap Action | Long-Press Action |
|---|---|---|
| **App / Site Card** *(Launcher)* | Launch the selected web app or game | **Long press (500ms):** Open Site Settings dialog to edit persistent User-Agent (`🖥`/`📱`) and Margin mode (`0dp` / `Right 106dp` / `All 4 Sides 106dp` / custom). |
| **`[ ✎ Edit ]` / `[ ✓ Save ]`** *(Launcher Row Header)* | Toggle Edit Mode for this category row. In edit mode, enables horizontal card dragging and reveals row navigation controls. Tap `[ ✓ Save ]` to persist custom card and row orders. | — |
| **`[ ▲ ]` / `[ ▼ ]`** *(Launcher Edit Mode)* | Move active category row up or down in the launcher with instant layout reordering and persistent saving | — |
| **Card Dragging** *(Launcher Edit Mode)* | Touch and drag any card horizontally to reorder apps within the row with smooth displacement animations and auto-scrolling | — |
| **Deadspace Tap** *(Launcher)* | Tap any empty area/margin outside apps to close Launcher (or exit Edit Mode if active) | Guarded by near-miss tolerance buffer (36px) to prevent accidental closure |
| **"CHESSBOARD" Title** *(Launcher Header)* | — | **Long press (500ms):** Open ChessBoard project GitHub repository (`https://github.com/adamlaster/ChessBoard`) |
| **`[ Theme: ... ]` Button** *(Launcher Header)* | Open the Tabletop Theme Picker modal to switch live between 6 themes (University of Michigan, Detroit Pistons, Detroit Lions, Detroit Tigers, Utica Unicorns, and Classic Slate) with instant re-theming and persistent saving | — |
| **`[ 📖 Controls Guide ]`** *(Launcher Header)* | Open the 2x tabletop-scaled interactive modal detailing all chess gestures, browser navigation, desktop persistence, margin toggles, screen rotation, and secondary tabs | — |
| **`✕`** | Close the Browser Controls overlay or Launcher (positioned in identical spot below Board button). The Browser Controls rail remains active during live page interaction and dismisses exclusively on `✕` tap. | — |
| **`⬅`** | Navigate back in `WebView` history (or exit fullscreen video) | — |
| **`＋`** / **`－`** | Step page zoom (`75%`, `100%`, `125%`, `150%`, `200%`) live | — |
| **`↺`** | Reload the current page live | — |
| **`🖥`** / **`📱`** | Toggle Desktop (`🖥`) vs. Mobile (`📱`) User-Agent live | **Long press:** Save as persistent default for active site |
| **Margin (`◧` / `⧈` / `▢`)** | Cycle tabletop margins: **No margin** (`0dp` edge-to-edge) → **Right margin** (`106dp` Board clearance) → **All 4 sides** (`106dp` inset padding) | **Long press:** Save active margin mode as persistent default for active site |
| **Split Screen (`◫`)** | Open Split Screen mode (prompts app picker from Launcher, or cycles presets `50/50` → `70/30` → `30/70` when active) | **Long press:** Close Split Screen mode and restore full screen |
| **Split Controls Bar** *(Top Center)* | `[ 🔊 LeftApp ]  [ ⇄ Swap ]  [ ◫ Presets ]  [ 🔇 RightApp ]  [ ✕ Exit ]` — Visible exclusively when Browser Controls are open. One-tap audio switching between panes, pane swapping, preset cycling, and clean exit. | **Long press on audio:** Mute both panes (`NONE`) |
| **Divider Dragging** *(Split Mode)* | Drag center divider thumb to adjust split ratio (`20%` to `80%`). Automatically presents high-performance dark themed overlay with live scaling app icons, titles, and live percentages, completely hiding WebViews for 60fps stutter-free dragging. Snaps to presets upon release. | **Double tap:** Instantly reset to balanced 50/50 split |
| **`90°`** | Rotate screen clockwise (`0°` → `90°` → `180°` → `270°`). The Browser Controls rail remains persistent and docked across rotation without dismissing. | Reset to default Landscape (`0°`) |
| **`♟`** | Switch directly from Browser Controls to the fullscreen Launcher | — |
| **`✕`** *(Floating)* | Close the secondary tab and return to active game | Drag to reposition anywhere on the tabletop |

---

## 🔘 Board OS System Menu Controls

Pressing the hardware or floating Board system button opens the Board OS pause overlay:
* **Resume:** Return to the active web page or chess game.
* **Launcher:** Open the fullscreen 4-row categorized app catalog (Primary Chess, Games, Watch, Web) featuring an upper-right corner cutout around the Board OS button and long-press per-site settings.
* **Browser Controls:** Open the slim vertical toolbar overlay docked directly on top of the live browser (below the Board OS button) for real-time visual feedback of zoom, UA, reload, and rotation.
* **Exit to Library:** Safely terminate ChessBoard and return to the Board home screen.

*(Note: The Controls Guide is accessible directly inside the Launcher header via `[ 📖 Controls Guide ]`)*

---

## ♟️ Tabletop Chess Gestures (Chess.com & Lichess)

On desktop browsers, tactical arrows and square highlights require mouse right-clicks (`button: 2`). **ChessBoard** provides an intuitive single-finger touch bridge that translates double-taps on the chessboard into right-click events without interfering with normal play:

* **Draw Tactical Arrows (Double-Tap & Drag):** Double-tap on a starting square and hold/drag on the second tap across to a destination square to draw live tactical arrows.
* **Highlight Square (Double-Tap in Place):** Double-tap quickly in place on any square to toggle its color highlight (green).
* **No Accidental Piece Grabs:** Double-tapping on occupied squares begins arrows cleanly without picking up or dragging pieces.
* **Additive Markings:** Multiple arrows and highlights can be drawn sequentially without previous lines disappearing; lines persist until explicitly cleared.
* **Clear Markings:** Single-tap any square once or make a piece move to clear all arrows and highlights.
* **Zero Movement Latency:** Standard single-finger piece drags (> 10px) retain 100% native, instant responsiveness.
* **Pinch-Zoom & Scroll Suppression:** Browser scrolling and pinch-zooming are suppressed during active arrow drawing.

---

## 🎮 Included Platforms & Web Apps

### ♟️ Primary Chess Suite
* **Chess.com** (`https://www.chess.com`)
* **ChessKids.com** (`https://www.chesskids.com`)
* **Lichess** (`https://lichess.org/analysis`)
* **ChessReps** (`https://chessreps.com`)

### 🗂️ Extra Apps (23 Apps Across 3 Categories)
* **🎲 Games (8):** Colonist.io (Catan), Dots & Boxes, PBS Kids, ABCmouse, AutoDraw (Google), 247 Backgammon, 247 Checkers, 247 Games (All)
* **📺 Watch (9):** YouTube, Peacock, Fox One, CBS Sports, MLB.TV, Disney+, Prime Video, Fandango, Apple TV
* **🌐 Web (6):** Google, Google Photos, Google Maps, Reddit, Wikipedia, Weather

---

## 📚 Additional Documentation

* **[`ARCHITECTURE.md`](./ARCHITECTURE.md)** — Comprehensive system architecture, modular component specifications, view hierarchy, and Mermaid flowcharts.
* **[`BUILD_SEQUENCE.md`](./BUILD_SEQUENCE.md)** — Step-by-step breakdown of the Gradle, Godot CLI, and `board-connect` build pipeline.
* **[`PROJECT_HISTORY.md`](./PROJECT_HISTORY.md)** — Complete chronological engineering history, architectural decisions, and upcoming feature roadmap.
* **[`AGENTS.md`](./AGENTS.md)** — Workspace conventions, canonical root file rules, and build/output hygiene.
