# Chess 4 Board — System Architecture & Component Specification

This document provides a comprehensive technical reference for the system architecture, component breakdown, view hierarchy, event lifecycles, and design decisions of **Chess 4 Board** (`com.lastersoft.chess4board`).

---

## 1. High-Level System Architecture

```mermaid
flowchart TD
    subgraph BoardHardware ["Board Digital Tabletop Device"]
        TouchScreen["Large Format Touchscreen Display"]
        BoardOS["Board Android OS (co.harrishill.board.*)"]
        BoardOverlay["SystemOverlayService & Pause Overlay"]
        BoardSession["SessionManagerBridge"]
    end

    subgraph GodotHost ["Godot 4.7 Host Application (chess-4-board)"]
        RootScene["chess.tscn / root.gd"]
        SplashTimer["3-Second Custom Splash Screen"]
        NativeLibs["libboard.so / libnativeBoardSDK.so"]
        GodotSignals["Signal: quit_app_requested"]
    end

    subgraph NativePlugin ["Native Android Plugin (GodotWebView AAR)"]
        Coordinator["GodotWebView.java\n(Facade & Coordinator)"]
        
        subgraph Subsystems ["Modular Subsystems"]
            Bridge["BoardBridge.java\n(Board OS Reflection)"]
            Secondary["SecondaryTabManager.java\n(Popup Tab & Multi-Window)"]
            DismissBtn["FloatingDismissButton.java\n(Draggable ✕ with Auto-Fade)"]
            MenuCard["MenuOverlayView.java\n(2x Scaled Tabletop Menu)"]
            UA["UserAgentManager.java\n(Desktop/Mobile UA & Prefs)"]
            Display["DisplayController.java\n(Zoom & 4-Way 90° Rotation)"]
            Catalog["AppCatalog.java\n(Sites & Categorized Apps)"]
            TouchHelper["ChessTouchHelper.java\n(Sidebar Touch & Flyout Bridge)"]
        end
    end

    subgraph ChromiumLayer ["Chromium WebView Engine"]
        PrimaryWeb["Primary WebView\n(Active Chess Match)"]
        PopupWeb["Secondary popupWebView\n(External Links / Popups)"]
    end

    TouchScreen --> BoardOS
    BoardOS --> BoardOverlay
    BoardOverlay <--> Bridge
    BoardSession <--> Bridge
    
    RootScene --> Coordinator
    Coordinator --> Bridge
    Coordinator --> Secondary
    Coordinator --> DismissBtn
    Coordinator --> MenuCard
    Coordinator --> UA
    Coordinator --> Display
    Coordinator --> Catalog
    Coordinator --> TouchHelper

    Secondary --> PopupWeb
    Coordinator --> PrimaryWeb
    TouchHelper -. Injects Bridge .-> PrimaryWeb
    TouchHelper -. Injects Bridge .-> PopupWeb
    Coordinator -. Emits .-> GodotSignals
    Bridge -. Terminates .-> BoardSession
```

---

## 2. Core Architectural Subsystems

The native plugin lives in package `com.lastersoft.godotwebview` and is modularized into 9 cohesive classes:

