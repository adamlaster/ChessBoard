# ChessBoard (`chessboard` & `godot-webview`) — Project & Session Lifecycle Log

This document tracks the complete architecture, chronological evolution, engineering decisions, and future roadmap for **ChessBoard** (`com.lastersoft.chessboard`) running on the **Board** digital tabletop device.

---

## 1. Project Context & Documentation Index

**ChessBoard** is a custom-engineered web application hub and tabletop gaming portal built for the **Board** hardware device (an Android-based large-format touch tabletop display).

For current technical specifications, controls, and build guides, refer to the canonical root documentation:
* **[`README.md`](./README.md)** — Project overview, quick-start guide, active menu controls reference, and full app catalog.
* **[`ARCHITECTURE.md`](./ARCHITECTURE.md)** — System architecture, modular component specifications, view hierarchy, and Mermaid flowcharts.
* **[`BUILD_SEQUENCE.md`](./BUILD_SEQUENCE.md)** — Detailed 4-step Gradle AAR, Godot CLI headless export, and `board-connect` deployment pipeline.
* **[`AGENTS.md`](./AGENTS.md)** — Always-on workspace rules, canonical root file policy, and build/output hygiene.

---

## 2. Complete Session & Project Chronology

### Phase 1: Codebase Inspection, Missing Imports & Full CLI Build Automation
1. **Initial `GodotWebView.java` Audit:**
   - Inspected `GodotWebView.java` and resolved missing Android/Java imports (`ColorStateList`, `Configuration`, `Typeface`, `MotionEvent`, `ViewConfiguration`, `ProgressBar`, `SignalInfo`, `HashSet`, `Set`) so the plugin could compile cleanly under Gradle + JDK 17.
2. **End-to-End CLI Pipeline (`build_and_deploy.sh`):**
   - Replaced manual Android Studio + Godot IDE export steps with a 100% automated 4-step CLI workflow documented in `BUILD_SEQUENCE.md`:
     1. `./gradlew --no-build-cache clean assemble` in `godot-webview/GodotWebView`
     2. Copy `GodotWebView-debug.aar` & `GodotWebView-release.aar` to `chessboard/addons/webview/`
     3. `/Applications/Godot.app/Contents/MacOS/Godot --headless --path ... --export-debug "Android" ./Chess4Board.apk`
     4. `board-connect install Chess4Board.apk --launch` (uploading over LAN to `10.0.0.65:8843`)

---

### Phase 2: Floating Menu UX Overhaul & Tabletop Ergonomics
1. **Outside-Tap Scrim Dismissal:**
   - Added a full-screen translucent backdrop (`scrimView`, `#4D000000`) behind the menu card so tapping anywhere outside the menu cleanly dismisses it.
2. **Draggable Floating Action Button (`☰`) with Screen Clamping:**
   - Collapsed the multi-button overlay into a single `58dp` circular floating `☰` button (`floatingButton`).
   - Implemented touch-slop detection (`ViewConfiguration.getScaledTouchSlop()`) to distinguish taps from drags, clamping coordinates within screen margins.
3. **Idle Auto-Fade:**
   - Added a 4-second inactivity timer (`idleHandler` / `idleFadeRunnable`) that fades the floating `☰` button to `35%` opacity during gameplay and restores `100%` opacity on touch.
4. **Top Loading Progress Bar:**
   - Added a `4dp` green (`#43A047`) horizontal `ProgressBar` anchored to the top of the viewport (`WebChromeClient.onProgressChanged`).
5. **Tactile "Slide to Exit" Safety Slider:**
   - Iterated from a bottom-row `✕` button to an upper-left corner button, and finally settled on a **Slide to Exit (`✕ Slide to Exit ❯❯❯`)** track at the bottom of the menu card.
   - Requires sliding the red thumb past `80%` of the track width to emit `quit_app_requested`, preventing accidental exits during tabletop play without requiring a two-step confirmation modal.
6. **Orientation Rotation Pinning (`90°`):**
   - Fixed an issue where rotating between Portrait and Landscape left the floating button stranded in the middle of the screen.
   - Added `layout.addOnLayoutChangeListener` and post-rotation snapping so the `☰` button always re-pins cleanly to the bottom-right corner on rotation.

---

### Phase 3: Refresh Controls, Custom Splash Screen & Launcher Branding
1. **Pull-to-Refresh vs. Menu Refresh Button (`↺`):**
   - Experimented with Android's `SwipeRefreshLayout`, which caused gesture conflicts with vertical scrolling and tabletop board dragging.
   - Removed `SwipeRefreshLayout` (and removed the `androidx.swiperefreshlayout` Gradle dependency completely) in favor of a dedicated **`↺`** refresh button in the control row.
   - Updated the rotate button label to bold **`90°`** so it is immediately distinguishable from **`↺`**.
2. **Custom Splash Screen & 3-Second Hold:**
   - Replaced default Godot boot images with the custom photo splash screen (`splash.png`) configured in `project.godot` and `chess.tscn` (`root.gd`), holding the splash image for 3.0 seconds before launching `GodotWebView`.
3. **Custom Pawn App Icon:**
   - Generated Android adaptive foreground/background icons (`icon.png`, `icon_192.png`, `icon_432.png`, `icon_background_432.png`) using the Dark Pawn emblem on a clean white background.

---

### Phase 4: Multi-Game Expansion, WebView Performance & Sideload Discovery
1. **Root Chess Suite:**
   - Configured the primary root menu with 4 core chess platforms:
     - `♟ Chess.com` (`https://www.chess.com`)
     - `♟ ChessKids.com` (`https://www.chesskids.com`)
     - `♟ Lichess` (`https://lichess.org/analysis`)
     - `♟ ChessReps` (`https://chessreps.com`)
   - Hid the legacy Godot debug UI nodes inside `chess.tscn`.
2. **Tabletop Touch Games Curation:**
   - Tested multiple web games for large-screen multi-touch suitability.
   - Added **Colonist.io (Catan)**, **Dots & Boxes**, **PBS Kids**, **247 Backgammon**, **247 Checkers**, and the full **247 Games** portal (`https://www.247games.com`).
   - Removed Euchre (`cardgames.io`) after testing showed poor tabletop UX.
3. **WebView Hardware Acceleration & Rendering Tuning:**
   - Enabled `View.LAYER_TYPE_HARDWARE` on the `WebView`.
   - Enabled `settings.setOffscreenPreRaster(true)` (API 23+) to prevent white flashes during scrolling/zooming.
   - Disabled overscroll glow (`OVER_SCROLL_NEVER`) and scrollbars.
   - Flushed `CookieManager` on `onPageFinished` so logins and game states persist reliably across sessions.
4. **Board OS Native SDK Sideload Discovery:**
   - Attempted to sideload a standalone open-source Checkers APK via `board-connect install`, which failed with:
     `[validation_failed] missing Board SDK native library (expected lib/<abi>/libboard.so or libnativeBoardSDK.so)`.
   - Confirmed that hosting web apps and games inside `Chess4Board.apk` (which bundles the Board SDK native library) is the ideal architectural path for expanding functionality on the device.

---

### Phase 5: Video Streaming Hub, User-Agent Spoofing & HTML5 Fullscreen
1. **Video Streaming Services & DRM Support:**
   - Added **YouTube** (`https://www.youtube.com`), **Peacock** (`https://www.peacocktv.com`), and **Fox One** (`https://www.fox.com`).
   - Implemented `WebChromeClient.onPermissionRequest` to automatically grant `PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID` for Encrypted Media Extensions (EME / Widevine DRM).
   - Enabled `settings.setMediaPlaybackRequiresUserGesture(false)`.
2. **Native App Intent Blocking:**
   - Updated `shouldOverrideUrlLoading` to intercept non-HTTP/HTTPS schemes (`intent://`, `vnd.youtube://`, `market://`), extract `browser_fallback_url` when available, and block native app launch errors (`ERR_UNKNOWN_URL_SCHEME`).
3. **On-Demand Mobile / Desktop User-Agent Toggle (`📱` / `🖥`):**
   - Added a toggle button in the controls row (`[ ⬅ ] [ － ] [ ＋ ] [ ↺ ] [ 📱/🖥 ] [ 90° ] [ ⬇ ]`).
   - Defaults to **Mobile Mode (`📱`)** and automatically reverts to **Mobile Mode** whenever switching sites via `navigateToSite()`.
   - Handles YouTube's sticky `PREF` cookie by appending `?app=desktop` when toggling to Desktop (`🖥`) and `?app=mobile` when toggling or resetting to Mobile (`📱`).
4. **HTML5 Fullscreen Video Support:**
   - Implemented `onShowCustomView` and `onHideCustomView` in `WebChromeClient` (along with `exitCustomFullscreen()`) to attach/detach Chromium's fullscreen video surface inside `layout` while keeping `floatingButton`, `scrimView`, and `menuCard` layered on top.

---

### Phase 6: Consolidated Categorized "Extra Apps" Menu (`MLB.TV` & `Google`) & Back Navigation (`⬅`)
1. **Consolidated Categorized Menu (`🗂️ Extra Apps`):**
   - Replaced the separate top-level `Extra Games` and `Video` buttons on the root menu card with a single collapsible **`🗂️  Extra Apps (11)  ▾`** menu button backed by a structured `List<CategoryEntry>` (`EXTRA_CATEGORIES`).
   - Inside `Extra Apps`, categories render as sleek accordion headers that expand/collapse their apps on tap:
     - **`🎲  Games (6)`**: Colonist.io (Catan), Dots & Boxes, PBS Kids, 247 Backgammon, 247 Checkers, 247 Games (All)
     - **`📺  Video (4)`**: YouTube, Peacock, Fox One, **MLB.TV** (`https://www.mlb.com/tv`)
     - **`🌐  Web (1)`**: **Google** (`https://www.google.com`)
   - Implemented an adaptive height cap (`onMeasure` `AT_MOST` `240dp`) on `extraAppsScrollView` so the menu stays compact when categories are collapsed and scrolls smoothly when large categories are expanded.
2. **Back Navigation Button (`⬅`):**
   - Added a **`⬅`** Back button at the start of the controls row (`[ ⬅ ] [ － ] [ ＋ ] [ ↺ ] [ 📱/🖥 ] [ 90° ] [ ⬇ ]`).
   - Automatically dims (`35%` alpha) when `webView.canGoBack()` is false; navigates back in history (or exits fullscreen video if active) when tapped.
3. **Immediate "Closing..." Slide-to-Exit Feedback & Fast Shutdown:**
   - As soon as the slider reaches `92%` during a drag (or is released past `80%`), the thumb locks at the right end with a **`✓`** icon, the track turns solid red (`#C62828`), the label displays **`Closing...`** in bold white, the scrim darkens to `90%` black, and `webView.onPause()` halts media playback immediately.
   - Restored `Engine.max_fps = 60` inside `_on_quit_app_requested()` in `root.gd` so Godot processes `get_tree().quit()` immediately without waiting up to 1 second on the `Engine.max_fps = 1` background throttle.

---

### Phase 7: Package Rename, 2x Tabletop Menu Scaling, Custom Photo App Icon & Board SDK Integration Fix
1. **Package Rename (`com.lastersoft.godotwebview`):**
   - Migrated the entire Android plugin package from `com.bsmx.godotwebview` to `com.lastersoft.godotwebview` across `GodotWebView.java`, `build.gradle`, `AndroidManifest.xml`, and `plugin.cfg`.
   - Added a `JavaCompile` exclusion rule (`exclude 'com/bsmx/**'`) in `build.gradle` to guard against stale IDE tab re-saves.
2. **2x Opened Menu Card (`MENU_SCALE = 2.0f`) & Tighter Slide-to-Exit Threshold:**
   - Doubled the dimensions and typography of the opened `menuCard` and all internal buttons/controls (`640dp` wide card, `88dp` site buttons, `74dp` circular controls, `92dp` slide track) for effortless tabletop visibility, while keeping the closed floating `☰` button at `58dp`.
   - Tightened the Slide-to-Exit trigger threshold (`progress >= 0.985f` on drag, `releaseProgress >= 0.95f` on release) to match the wider 2x track.
3. **App Icon Restoration (Dark Pawn):**
   - Tested a custom photo launcher icon (`icon.png`, `icon_192.png`, `icon_432.png`), then restored the crisp **Dark Pawn on white** adaptive icons (`icon.png`, `icon_192.png`, `icon_432.png`, `icon_background_432.png`).
4. **Board SDK (`https://docs.dev.board.fun/`) Touch Passthrough & "Exit to Library" Fix:**
   - **Touch Passthrough:** Configured `BoardNativePlugin.setSwallowSystemTouches(false)` and `BoardNativePlugin.nativeLibLoaded = false` in `GodotWebView.prepareBoardTouchPassthrough()` so `Board.initialize(...)` binds to `SystemOverlayService` without attaching `touchSwallower` or starting `RawDataGlyphDetector` (preserving 100% standard Android touch in the `WebView`).
   - **Async Pause Context Registration:** Fixed a race condition where `Board.pause.set_context(...)` was called before `SystemOverlayService` finished binding (`are_services_ready()`), ensuring the Board pause callback is always registered.
   - **Direct Java-Side "Exit to Library" Handler:** Hooked `PauseResultCallbackImpl` on the Java side so tapping **"Exit to Library"** on the Board's system menu immediately calls `SessionManagerBridge.terminateApplication()` (`ISystemOverlayService.terminateApp()`) without waiting on Godot's 1-FPS throttled main loop.
   - **Hidden Custom Slide-to-Exit Bar:** Set `slideTrack.setVisibility(View.GONE)` now that the native Board system menu handles exiting cleanly.

---

### Phase 8: Board System "Web Menu" Button, Desktop Default UA, 4-Way Rotation & Expanded Web Apps
1. **Board System Menu Custom "Web Menu" Button & Removal of Floating `☰` Button:**
   - Added a custom button (`{ "id": "open_menu", "title": "Web Menu", "icon": Board.pause.ICON_SQUARE }`) to the Board OS pause overlay via `Board.pause.set_context(...)` in `root.gd` (and fallback registration in `GodotWebView.ensureBoardPauseContextRegistered()`).
   - Hooked `PauseResultCallbackImpl.getCustomButtonId()` alongside `getActionType()` in `GodotWebView.hookBoardPauseListener()` so tapping **"Web Menu"** on the Board system menu immediately dismisses the Board overlay and opens our `menuCard` centered on screen (`runOnUiThread(this::showMenu)`).
   - Hid the custom floating `☰` button (`floatingButton.setVisibility(View.GONE)`) and updated `repositionMenuCard()` to center `menuCard` on the display.
