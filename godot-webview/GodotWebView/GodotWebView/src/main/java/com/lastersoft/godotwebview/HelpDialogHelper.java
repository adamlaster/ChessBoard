package com.lastersoft.godotwebview;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Displays a comprehensive, 2x scaled dark modal dialog outlining all controls,
 * gestures (single-finger double-tap arrows, piece movement, long presses),
 * split-screen multitasking, tabletop themes, and interface features of ChessBoard.
 */
public final class HelpDialogHelper {

    private static AlertDialog currentDialog = null;

    private HelpDialogHelper() {}

    public static void dismiss() {
        if (currentDialog != null) {
            try {
                if (currentDialog.isShowing()) {
                    currentDialog.dismiss();
                }
            } catch (Exception ignored) {}
            currentDialog = null;
        }
    }

    public static boolean isShowing() {
        return currentDialog != null && currentDialog.isShowing();
    }

    public static void show(Activity activity, float menuDensity) {
        if (activity == null || activity.isFinishing()) return;
        dismiss();

        ThemeManager.Theme activeTheme = ThemeManager.getActiveTheme(activity);

        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;

        int cardWidth = Math.min((int) (480 * menuDensity), (int) (screenWidth * 0.90f));
        int maxCardHeight = (int) (screenHeight * 0.88f);

        // Root container of the dialog
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (18 * menuDensity);
        root.setPadding(pad, pad, pad, pad);

        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setShape(GradientDrawable.RECTANGLE);
        rootBg.setColor(activeTheme.launcherBackdropColor);
        rootBg.setCornerRadius(18 * menuDensity);
        rootBg.setStroke((int) (1.5f * menuDensity), activeTheme.accentColor);
        root.setBackground(rootBg);

        // Header container with title/subtitle
        RelativeLayout headerBar = new RelativeLayout(activity);
        LinearLayout.LayoutParams headerBarParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerBarParams.setMargins(0, 0, 0, (int) (8 * menuDensity));
        headerBar.setLayoutParams(headerBarParams);

        // Titles Container (centered)
        LinearLayout titleContainer = new LinearLayout(activity);
        titleContainer.setOrientation(LinearLayout.VERTICAL);
        titleContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        RelativeLayout.LayoutParams titleParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        titleParams.setMargins(0, 0, 0, 0);
        titleContainer.setLayoutParams(titleParams);

        TextView titleView = new TextView(activity);
        titleView.setText("♟️ Controls & Gestures Guide");
        titleView.setTextColor(activeTheme.accentColor);
        titleView.setTextSize(15 * 2.0f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);
        titleContainer.addView(titleView);

        TextView subtitleView = new TextView(activity);
        subtitleView.setText("Quick reference for tabletop play on the Board");
        subtitleView.setTextColor(Color.parseColor("#94A3B8"));
        subtitleView.setTextSize(10.5f * 2.0f);
        subtitleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.setMargins(0, (int) (2 * menuDensity), 0, 0);
        subtitleView.setLayoutParams(subParams);
        titleContainer.addView(subtitleView);

        headerBar.addView(titleContainer);
        root.addView(headerBar);

        // Scrollable content area
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setVerticalScrollBarEnabled(true);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollParams.setMargins(0, 0, 0, (int) (12 * menuDensity));
        scrollView.setLayoutParams(scrollParams);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(content);

        // --- SECTION 1: CHESSBOARD GESTURES ---
        addSectionHeader(content, "♟️ Tabletop Chess Gestures (Chess.com & Lichess)", activeTheme, menuDensity);
        addItemRow(content,
                "Double-Tap & Drag",
                "Double-tap on a starting square and drag across to draw live tactical arrows. Pieces on the origin square will not move.",
                activeTheme, menuDensity);
        addItemRow(content,
                "Double-Tap in Place",
                "Double-tap quickly on any square without dragging to toggle its color highlight.",
                activeTheme, menuDensity);
        addItemRow(content,
                "Additive Markings",
                "Multiple arrows and square highlights persist together on the board without disappearing as you draw additional lines.",
                activeTheme, menuDensity);
        addItemRow(content,
                "Clear Markings",
                "Tap any square once or make a piece move to immediately clear all arrows and square highlights.",
                activeTheme, menuDensity);
        addItemRow(content,
                "1-Finger Piece Movement",
                "Touch and drag any piece (> 10px) to move it normally with instant 0ms native responsiveness.",
                activeTheme, menuDensity);
        addItemRow(content,
                "Desktop Sidebar Menus",
                "On Chess.com desktop mode, tapping main sidebar categories (Play, Puzzles, Learn, etc.) opens and stabilizes flyout submenus so options can be chosen without navigating away. Tap outside to dismiss.",
                activeTheme, menuDensity);
        addItemRow(content,
                "Bezel Swipes (Edge Gestures)",
                "• Top Bezel Swipe (downward): Pull down from the top edge of the bezel to instantly reveal the Fullscreen Launcher.\n• Right Bezel Swipe (leftward): Pull in from the right edge of the bezel (below the Board OS button) to instantly reveal the docked Browser Controls rail.",
                activeTheme, menuDensity);

        // --- SECTION 2: SPLIT SCREEN & DUAL-APP MULTITASKING ---
        addSectionHeader(content, "◫ Split Screen & Dual-App Multitasking", activeTheme, menuDensity);
        addItemRow(content,
                "Split Screen Toggle (◫)",
                "• Short tap: When in single-app mode, opens Launcher to choose a second app side-by-side. When split screen is active, cycles preset ratios (50/50 → 70/30 → 30/70).\n• Long press: Closes split screen mode and restores the active app to full screen.",
                activeTheme, menuDensity);
        addItemRow(content,
                "Center Divider Dragging",
                "Touch and drag the center divider thumb to adjust split ratio between 20% and 80%. A high-performance dark themed curtain hides WebViews with live GPU-scaled cards and percentages for 60 FPS stutter-free dragging; snaps cleanly upon release.",
                activeTheme, menuDensity);
        addItemRow(content,
                "Double-Tap Divider",
                "Double-tap the center divider thumb at any time to instantly reset to a balanced 50/50 split.",
                activeTheme, menuDensity);
        addItemRow(content,
                "Top Split Controls Bar",
                "Docked top-center and visible exclusively when Browser Controls are open: [ 🔊 LeftApp ]  [ ⇄ Swap ]  [ ◫ Presets ]  [ ⏯ Play/Pause ]  [ 🔇 RightApp ]  [ 🔀 App ]  [ ◂ Back ]  [ ↺ Reload ]  [ 🔄 180° ]  [ ✕ Exit ]. Smooth horizontal scrolling on compact screens; keeps the tabletop 100% clutter-free during normal play.",
                activeTheme, menuDensity);
        addItemRow(content,
                "Head-to-Head 180° Inversion (🔄 180°)",
                "Tap [ 🔄 180° ] to invert the secondary pane 180° for opposite-seat tabletop players. Android hardware-accelerated touch rotation ensures seamless interactive play across the board.",
                activeTheme, menuDensity);
        addItemRow(content,
                "Secondary App Hot-Swap & Controls (🔀 App, ◂, ↺)",
                "• [ 🔀 App ]: Hot-swap the secondary pane to any other app from the Launcher without leaving split screen.\n• [ ◂ ] / [ ↺ ]: Dedicated history back and page refresh controls for the secondary pane.",
                activeTheme, menuDensity);
        addItemRow(content,
                "Media Play/Pause Quick Control (⏯)",
                "Tap [ ⏯ ] to toggle play/pause on all HTML5 video/audio streams in the active pane without fumbling with tiny on-screen web player controls.",
                activeTheme, menuDensity);
        addItemRow(content,
                "Independent Audio Switching",
                "Single-source audio isolation bridge mutes inactive pane automatically.\n• Short tap [ 🔊 ] / [ 🔇 ]: Instantly switch audio playback between left and right apps.\n• Long press: Mute audio on both panes simultaneously (NONE).",
                activeTheme, menuDensity);
        addItemRow(content,
                "Swap Panes (⇄)",
                "Tap [ ⇄ Swap ] in the split control bar to instantly exchange the positions of the left and right applications.",
                activeTheme, menuDensity);

        // --- SECTION 3: BROWSER CONTROLS RAIL & THEMES ---
        addSectionHeader(content, "🎛️ Browser Controls Rail & Themes", activeTheme, menuDensity);
        addItemRow(content,
                "Tabletop Themes ([ Theme: ... ])",
                "Tap the theme button in the Launcher header to open the Tabletop Theme Picker modal. Choose between 9 authentic themes: University of Michigan (Maize & Blue), Detroit Pistons, Detroit Lions, Detroit Tigers, Utica Unicorns, Tournament Walnut (Walnut & Cream), Club Emerald (Tournament Green & Ivory), Midnight Carbon (Stealth Carbon & Ice), and Classic Slate. Dynamic live re-theming without reloading web pages, persisted in SharedPreferences.",
                activeTheme, menuDensity);
        addItemRow(content,
                "Tabletop Duos & One-Tap Split Presets",
                "Launch popular paired app combinations in a single tap from the 'Tabletop Duos' row in Launcher (Chess + YouTube, ChessKids + YouTube, Chess + Apple TV, Lichess + Twitch, Chess + Reddit, Colonist + YouTube).",
                activeTheme, menuDensity);
        addItemRow(content,
                "App Launcher & Custom Reordering",
                "• Tap any app card to launch it.\n• Long press card (500ms): Opens Site Settings to configure User-Agent & Right Margin.\n• [✎ Edit] / [✓ Save] button: Tap to enter edit mode, allowing you to drag cards horizontally to reorder them and use [▲] / [▼] to move that entire category row up/down. Tap [✓ Save] to persist your layout.",
                activeTheme, menuDensity);
        addItemRow(content,
                "✕  Close Menu",
                "Short tap: Dismiss the Browser Controls rail or Launcher. The Browser Controls rail persists across page play and rotations until explicitly dismissed via ✕.",
                activeTheme, menuDensity);
        addItemRow(content,
                "⬅  Back",
                "Short tap: Navigate backward in browser history or exit fullscreen HTML5 video.",
                activeTheme, menuDensity);
        addItemRow(content,
                "＋  Zoom In",
                "Short tap: Step web page zoom up (75% → 100% → 125% → 150% → 200%).",
                activeTheme, menuDensity);
        addItemRow(content,
                "－  Zoom Out",
                "Short tap: Step web page zoom down (200% → 150% → 125% → 100% → 75%).",
                activeTheme, menuDensity);
        addItemRow(content,
                "↺  Refresh",
                "Short tap: Reload the current web page.",
                activeTheme, menuDensity);
        addItemRow(content,
                "🖥 / 📱  Desktop / Mobile User-Agent",
                "Short tap: Toggle between Desktop (🖥) and Mobile (📱) mode for the current session.\n3-second hold: Save Desktop or Mobile as the persistent default for just this site.",
                activeTheme, menuDensity);
        addItemRow(content,
                "◧  Margin & Tabletop Padding",
                "Short tap: 3-way cycle between Right Margin (106dp gutter), All 4 Sides (106dp inset padding), and No Margin (0dp edge-to-edge).\nLong press: Save current margin mode as the persistent default for active site.",
                activeTheme, menuDensity);
        addItemRow(content,
                "90°  Screen Rotation",
                "Short tap: Rotate display clockwise 90° through all 4 tabletop orientations (Landscape → Portrait → Rev. Landscape → Rev. Portrait). The Browser Controls rail remains persistent across rotations.\nLong press: Reset display orientation back to default Landscape.",
                activeTheme, menuDensity);
        addItemRow(content,
                "♟  Launcher Shortcut",
                "Short tap: Switch directly from the Browser Controls rail to the fullscreen Launcher.",
                activeTheme, menuDensity);

        // --- SECTION 4: SECONDARY TABS & POPUPS ---
        addSectionHeader(content, "✕ Secondary Tabs & External Links", activeTheme, menuDensity);
        addItemRow(content,
                "Isolated Secondary Tabs",
                "External links, new-window requests (target=\"_blank\"), and popups open in an isolated secondary tab layered on top, keeping your active game alive underneath.",
                activeTheme, menuDensity);
        addItemRow(content,
                "Draggable Floating ✕ Button",
                "A circular ✕ button appears when a secondary tab is active. Drag it anywhere on the tabletop screen; tap it to close the tab and return to your game. Fades to 35% opacity after 4s idle.",
                activeTheme, menuDensity);

        // --- SECTION 5: BOARD HARDWARE SYSTEM MENU ---
        addSectionHeader(content, "🔘 Board OS System Menu", activeTheme, menuDensity);
        addItemRow(content,
                "Hardware Menu Button",
                "Press the physical or floating Board system button to open the Board OS pause overlay.",
                activeTheme, menuDensity);
        addItemRow(content,
                "System Overlay Actions",
                "• Resume — Return to the active web page / game.\n• App Menu — Open the quick site launcher and bottom controls row.\n• Controls Guide — Open this tabletop reference guide.\n• Exit to Library — Safely quit ChessBoard and return to the Board home screen.",
                activeTheme, menuDensity);

        root.addView(scrollView);

        // Sleek, centered pill-shaped "Close Guide" button
        Button doneBtn = new Button(activity);
        doneBtn.setText("✕  Close Guide");
        doneBtn.setAllCaps(false);
        doneBtn.setTextColor(activeTheme.accentColor);
        doneBtn.setTextSize(13 * 2.0f);
        doneBtn.setTypeface(Typeface.DEFAULT_BOLD);

        int padH = (int) (32 * menuDensity);
        doneBtn.setPadding(padH, 0, padH, 0);

        GradientDrawable doneBg = new GradientDrawable();
        doneBg.setShape(GradientDrawable.RECTANGLE);
        doneBg.setColor(activeTheme.cardBgColor);
        doneBg.setCornerRadius(20 * menuDensity);
        doneBg.setStroke((int) (1.2f * menuDensity), activeTheme.accentColor);
        doneBtn.setBackground(doneBg);

        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (40 * menuDensity));
        doneParams.gravity = Gravity.CENTER_HORIZONTAL;
        doneParams.setMargins(0, (int) (4 * menuDensity), 0, 0);
        doneBtn.setLayoutParams(doneParams);
        root.addView(doneBtn);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(root)
                .setCancelable(true)
                .create();

