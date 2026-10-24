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
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Tabletop dialog allowing users to choose between UI themes:
 * - University of Michigan (Maize & Blue)
 * - Detroit Pistons (Royal Blue & Red)
 * - Detroit Lions (Honolulu Blue & Silver)
 * - Detroit Tigers (Midnight Navy & Orange)
 * - Utica Unicorns (Purple & Red)
 * - Classic Slate (Obsidian & Indigo)
 */
public final class ThemePickerDialogHelper {

    private static AlertDialog currentDialog = null;

    public interface OnThemeSelectedListener {
        void onThemeSelected(ThemeManager.Theme selectedTheme);
    }

    private ThemePickerDialogHelper() {}

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

    public static void show(Activity activity, float menuDensity, OnThemeSelectedListener listener) {
        if (activity == null || activity.isFinishing()) return;
        dismiss();

        ThemeManager.Theme activeTheme = ThemeManager.getActiveTheme(activity);

        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        int cardWidth = Math.min((int) (480 * menuDensity), (int) (screenWidth * 0.90f));
        int maxDialogHeight = (int) (screenHeight * 0.88f);
        int dialogHeight = Math.min((int) (520 * menuDensity), maxDialogHeight);

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

        // Header Title
        TextView titleView = new TextView(activity);
        titleView.setText("🎨 Tabletop Theme");
        titleView.setTextColor(activeTheme.accentColor);
        titleView.setTextSize(16.0f * MenuOverlayView.MENU_SCALE);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(titleView);

        // Subtitle
        TextView subtitleView = new TextView(activity);
        subtitleView.setText("Select your preferred color scheme for ChessBoard");
        subtitleView.setTextColor(Color.parseColor("#94A3B8"));
        subtitleView.setTextSize(9.5f * MenuOverlayView.MENU_SCALE);
        subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, (int) (4 * menuDensity), 0, (int) (14 * menuDensity));
        subtitleView.setLayoutParams(subLp);
        root.addView(subtitleView);

        // Scrollable Options Container
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setVerticalScrollBarEnabled(true);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollView.setLayoutParams(scrollLp);

        LinearLayout optionsContainer = new LinearLayout(activity);
        optionsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams optContLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        optionsContainer.setLayoutParams(optContLp);

        for (ThemeManager.Theme theme : ThemeManager.Theme.values()) {
            boolean isSelected = (theme == activeTheme);
            View optionView = createThemeOptionCard(activity, theme, isSelected, menuDensity, v -> {
                ThemeManager.setActiveTheme(activity, theme);
                dismiss();
                if (listener != null) {
                    listener.onThemeSelected(theme);
                }
            });
            optionsContainer.addView(optionView);
        }
        scrollView.addView(optionsContainer);
        root.addView(scrollView);

        // Done Button
        Button doneBtn = new Button(activity);
        doneBtn.setText("Done");
        double luminance = (0.299 * Color.red(activeTheme.accentColor)
                + 0.587 * Color.green(activeTheme.accentColor)
                + 0.114 * Color.blue(activeTheme.accentColor)) / 255.0;
        doneBtn.setTextColor(luminance > 0.55 ? Color.parseColor("#001226") : Color.WHITE);
        doneBtn.setTextSize(11.0f * MenuOverlayView.MENU_SCALE);
        doneBtn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable doneBg = new GradientDrawable();
        doneBg.setShape(GradientDrawable.RECTANGLE);
        doneBg.setColor(activeTheme.accentColor);
        doneBg.setCornerRadius(12 * menuDensity);
        doneBtn.setBackground(doneBg);
        int doneH = (int) (36 * menuDensity);
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(
                (int) (140 * menuDensity), doneH);
        doneLp.setMargins(0, (int) (14 * menuDensity), 0, 0);
        doneBtn.setLayoutParams(doneLp);
        doneBtn.setOnClickListener(v -> dismiss());
        root.addView(doneBtn);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setView(root);
        builder.setCancelable(true);