2. **Desktop User-Agent by Default + 3-Second Long-Press Default Saver & Ack Dialog:**
   - Changed the default User-Agent mode to **Desktop (`🖥`)**, persisted in `SharedPreferences` (`chess4board_prefs` / `default_desktop_mode`).
   - **Short tap on `🖥` / `📱`:** Toggles User-Agent for the current site only (reverting to the saved default when navigating to another site).
   - **3-second long-press on `🖥` / `📱`:** Toggles and saves the new global default User-Agent in `SharedPreferences` and pops a dark-themed confirmation modal (`showDefaultUaAckDialog`).
3. **4-Way `90°` Tabletop Orientation Cycle + Long-Press Reset:**
   - Updated the `90°` button (`ORIENTATION_CYCLE`) to step clockwise through all 4 tabletop orientations: `SCREEN_ORIENTATION_LANDSCAPE` → `SCREEN_ORIENTATION_PORTRAIT` → `SCREEN_ORIENTATION_REVERSE_LANDSCAPE` → `SCREEN_ORIENTATION_REVERSE_PORTRAIT`.
   - **Long-pressing `90°`** immediately resets the screen back to the default `SCREEN_ORIENTATION_LANDSCAPE` (index `0`) with no dialog required.
4. **Expanded Web Category (`Google Photos`, `Google Maps`, `Reddit`, `Wikipedia`, `Weather`) & Streaming Additions (`Disney+`, `Prime Video`):**
   - Expanded `Extra Apps` to **18 apps** across `Games (6)`, `Video (6)`, and `Web (6)`.

---

### Phase 9: Godot Resource Cleanup, Canonical Root Docs/Script & "Chess 4 Board Menu" Rename
1. **Removed Unused Godot Resources in `chessboard`:**
   - Deleted legacy test scripts and `.uid` sidecars (`app.gd`, `button_2.gd`, `button_3.gd`, `button_test.gd`, `panel_container.gd`, `v_box_container.gd`).
   - Removed unused icon and import files (`icon.svg`, `icon_square.png`, `splash_download.png.import`) and orphaned `.godot/imported/` cache entries.
   - Removed unused `assets/models/model.tflite` (~952 KB), as `Chess 4 Board` uses touch passthrough rather than piece glyph detection.
2. **Consolidated Single Canonical `README.md`, `AGENTS.md`, `BUILD_SEQUENCE.md`, `PROJECT_HISTORY.md`, and `build_and_deploy.sh` in Root (`/Users/adamlaster/Development/BoardGames/Chess4Board/`):**
   - Moved `build_and_deploy.sh` to the repository root (`/Users/adamlaster/Development/BoardGames/Chess4Board/build_and_deploy.sh`) and removed duplicate scripts from `chessboard/` and `godot-webview/`.
   - Consolidated `README.md`, `AGENTS.md`, `BUILD_SEQUENCE.md`, and `PROJECT_HISTORY.md` exclusively in the repository root and removed all duplicate copies in `chessboard/`, `godot-webview/`, and `Documents/AI_Markdown/`.
3. **Renamed Board System Menu Button to "Chess 4 Board Menu":**
   - Updated the custom Board OS pause menu button title from `"Web Menu"` to `"Chess 4 Board Menu"` in both `root.gd` and `GodotWebView.ensureBoardPauseContextRegistered()`.
4. **Unified Root `.gitignore` & Git Index Cleanup:**
   - Created a single canonical root `.gitignore` covering macOS (`.DS_Store`), Godot 4+ (`.godot/`, `chessboard/android/`), Android/Gradle (`.gradle/`, `**/build/`, `local.properties`), IDEs (`.idea/`, `.vscode/`), and packaged binaries (`*.apk`, while preserving `chessboard/addons/**/*.aar`).
   - Removed duplicate sub-`.gitignore` files and untracked previously committed `.DS_Store`, `.gradle/`, `.idea/`, `.vscode/`, and `build/` cache files from the git index.

---

### Phase 10: Multi-Window Second Tab Support & Draggable Floating Dismissal (`✕`)
1. **Isolated Multi-Window Secondary Tab Container:**
   - Enabled `setSupportMultipleWindows(true)` and `setJavaScriptCanOpenWindowsAutomatically(true)` on the primary `WebView`.
   - Implemented `WebChromeClient.onCreateWindow(...)` and `onCloseWindow(...)` to catch `target="_blank"`, `window.open()`, and external links clicked during a game.
   - Dynamically instantiates an isolated `popupWebView` inside a `popupContainer` overlay, keeping the primary Chess.com game alive underneath without page reload, websocket disconnect, or loss of game state.
2. **Draggable Floating Dismiss Action Button (`✕`):**
   - Repurposed the floating button paradigm to display a sleek `58dp` circular **`✕`** dismiss button (dark circle with `#E53935` red border) whenever a secondary tab is active.
   - Retained full touch-slop dragging and boundary margin clamping so players can reposition the `✕` button anywhere on the 24-inch tabletop to avoid covering content.
   - Retained 4-second idle auto-fade (`35%` opacity) to prevent distraction during reading/viewing, snapping back to `100%` on touch.
   - Tapping `✕` immediately tears down `popupWebView` (`stopLoading()`, pauses audio/media, destroys WebView), hides the floating button, and restores focus to the primary game.
3. **Controls & History Synchronization:**
   - Updated `updateBackButtonState()` and `backButton` (`[ ⬅ ]`) to step back in the secondary tab's browsing history while it is active.
   - Updated `refreshButton` and step zoom (`applyCurrentZoom`) to target the secondary tab when open.

---

### Phase 11: Modular Architectural Refactoring & Plugin Decomposition
1. **Decomposed Monolithic `GodotWebView.java`:**
   - Decomposed the 1,850-line monolithic Godot plugin into single-responsibility, cohesive classes under `com.lastersoft.godotwebview`:
     - **`AppCatalog.java`**: Encapsulates primary chess platforms (`MAIN_SITES`), categorized extra applications (`EXTRA_CATEGORIES`), and data models (`SiteEntry`, `CategoryEntry`).
     - **`BoardBridge.java`**: Encapsulates all reflection-based communication with the native Board SDK (`co.harrishill.board.*`), touch passthrough setup, pause menu action hooking (`PauseResultCallbackImpl`), pause context registration (`setPauseContext`), and clean session termination (`SessionManagerBridge`).
     - **`UserAgentManager.java`**: Manages Desktop vs. Mobile User-Agent state, `SharedPreferences` persistence (`chess4board_prefs` / `default_desktop_mode`), YouTube query parameter formatting (`app=desktop` / `app=mobile`), button styling, and default confirmation dialog.
     - **`DisplayController.java`**: Encapsulates step zoom stepping (`75%` to `200%`), 4-way 90° orientation cycling (`ORIENTATION_CYCLE`), long-press reset to Landscape, and layout resize invalidation.
     - **`FloatingDismissButton.java`**: Encapsulates the 58dp circular draggable action button (`✕`), touch-slop dragging, screen edge clamping, 4-second idle auto-fade (`35%` opacity), and tap handling.
     - **`SecondaryTabManager.java`**: Encapsulates isolated popup `WebView` lifecycle, multi-window interception (`onCreateWindow` / `onCloseWindow`), custom fullscreen video management, progress bar, and teardown.
     - **`MenuOverlayView.java`**: Encapsulates the 2x scaled centered menu card, outside-tap scrim backdrop, quick site buttons, collapsible category accordion, bottom control row, and slide-to-exit slider.
     - **`GodotWebView.java`**: Retained as the lightweight coordinator/facade (~450 lines) implementing `@UsedByGodot` entry points (`open`, `close`, `show_menu`, `prepareBoardTouchPassthrough`) and Godot signal registration (`quit_app_requested`).
2. **Build Verification & Field Deployment:**
   - Compiled via Gradle (`./gradlew --no-build-cache assembleDebug`) with zero errors.
   - Headlessly exported `Chess4Board.apk` via Godot CLI and deployed/launched on the Board hardware tabletop display via `./build_and_deploy.sh`.

---

### Phase 12: Chess.com Desktop Sidebar Touch Ergonomics & Flyout Bridge
1. **Desktop Sidebar Touch/Hover Mismatch Diagnosis:**
   - In Desktop User-Agent mode, Chess.com's left navigation sidebar uses `mouseenter` hover events (`Pw.mountSubMenu`) to reveal flyout submenus (`Play Online`, `Computer`, `Tournaments`, etc.).
   - On physical touchscreens, tapping a category item synthesizes an immediate `click` on the underlying `<a href="...">` tag, which causes the browser to instantly hotlink away from the active chess match without revealing the flyout submenu. Furthermore, `Pw.mountSubMenu` rejects events with `e.buttons > 0`, and unmounts after 100ms when `:hover` is lost upon finger release.