        doneBtn.setOnClickListener(v -> dismiss());

        currentDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (currentDialog == dialog) {
                currentDialog = null;
            }
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(cardWidth, Math.min(maxCardHeight, (int) (screenHeight * 0.88f)));
        }
    }

    private static void addSectionHeader(LinearLayout parent, String title,
                                         ThemeManager.Theme theme, float menuDensity) {
        TextView tv = new TextView(parent.getContext());
        tv.setText(title);
        tv.setTextColor(theme.accentColor);
        tv.setTextSize(13 * 2.0f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int) (12 * menuDensity), 0, (int) (6 * menuDensity));
        tv.setLayoutParams(lp);
        parent.addView(tv);
    }

    private static void addItemRow(LinearLayout parent, String label, String description,
                                   ThemeManager.Theme theme, float menuDensity) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(theme.cardBgColor);
        rowBg.setCornerRadius(8 * menuDensity);
        rowBg.setStroke((int) (1 * menuDensity), theme.cardBorderColor);
        row.setBackground(rowBg);

        int padH = (int) (12 * menuDensity);
        int padV = (int) (8 * menuDensity);
        row.setPadding(padH, padV, padH, padV);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, (int) (6 * menuDensity));
        row.setLayoutParams(rowParams);

        TextView labelTv = new TextView(parent.getContext());
        labelTv.setText(label);
        labelTv.setTextColor(Color.WHITE);
        labelTv.setTextSize(12 * 2.0f);
        labelTv.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(labelTv);

        TextView descTv = new TextView(parent.getContext());
        descTv.setText(description);
        descTv.setTextColor(Color.parseColor("#CBD5E1"));
        descTv.setTextSize(10.5f * 2.0f);
        descTv.setLineSpacing(2 * menuDensity, 1.15f);

        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.setMargins(0, (int) (2 * menuDensity), 0, 0);
        descTv.setLayoutParams(descParams);
        row.addView(descTv);

        parent.addView(row);
    }
}