        currentDialog = builder.create();
        if (currentDialog.getWindow() != null) {
            currentDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        currentDialog.show();
        if (currentDialog.getWindow() != null) {
            currentDialog.getWindow().setLayout(cardWidth, dialogHeight);
        }
    }

    private static View createThemeOptionCard(Activity act, ThemeManager.Theme theme, boolean isSelected,
                                              float menuDensity, View.OnClickListener clickListener) {
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int padH = (int) (14 * menuDensity);
        int padV = (int) (10 * menuDensity);
        card.setPadding(padH, padV, padH, padV);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setColor(theme.cardBgColor);
        cardBg.setCornerRadius(14 * menuDensity);
        if (isSelected) {
            cardBg.setStroke((int) (2 * menuDensity), theme.accentColor);
        } else {
            cardBg.setStroke((int) (1 * menuDensity), Color.parseColor("#253347"));
        }
        card.setBackground(cardBg);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, (int) (8 * menuDensity));
        card.setLayoutParams(cardLp);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(clickListener);

        // Preview Swatch Pill
        TextView swatchView = new TextView(act);
        swatchView.setText(theme.swatchText);
        swatchView.setTextSize(12.0f * MenuOverlayView.MENU_SCALE);
        swatchView.setTypeface(Typeface.DEFAULT_BOLD);
        swatchView.setTextColor(theme.accentColor);
        swatchView.setGravity(Gravity.CENTER);
        GradientDrawable swatchBg = new GradientDrawable();
        swatchBg.setShape(GradientDrawable.OVAL);
        swatchBg.setColor(theme.primaryColor);
        swatchBg.setStroke((int) (1.5f * menuDensity), theme.accentColor);
        swatchView.setBackground(swatchBg);
        int swatchSize = (int) (38 * menuDensity);
        LinearLayout.LayoutParams swatchLp = new LinearLayout.LayoutParams(swatchSize, swatchSize);
        swatchLp.setMargins(0, 0, (int) (12 * menuDensity), 0);
        swatchView.setLayoutParams(swatchLp);
        card.addView(swatchView);

        // Text Info (Title + Subtitle)
        LinearLayout infoLayout = new LinearLayout(act);
        infoLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        infoLayout.setLayoutParams(infoLp);

        TextView nameView = new TextView(act);
        nameView.setText(theme.displayName);
        nameView.setTextColor(isSelected ? theme.accentColor : Color.parseColor("#F1F5F9"));
        nameView.setTextSize(11.0f * MenuOverlayView.MENU_SCALE);
        nameView.setTypeface(Typeface.DEFAULT_BOLD);
        infoLayout.addView(nameView);

        TextView descView = new TextView(act);
        descView.setText(theme.subtitle);
        descView.setTextColor(Color.parseColor("#94A3B8"));
        descView.setTextSize(8.5f * MenuOverlayView.MENU_SCALE);
        infoLayout.addView(descView);

        card.addView(infoLayout);

        // Status Indicator Badge
        if (isSelected) {
            TextView activeBadge = new TextView(act);
            activeBadge.setText("✓ ACTIVE");
            activeBadge.setTextSize(8.0f * MenuOverlayView.MENU_SCALE);
            activeBadge.setTypeface(Typeface.DEFAULT_BOLD);
            activeBadge.setTextColor(theme.accentColor);
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setShape(GradientDrawable.RECTANGLE);
            badgeBg.setColor(Color.parseColor("#152538"));
            badgeBg.setCornerRadius(8 * menuDensity);
            badgeBg.setStroke((int) (1 * menuDensity), theme.accentColor);
            activeBadge.setBackground(badgeBg);
            activeBadge.setPadding((int) (6 * menuDensity), (int) (2 * menuDensity),
                    (int) (6 * menuDensity), (int) (2 * menuDensity));
            card.addView(activeBadge);
        }

        return card;
    }
}