2. **Implementation & Refinement of `ChessTouchHelper.java`:**
   - Created [`ChessTouchHelper.java`](file:///Users/adamlaster/Development/BoardGames/Chess4Board/godot-webview/GodotWebView/GodotWebView/src/main/java/com/lastersoft/godotwebview/ChessTouchHelper.java) in package `com.lastersoft.godotwebview`.
   - Injects a client-side bridge on Chess.com page loads that:
     - Intercepts `click` events in the capture phase on desktop sidebar category wrappers (`[data-interaction="play"]`, `puzzles`, `learn`, `watch`, `community`, `other`, `train`).
     - **Root Redirect Elimination:** Always cancels default `<a>` tag navigation on category items via `e.preventDefault()`, `stopPropagation()`, and `stopImmediatePropagation()`, ensuring tapping "Play" / "Puzzles" / "Learn" never prematurely redirects the browser away to `/play` or `/puzzles`.
     - **Toggle & Hover Flyout Ergonomics:** Tapping an unopened category mounts the flyout submenu via synthetic `mouseenter` (`buttons: 0`) and focus tracking (`tabElement.contains(document.activeElement)`); tapping the same open category a second time toggles it closed cleanly.
     - **Premature Unmount Suppression:** Intercepts and blocks `mouseleave` and `focusout` events on `#sidebar-main-menu` in the capture phase while a submenu is active, preventing Android WebView finger lifting or focus shifts from causing Chess.com's 100ms `performDelayedUnmount` timer to unmount the menu before the user can tap.
     - **Natural Sub-Item Navigation:** Sub-item clicks inside `[data-interaction-mount-point]` (e.g. *Play Online*, *Bots/Computer*, *Tournaments*) and the top header logo are passed through unimpeded.
     - **Fast Outside Dismissal:** Tapping anywhere outside the sidebar (e.g. the chessboard or background) dismisses the flyout submenu immediately.
3. **Build & Field Deployment:**
   - Integrated into both primary `WebView` and `SecondaryTabManager` page finish listeners.
   - Verified clean compilation and deployed to the Board hardware display via `./build_and_deploy.sh`.

---

### Phase 13: Local Environment Portability & Configuration System
1. **Seamless Developer Onboarding & Zero-Friction Portability:**
   - Introduced [`.env.example`](./.env.example) configuration template and enabled automatic loading of `.env` and `.env.local` in [`build_and_deploy.sh`](./build_and_deploy.sh).
   - Updated [`.gitignore`](./.gitignore) to ignore `.env`, `.env.local`, and `.env.*.local` so developer-specific path overrides are never accidentally committed.
   - Enhanced [`build_and_deploy.sh`](./build_and_deploy.sh) with robust auto-detection for `JAVA_HOME` (Apple Silicon & Intel Homebrew OpenJDK 17, Android Studio JBR, Linux OpenJDK, `/usr/libexec/java_home`), `ANDROID_HOME` (`~/Library/Android/sdk`, `~/Android/Sdk`), `GODOT_BIN`, and `BOARD_CONNECT_BIN`.
   - Preserves exact existing default paths (`../output/Chess4Board.apk`) for zero-change execution on current systems, while allowing full customization via `.env`.
2. **Environment Doctor Diagnostic Mode:**
   - Added `./build_and_deploy.sh --doctor` (or `--check`) to inspect local toolchains, SDKs, Godot binary, and target output directories with visual indicators and actionable installation guidance.
3. **Target Device Parameterization:**
   - Added `--device <target>` / `-d <target>` flag to [`build_and_deploy.sh`](./build_and_deploy.sh) for deploying to a specific Board IP or hostname on LAN.
4. **Documentation Synchronization:**
   - Updated [`README.md`](./README.md), [`BUILD_SEQUENCE.md`](./BUILD_SEQUENCE.md), [`ARCHITECTURE.md`](./ARCHITECTURE.md), and [`AGENTS.md`](./AGENTS.md) with environment customization workflows and doctor references.

---

### Phase 14: Accidental Menu Drag Dismissal Prevention
1. **Root Cause Analysis:**
   - Previously, the fullscreen backdrop `scrimView` used a standard Android `OnClickListener`. On Android touchscreens, dragging a finger across the screen still triggers `onClick()` upon release as long as `ACTION_UP` occurs within the view bounds (which covers the entire display).
   - In addition, touches landing on empty space or card padding within `menuCard` were not consumed by `menuCard`, causing them to fall through to `scrimView` underneath and dismiss the menu on finger lift.
2. **Implementation in `MenuOverlayView.java`:**
   - **Touch-Slop Tap Filtering:** Replaced `scrimView.setOnClickListener` with an `OnTouchListener` utilizing `ViewConfiguration.get(activity).getScaledTouchSlop()`. Tracks pointer displacement (`Math.hypot(dx, dy) > touchSlop`) to distinguish intentional taps from drags or swipes. Only genuine taps outside the menu dismiss the overlay; drags are completely ignored.
   - **Menu Card Touch Consumption:** Attached `menuCard.setOnTouchListener((v, event) -> true)` and `slideTrack.setOnTouchListener((v, event) -> true)` to consume all unhandled touches that land anywhere on the menu card, preventing touches, scrolling, or dragging from falling through to the backdrop scrim.
   - Preserves explicit dismissal via the `⬇` button and intentional single-taps outside the card.

---

### Phase 15: Two-Finger Right-Click Touch Gesture for Arrow Drawing & Square Highlighting
1. **Problem Analysis:**
   - Tabletop players using the Board hardware display wanted to draw tactical arrows and highlight squares on Chess.com (and Lichess) during analysis and game review.
   - On desktop browsers, arrows and square highlights require a mouse right-click and drag (`button: 2`, `buttons: 2`). Android WebView does not synthesize right-clicks from multi-touch natively; instead, Chromium interprets two fingers as pinch-to-zoom or page scrolling.
   - Analysis of Chess.com's client bundles (`shared.eager.chunk.js`) revealed that the chessboard web component (`<wc-chess-board>`) listens for `pointerdown`, `pointermove`, and `pointerup` events, entering `AddingMarkings` state when `e.which === 3` (`button: 2`).
2. **Implementation in `ChessTouchHelper.java` (`CHESS_ARROW_TOUCH_JS`):**
   - **Board Target Detection:** Detects touches occurring on `<wc-chess-board>`, `<chess-board>`, `<cg-board>`, `#board-layout-chessboard`, `#board-single`, or `.board`. Includes a 40px bounding box safety margin so edge squares (a/h files, 1st/8th ranks) trigger reliably.
   - **Hand Proximity Threshold (`MAX_FINGER_SPREAD = 600px`):** Accommodates broad two-finger hand placement across multiple squares on the 24" display while filtering opposing player multi-touch.
   - **First-Touch Buffering (Additive Arrows & Piece Drag Prevention):**
     - Analysis of Chess.com's state machine revealed that any native `PointerdownLeft` event instantly executes `markings.removeAllWhere()`, wiping out all existing markings, and grabs any piece under the touch. Because two fingers naturally touch down 20-50ms apart, Finger 1's premature unbuffered left-click was erasing previous arrows and grabbing pieces.
     - Added a 70ms first-touch buffer on the chessboard: when Finger 1 lands, its `pointerdown` is held. If Finger 2 lands within 70ms, Finger 1's left-click is completely discarded without ever reaching `<wc-chess-board>`, preserving existing arrows and preventing piece grabs. If no second finger lands (genuine single-finger interaction), or if the finger moves > 12px or lifts, the buffered `pointerdown` flushes instantly, preserving 100% native piece movement and tap responsiveness.
   - **Synthetic Right-Click Translation:**
     - On two-finger detection: Dispatches synthetic `pointerdown` and `mousedown` with `button: 2`, `buttons: 2`, `which: 3`, `pointerId: 1`, and `pointerType: 'mouse'` at the midpoint between fingers.
     - On `touchmove`: Tracks the midpoint, dispatches synthetic `pointermove` with `button: 2`, `buttons: 2`, `which: 3` to the board element and `document`, and suppresses browser scrolling/pinch-zoom.
     - On `touchend`: When either finger lifts, dispatches synthetic `pointerup` (`button: 2`, `buttons: 0`, `which: 3`) and `contextmenu` to commit the arrow (or square highlight if released on the start square). Enters suppression mode until all touches lift (`e.touches.length === 0`), and blocks lingering synthetic clicks for 500ms so arrows are never erased on release.
   - **Defensive Pointer Capture:** Intercepts `Element.prototype.setPointerCapture` and `releasePointerCapture` to swallow `DOMException: InvalidPointerId` errors when client code captures synthetic pointer events.
   - **Additive Tactical Arrows:** Allows multiple arrows and square highlights to be drawn sequentially on the board without previous lines disappearing. Markings persist until cleared by a single-finger tap or piece move.
3. **Build & Field Verification:**
   - Unified injection under `ChessTouchHelper.injectTouchEnhancements()`, called in both primary `GodotWebView` and `SecondaryTabManager`.
   - Built and deployed to the Board hardware display via `./build_and_deploy.sh`.

---

### Phase 16: Chess.com Bot Engine Out-of-Memory Fix via On-The-Fly WebAssembly Memory Section Patching
1. **Problem Analysis:**
   - When attempting to play bots or Coach matches on Chess.com (`/play/computer` or `/play/coach`), the page failed to start the game with the error alert: *"Couldn't load the bot engine. Please check your connection and reload the page."*
   - Inspection of Android logcat on the Board hardware revealed a critical V8 WebAssembly engine crash:
     `CEE wasm streaming compile failed, falling back to a buffered fetch: RangeError: WebAssembly.instantiate(): Out of memory: Cannot allocate Wasm memory for new instance`
     Source: `https://www.chess.com/r2/assets-chess-engine/explanation-engine/1.130.53/default/explanation-engine.wasm`
   - When CEE (Chess Explanation Engine / Komodo TEP) fails to instantiate, Chess.com sets `Qu.ceeLoadFailure = 'engine'`, triggering the failure modal and refusing to start the match.
2. **Reverse Engineering the WebAssembly Binary:**
   - Downloaded and parsed `explanation-engine.wasm` (27.7 MB).
   - Inspected Section 5 (Memory Section) at file offset 21,902:
     - Declared `initial pages: 6,176 (386.0 MB)` and `maximum pages: 32,768 (2048.0 MB)`.
   - Inspected Section 11 (Data Section): The maximum data segment offset is only **12.14 MB (195 pages)**! The engine was compiled with an excessively large default initial memory footprint (`-sINITIAL_MEMORY=404750336`).
   - On desktop 64-bit Chrome, 386 MB contiguous virtual memory plus multi-gigabyte guard pages allocate without incident. On Android WebView's 32/64-bit hybrid memory space, V8 cannot satisfy this virtual memory allocation, resulting in a fatal `RangeError: Out of memory`.
   - Because CEE runs in a dedicated `Worker`, JavaScript monkey-patching via `evaluateJavascript` cannot reach `self.WebAssembly`.
3. **Implementation in `WasmPatchHelper.java`:**
   - **Network-Level Interception:** Hooked `WebViewClient.shouldInterceptRequest()` in both `GodotWebView.java` and `SecondaryTabManager.java` to detect requests ending in `explanation-engine.wasm`.
   - **Binary Section 5 Memory Header Patching:**
     - Parses the binary to locate Section 5 (Memory Section).
     - Rewrites the memory definition:
       - Initial memory: Reduced from 6,176 pages (386 MB, LEB128: `[0xa0, 0x30]`) to 512 pages (32 MB, LEB128: `[0x80, 0x04]`).
       - Maximum memory: Reduced from 32,768 pages (2048 MB, LEB128: `[0x80, 0x80, 0x02]`) to 8,192 pages (512 MB, LEB128: `[0x80, 0xc0, 0x00]`).
     - Perfectly preserves the exact 7-byte section length so no downstream byte offsets or segment pointers shift anywhere in the 27.7 MB binary.
   - **Local Disk Caching:** Persists the patched `.wasm` binary to app cache (`cacheDir/wasm_cache/cee_<hash>.wasm`). Cache hits serve instantly (~10ms) from local storage with proper `application/wasm` headers and CORS policy.
4. **Verification:**
   - Verified on physical Board display: Bot Vinh (1300) and Count Morphy loaded instantly, executed opening moves (`1. d4`), and engaged in bot chat dialogue with zero engine crash alerts.

---

### Phase 17: Single-Finger Double-Tap Right-Click Gesture Engine (Arrow Drawing & Square Highlighting)
1. **Problem Analysis:**
   - Multi-touch two-finger gestures on large tabletop capacitive touchscreens frequently suffer from driver contact merging (collapsing two touch points into one during motion), palm rejection interference, and timing skew between fingers. This made two-finger tapping and two-finger dragging difficult to balance reliably across different hand postures.
   - User requested transitioning from the two-finger interaction to a single-finger **Double-Tap** paradigm:
     - On double-tap:
       a.) Do not interpret as a piece movement.
       b.) Draw an arrow if dragged.
       c.) Select/highlight the square if released in place.
2. **Implementation in `ChessTouchHelper.java` (`CHESS_ARROW_TOUCH_JS`):**
   - **Single-Finger Double-Tap State Machine:**
     - **Tap 1 Interception:** When a finger lands on the chessboard, native left-click dispatch is buffered (`TAP_MAX_HOLD_TIME = 350ms`).
       - If the finger moves > 10px (`PIECE_DRAG_THRESHOLD`), it is immediately flushed as a native piece drag (`pointerdown` with `button: 0, buttons: 1`), ensuring 0ms latency for drag-and-drop piece moves.
       - If the finger lifts quickly without moving (`<= 350ms`), `pendingTap1.released` is set and a gap timer of 280ms (`DOUBLE_TAP_MAX_GAP`) is started.
       - If no second tap arrives within 280ms, Tap 1 flushes as a normal single left-click (`pointerdown` + `pointerup` + `click`).
     - **Tap 2 Trigger (Double-Tap Confirmed):**
       - When a second touch lands on the same square (or within 55px) within the 280ms gap:
       - Tap 1's left-click is **completely discarded**, preventing piece grabs, move hints, or arrow erasure.
       - System enters **Double-Tap Active Mode** and dispatches synthetic right-click `pointerdown` (`button: 2, buttons: 2, which: 3`) on the origin square.
     - **Tap 2 Motion (Arrow Drag):**
       - If the user moves > 18px or enters another square, `isArrowDrag` flags `true`.
       - Synthetic `pointermove` (`button: 2, buttons: 2, which: 3`) continuously streams to the board, rendering live arrow previews.
     - **Tap 2 Release:**
       - If `isArrowDrag` is true and `endSquare !== startSquare`: Dispatches synthetic right-click `pointerup` on the target square to commit the tactical arrow.
       - If `isArrowDrag` is true and `endSquare === startSquare`: Cancels marking cleanly via `dispatchPointerCancel` without highlighting.
       - If `isArrowDrag` is false (released in place): Dispatches synthetic right-click `pointerup` and `contextmenu` on the square, toggling the square highlight.
   - **Additive Markings:** Because Tap 1 of subsequent double-taps is buffered and discarded upon Tap 2, existing tactical arrows and square highlights are never cleared when drawing multiple arrows sequentially. Markings clear on a standard single-tap or piece move.
3. **Verification & Deployment:**
   - Compiled AAR and Godot APK via `./build_and_deploy.sh -d 10.0.0.65`.
   - Verified on physical Board display: Double-tap in place highlights the square cleanly; double-tap and drag draws smooth live arrows; single-finger piece dragging retains 100% native responsiveness.

---

### Phase 18: Cohesive Material Vector Iconography for Controls Row
1. **Problem Analysis:**
   - The menu control row previously used text unicode glyphs (`"⬅"`, `"－"`, `"＋"`, `"↺"`, `"⬇"`) inside circular dark buttons (`Button.setText()`).
   - Font metrics (typographic baseline, ascent/descent, and uneven font side bearings) caused text glyphs—especially the left back arrow `"⬅"`—to render thin and noticeably off-center vertically and horizontally.
   - User requested replacing the back button with a bold Material-style arrow, with the explicit design requirement: *"all of the icons to match in style"*.
2. **Implementation:**
   - **`ControlIconDrawable` (`MenuOverlayView.java`):**
     - Engineered a custom vector `Drawable` rendering canonical Google Material Design vector paths parsed via `androidx.core.graphics.PathParser`:
       - `BACK`: Material `arrow_back` (`M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z`)
       - `ZOOM_OUT`: Material `remove` (`M19 13H5v-2h14v2z`)
       - `ZOOM_IN`: Material `add` (`M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z`)
       - `REFRESH`: Material `refresh` (`M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z`)
       - `CLOSE_DOWN`: Material `arrow_downward` (`M20 12l-1.41-1.41L13 16.17V4h-2v12.17l-5.58-5.59L4 12l8 8 8-8z`)
     - Mathematically centers the canonical 24x24 coordinate box onto `(exactCenterX, exactCenterY)` and scales it uniformly to `size * 0.51f`, giving 100.0% geometric and optical centering.
   - **`ImageButton` Migration:**
     - Replaced text `Button` with `ImageButton` for all 5 vector controls, retaining identical background circles and touch click targets.
     - Centered `desktopModeButton` and `rotateButton` (`"90°"`) with `setIncludeFontPadding(false)` and `setGravity(Gravity.CENTER)` to eliminate font baseline offsets.
3. **Verification & Deployment:**
   - Recompiled AAR and exported APK via `./build_and_deploy.sh -d 10.0.0.65`.
   - Verified on physical Board display: all 5 control buttons display matching visual stroke weight, sharp vector clarity, and perfect optical centering in their circular badges.

---

### Phase 19: Right Margin Gutter Toggle (Board System Menu Clearance)
1. **Problem Analysis:**
   - On the Board hardware display, the Board OS runs a system overlay that renders a floating system pause/menu button along the right screen edge.
   - On certain web applications (such as Chess.com notation, right-hand sidebars, or control buttons), web UI elements sit directly underneath the Board's system button, making them difficult to tap without triggering the system menu.
   - User requested adding a toggle button in the menu that applies a right margin to shift the page content out from under the Board's system button. The margin is off by default and used on-demand.
2. **Implementation:**
   - **`GodotWebView.java` Right Margin Mechanics:**
     - Defined `RIGHT_MARGIN_DP = 94` (`(int) (94 * density)` pixels, expanded from 84dp by +10px for generous clearance beyond the Board OS system overlay button).
     - Applied `rightMargin` to `webView.getLayoutParams()` and `progressBar.getLayoutParams()`.
     - Set `layout.setBackgroundColor(Color.BLACK)` to provide a seamless, dark gutter along the right edge when active.
     - Automatically dispatched `window.dispatchEvent(new Event('resize'))` to trigger immediate DOM / chessboard recalculation.
     - Updated secondary tab manager (`SecondaryTabManager.java`) to inherit and apply the right margin to `popupContainer`.
     - Inset floating dismiss button (`✕`) pinning by `rightOffset` so it never conflicts with the Board button.
   - **Controls Row Button & Vector Icon (`view_sidebar`):**
     - Added `RIGHT_MARGIN` type to `ControlIconDrawable` in `MenuOverlayView.java` rendering canonical Google Material `view_sidebar` (`M16 20H4...`).
     - Added `rightMarginButton` to `controlsRow` between `desktopModeButton` and `rotateButton`.
     - Expanded `menuCard` width to `(int) (365 * menuDensity)` to comfortably accommodate all 8 control buttons without crowding.
     - Added dynamic state styling via `updateRightMarginButtonStyle`:
       - **Inactive (Default):** Neutral dark circle (`#2E2E2E`) with white icon (`#FFFFFF`).
       - **Active:** Navy circle (`#1E2433`) with blue border (`#5C7CFA`) and light blue icon (`#91A7FF`), providing instant visual confirmation.
3. **Verification & Deployment:**
   - Compiled plugin AARs and exported APK via `./build_and_deploy.sh -d 10.0.0.65`.
   - Verified on physical Board display: tapping the margin button smoothly shifts web content 94dp away from the right edge, moving all UI out from under the Board's system button.