| Class | Responsibility | Key Collaborators / Dependencies |
|---|---|---|
| [`GodotWebView.java`](file:///Users/adamlaster/Development/BoardGames/Chess4Board/godot-webview/GodotWebView/GodotWebView/src/main/java/com/lastersoft/godotwebview/GodotWebView.java) | **Plugin Facade & Coordinator:** Extends `GodotPlugin`, exposes `@UsedByGodot` entry points (`open`, `close`, `show_menu`, `prepareBoardTouchPassthrough`), binds primary `WebViewClient` and `WebChromeClient`, and manages top-level view lifecycle. | Coordinates all component managers; interacts directly with Godot Engine. |
| [`BoardBridge.java`](file:///Users/adamlaster/Development/BoardGames/Chess4Board/godot-webview/GodotWebView/GodotWebView/src/main/java/com/lastersoft/godotwebview/BoardBridge.java) | **Board OS Hardware Bridge:** Reflection-based integration with `co.harrishill.board.*`. Sets `BoardNativePlugin.setSwallowSystemTouches(false)` to preserve standard touch input, hooks `PauseResultCallbackImpl` to intercept system pause menu actions, registers `"Chess 4 Board Menu"` action context, and invokes `SessionManagerBridge.terminateApplication()` on exit. | `co.harrishill.board.core.BoardNativePlugin`, `co.harrishill.board.pausescreen.PauseResultCallbackImpl`, `co.harrishill.board.session.SessionManagerBridge` |
| [`SecondaryTabManager.java`](file:///Users/adamlaster/Development/BoardGames/Chess4Board/godot-webview/GodotWebView/GodotWebView/src/main/java/com/lastersoft/godotwebview/SecondaryTabManager.java) | **Multi-Window Popup Subsystem:** Manages the isolated popup `WebView` container (`popupContainer`), progress bar, and WebChromeClient / WebViewClient event hooks. Catches `target="_blank"`, `window.open()`, and outbound links clicked during a chess game without reloading or disconnecting the primary game underneath. | `FloatingDismissButton`, `MenuOverlayView`, `UserAgentManager`, `DisplayController`, `ChessTouchHelper` |
| [`FloatingDismissButton.java`](file:///Users/adamlaster/Development/BoardGames/Chess4Board/godot-webview/GodotWebView/GodotWebView/src/main/java/com/lastersoft/godotwebview/FloatingDismissButton.java) | **Ergonomic Floating Controls:** 58dp circular draggable action button (`✕`). Uses `ViewConfiguration.getScaledTouchSlop()` to differentiate taps from drags, clamps movement within container margins, implements a 4-second idle auto-fade (`35%` opacity) to prevent obscuring game content, and triggers secondary tab teardown on tap. | Android `MotionEvent`, `FrameLayout` |
| [`MenuOverlayView.java`](file:///Users/adamlaster/Development/BoardGames/Chess4Board/godot-webview/GodotWebView/GodotWebView/src/main/java/com/lastersoft/godotwebview/MenuOverlayView.java) | **2x Tabletop Menu Interface:** 2x scaled centered card (`menuCard`) styled in dark acrylic (`#F01A1A1A`) with outside-click dismiss scrim (`scrimView`). Houses quick chess buttons, collapsible extra apps accordion, bottom control row (`[ ⬅ ] [ － ] [ ＋ ] [ ↺ ] [ 🖥/📱 ] [ 90° ] [ ⬇ ]`), and the safety exit slider. | `AppCatalog`, `UserAgentManager`, `MenuActionListener` |
| [`UserAgentManager.java`](file:///Users/adamlaster/Development/BoardGames/Chess4Board/godot-webview/GodotWebView/GodotWebView/src/main/java/com/lastersoft/godotwebview/UserAgentManager.java) | **User-Agent Management & Persistence:** Manages Desktop (`🖥`) vs. Mobile (`📱`) modes with persistent storage in `SharedPreferences` (`chess4board_prefs` / `default_desktop_mode`). Rewrites YouTube query parameters (`app=desktop` / `app=mobile`), styles the toggle button, and displays the acknowledgement dialog. | `SharedPreferences`, `WebSettings`, `AlertDialog` |
| [`DisplayController.java`](file:///Users/adamlaster/Development/BoardGames/Chess4Board/godot-webview/GodotWebView/GodotWebView/src/main/java/com/lastersoft/godotwebview/DisplayController.java) | **Zoom Stepping & Tabletop Orientation:** Implements 5-level step zoom (`75%`, `100%`, `125%`, `150%`, `200%`) applied via `setTextZoom` + CSS `document.documentElement.style.zoom`. Cycles clockwise through 4 tabletop orientations (`Landscape` → `Portrait` → `Reverse Landscape` → `Reverse Portrait`) with long-press reset to Landscape, scheduling layout invalidation and resize events. | Android `ActivityInfo`, `Configuration`, `WebView` |
| [`ChessTouchHelper.java`](file:///Users/adamlaster/Development/BoardGames/Chess4Board/godot-webview/GodotWebView/GodotWebView/src/main/java/com/lastersoft/godotwebview/ChessTouchHelper.java) | **Chess.com Desktop Sidebar Touch Bridge:** Injects client-side event interception into Chess.com to resolve desktop hover/touch mismatch. Always cancels default navigation on category links (`e.preventDefault()`, `stopPropagation()`) to eliminate premature redirects to `/play` or `/puzzles`, mounts flyout submenus via synthetic `mouseenter` (buttons: 0) and focus tracking, blocks `mouseleave`/`focusout` in capture phase to prevent premature unmount on finger lift, and supports toggle-to-close and natural sub-item navigation. | Android `WebView` evaluation, Chess.com DOM architecture |
| [`AppCatalog.java`](file:///Users/adamlaster/Development/BoardGames/Chess4Board/godot-webview/GodotWebView/GodotWebView/src/main/java/com/lastersoft/godotwebview/AppCatalog.java) | **Application & Category Registry:** Immutable registry of primary chess sites (`MAIN_SITES`) and categorized extra applications (`EXTRA_CATEGORIES` across Games, Video, and Web). Ready for future runtime persistence. | Data models `SiteEntry` and `CategoryEntry` |

---

## 3. View Hierarchy & Z-Ordering

All views are hosted within a single root `FrameLayout` added to the Godot `Activity`. The Z-order layering guarantees that controls, popups, and scrims stack correctly without intercepting underlying touch events when hidden:

```text
Root FrameLayout (Activity Root)
 │
 ├── [Layer 0] Primary WebView (Match Parent)
 │     └── Hardware-accelerated Chromium rendering surface (active game)
 │
 ├── [Layer 1] Primary ProgressBar (Top, 4dp height, #43A047 green)
 │     └── Visible only during page loading (WebChromeClient.onProgressChanged)
 │
 ├── [Layer 2] Secondary Tab Container (FrameLayout, popupContainer)
 │     │   (Injected below Scrim & Menu so menus can open on top of popups)
 │     ├── Secondary popupWebView (Match Parent)
 │     ├── Secondary popupProgressBar (Top, 4dp)
 │     └── popupCustomView (Fullscreen video playback surface, if active)
 │
 ├── [Layer 3] Fullscreen Backdrop Scrim (View, scrimView)
 │     └── Translucent 30% black (#4D000000), dismisses menu on outside click
 │
 ├── [Layer 4] Centered Menu Card (LinearLayout, menuCard)
 │     └── 2x scaled (320dp * 2.0 = 640dp wide), rounded corners (18dp), elevation (12dp)
 │
 ├── [Layer 5] Floating Dismiss Button (Button, floatingButton)
 │     └── 58dp circular button ("✕" with red border when secondary tab is open)
 │
 └── [Layer 6] Primary customView (FrameLayout, if active)
       └── Fullscreen video surface for primary WebView (brings scrim/card/button to front)
```

---

## 4. Key Event Sequences

### 4.1 Secondary Tab Spawning & Dismissal Flow
Protects the active chess game from navigation changes when an external or new-window link is clicked:

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant Primary as Primary WebView
    participant Client as WebChromeClient
    participant SecMgr as SecondaryTabManager
    participant Popup as popupWebView
    participant Fab as FloatingDismissButton

    User->>Primary: Clicks external link / window.open()
    Primary->>Client: onCreateWindow(resultMsg)
    Client->>SecMgr: handleCreateWindow(resultMsg)
    SecMgr->>Popup: Instantiate isolated WebView inside popupContainer
    SecMgr->>Fab: showDismissStyle() (✕ button visible at bottom-right)
    SecMgr->>Client: transport.setWebView(popupWebView) & sendToTarget()
    Popup->>User: Displays secondary site (Primary match intact underneath)
    Fab->>Fab: After 4s idle, auto-fades to 35% opacity

    User->>Fab: Taps ✕ button
    Fab->>SecMgr: closeSecondTab()
    SecMgr->>Popup: stopLoading(), onPause(), loadUrl("about:blank"), destroy()
    SecMgr->>Fab: hide() (canceled animation, GONE)
    SecMgr->>Primary: requestFocus()
    Primary->>User: Returns directly to active chess match
```

---

### 4.2 Board OS System Pause Menu Integration
Hooks into the Board hardware/OS menu overlay via dynamic proxy reflection:

```mermaid
sequenceDiagram
    autonumber
    participant Hardware as Board Hardware Menu Button
    participant OS as Board OS SystemOverlayService
    participant Bridge as BoardBridge
    participant Godot as Godot host (root.gd)
    participant Menu as MenuOverlayView

    Hardware->>OS: User presses system menu button
    OS->>OS: Opens native pause overlay with options:
    Note over OS: 1. Resume<br/>2. Chess 4 Board Menu (Custom Action)<br/>3. Exit to Library
    
    alt User taps "Chess 4 Board Menu"
        OS->>Bridge: PauseResultCallbackImpl.onPauseResult("CUSTOM_ACTION", "open_menu")
        Bridge->>Menu: showMenu() (opens centered 2x menuCard)
    else User taps "Exit to Library"
        OS->>Bridge: PauseResultCallbackImpl.onPauseResult("EXIT_GAME_UNSAVED")
        Bridge->>Godot: emitSignal("quit_app_requested")
        Bridge->>OS: SessionManagerBridge.terminateApplication()
        Bridge->>Bridge: activity.finishAndRemoveTask()
    end
```

---

### 4.3 4-Way Tabletop Orientation Cycling & Re-Pinning
Enables tabletop players seated at any side of the physical table to rotate the viewport:

```mermaid
sequenceDiagram
    autonumber
    participant Player
    participant Menu as MenuOverlayView
    participant Display as DisplayController
    participant Activity as Android Activity
    participant Layout as Root FrameLayout
    participant Fab as FloatingDismissButton

    alt Short Tap on 90°
        Player->>Menu: Tap 90° button
        Menu->>Display: cycleOrientation()
        Display->>Activity: setRequestedOrientation(next in Landscape->Portrait->RevLand->RevPort)
    else Long-Press on 90°
        Player->>Menu: Hold 90° button
        Menu->>Display: resetOrientationToLandscape()
        Display->>Activity: setRequestedOrientation(SCREEN_ORIENTATION_LANDSCAPE)
    end

    Activity->>Layout: Activity reconfigures / layout dimensions change
    Layout->>Fab: onLayoutChange -> pinToBottomRight(newW, newH)
    Display->>Layout: schedulePostRotateRefresh (requests layout, dispatches window resize event)
```

---

## 5. Architectural Design Decisions & Trade-Offs

### 5.1 Reflection for Board SDK Integration
* **Decision:** All interactions with `co.harrishill.board.*` are executed via Java reflection in [`BoardBridge.java`](file:///Users/adamlaster/Development/BoardGames/Chess4Board/godot-webview/GodotWebView/GodotWebView/src/main/java/com/lastersoft/godotwebview/BoardBridge.java) rather than compile-time Gradle dependencies.
* **Rationale:** The Board SDK `.aar` files (`boardNativePlugin-release.aar`, `sessionManager-release.aar`, `systemOverlay-release.aar`) are bundled exclusively within the Godot export template (`chess-4-board/addons/board_sdk/`). Linking them directly into the `GodotWebView` library would create duplicate class collisions at APK packaging time and prevent the AAR from compiling independently. Reflection maintains loose coupling and zero-overhead execution.

### 5.2 Out-of-Tree APK Export Pipeline
* **Decision:** The compiled binary `Chess4Board.apk` is exported by default directly to the out-of-tree sibling directory `../output/Chess4Board.apk` (configurable via `OUTPUT_DIR` in `.env` / `.env.local`).
* **Rationale:** Mandated by workspace hygiene rules ([`AGENTS.md`](file:///Users/adamlaster/Development/BoardGames/Chess4Board/AGENTS.md)). Large 90+ MB APK binaries are kept entirely outside the git workspace tree to prevent repository bloat, while still remaining easily deployable via `board-connect`.

### 5.3 Hardware Touch Passthrough over Raw Input
* **Decision:** Touch input is captured directly by Android's native view hierarchy via Chromium's touch pipeline after disabling Board's touch swallower (`BoardNativePlugin.setSwallowSystemTouches(false)`).
* **Rationale:** Early iterations suffered from swallowed touches or laggy simulated gestures. Disabling touch swallowing allows the Android Chromium `WebView` to receive 100% of raw multi-touch events directly from the Linux input subsystem, enabling buttery-smooth piece dragging, board zooming, and web navigation.

### 5.4 Dual-Layer WebView for Multi-Window Isolation
* **Decision:** Secondary tabs instantiate a brand-new `WebView` inside `popupContainer` rather than navigating the main `WebView`.
* **Rationale:** Chess platforms (such as Chess.com and Lichess) maintain active WebSockets and Web Worker game trees. Navigating away terminates the connection and forfeits the game. An isolated overlay WebView guarantees that game state, clock synchronization, and audio remain undisturbed while checking external links.

---

## 6. Related Documentation

* **[`README.md`](./README.md)** — Project overview, menu controls quick reference, and complete catalog of primary chess suites and extra web applications.
* **[`BUILD_SEQUENCE.md`](./BUILD_SEQUENCE.md)** — Step-by-step breakdown of the Gradle, Godot CLI headless export, and `board-connect` deployment pipeline.
* **[`PROJECT_HISTORY.md`](./PROJECT_HISTORY.md)** — Complete chronological engineering log, architectural decisions log, and future roadmap.
* **[`AGENTS.md`](./AGENTS.md)** — Workspace conventions, canonical root file rules, and build/output hygiene.


