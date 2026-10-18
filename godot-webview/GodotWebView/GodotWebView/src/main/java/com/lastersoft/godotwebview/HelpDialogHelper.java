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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Displays a comprehensive, 2x scaled dark modal dialog outlining all controls,
 * gestures (single-finger double-tap arrows, piece movement, long presses),
 * and interface features of ChessBoard.
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
        rootBg.setColor(Color.parseColor("#F01E1E1E")); // High-opacity dark acrylic
        rootBg.setCornerRadius(18 * menuDensity);
        rootBg.setStroke((int) (1.5f * menuDensity), Color.parseColor("#33FFFFFF"));
        root.setBackground(rootBg);

        // Header container with title/subtitle and top-right close button
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
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(15 * 2.0f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);
        titleContainer.addView(titleView);

        TextView subtitleView = new TextView(activity);
        subtitleView.setText("Quick reference for tabletop play on the Board");
        subtitleView.setTextColor(Color.parseColor("#99FFFFFF"));
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
        addSectionHeader(content, "♟️ Tabletop Chess Gestures (Chess.com & Lichess)", menuDensity);
        addItemRow(content,
                "Double-Tap & Drag",
                "Double-tap on a starting square and drag across to draw live tactical arrows. Pieces on the origin square will not move.",
                menuDensity);
        addItemRow(content,
                "Double-Tap in Place",
                "Double-tap quickly on any square without dragging to toggle its color highlight.",
                menuDensity);
        addItemRow(content,
                "Additive Markings",
                "Multiple arrows and square highlights persist together on the board without disappearing as you draw additional lines.",
                menuDensity);
        addItemRow(content,
                "Clear Markings",
                "Tap any square once or make a piece move to immediately clear all arrows and square highlights.",
                menuDensity);
        addItemRow(content,
                "1-Finger Piece Movement",
                "Touch and drag any piece (> 10px) to move it normally with instant 0ms native responsiveness.",
                menuDensity);
        addItemRow(content,
                "Desktop Sidebar Menus",
                "On Chess.com desktop mode, tapping main sidebar categories (Play, Puzzles, Learn, etc.) opens and stabilizes flyout submenus so options can be chosen without navigating away. Tap outside to dismiss.",
                menuDensity);

        // --- SECTION 2: MENU CONTROLS ROW ---
        addSectionHeader(content, "🎛️ Bottom Menu Controls Row", menuDensity);
        addItemRow(content,
                "App Launcher & Categories",
                "Tap any primary site button (Chess.com, Lichess, etc.) or expand the Extra Apps category accordion to switch web apps instantly.",
                menuDensity);
        addItemRow(content,
                "⬅  Back",
                "Short tap: Navigate backward in browser history or exit fullscreen HTML5 video.",
                menuDensity);
        addItemRow(content,
                "＋  Zoom In",
                "Short tap: Step web page zoom up (75% → 100% → 125% → 150% → 200%).",
                menuDensity);
        addItemRow(content,
                "－  Zoom Out",
                "Short tap: Step web page zoom down (200% → 150% → 125% → 100% → 75%).",
                menuDensity);
        addItemRow(content,
                "↺  Refresh",
                "Short tap: Reload the current web page.",
                menuDensity);
        addItemRow(content,
                "🖥 / 📱  Desktop / Mobile User-Agent",
                "Short tap: Toggle between Desktop (🖥) and Mobile (📱) mode for the current session.\n3-second hold: Save Desktop or Mobile as the persistent default for just this site.",
                menuDensity);
        addItemRow(content,
                "◧  Right Margin / Board Gutter",
                "Short tap: Toggle between edge-to-edge (0dp) and 106dp right margin gutter live.\nLong press: Save margin mode as persistent default for active site.",
                menuDensity);
        addItemRow(content,
                "90°  Screen Rotation",
                "Short tap: Rotate display clockwise 90° through all 4 tabletop orientations (Landscape → Portrait → Rev. Landscape → Rev. Portrait).\nLong press: Reset display orientation back to default Landscape.",
                menuDensity);
        addItemRow(content,
                "✕  Close Menu",
                "Short tap: Dismiss the ChessBoard menu card (tapping the backdrop scrim outside the card also dismisses it).",
                menuDensity);

        // --- SECTION 3: SECONDARY TABS & POPUPS ---
        addSectionHeader(content, "✕ Secondary Tabs & External Links", menuDensity);
        addItemRow(content,
                "Isolated Secondary Tabs",
                "External links, new-window requests (target=\"_blank\"), and popups open in an isolated secondary tab layered on top, keeping your active game alive underneath.",
                menuDensity);
        addItemRow(content,
                "Draggable Floating ✕ Button",
                "A circular ✕ button appears when a secondary tab is active. Drag it anywhere on the tabletop screen; tap it to close the tab and return to your game. Fades to 35% opacity after 4s idle.",
                menuDensity);

        // --- SECTION 4: BOARD HARDWARE SYSTEM MENU ---
        addSectionHeader(content, "🔘 Board OS System Menu", menuDensity);
        addItemRow(content,
                "Hardware Menu Button",
                "Press the physical or floating Board system button to open the Board OS pause overlay.",
                menuDensity);
        addItemRow(content,
                "System Overlay Actions",
                "• Resume — Return to the active web page / game.\n• App Menu — Open the quick site launcher and bottom controls row.\n• Controls Guide — Open this tabletop reference guide.\n• Exit to Library — Safely quit ChessBoard and return to the Board home screen.",
                menuDensity);

        root.addView(scrollView);

        // Sleek, centered pill-shaped "Close Guide" button
        Button doneBtn = new Button(activity);
        doneBtn.setText("✕  Close Guide");
        doneBtn.setAllCaps(false);
        doneBtn.setTextColor(Color.parseColor("#B4C6FF"));
        doneBtn.setTextSize(13 * 2.0f);
        doneBtn.setTypeface(Typeface.DEFAULT_BOLD);

        int padH = (int) (32 * menuDensity);
        doneBtn.setPadding(padH, 0, padH, 0);

        GradientDrawable doneBg = new GradientDrawable();
        doneBg.setShape(GradientDrawable.RECTANGLE);
        doneBg.setColor(Color.parseColor("#1E2433"));
        doneBg.setCornerRadius(20 * menuDensity);
        doneBg.setStroke((int) (1.2f * menuDensity), Color.parseColor("#5C7CFA"));
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

    private static void addSectionHeader(LinearLayout parent, String title, float menuDensity) {
        TextView tv = new TextView(parent.getContext());
        tv.setText(title);
        tv.setTextColor(Color.parseColor("#91A7FF")); // Soft accent blue
        tv.setTextSize(13 * 2.0f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int) (10 * menuDensity), 0, (int) (6 * menuDensity));
        tv.setLayoutParams(lp);
        parent.addView(tv);
    }

    private static void addItemRow(LinearLayout parent, String label, String description, float menuDensity) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(Color.parseColor("#262626"));
        rowBg.setCornerRadius(8 * menuDensity);
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
        descTv.setTextColor(Color.parseColor("#D0D0D0"));
        descTv.setTextSize(11 * 2.0f);
        descTv.setLineSpacing(2 * menuDensity, 1.15f);

        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.setMargins(0, (int) (2 * menuDensity), 0, 0);
        descTv.setLayoutParams(descParams);
        row.addView(descTv);

        parent.addView(row);
    }
}