---

### Phase 20: Comprehensive In-App Controls & Gestures Guide (`HelpDialogHelper`)
1. **Problem Analysis:**
   - As Chess 4 Board's feature set grew, several non-obvious controls and touch gestures became essential to tabletop play:
     - Single-finger double-tap and drag for tactical arrows vs. double-tap in place for square highlights.
     - 3-second long press on Desktop mode button (`🖥`) to toggle and save global default mode.
     - Long press on `90°` button to reset orientation to default Landscape.
     - Right margin gutter toggle (`◧`) to avoid occlusion by the Board OS floating system button.
     - Floating dismiss button (`✕`) repositioning and idle auto-fade for secondary tabs.
     - Safety slide to exit track (`✕ Slide to Exit ❯❯❯`).
     - Board hardware button system menu options (Resume, Chess 4 Board Menu, Exit to Library).
   - User requested an in-app Help page outlining all controls and gestures, integrated directly into the Board system menu to keep the bottom controls row clean and uncluttered.
2. **Implementation:**
   - **`HelpDialogHelper.java`:**
     - Created a specialized modal helper rendering a 2x tabletop-scaled dark acrylic dialog (`#F01E1E1E` background, 18dp rounded corners, subtle translucent border `#33FFFFFF`).
     - Sized to `480 * menuDensity` width (960px on Board display, ~50% screen width) and capped at 88% screen height.
     - Organized into 5 distinct, high-contrast sections with soft blue headers (`#91A7FF`) and elevated dark item cards (`#262626`):
       1. **♟️ Tabletop Chess Gestures (Chess.com & Lichess):** Double-tap & drag arrows, double-tap square highlights, additive markings, clearing with single tap/move, 1-finger piece dragging.
       2. **🎛️ Bottom Menu Controls Row:** Back, Zoom In/Out (`＋`/`－`, Plus left of Minus), Refresh, Desktop/Mobile UA (short tap vs. 3s hold), Page Margin Gutter (94dp), Screen Rotation (short tap vs. long press reset), Close Menu (`✕`).
       3. **✕ Secondary Tabs & External Links:** Isolated popup tabs, draggable floating dismiss button (`✕`) with idle fade.
       4. **🛡️ Safety Slide to Exit:** Sliding the red thumb across the track (> 95%) to safely quit.
       5. **🔘 Board OS System Menu:** Board hardware button functions (Resume, Chess 4 Board Menu, Controls & Gestures Guide, Exit to Library).
     - Fixed "Done" button styled in accent blue (`#5C7CFA`) pinned at the bottom of the card for easy single-tap dismissal.
   - **Board OS System Menu Integration (`root.gd` & `BoardBridge.java`):**
     - Registered `"show_help"` (`Controls & Gestures Guide`) alongside `"open_menu"` in `Board.pause.set_context()` (`chessboard/root.gd`) and native fallback `BoardBridge.ensureBoardPauseContextRegistered()`.
     - Connected `result.custom_button_id == "show_help"` to `webview_plugin.show_help()` in `root.gd`, and hooked `PauseResultCallbackImpl` in `BoardBridge.java`.
     - Exposed `@UsedByGodot public void show_help()` in `GodotWebView.java` to invoke `HelpDialogHelper.show()`.
   - **Controls Row Ergonomics (`MenuOverlayView.java`):**
     - Sized the 8 control buttons to standard `controlBtnSize = (int) (37 * menuDensity)` with `(int) (5 * menuDensity)` margins on a `(int) (365 * menuDensity)` card width.
     - Flipped Zoom button ordering so Zoom In (`＋`) is positioned to the left of Zoom Out (`－`).
     - Replaced the down arrow (`CLOSE_DOWN`) with canonical Google Material `close` (`✕`) vector geometry (`CLOSE`, `M19 6.41L17.59 5 12 10.59...`) for standard, immediate recognition.
3. **Verification & Deployment:**
   - Built and deployed via `./build_and_deploy.sh -d 10.0.0.65`.
   - Verified on physical Board display: pressing the hardware Board button shows `"Controls & Gestures Guide"` which opens the 2x modal guide cleanly; bottom menu control row features 8 perfectly-spaced buttons with `＋` left of `－` and `✕` close.

### Phase 21: Controls & Gestures Guide Audit & Obsolete Exit Code Removal
1. **Problem Analysis:**
   - The initial draft of the Controls & Gestures Guide included an obsolete section for "🛡️ Safety Slide to Exit" — a legacy experiment permanently hidden (`View.GONE`) when the Board OS hardware system pause overlay took over application exit ("Exit to Library").
   - Referencing an invisible UI element caused user confusion. Furthermore, dead `slideTrack`, `slideThumb`, and `slideLabel` variables, listeners, and `onExitRequested` callbacks still resided in `MenuOverlayView.java`.
2. **Implementation:**
   - **`HelpDialogHelper.java`:**
     - Completely removed Section 4 ("🛡️ Safety Slide to Exit").
     - Re-indexed Section 4 as **🔘 Board OS System Menu**, detailing the hardware menu button and the 4 pause overlay actions (Resume, Chess 4 Board Menu, Controls & Gestures Guide, Exit to Library).
     - Expanded Section 1 (**♟️ Tabletop Chess Gestures & Touch Ergonomics**) to include Chess.com desktop sidebar navigation behavior (first-tap flyout expansion and outside-tap dismiss).
     - Structured Section 2 (**🎛️ Bottom Menu Controls Row**) to match the exact visual layout of all 8 buttons (`[ ⬅ Back ] [ ＋ Zoom In ] [ － Zoom Out ] [ ↺ Refresh ] [ 🖥/📱 Desktop ] [ ◧ Margin ] [ 90° Rotate ] [ ✕ Close ]`), clearly differentiating short taps from long-press/hold actions, and added a quick summary of the primary site launcher and collapsible category accordion.
     - Kept Section 3 (**✕ Secondary Tabs & External Links**) covering isolated popup layers and the draggable floating `✕` dismiss button with 4s idle fade.
   - **`MenuOverlayView.java` & `GodotWebView.java` Cleanup:**
     - Purged dead `slideTrack`, `slideThumb`, and `slideLabel` view instantiations, drag listeners, and layout additions from `MenuOverlayView.setup()`.
     - Removed obsolete `slideThumb` / `slideLabel` reset logic from `showMenu()` and teardown from `destroy()`.
     - Removed unused `onExitRequested()` from `MenuActionListener` interface and its implementation in `GodotWebView.java`.
   - **Help Screen Button Redesign & Mutual Dismissal (`HelpDialogHelper.java`, `GodotWebView.java`, `root.gd`):**
     - Replaced the harsh, full-width `#5C7CFA` blue "Done" button with a sleek, centered rounded pill button (`#1E2433` dark navy slate background, `#5C7CFA` subtle outline, `#B4C6FF` text) matching the app's dark acrylic design language.
     - Added a top-right circular `✕` button (`closeHeaderBtn`) directly in the dialog header for immediate one-tap dismissal without scrolling.
     - Added static `currentDialog` tracking with `dismiss()` and `isShowing()` methods to `HelpDialogHelper`.
     - Integrated `HelpDialogHelper.dismiss()` into `GodotWebView.showMenu()` so opening the links menu immediately closes the help screen.
     - Replaced `Board.pause.ICON_SQUARE` with `Board.pause.ICON_NONE` in `root.gd` and `BoardBridge.java`, removing the unrendered square box glyph `□` from the Board OS pause screen.
### Phase 22: Evaluation of Board OS Menu Controls & Reversion to In-App Controls Row
1. **Experimentation (Board OS System Menu Controls):**
   - Explored moving browser function buttons (`Back`, `Zoom In/Out`, `Refresh`, `Desktop Mode`, `Right Margin`, `Rotate 90°`) into the native Board OS System Menu with dynamic live titles (`Board.pause.set_context(...)`), streamlining the in-app menu strictly to app links.
2. **On-Device Usability Evaluation:**
   - Physical testing on the Board device confirmed that routing frequent browser interactions through the hardware/system overlay added friction and interrupted gameplay flow compared to having immediate, direct access inside the in-app card.
3. **Reversion & Retention of Ergonomic Improvements:**
   - **Reverted In-App Controls Row (`MenuOverlayView.java`):** Restored all 8 circular control buttons (`[ ⬅ Back ] [ ＋ Zoom In ] [ － Zoom Out ] [ ↺ Refresh ] [ 🖥/📱 Desktop ] [ ◧ Margin ] [ 90° Rotate ] [ ✕ Close ]`) at the bottom of `menuCard`.
   - **Retained Enhancements:** Kept Zoom In (`＋`) positioned to the left of Zoom Out (`－`), canonical Material `✕` close geometry, the +10px (94dp) right margin gutter, sleek pill button on the help screen, and mutual auto-dismissal between the links menu and help dialog (`HelpDialogHelper.dismiss()`).
   - **Board OS System Menu (`root.gd` & `BoardBridge.java`):** Restored the clean 2-button system context: `"Chess 4 Board Menu"` and `"Controls & Gestures Guide"`.
   - **Guide Synchronization (`HelpDialogHelper.java`):** Section 2 accurately documents the 8 in-app controls row buttons, and Section 4 documents the Board OS menu options.
4. **Workflow Evolution — Sandboxed Prototyping Rule (`AGENTS.md`):**
   - Formalized the **Sandbox Rule** (Section 5 of [`AGENTS.md`](./AGENTS.md)): All future exploratory spikes, alternative UX architectures, and experimental features must be isolated on dedicated sandbox branches (e.g., `experiment/<name>`).
   - If an experiment is rejected after hardware evaluation, switching back to `main` immediately restores the verified baseline and redeploys without manual code surgery or risk of regressions.

---

### Phase 23: Board OS Pause Screen Button Layout & Title Optimization
1. **Board OS Layout Constraint Analysis:**
   - Evaluated separating the Menu and Guide buttons into discrete rows.
   - Per official Board Developer Documentation (`https://docs.dev.board.fun/guides/pause-menu`), Board OS hardcodes the custom button layout in 2-column rows (*"Custom buttons fill rows of two. An even count gives all half-width buttons; an odd count promotes the trailing button to a full-width row"*). The underlying AIDL service (`ISystemOverlayService`) exposes no layout parameters or row-break flags.
2. **Title Optimization (`App Menu` & `Controls Guide`):**
   - The visual tightness and ellipsis truncation (`"Controls & Gest..."`) were caused by verbose labels (`"Chess 4 Board Menu"` and `"Controls & Gestures Guide"`) exceeding the half-width button bounds.
   - Streamlined button labels to **`"App Menu"`** and **`"Controls Guide"`** in `root.gd` and `BoardBridge.java`.
   - Tested and verified on physical Board hardware: both buttons fit cleanly on a single line within each half-width button with generous padding, zero truncation, and instant tactile clarity.
3. **Documentation Synchronization:**
   - Synchronized [`README.md`](./README.md), [`ARCHITECTURE.md`](./ARCHITECTURE.md), and [`HelpDialogHelper.java`](file:///Users/adamlaster/Development/BoardGames/Chess4Board/godot-webview/GodotWebView/GodotWebView/src/main/java/com/lastersoft/godotwebview/HelpDialogHelper.java) Section 4.

---

### Phase 24: Right Margin Bezel-Symmetric Spacing Calibration (106dp)
1. **Pixel Measurement & Geometric Analysis:**
   - Captured and analyzed on-device framebuffer metrics (`1920x1080`, `mdpi` density = 1.0):
     - Board hardware button bounds: $x \in [1828, 1907]$ ($80\text{px}$ diameter circle).
     - Margin from button right edge ($x = 1907$) to screen bezel ($x = 1920$): $13\text{px}$.
     - With prior `RIGHT_MARGIN_DP = 94`, WebView ended at $x = 1826$, leaving only $2\text{px}$ between the web content and button left edge ($1828$).
2. **Bezel-Symmetric Margin Calibration:**
   - Expanded `RIGHT_MARGIN_DP` to **`106dp`** (`GodotWebView.java`):
     - WebView right edge now sits at $x = 1814$.
     - Space between WebView and button left edge ($1828 - 1814$) is **$14\text{px}$**, perfectly matching the **$13\text{px}$** space between the button right edge and display bezel ($1920 - 1907$).
     - The Board floating system menu button is now visually framed with balanced, even negative space on both sides.
3. **Documentation & Help Synchronization:**
   - Synchronized [`HelpDialogHelper.java`](file:///Users/adamlaster/Development/BoardGames/Chess4Board/godot-webview/GodotWebView/GodotWebView/src/main/java/com/lastersoft/godotwebview/HelpDialogHelper.java), [`README.md`](file:///Users/adamlaster/Development/BoardGames/Chess4Board/README.md), and [`PROJECT_HISTORY.md`](file:///Users/adamlaster/Development/BoardGames/Chess4Board/PROJECT_HISTORY.md).

---

### Phase 25: Catalog Expansion — ABCmouse, CBS Sports, AutoDraw & 'Watch' Category Refinement
1. **Catalog Expansion (`AppCatalog.java`):**
   - **Watch Category (`📺  Watch`):** Renamed category from `"📺  Video"` to `"📺  Watch"` to reflect modern content consumption and added **CBS Sports** (`https://www.cbssports.com/watch`).
   - **Games Category (`🎲  Games`):** Added **ABCmouse** (`https://app.abcmouse.com/app/`) and **AutoDraw (Google)** (`https://www.autodraw.com`), an AI-powered drawing canvas designed for touchscreens that turns rough finger sketches into finished vector art.
   - Expanded total extra apps to **21 apps** (`Games (8)`, `Watch (7)`, `Web (6)`).
2. **Documentation Synchronization:**
   - Synchronized [`README.md`](./README.md) and [`PROJECT_HISTORY.md`](./PROJECT_HISTORY.md).

---

### Phase 26: Complete Application Rebrand & Renaming to 'ChessBoard' (`com.lastersoft.chessboard`) & GitHub Repository Rename
1. **GitHub Repository Rename:**
   - Executed remote repository rename from `adamlaster/chess-4-board` to `adamlaster/ChessBoard` via GitHub REST API (`PATCH /repos/adamlaster/chess-4-board`).
   - Updated local git remote origin to `https://github.com/adamlaster/ChessBoard.git`.
2. **In-App Name & Android Package ID Rename:**
   - **Godot Host Configuration (`project.godot`):** Renamed `config/name` from `Chess4Board` to `ChessBoard`.
   - **Godot Export Presets (`export_presets.cfg`):** Updated `package/unique_name` to `com.lastersoft.chessboard`, `package/name` to `ChessBoard`, and default export path to `../../output/ChessBoard.apk`.
   - **Board OS Pause Menu (`root.gd` & `BoardBridge.java`):** Updated `game_name` parameter to `"ChessBoard"`.
   - **Native SharedPreferences Namespace (`UserAgentManager.java`):** Updated preference key namespace from `chess4board_prefs` to `chessboard_prefs`.
   - **Touch & Arrow Bridge Init Guards (`ChessTouchHelper.java`):** Updated window guard properties from `__chess4board_touch_init` and `__chess4board_arrow_bridge_init` to `__chessboard_touch_init` and `__chessboard_arrow_bridge_init`.
   - **In-App Controls Guide Dialog (`HelpDialogHelper.java`):** Updated all references in strings and dismiss descriptions to `ChessBoard`.
3. **Build, Packaging & Deployment Pipeline:**
   - Updated `build_and_deploy.sh` default APK name to `ChessBoard.apk` and aligned all CLI status headers and logs.
   - Updated `.env.example` template to reflect `ChessBoard.apk`.
4. **Clean Hardware Migration:**
   - Uninstalled legacy package `com.lastersoft.chess4board` from Board hardware display.
   - Deployed and launched new package `com.lastersoft.chessboard` (`ChessBoard.apk`).
5. **Documentation Synchronization:**
   - Synchronized all canonical root documentation: [`README.md`](./README.md), [`ARCHITECTURE.md`](./ARCHITECTURE.md), [`BUILD_SEQUENCE.md`](./BUILD_SEQUENCE.md), [`AGENTS.md`](./AGENTS.md), and [`.gitignore`](./.gitignore).

---

### Phase 27: Per-Site Persistent Defaults for User-Agent & Right Margin Gutter
1. **Per-Site User-Agent Default (`UserAgentManager.java` & `GodotWebView.java`):**
   - Added `UserAgentManager.getSiteKey(String url)` utility to normalize hostnames (e.g., `chess.com`, `youtube.com`).
   - Replaced global default overriding with per-site preferences (`chessboard_prefs` / `site_ua_<host>`).
   - **Short tap on `🖥` / `📱`:** Toggles Desktop vs Mobile mode for the current browsing session only.
   - **3-second hold on `🖥` / `📱`:** Saves Desktop or Mobile mode as the persistent default for just that site with dark modal feedback (`showSiteUaAckDialog`).
   - Site defaults auto-load upon launcher selection and during in-app navigation (`WebViewClient.onPageStarted`).
2. **Per-Site Right Margin Default (`MenuOverlayView.java` & `GodotWebView.java`):**
   - Added 3-second hold `OnTouchListener` to `rightMarginButton` (`◧`) with visual alpha feedback (`0.7f`), slop filtering, and cancellation handling.
   - Updated `MenuActionListener.onToggleRightMargin(boolean isLongPressSaveDefault)`.
   - **Short tap on `◧`:** Toggles 106dp right margin in real time on screen without closing the menu.
   - **3-second hold on `◧`:** Saves Right Margin ON or OFF as the persistent default for just that site (`chessboard_prefs` / `site_margin_<host>`) and pops dark modal feedback (`showSiteMarginAckDialog`).
   - Site margin defaults auto-load upon launcher selection and during in-app navigation.
3. **Documentation & Help Synchronization:**
   - Synchronized [`HelpDialogHelper.java`](file:///Users/adamlaster/Development/BoardGames/Chess4Board/godot-webview/GodotWebView/GodotWebView/src/main/java/com/lastersoft/godotwebview/HelpDialogHelper.java), [`README.md`](./README.md), [`ARCHITECTURE.md`](./ARCHITECTURE.md), and [`PROJECT_HISTORY.md`](./PROJECT_HISTORY.md).

---

### Phase 28: Google Sign-In & Cloudflare Turnstile Captcha Fixes, Native Long-Press Per-Site Defaults & v1.1.0 Bump
1. **Google OAuth & Cloudflare Turnstile Captcha Safeguards:**
   - **Google Sign-In (Error 403 `disallowed_useragent`):** Root cause identified in logcat where `targetUrl.contains("chess.com")` matched Google OAuth URLs due to `redirect_uri=https://www.chess.com/...` in query parameters. Injecting synthetic touch fixes and `window.__chessboard_touch_init` into `accounts.google.com` triggered Google's identity anti-tamper security.
   - **Lichess Captcha (`failure_retry` Loop):** Root cause identified in `ChessTouchHelper.java` monkey-patching `Element.prototype.setPointerCapture`. Cloudflare Turnstile verified JavaScript native prototypes and flagged the WebView as an automated bot.
   - **Safeguard Implementation:**
     - Replaced substring matching with strict `Uri.getHost()` parsing in `ChessTouchHelper.java`.
     - Hardened exclusions: completely exempts `accounts.google.com`, `*.google.com`, `challenges.cloudflare.com`, `*.cloudflare.com`, `turnstile`, `recaptcha`, `hcaptcha`, and all authentication/credential paths (`/login`, `/signin`, `/signup`, `/register`, `/auth`, `/oauth`, `/password`, `/challenge`, `/verify`).
     - Removed all `Element.prototype` monkey-patching.
     - Added DOM chessboard element guard in `CHESS_ARROW_TOUCH_JS` so scripts never run on pages without an active chessboard.
     - Sanitized default User-Agent via regex (`replaceAll(";\\s*wv", "").replaceAll("Version/\\d+\\.\\d+\\s*", "")`) in `UserAgentManager.java` and standardized `DESKTOP_USER_AGENT` to Windows Chrome 131.
     - In `DisplayController.java`, skipped evaluating `document.documentElement.style.zoom` when zoom is 100%.
2. **Native Long-Press Per-Site Defaults & Confirmation Modals:**
   - Replaced rigid 3000ms custom `OnTouchListener` with standard Android `setOnClickListener` and `setOnLongClickListener` on `desktopModeButton` (`🖥 / 📱`) and `rightMarginButton` (`◧`).
   - Added native tactile haptic feedback (`HapticFeedbackConstants.LONG_PRESS`) and touch-slop handling.
   - Posted confirmation modals (`showSiteUaAckDialog` & `showSiteMarginAckDialog`) with a 150ms delay following menu close so touch release and transition finish cleanly.
   - Enhanced `showAckDialog` with UI-thread safety, single-instance dialog management (`currentAckDialog`), and screen-bounded card sizing.
   - Safeguarded primary tab games during secondary tab User-Agent toggles by reloading only the active tab, and restoring main site defaults upon closing secondary tabs.
3. **Version Bump to v1.1.0 (Code 2):**
   - Updated `export_presets.cfg` (`version/code=2`, `version/name="1.1.0"`).
   - Updated `GodotWebView/build.gradle` (`versionCode 2`, `versionName "1.1.0"`).
   - Updated `addons/webview/plugin.cfg` (`version="1.1.0"`).

---

### Phase 29: Architectural Second Tab Isolation, Global Single-Window Enforcement & Auth Continuity
1. **Global Single-Window Mode Enforcement (`setSupportMultipleWindows(false)`):**
   - **Root Cause of Turnstile Failure:** Cloudflare Turnstile on Lichess failed with `TurnstileError: 600010` and `"Failed to execute 'postMessage' on 'DOMWindow': The target origin does not match the recipient"` because Android WebView's multi-window subsystem intercepted internal iframe window messages and failed origin cross-checks.
   - **Solution:** Disabled `setSupportMultipleWindows(false)` and `setJavaScriptCanOpenWindowsAutomatically(false)` across both primary and secondary WebViews. Turnstile now runs within standard single-window sandboxed iframe semantics, resolving verification failures.
2. **Primary Tab Auth Continuity (`isAuthUrl` Guarding):**
   - **Root Cause of Google Sign-In Detachment:** In `shouldOverrideUrlLoading`, links on `chess.com` whose host did not end with `chess.com` were treated as external links and opened in `popupWebView`. Clicking "Log in with Google" routed `accounts.google.com` to the second tab, severing the OAuth redirect callback (`window.opener` / session cookies) from the primary game tab.
   - **Solution:** Enforced strict auth continuity in `shouldOverrideUrlLoading`: any URL matching `isAuthUrl` (`accounts.google.com`, `appleid.apple.com`, `challenges.cloudflare.com`, `turnstile`, `recaptcha`, `/oauth`, `/signin`, `/login`, `/auth`) returns `false` to stay in the primary WebView.
3. **Platform-Compatible Linux x86_64 Desktop User-Agent:**
   - **Root Cause of Google "Browser Not Secure":** Setting `DESKTOP_USER_AGENT` to `Windows NT 10.0` caused a severe fingerprint contradiction with the Android device's native `Linux armv8l` platform and touch hardware, triggering Google's `disallowed_useragent` and anti-tamper heuristics.
   - **Solution:** Restored `DESKTOP_USER_AGENT` to `Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36`.
4. **Eliminated Navigation Abortions in `onPageStarted`:**
   - Removed redundant calls to `uaManager.configureWebSettings(settings)` inside `onPageStarted` across `GodotWebView` and `SecondaryTabManager`. Calling `setUserAgentString()` while a navigation request was in-flight aborted connections mid-stream, which caused YouTube sign-in to bounce back and refresh YouTube.
5. **Second Tab Game Protection Retained:**
   - External links clicked during active gameplay on Chess.com (articles, videos, forum threads) continue to open cleanly in `SecondaryTabManager` with the floating circular `✕` button, keeping live games uninterrupted.

---

### Phase 30: Google Sign-In & Turnstile Captcha Resolution via X-Requested-With Stripping, Engine-Matched UA & Multi-Window Second Tab Restoration
1. **Google OAuth Sign-In ("Browser or app may not be secure") Resolution:**
   - **Root Cause:** Android WebView automatically injected the `X-Requested-With: com.lastersoft.chessboard` HTTP header on all network requests, triggering Google's server-side embedded WebView block. Furthermore, using a hardcoded `Chrome/131.0.0.0` desktop Linux User-Agent string on an Android hardware touchscreen device caused severe fingerprint mismatches with the device's native Chromium 147 engine, client hints (`Sec-Ch-Ua`), ARM GPU, and multi-touch sensors.
   - **Solution:**
     - Integrated `androidx.webkit:webkit:1.12.1` across `GodotWebView` and Godot export dependencies (`plugin.gd`).
     - Invoked `WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, Collections.emptySet())` across both primary and secondary WebViews to completely eliminate the `X-Requested-With` header from all requests.
     - In `UserAgentManager.java`, implemented `getCleanMobileUserAgent()`, `getDesktopUserAgent()`, and `applyAuthUserAgent()`. When navigating to authentication URLs (`accounts.google.com`, `appleid.apple.com`, `/oauth`, `/signin`, `/login`), the WebView automatically switches to the clean, native mobile User-Agent derived from the system Chromium engine (Chrome 147) without `; wv` or `Version/4.0`, satisfying Google's identity checks.
2. **Cloudflare Turnstile CAPTCHA (Lichess) Resolution:**
   - **Root Cause:** Turnstile's automated and interactive verification failed with "Verification failed. Troubleshoot" due to `X-Requested-With` header detection and desktop User-Agent spoofing on ARM touchscreen hardware.
   - **Solution:** Configured `setAllowFileAccess(true)` and `setAllowContentAccess(true)` per Cloudflare WebView standards, stripped `X-Requested-With`, and applied the clean engine-matched mobile User-Agent for challenge verification on `lichess.org/login`. Turnstile verification now passes instantly.
3. **Multi-Window Second Tab Restoration & Auth Handshake Continuity:**
   - **Root Cause:** Global `setSupportMultipleWindows(false)` previously disabled `WebChromeClient.onCreateWindow(...)`, causing Android WebView to silently drop `<a target="_blank">` external link clicks on Chess.com.
   - **Solution:**
     - Re-enabled `setSupportMultipleWindows(true)` and `setJavaScriptCanOpenWindowsAutomatically(true)` on the primary WebView.
     - In `onCreateWindow`, filtered requests: rejected background/automated requests (`!isUserGesture`), kept auth URLs in the primary session (`view.loadUrl(extraUrl)`), and routed external link clicks directly to `SecondaryTabManager.handleCreateWindow(...)`.
     - In `SecondaryTabManager.java`, added an `onAuthRedirect` fallback in `shouldOverrideUrlLoading` so any auth URL opened as a popup seamlessly dismisses the secondary tab and loads in the primary session, maintaining full OAuth cookie and callback continuity.
4. **Hardware Verification on Board (10.0.0.65):**
   - Verified on physical Board display: Google Sign-in on YouTube and web services loads and authenticates without security warnings; Cloudflare Turnstile on `lichess.org` verifies smoothly; external links clicked during Chess.com play open cleanly in the dismissible secondary tab with the floating circular `✕` button.

---

### Phase 31: Canonical Directory & Project Name Normalization (`chess-4-board/` -> `chessboard/`)
1. **Directory Normalization:**
   - Renamed the Godot project root directory from `chess-4-board/` to `chessboard/` (`git mv chess-4-board chessboard`) to align with project-wide naming conventions (`ChessBoard`, `com.lastersoft.chessboard`) and eliminate hyphenated legacy directory artifacts.
2. **Build Scripts & Configuration Synchronization:**
   - Updated `build_and_deploy.sh` to target `$ROOT_DIR/chessboard` for Godot headless exports and plugin AAR destinations.
   - Updated `.gitignore` to track `chessboard/.godot/`, `chessboard/android/`, and `!chessboard/addons/**/*.aar`.
   - Updated all canonical root documentation (`README.md`, `ARCHITECTURE.md`, `BUILD_SEQUENCE.md`, `AGENTS.md`, and `PROJECT_HISTORY.md`) to reflect `chessboard/`.
3. **Pipeline Verification:**
   - Confirmed full build pipeline (`./build_and_deploy.sh`) successfully compiles the Android AAR, exports Godot APK from `chessboard/`, and deploys cleanly to the Board hardware.

---

### Phase 32: Decoupled Fullscreen Launcher & Standalone Live Browser Controls Overlay
1. **Decoupled Architecture & UI Evolution:**
   - **Fullscreen Launcher (`launcherRoot`):** Converted the legacy centered menu dialog into a fullscreen 4-row categorized application catalog (Primary Chess, Games, Watch, Web) with horizontal scrolling cards, dark slate styling (`#F50C0E16`), brand header with live clock, Controls Guide shortcut, and persistent 106dp right-margin gutter clearance to ensure the physical Board OS hardware pause button remains completely unobstructed.
   - **Standalone Browser Controls Rail (`controlsRail`):** Decoupled browser controls from the app menu into a slim vertical toolbar overlay docked directly above the live browser, preserving 106dp gutter clearance and 14dp rounded pill aesthetics. Houses Close (`✕`), Back (`⬅`), Zoom In (`＋`), Zoom Out (`－`), Refresh (`↺`), Desktop UA (`🖥/📱`), Right Margin (`◧`), Rotation (`90°`), and Launcher switch (`♟`).
   - **Live Browser Feedback:** Maintained controls rail visibility during zoom adjustments, page refreshing, margin toggling, and temporary desktop UA toggling, enabling users to see immediate visual changes to web content in real time.
   - **Board OS System Pause Menu:** Replaced the legacy single `"App Menu"` button with dedicated `"Launcher"` and `"Browser Controls"` buttons registered in `chessboard/root.gd` and native reflection fallback in `BoardBridge.java`.
2. **Google Photos Redirect Loop Resolution:**
   - Fixed Google Photos reloading issue by updating target URL to `https://photos.google.com/login` (bypassing marketing splash and Android app intent redirects).
   - Added an `isExplicitNavigation` guard in `GodotWebView.java` to prevent in-flight site transitions from being intercepted as external popups in `shouldOverrideUrlLoading`.
   - Eliminated mid-flight `uaManager.configureWebSettings()` calls in `onPageStarted` which were resetting the WebView engine and terminating active network connections.
3. **Hardware Gutter Clearance Enforcement:**
   - Enforced `Math.max(marginPx, defaultGutter)` on both `launcherRoot` and `controlsRail` in `MenuOverlayView.java`, ensuring all UI overlays consistently reserve the 106dp Board hardware bezel gutter regardless of whether site margin is toggled on or off.

---

### Phase 33: Edge-to-Edge Display Architecture & Upper-Right Corner Cutout
1. **Full-Width Edge-to-Edge Architecture (Gutter Removal):**
   - **Gutter Elimination:** Removed the full-height 106dp vertical gutter across all components (`webView`, `progressBar`, `popupWebView`, `launcherRoot`, and `controlsRail`). The browser and secondary tabs now run 100% full width edge-to-edge across the 1920x1080 display.
   - **Controls & Logic Deprecation:** Removed `isRightMarginEnabled`, `RIGHT_MARGIN_DP`, `getSiteDefaultRightMargin`, `saveSiteDefaultRightMargin`, `applyRightMargin()`, `toggleRightMargin()`, and the `◧` margin toggle button from `GodotWebView.java`, `MenuOverlayView.java`, `UserAgentManager.java`, `SecondaryTabManager.java`, and `HelpDialogHelper.java`.
2. **Upper-Right Corner Hardware Cutout (`CutoutBackgroundDrawable`):**
   - **Physical Coordinates:** Measured exact display coordinates of the Board OS floating system menu button from device screen captures: `x=[1828..1907]` (width 79px), `y=[16..95]` (height 79px) on 1920x1080 display (density = 1.5).
   - **Cutout Dimensions:** Configured `cutoutSize = (int) (74 * density)` (~111px), providing a clean box `[1809..1920, 0..111]` completely containing the Board button with generous margin.
   - **Inverted Fillet Geometry:** Created `CutoutBackgroundDrawable` in `MenuOverlayView.java`, which renders the dark slate card (`#F50C0E16`) with a smooth inverted fillet notch around the Board button using convex entry arc, vertical wall, concave corner arc, and exit arc.
   - **Header Bar Alignment:** Added `paddingRight = cutoutSize` to `headerBar` so brand title, badges, action buttons, live clock, and red `✕` close button end cleanly to the left of the cutout.
   - **Touch Passthrough:** Added touch listeners to both `launcherRoot` and `launcherScrimView` returning `false` for taps within `(x >= w - cutoutSize && y <= cutoutSize)`, allowing direct tactile interaction with the Board OS system menu button at all times.
   - **Edge-to-Edge Rows:** All 4 application rows below `y = 111px` enjoy 100% full screen width edge-to-edge.
3. **Browser Controls Rail Docking:**
   - Docks on the right edge directly below the cutout (`topMargin = cutoutSize + 6dp`, `rightMargin = 6dp`), avoiding any overlap with the Board button.
   - Formatted as a sleek vertical capsule pill with 8 buttons: Close (`✕`), Back (`⬅`), Zoom In (`＋`), Zoom Out (`－`), Refresh (`↺`), Desktop UA (`🖥/📱`), Rotation (`90°`), and Launcher shortcut (`♟`).
   - `controlsScrimView` also implements cutout touch passthrough so the Board menu button can be tapped even while Browser Controls are open.
4. **Hardware Verification on Board (10.0.0.65):**
   - Verified on physical Board display: web pages (Chess.com, Google Photos) render full-width edge-to-edge; Launcher displays the clean inverted corner cutout around the Board OS button with full touch interactivity; Browser Controls overlay docks gracefully below the Board button with immediate live feedback on the active web page.

---

### Phase 34: Per-Site User-Agent & Custom Right Margin Configuration via Long-Press, Rail Repositioning & Board Menu Simplification
1. **Per-Site Settings Dialog (`SiteSettingsDialogHelper`):**
   - **500ms Long-Press Gesture:** In `MenuOverlayView.java` (`setupCardTouchBehavior`), added a 500ms long-press listener to all application/site cards across all 4 categories. Long-pressing any card highlights the card in warm gold (`#FAB005`), performs haptic feedback, and opens the Site Settings modal dialog.
   - **Dual Configuration Interface:**
     - **User-Agent Mode:** Segmented toggle between Desktop (`[ 🖥 Desktop ]`) and Mobile (`[ 📱 Mobile ]`).
     - **Right Margin Value:** Quick preset buttons for Edge-to-Edge (`[ 0 dp (Edge-to-Edge) ]`) and Board Gutter (`[ 106 dp (Gutter) ]`), accompanied by a fine-tuning stepper (`[ － ]` `... dp` `[ ＋ ]`) allowing custom margins from 0 to 200 dp in 10 dp increments.
   - **Persistence & Backward Compatibility:** Stored per-site margin in `chessboard_prefs` under `site_margin_dp_<siteKey>`, with automatic backward compatibility for legacy boolean preferences (`site_margin_<siteKey>`).
   - **Live Application & Confirmation:** If the configured site matches the currently active web session, User-Agent and Right Margin are immediately applied live without requiring an app restart: `webView.setLayoutParams(flp)`, `progressBar.setLayoutParams(flp)`, `secondaryTabManager.applyRightMargin(marginPx)`, and `floatingDismissButton.pinToBottomRight(w - marginPx, h)`. Displays a custom 2x tabletop confirmation dialog (`showSaveAckDialog`).
2. **Browser Controls Rail Repositioning & Vertical Proportioning:**
   - **Relieved Bezel Crowding:** Slid the Browser Controls rail further down from the Board OS system menu button by setting `railParams.topMargin = cutoutSize + (int) (24 * density)` (~147px / 98dp), creating a comfortable 52px gap below the physical button.
   - **Complete 1080p Display Fit:** Resized rail buttons to `27 * menuDensity` (54dp) and inter-button spacing to `3.5 * menuDensity` (7dp) with `railWidth = 38 * menuDensity` and `padding = 6 * menuDensity`. The total height of the 8-button rail is ~758px (ending at `y ≈ 905px`), leaving ~175px of clean clearance above the bottom of the screen, ensuring the bottom Launcher shortcut button (`♟`) is never clipped.
3. **Board OS System Pause Menu Streamlining:**
   - Removed the `"Controls Guide"` action from the hardware pause screen in `chessboard/root.gd` (`Board.pause.set_context`) and the native reflection fallback in `BoardBridge.java`, leaving a clean, two-action pause menu: `"Launcher"` and `"Browser Controls"`.
   - The Controls Guide remains instantly accessible directly from the Launcher header via `[ 📖 Controls Guide ]`.
4. **Controls Guide Modal Polish (`HelpDialogHelper`):**
   - Removed the top-right circular `✕` close button (`closeHeaderBtn`), resolving the redundancy of having two close buttons.
   - Centered the title and subtitle in the dialog header, leaving the centered bottom pill button (`doneBtn`) as the sole, intuitive dismissal action.
5. **Hardware Verification on Board (10.0.0.65):**
   - Built and deployed via `./build_and_deploy.sh -d 10.0.0.65`. Captured screen captures confirming:
     - The Launcher displays cleanly with the top-right cutout and header action shortcuts.
     - The Browser Controls vertical rail floats over the live web browser with a 52px gap beneath the Board OS menu button, with all 8 buttons fully visible and well clear of the screen bottom.

---

### Phase 35: Deadspace Tap Dismissal with Near-Miss Tolerance & Unified Close Button Positioning
1. **Deadspace Tap Dismissal Engine (`MenuOverlayView.java`):**
   - **User Problem:** The Launcher required reaching for the top close button to exit. The user requested intuitive dismissal by tapping deadspace (empty margins, space to the right of short rows, header empty areas), but strictly required preventing accidental closure if finger taps slightly miss an app card or header button on the tabletop touch glass. Dragging/swiping cards horizontally must also never dismiss the launcher.
   - **Root Container Refactor:** Converted `launcherRoot` from `LinearLayout` to `FrameLayout`, separating root touch dispatching from internal layout (`launcherContent`).
   - **Deadspace Hit-Testing Architecture:**
     - Overrode `dispatchTouchEvent(MotionEvent ev)` and `onTouchEvent(MotionEvent event)` in `launcherRoot`.
     - Tracks all application cards (`allCardViews`) and all interactive header controls (`protectedHeaderViews`).
     - Uses `View.getLocationOnScreen()` for real-time bounding box calculation, ensuring hit testing dynamically reflects horizontal scroll offsets.
     - **Card Near-Miss Tolerance:** Applied a `36px` (`12 * menuDensity`, ~one fingertip width) tolerance buffer around all app cards. Because inter-card spacing is `21px`, this buffer completely bridges the gap between adjacent cards and shields any near-miss tap around card boundaries from triggering dismissal.
     - **Header Near-Miss Tolerance:** Applied an `18px` (`12 * density`) tolerance buffer around header buttons (`brandTitle`, `launcherBadge`, `ctrlSwitchBtn`, `guideBtn`, `clockTextView`).
     - **Drag / Swipe Immunity:** Differentiates taps (`!isDrag`, movement `<= touchSlop`) from scrolls (`dx/dy > touchSlop`). Swiping through card rows or swiping in deadspace will never dismiss the launcher.
     - **Deadspace Tap Execution:** When an intentional tap lands outside all protected card/header buffers and outside the Board OS cutout notch, triggers `performHapticFeedback(VIRTUAL_KEY)`, dispatches `ACTION_CANCEL` to children to clean up touch states, and invokes `listener.onCloseMenu()`.
2. **Close Button Coordinate Parity with Browser Controls:**
   - **Unified Positioning:** Moved `launcherCloseBtn` from `headerBar` into `launcherRoot` at coordinates matching the Browser Controls close button to the exact pixel:
     - `topMargin = cutoutSize + (int) (24 * density) + (int) (6 * menuDensity)` (~165px)
     - `rightMargin = (int) (6 * density) + (railWidth - controlBtnSize) / 2` (~25.5px)
     - `controlBtnSize = (int) (27 * menuDensity)` (54dp)
   - When users toggle between Launcher and Browser Controls, the red circular close button (`✕`) remains in the identical physical spot on screen directly below the Board OS hardware button cutout.
   - Sits in the open, unpopulated area of Row 1 (Primary Chess, which only occupies `x ≈ 42..1485px` on a 1920px screen), completely free of card collisions.
   - The launcher `headerBar` now displays the live clock on the right side, padded away from the cutout notch.
3. **Build & Hardware Deployment:**
   - Compiled Gradle AAR, exported Godot APK, and deployed to physical Board at `10.0.0.65` via `build_and_deploy.sh`. Verified edge-to-edge layout, Board OS button passthrough, and seamless control parity.

---

### Phase 36: Restoring Live Right Margin Gutter Toggle Button on Browser Controls Rail
1. **Restoration of Right Margin (`◧`) Control to Browser Controls Rail:**
   - **User Requirement:** While per-site margin defaults can be configured via 500ms long-press on Launcher cards, quick toggling between edge-to-edge (0dp) and the Board menu clearance gutter (106dp) directly over the active web session is essential for viewing web pages that place critical top-right buttons under the Board OS system button.
   - **Visual Icon & Rail Geometry:**
     - Added `ControlIconDrawable.Type.RIGHT_MARGIN` (`M16 20H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2h12c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2zm0-14H4v12h12V6zM20 4h2v16h-2z`).
     - Positioned the `◧` button between Desktop Mode (`🖥/📱`) and Rotation (`90°`) (button 7 of 9).
     - Tightened inter-button spacing slightly from `3.5 * menuDensity` to `3 * menuDensity` (9px) so the 9-button rail measures `837px` high, docking cleanly at `y = 147px` with `96px` (~64dp) of generous clearance above the bottom of the 1080p screen.
2. **Dual Short-Tap / Long-Press Functionality:**
   - **Short Tap (Live Layout Shift):** Instantly toggles the active margin between `0dp` and `106dp` (`DEFAULT_GUTTER_DP`), shifts the `WebView` and progress bar layout in real time, updates the button stroke and icon color (`#5C7CFA` border / `#91A7FF` icon when active; slate `#202430` / `#CBD5E1` when inactive), and keeps the Browser Controls rail open so the user immediately observes the page reflow.
   - **Long Press (800ms Default Persistence):** Performs haptic feedback, toggles and saves the target margin as the persistent site default in `SharedPreferences` (`site_margin_dp_<siteKey>`), and presents `SiteSettingsDialogHelper.showSaveAckDialog` confirming the persistent preference.
3. **Synchronized State & Guide Integration:**
   - Added `updateRightMarginButtonStyle` hooks in `showBrowserControls()`, `onTabClosed()`, `onSiteSettingsSaved()`, and `navigateToSite()` so the button appearance always reflects the active session's margin.
   - Re-added the `◧  Right Margin / Board Gutter` row to the tabletop Controls Guide modal (`HelpDialogHelper.java`).
4. **Catalog Expansion — Fandango Added to Watch Category:**
   - Added **Fandango** (`https://athome.fandango.com/`, tag `MOVIES`, icon `🎟️`) to the `Watch` category in `AppCatalog.java`.
   - Expanded the Extra Apps collection to 22 web apps across 3 categories (8 Games, 8 Watch, 6 Web).
5. **Build & Hardware Deployment:**
   - Verified Gradle compile and deployed build to Board at `10.0.0.65`.

---

### Phase 37: Dedicated Launcher Row & Card Edit Mode with Horizontal Drag Reordering and [▲] / [▼] Row Navigation
1. **Dedicated Per-Row Edit Mode Engine:**
   - **User Problem Addressed:** Long-press + immediate vertical dragging gesture disambiguation on a large touchscreen tabletop proved finicky and conflicted with horizontal scrolling and site settings.
   - **Header Edit Toggle (`[ ✎ Edit ]` / `[ ✓ Save ]`):** Added a dedicated edit button to each category row header:
     - Tapping `[ ✎ Edit ]` activates Edit Mode for that specific row, displaying a vibrant green `[ ✓ Save ]` button (`#143E26` background, `#40C057` border), a warm amber `⠿ Drag cards to reorder` status hint, row navigation buttons (`[ ▲ ]` / `[ ▼ ]`), and highlighting the active row with a `#4C6EF5` boundary.
     - Automatically exits and saves any previously editing row when entering edit mode on another row.
2. **Row Navigation (`[ ▲ ]` / `[ ▼ ]`):**
   - Added Up/Down arrow buttons to the header during edit mode to immediately shift the entire category row up or down in `rowsContainer`.
   - Snappy layout re-indexing with dynamic button disabling/dimming at bounds (`idx == 0` or `idx == count - 1`), instant `SharedPreferences` persistence (`launcher_row_order`), and virtual key haptic feedback.
3. **Horizontal Card Drag Reordering:**
   - In edit mode, standard tap-to-launch and 500ms long-press site settings dialog are disabled.
   - Touching and dragging any card horizontally lifts the card with elevation and amber highlight (`#FAB005`), dynamically sliding sibling cards left or right to make room.
   - Releasing snaps the card smoothly to its new slot, re-indexes the card in `cardsRow`, and emits confirmation haptics.
   - Automatic edge scrolling for `HorizontalScrollView` when dragging cards near screen edges.
4. **Persistent Order Architecture (`AppCatalog.java`):**
   - Implemented `getOrderedCategories(Context)` and `saveRowOrder(Context, List<String>)` for category row ordering.
   - Implemented `getOrderedSitesForCategory(Context, CategoryEntry)` and `saveSitesOrderForCategory(Context, String, List<String>)` for per-category app card ordering.
   - Auto-saves all changes on `[ ✓ Save ]` tap, deadspace tap, or when launcher is closed or destroyed.
5. **Deadspace Protection:**
   - Header buttons added to `protectedHeaderViews` to protect from accidental dismissal.
   - Tapping deadspace while a row is in edit mode exits edit mode and saves changes instead of dismissing the entire launcher.
6. **Build & Hardware Deployment:**
   - Compiled Gradle AAR and deployed build to Board at `10.0.0.65`.

---

### Phase 38: Catalog Expansion — Apple TV Added to Watch Category
1. **Catalog Expansion:**
   - Added **Apple TV** (`https://tv.apple.com`, icon `🍎`, subtitle `Originals & Movies`, tag `TV`) to the `Watch` category in `AppCatalog.java`.
   - Forward-compatible `getOrderedSitesForCategory` auto-appends newly added sites to the end of any existing saved custom site order for the category.
2. **Canonical Documentation Updates:**
   - Updated `README.md` catalog reference to reflect 23 total extra apps (9 in Watch category).
3. **Build & Hardware Deployment:**
   - Compiled Gradle AAR and deployed build to Board at `10.0.0.65`.

---

### Phase 39: Header Brand Title Long-Press Shortcut to GitHub Repository
1. **Developer Easter Egg / Quick Source Access:**
   - Attached an `OnTouchListener` to the `"CHESSBOARD"` brand `TextView` in the launcher header bar with 500ms long-press detection and haptic feedback.
   - Long-pressing navigates directly to `https://github.com/adamlaster/ChessBoard` and closes the launcher.
   - Touch-slop motion guard cancels the timer on finger drag and resets color to prevent accidental triggers.
2. **Canonical Documentation Updates:**
   - Updated `README.md` controls reference table.
3. **Build & Hardware Deployment:**
---

### Phase 40: Persistent Browser Controls Rail (Explicit Dismiss via ✕ Only)
1. **Uninterrupted Tabletop Browsing & Live Gameplay:**
   - **Problem Addressed:** Previously, opening the Browser Controls rail introduced a transparent full-screen scrim (`controlsScrimView`) that intercepted all page touches and dismissed the rail on any tap outside the rail. This blocked normal webpage interaction (e.g. playing chess moves, scrolling, or clicking) whenever the controls rail was open.
   - **Solution:** Removed the `controlsScrimView` layer entirely. The Browser Controls rail (`controlsRail`) now docks cleanly on the right edge directly over the live browser without any intercepting backdrop.
   - **Live Touch Transparency:** Webpage interactions (chess moves, touches, drags, scrolling) pass directly through to `WebView` undisturbed while the rail remains open.
   - **Strict Explicit Dismissal:** The Browser Controls rail remains persistent and dismisses exclusively when the user taps its `✕` button (or switches to the launcher `♟` or resets orientation `rotateButton`).
2. **Canonical Documentation Updates:**
   - Updated `ARCHITECTURE.md` (Section 3 View Hierarchy Layer 3 & Layer 5).
   - Updated `README.md` (Controls Reference table).
3. **Build & Hardware Deployment:**
   - Compiled Gradle AAR and deployed build to Board at `10.0.0.65`.

---

### Phase 41: 3-State Margin Engine (Right, All 4 Sides, None) & Persistent Browser Controls Rail Across Screen Rotation
1. **Persistent Browser Controls Rail Across Screen Rotation:**
   - **Problem Addressed:** Tapping the `90°` orientation button rotated the display but triggered `hideMenu()` and the layout change listener, inadvertently closing the Browser Controls rail.
   - **Solution:** Preserved `controlsRail` during rotation. Updated `onRotateScreen()` and `addOnLayoutChangeListener` to keep the rail open, recomputing top and right layout parameters dynamically in `postRotate()` to ensure the toolbar remains docked below the hardware cutout across 0°, 90°, 180°, and 270° orientations.
2. **3-State Tabletop Margin Engine (Right Gutter, All 4 Sides, Edge-to-Edge):**
   - Transformed the binary right margin toggle into a 3-way cyclic margin state engine:
     - `MARGIN_STATE_NONE` (`0`): Edge-to-edge full screen (0dp margin). Button renders in neutral slate gray (`#202430` background, `#CBD5E1` border/icon) with `NO_MARGIN` vector icon.
     - `MARGIN_STATE_RIGHT` (`1`): 106dp right gutter clearance. Button renders in active indigo (`#1E2433` background, `#5C7CFA` border, `#91A7FF` icon) with `RIGHT_MARGIN` vector icon.
     - `MARGIN_STATE_ALL` (`2`): 106dp symmetric inset padding on all 4 borders (left, top, right, bottom). Button renders in active cyan/mint (`#152836` background, `#22B8CF` border, `#66D9E8` icon) with `ALL_MARGINS` dual-frame hollow bezel vector icon using `Path.FillType.EVEN_ODD`.
   - Applied margin state across primary `WebView`, `ProgressBar`, `SecondaryTabManager` (all 4 sides of `popupContainer`), and `FloatingDismissButton` (pinned with bottom and right clearance).
3. **Per-Site Settings Dialog Support:**
   - Updated `SiteSettingsDialogHelper` to display 3 preset buttons: `None (0 dp)`, `Right (106 dp)`, and `All 4 (106 dp)`.
   - Persisted both `site_margin_mode_<siteKey>` and `site_right_margin_<siteKey>` in `SharedPreferences`.
   - Updated long-press save acknowledgment popup to clearly state the active mode ("Edge-to-Edge", "Right Gutter", or "All 4 Sides").
4. **Canonical Documentation Updates:**
   - Updated `README.md` Controls Reference table.
   - Updated `ARCHITECTURE.md` component table and view hierarchy.
   - Updated `HelpDialogHelper.java` Tabletop Controls Guide.
5. **Build & Hardware Deployment:**
   - Compiled Gradle AAR and deployed build to Board at `10.0.0.65`.

---

### Phase 42: Fix Launcher Site Settings Persistence & Domain Key Extraction
1. **Root Cause Analysis of Site Settings Not Saving:**
   - **Empty Domain Key on Auth/Login URLs:** `UserAgentManager.getSiteKey(url)` previously checked `if (isAuthUrl(url)) return ""` in an effort to prevent false site-switching during OAuth redirections. However, sites with `/login` or `/auth` in their catalog URLs (such as Google Photos: `https://photos.google.com/login`) returned an empty string (`""`) for `siteKey`.
   - **Rejected Empty Keys:** The SharedPreferences persistence helpers (`saveSiteDefaultRightMarginDp`, `saveSiteDefaultMarginMode`, and `saveSiteDefaultDesktopMode`) explicitly guard against empty keys (`!siteKey.isEmpty()`), causing all save attempts on affected sites to silently drop.
   - **Asynchronous Disk Writes:** `apply()` was used instead of synchronous `commit()`, leading to potential race conditions on immediate dialog dismissal or re-reading.
2. **Comprehensive Resolution:**
   - **Clean Host Extraction in `getSiteKey`:** Removed `isAuthUrl(url)` from `UserAgentManager.getSiteKey(url)`. Domain parsing is now pure host extraction (stripping `www.` / `m.`), correctly resolving `photos.google.com`, `chess.com`, etc. regardless of path or parameters. OAuth redirect protection remains safely handled in `onPageStarted` where `isAuthUrl(url)` routes to `applyAuthUserAgent` before checking domain switches.
   - **SiteKey Fallback in `SiteSettingsDialogHelper`:** Added fallback to `site.name` sanitized (`replaceAll("[^a-z0-9._-]", "")`) so `siteKey` can never be empty.
   - **Synchronous `commit()`:** Upgraded all preference mutations to `.commit()` for immediate, synchronous disk persistence.
   - **App Catalog URL Cleanup:** Normalized Google Photos URL in `AppCatalog.java` to clean canonical `https://photos.google.com`.
   - **Margin Synchronization in `onPageStarted`:** Added margin state synchronization in `WebViewClient.onPageStarted` so when navigating to a new domain, its saved margin mode and gutter width are applied immediately.
3. **Build & Hardware Deployment:**
   - Compiled Gradle AAR and deployed build to Board at `10.0.0.65`.

---

## 3. Current Controls & Platform Catalog Reference

To avoid information duplication, the live user-facing specifications are maintained in their respective canonical files:
* **Active Menu Controls & Gestures:** See [`README.md` (Menu Controls Reference)](./README.md#%EF%B8%8F-menu-controls-reference).
* **Included Platforms & Web Applications:** See [`README.md` (Included Platforms & Web Apps)](./README.md#-included-platforms--web-apps).
* **System Architecture & View Hierarchy:** See [`ARCHITECTURE.md`](./ARCHITECTURE.md).
* **Build & Deployment Sequence:** See [`BUILD_SEQUENCE.md`](./BUILD_SEQUENCE.md).

---

## 4. Future Roadmap & Planned Features

### 📌 Planned Feature: Native On-Device URL & Category Manager
* **Goal:** Allow adding, editing, and removing custom URLs and custom Categories directly from the Board screen at runtime without modifying Java code or rebuilding the APK.
* **Proposed Technical Design:**
  1. **Data Persistence (`SharedPreferences` JSON Store):**
     - Seed `SharedPreferences` (`chess4board_custom_apps`) with the default `EXTRA_CATEGORIES` list on first launch.
     - Deserialize `List<CategoryEntry>` dynamically via `org.json.JSONArray` / `JSONObject` (built into the Android SDK with zero external dependencies) whenever the menu builds.
  2. **Native Android Dialog UI (`＋ Add App / Category`):**
     - Add a **`＋ Add Link`** button inside the `Extra Apps` menu (or controls area).
     - Trigger a native Android `AlertDialog` styled in dark mode containing:
       - **App Name** (`EditText`, e.g., `"ESPN"`)
       - **URL** (`EditText` with `InputType.TYPE_TEXT_VARIATION_URI`, auto-prefixing `https://` if omitted)
       - **Category Selector** (`Spinner` or pill list of existing categories + `"＋ New Category..."` option with a category name `EditText`)
       - **Default User-Agent Preference (Optional):** Per-site flag to remember whether a site prefers Mobile (`📱`) or Desktop (`🖥`) mode.
  3. **Management / Deletion:**
     - Long-pressing any custom app or category button opens a quick native confirmation dialog to **Edit** or **Delete** the entry, or **Reset to Defaults**.


---

### Phase 17: Split-Screen Dual-App Tabletop Mode with Zero-Clutter Integrated Controls & Themed Live-Scaling Drag Overlay
1. **Architecture & Subsystem Design (`SplitScreenManager.java`):**
   - Implemented a high-performance dual-app tabletop subsystem hosting two concurrent hardware-accelerated `WebView` instances (Primary and Secondary).
   - Added split screen toggle (`◫`) in the Browser Controls rail. In Split Mode, user picks a secondary app from the categorized Launcher.
   - Preserves tabletop margins, Board hardware cutout safety in the upper-right corner, and hardware-accelerated video/canvas rendering.
2. **Zero-Clutter Integrated Controls Bar:**
   - Eliminated permanent floating corner pills that collided with web app navigation (e.g. Chess.com sidebar and YouTube search/profile).
   - Created a unified horizontal control bar (`[ 🔊 LeftApp ]  [ ⇄ Swap ]  [ ◫ Presets ]  [ 🔇 RightApp ]  [ ✕ Exit ]`) centered at the top display edge.
   - **Context-Aware Visibility:** Synchronized with `MenuOverlayView` so split controls appear exclusively when Browser Controls are open, leaving the active split canvas 100% clean and unobstructed during gameplay and media playback.
   - **Mutual Exclusivity & Direct A/B Audio Toggling:** Muting active pane immediately transfers audio focus to the opposing pane; long-pressing either button mutes both panes (`NONE`).
3. **Themed Drag Overlay with Live GPU-Scaled Cards:**
   - Solved layout thrashing and WebView buffer stutter during divider dragging by introducing an obsidian curtain overlay (`dragOverlayView`) that completely conceals the underlying WebViews while the slider is in motion.
   - Dual dark slate cards display resolved app badges, titles, responsive wireframe bars, and real-time scaling split percentages (`60% / 40%`).
   - Content scaling is driven via GPU/RenderThread transforms (`setScaleX`/`setScaleY`), executing in < 0.2ms per frame at a solid 60 FPS.
   - When the thumb is released, snaps cleanly to presets (`50/50`, `70/30`, `30/70`) and triggers a single, synchronized WebView relayout.

---

### 📌 Planned Feature: Multi-Tab Application Architecture & Background Lifecycle Pausing
* **Goal:** Enable users to run multiple web applications concurrently (e.g. active Chess match in Tab 1, Spotify/YouTube background music in Tab 2, analysis board or community chat in Tab 3) with tabletop switching controls and aggressive background resource throttling to maintain 60 FPS tabletop performance on Board hardware.
* **Proposed Technical Design:**
  1. **Unified Multi-Tab Manager (`TabManager.java`):**
     - Generalize `SecondaryTabManager` into a unified tab orchestration engine managing an ordered list of `TabEntry` models:
       ```java
       public class TabEntry {
           public final String id;
           public String url;
           public String title;
           public String siteKey;
           public String icon;
           public WebView webView;
           public Bundle savedState;       // Serialized state when hibernated
           public boolean isHibernated;     // True if native WebView was destroyed to save RAM
           public long lastActiveTimestamp;
       }
       ```
     - Handles tab lifecycle: `createTab(url, openInBackground)`, `switchToTab(id)`, `closeTab(id)`, and `reorderTabs(from, to)`.
     - Automatically routes per-tab User-Agent modes, margin states (`None`, `Right`, `All 4`), and zoom levels when switching active tabs.
  2. **Aggressive Background Tab Pausing & Performance Engine:**
     - **Execution & Animation Freeze:** When a tab transitions to the background, immediately invoke `webView.onPause()` to halt JavaScript timers, `requestAnimationFrame` rendering loops, CSS animations, WebGL draw calls, and video decoding.
     - **GPU Hardware Layer Release:** Set background WebViews to `webView.setVisibility(View.GONE)` and `webView.setLayerType(View.LAYER_TYPE_NONE, null)` to immediately release costly GPU texture memory back to the Android system.
     - **LRU Tab Hibernation (Zero-OOM Safety Valve):** On memory-constrained devices, enforce a maximum active WebView threshold (e.g., 3-4 concurrent WebViews). If a new tab is spawned, the Least Recently Used (LRU) background tab serializes its navigation history via `webView.saveState(bundle)` and destroys its native `WebView` (`destroy()`). When the user switches back, `TabManager` transparently re-instantiates the `WebView` and restores its exact state via `webView.restoreState(bundle)`.
     - **Audio Background Exception:** Provide an optional per-site flag (or background media detection) allowing audio playback tabs (e.g. YouTube, Spotify) to continue background audio without visual rendering overhead.
  3. **Tabletop Tab Switcher UI & Ergonomics:**
     - **Browser Controls Rail Switcher Button (`🗂️` / `⧉`):** Add a dedicated tab switcher icon to the persistent right-docked rail showing the open tab count (e.g. `[ 🗂️ 3 ]`). Tapping opens a sleek acrylic slide-out Tab Tray docked alongside the browser.
     - **Tab Switcher Tray:** Displays touch-friendly vertical tab cards featuring:
       - App Icon + Page Title + Hostname badge
       - Active indicator pulse
       - Quick Close button (`✕`)
       - `[ ＋ Open New App ]` action button leading directly to the Launcher
     - **Launcher Open Indicators:** In the Launcher grid, cards for currently running applications display a subtle glowing badge (`● OPEN`). Tapping an already-open card instantly switches directly to its live tab without reloading or resetting game state.
     - **Card Drag Swiping:** In the Tab Switcher shelf, horizontal card flings dismiss/close tabs with smooth velocity animations.


---

### 📌 Feasibility Study & Architecture: Split-Screen Dual-App Tabletop Mode
* **Goal:** Allow two web applications to run side-by-side (or top-and-bottom in portrait) simultaneously on the tabletop screen (e.g. Chess match on the left + YouTube/Sports stream or companion game on the right) with concurrent playback, independent touch routing, audio focus switching/muting, and adjustable split ratio.
* **Feasibility Analysis & Technical Findings:**
  1. **Dual Hardware-Accelerated WebViews (100% Feasible):**
     - Android Chromium `WebView` fully supports multiple simultaneous hardware-accelerated View instances in a single activity.
     - View layout can be structured as a split container (`LinearLayout` with horizontal/vertical orientation and layout weights `weight="0.5"`, or two dynamic `FrameLayout` panes).
     - Benchmarking on Android rockchip/ARM64 SoCs shows two concurrent WebViews render smoothly at 60 FPS as long as background inactive tabs remain paused.
  2. **Concurrent Media Playback & Hardware Decoders (Feasible with Codec Considerations):**
     - Modern Android SoCs (including the Board's rockchip architecture) have multi-stream hardware VPU video decoders capable of decoding at least two 1080p/720p streams simultaneously.
     - `WebSettings.setMediaPlaybackRequiresUserGesture(false)` (already active in ChessBoard) allows both streams to play concurrently without touch-blocking.
  3. **Audio Routing & Audio Switching (Feasible via DOM/Audio Bridge):**
     - **Default OS Behavior:** Android's audio subsystem mixes audio from both WebViews together by default (e.g. game move sound effects play over background YouTube audio).
     - **Audio Focus / Pane Muting:** Because Chromium shares the process audio session, per-WebView audio switching is implemented via a lightweight JavaScript injection bridge:
       ```javascript
       // Mute / Unmute specific pane
       function setPaneMuted(muted) {
           document.querySelectorAll('video, audio').forEach(el => { el.muted = muted; });
       }
       // Observe dynamically spawned media elements
       new MutationObserver((mutations) => {
           document.querySelectorAll('video, audio').forEach(el => { if (window._forceMuted) el.muted = true; });
       }).observe(document.body, { childList: true, subtree: true });
       ```
     - **UI Control:** Each pane header or the controls rail can feature an audio toggle (`🔊` / `🔇`) allowing instant one-tap muting or soloing of either pane.
  4. **Multi-Touch Isolation (100% Feasible):**
     - Android's native touch dispatcher isolates multi-touch pointer IDs based on view hit bounds. Player 1 interacting with Pane 1 (moving chess pieces or drawing arrows) will not conflict with Player 2 interacting with Pane 2.
  5. **Tabletop Margins & Hardware Cutout Alignment:**
     - In Landscape split (Left / Right):
       - **Left Pane:** Occupies `0` to `width / 2`. Has zero obstruction from the Board OS button, allowing full `0dp` edge-to-edge content.
       - **Right Pane:** Occupies `width / 2` to `width`. Houses the Board OS button in its top-right corner, applying the `106dp` gutter clearance or `All 4` inset margins independently.
  6. **Proposed Ergonomics & Split Layout Options:**
     - **Preset Layouts:** `50 / 50` (equal split), `70 / 30` (dominant game + companion media), `30 / 70` (dominant media + companion app).
     - **Draggable Center Divider:** A centered divider bar with a touch grip pill (`||`) allowing live dragging to resize pane proportions.
     - **Split Toggle in Controls Rail:** Add a Split Screen button (`◫` / `◫ Split`) in the Browser Controls rail to select a second app from the Launcher to enter split mode.


---

### Phase 41: Tabletop Split-Screen Dual-App Architecture with Drag Overlay & Integrated Controls Bar
1. **Concurrent Dual-App Engine (`SplitScreenManager.java`):**
   - Implemented side-by-side execution of two independent hardware-accelerated WebViews (Primary & Secondary) running concurrently on the tabletop display.
   - Fixed video playback blocking on the right pane: configured `secondaryWebView.setMediaPlaybackRequiresUserGesture(false)`, `setMixedContentMode(MIXED_CONTENT_ALWAYS_ALLOW)`, enabled hardware acceleration layer, and injected touch and audio enhancements on page load.
   - Clean lifecycle handling: `closeSplit()` restores single-app fullscreen mode without page reload or state loss.
2. **Audio Isolation & Solo/Mute Bridge:**
   - Decoupled audio focus between panes via DOM MutationObserver & media muting script injection.
   - Enforced single-source audio: unmuting one pane automatically mutes the opposite pane.
   - Long-press audio button mutes both panes (`NONE`).
3. **High-Performance Themed Drag Overlay (Deferred WebView Redraw):**
   - **Problem Addressed:** Live resizing of concurrent WebViews during touch dragging caused continuous reflow calculations and noticeable GPU stutter.
   - **Solution:** While dragging the center divider, a full-screen curtain (`dragOverlayView`) completely covers the WebViews.
   - Displays Left and Right app cards with dynamically resolved application icons (`resolveAppInfo`), titles, and real-time scaling text percentages and wireframe skeleton bars.
   - Uses RenderThread hardware transformations (`setScaleX`/`setScaleY`) requiring `< 0.2ms` per touch move frame, achieving 60 FPS fluidity.
   - WebViews are redrawn once when the user releases their finger, snapping to preset ratios (`50/50`, `70/30`, `30/70`).
4. **Integrated Top Split Control Bar:**
   - Centered top toolbar (`splitControlBar`): `[ 🔊 LeftApp ]  [ ⇄ Swap ]  [ ◫ Presets ]  [ 🔇 RightApp ]  [ ✕ Exit ]`.
   - Hidden during normal gameplay to preserve complete screen real estate; visible exclusively when the Browser Controls overlay is open.

---

### Phase 42: University of Michigan (UofM) UI Theme System & Dynamic Tabletop Theme Engine
1. **Official Brand Aesthetics (`ThemeManager.java`):**
   - Created `ThemeManager.java` defining official University of Michigan color tokens:
     - Michigan Blue: `#00274C` (deep navy primary)
     - Michigan Maize: `#FFCB05` (warm vibrant gold accent)
     - Dark Obsidian Navy surfaces: `#001226` (backdrop), `#08182E` (card surface), `#143156` (card borders)
     - Split Screen divider handle in Maize (`#FFCB05`) and divider track in Michigan Blue Navy (`#001428`)
   - Fully supports switching back to `CLASSIC` theme (Slate Charcoal `#121622` with Indigo `#4C6EF5` accents).
   - Set `MICHIGAN` as the default theme, persisting selection in `SharedPreferences`.
2. **Tabletop Theme Picker Dialog (`ThemePickerDialogHelper.java`):**
   - Modal tabletop dialog with live preview swatch badges (`〽️ M` vs. `♟ S`), active indicator badges (`✓ ACTIVE`), and instant theme switching.
   - Added `themeBtn` (`"〽️ Theme: U of M"` / `"🎨 Theme: Classic"`) to the Launcher header bar next to the Controls Guide button.
3. **Live Dynamic Retheming (`applyTheme`):**
   - Implemented dynamic retheming across `MenuOverlayView.java`, `SplitScreenManager.java`, and `GodotWebView.java`.
   - Updates Launcher backdrop cutout, brand title ("CHESSBOARD"), badges, category headers, app count pills, `[ ✎ Edit ]` buttons, and app cards without requiring an app restart or webpage reload.
   - Updates Browser Controls rail background, stroke, and button circles.
   - Updates Split Screen divider handle, track, drag overlay cards, and top split control bar.
4. **Canonical Documentation Updates:**
   - Updated `PROJECT_HISTORY.md`, `README.md`, and `ARCHITECTURE.md`.
5. **Build & Hardware Deployment:**
   - Compiled Gradle AAR and deployed build to Board at `10.0.0.65`.


---

### Phase 43: Detroit & Michigan Sports UI Themes (Pistons, Lions, Tigers, Utica Unicorns)
1. **Michigan Sports Brand Palettes (`ThemeManager.java`):**
   - Expanded `ThemeManager.Theme` to support 6 distinctive, high-contrast tabletop themes:
     - **University of Michigan (`MICHIGAN`):** Michigan Blue (`#00274C`), Michigan Maize (`#FFCB05`), and Obsidian Navy (`#001226`).
     - **Detroit Pistons (`PISTONS`):** Pistons Royal Blue (`#1D42BA`), Pistons Red (`#ED174C`), and Midnight Royal Navy surfaces (`#060C1C`).
     - **Detroit Lions (`LIONS`):** Lions Honolulu Blue (`#0076B6`), Vibrant Honolulu Cyan (`#00A3E0`), Metallic Silver (`#F1F5F9`), and Steel Obsidian surfaces (`#030E19`).
     - **Detroit Tigers (`TIGERS`):** Tigers Midnight Navy (`#0C2340`), Vibrant Tiger Orange (`#FA4616`), and Deep Obsidian surfaces (`#020914`).
     - **Utica Unicorns (`UNICORNS`):** USPBL Unicorn Purple (`#5A2D81`), Vibrant Red (`#ED1C24`), and Obsidian Plum surfaces (`#12061A`).
     - **Classic Slate (`CLASSIC`):** Slate Charcoal (`#121622`), Indigo (`#4C6EF5`), and Cyan (`#22B8CF`).
   - Added `buttonLabel` and `swatchText` to the `Theme` enum for clean, self-describing UI rendering.
2. **Scrollable Tabletop Theme Picker Dialog (`ThemePickerDialogHelper.java`):**
   - Wrapped option cards in a vertical `ScrollView` so all 6 team themes scroll smoothly on any display orientation or resolution without clipping.
   - Dynamic preview swatch badges with team emojis (`〽️ M`, `🏀 P`, `🦁 L`, `🐅 D`, `🦄 U`, `♟ S`).
   - Added WCAG luminance-based contrast calculation for the `Done` action button to ensure crisp legibility against any theme's accent color.
3. **Dynamic Header Button Updates (`MenuOverlayView.java`):**
   - Updated `themeBtn` to dynamically display the active theme's branded button label (e.g. `[ 🏀 Theme: Pistons ]`, `[ 🦁 Theme: Lions ]`, `[ 🐅 Theme: Tigers ]`, `[ 🦄 Theme: Unicorns ]`).
4. **Canonical Documentation Updates:**
   - Updated `PROJECT_HISTORY.md`, `README.md`, and `ARCHITECTURE.md`.