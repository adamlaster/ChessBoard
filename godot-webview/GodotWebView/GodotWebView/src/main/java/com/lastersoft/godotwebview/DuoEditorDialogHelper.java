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
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 2x Tabletop-scaled dialog for adding custom Tabletop Duos (split-screen app combinations)
 * directly from the launcher.
 */
public class DuoEditorDialogHelper {

    public interface OnDuoCreatedListener {
        void onDuoCreated(AppCatalog.SiteEntry duo);
    }

    private static AlertDialog currentDialog = null;

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

    public static void show(Activity activity, ThemeManager.Theme theme, float menuDensity, OnDuoCreatedListener listener) {
        dismiss();
        if (activity == null || activity.isFinishing()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        ScrollView scrollView = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * menuDensity);
        root.setPadding(pad, pad, pad, pad);

        GradientDrawable dialogBg = new GradientDrawable();
        dialogBg.setShape(GradientDrawable.RECTANGLE);
        dialogBg.setColor(Color.parseColor("#121620"));
        dialogBg.setCornerRadius(16 * menuDensity);
        dialogBg.setStroke((int) (1.5f * menuDensity), theme.accentColor);
        root.setBackground(dialogBg);

        // Header Title
        TextView titleView = new TextView(activity);
        titleView.setText("➕ Create Tabletop Duo");
        titleView.setTextColor(theme.accentColor);
        titleView.setTextSize(14f * MenuOverlayView.MENU_SCALE);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(titleView);

        TextView subtitleView = new TextView(activity);
        subtitleView.setText("Pair any two web applications for simultaneous side-by-side tabletop play.");
        subtitleView.setTextColor(Color.parseColor("#94A3B8"));
        subtitleView.setTextSize(9f * MenuOverlayView.MENU_SCALE);
        subtitleView.setPadding(0, (int) (2 * menuDensity), 0, (int) (14 * menuDensity));
        root.addView(subtitleView);

        // --- LEFT APP SECTION ---
        TextView leftSectionLabel = new TextView(activity);
        leftSectionLabel.setText("LEFT SCREEN (PRIMARY APP)");
        leftSectionLabel.setTextColor(Color.parseColor("#E2E8F0"));
        leftSectionLabel.setTextSize(9.5f * MenuOverlayView.MENU_SCALE);
        leftSectionLabel.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(leftSectionLabel);

        HorizontalScrollView leftPillsScroll = new HorizontalScrollView(activity);
        leftPillsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout leftPills = new LinearLayout(activity);
        leftPills.setOrientation(LinearLayout.HORIZONTAL);
        leftPills.setPadding(0, (int) (4 * menuDensity), 0, (int) (6 * menuDensity));

        EditText leftUrlInput = createStyledEditText(activity, theme, menuDensity, "https://www.chess.com");
        EditText duoNameInput = createStyledEditText(activity, theme, menuDensity, "Chess + YouTube");
        EditText duoIconInput = createStyledEditText(activity, theme, menuDensity, "♟️▶️");

        final String[] leftName = {"Chess"};
        final String[] leftIcon = {"♟"};
        final String[] rightName = {"YouTube"};
        final String[] rightIcon = {"▶️"};

        Runnable updateAutoFields = () -> {
            duoNameInput.setText(leftName[0] + " + " + rightName[0]);
            duoIconInput.setText(leftIcon[0] + rightIcon[0]);
        };

        addAppPill(activity, leftPills, theme, menuDensity, "♟ Chess.com", "https://www.chess.com", true, (n, u) -> {
            leftName[0] = "Chess";
            leftIcon[0] = "♟";
            leftUrlInput.setText(u);
            updateAutoFields.run();
        });
        addAppPill(activity, leftPills, theme, menuDensity, "♞ Lichess", "https://lichess.org/analysis", false, (n, u) -> {
            leftName[0] = "Lichess";
            leftIcon[0] = "♞";
            leftUrlInput.setText(u);
            updateAutoFields.run();
        });
        addAppPill(activity, leftPills, theme, menuDensity, "👑 ChessKids", "https://www.chesskid.com", false, (n, u) -> {
            leftName[0] = "ChessKids";
            leftIcon[0] = "🧒";
            leftUrlInput.setText(u);
            updateAutoFields.run();
        });
        addAppPill(activity, leftPills, theme, menuDensity, "🎯 ChessReps", "https://chessreps.com", false, (n, u) -> {
            leftName[0] = "ChessReps";
            leftIcon[0] = "🎯";
            leftUrlInput.setText(u);
            updateAutoFields.run();
        });
        addAppPill(activity, leftPills, theme, menuDensity, "🐑 Colonist", "https://colonist.io", false, (n, u) -> {
            leftName[0] = "Colonist";
            leftIcon[0] = "🐑";
            leftUrlInput.setText(u);
            updateAutoFields.run();
        });

        leftPillsScroll.addView(leftPills);
        root.addView(leftPillsScroll);
        root.addView(leftUrlInput);

        View spacer1 = new View(activity);
        spacer1.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (12 * menuDensity)));
        root.addView(spacer1);

        // --- RIGHT APP SECTION ---
        TextView rightSectionLabel = new TextView(activity);
        rightSectionLabel.setText("RIGHT SCREEN (SECONDARY APP)");
        rightSectionLabel.setTextColor(Color.parseColor("#E2E8F0"));
        rightSectionLabel.setTextSize(9.5f * MenuOverlayView.MENU_SCALE);
        rightSectionLabel.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(rightSectionLabel);

        HorizontalScrollView rightPillsScroll = new HorizontalScrollView(activity);
        rightPillsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout rightPills = new LinearLayout(activity);
        rightPills.setOrientation(LinearLayout.HORIZONTAL);
        rightPills.setPadding(0, (int) (4 * menuDensity), 0, (int) (6 * menuDensity));

        EditText rightUrlInput = createStyledEditText(activity, theme, menuDensity, "https://www.youtube.com");

        addAppPill(activity, rightPills, theme, menuDensity, "▶️ YouTube", "https://www.youtube.com", true, (n, u) -> {
            rightName[0] = "YouTube";
            rightIcon[0] = "▶️";
            rightUrlInput.setText(u);
            updateAutoFields.run();
        });
        addAppPill(activity, rightPills, theme, menuDensity, "🍎 Apple TV", "https://tv.apple.com", false, (n, u) -> {
            rightName[0] = "Apple TV";
            rightIcon[0] = "🍎";
            rightUrlInput.setText(u);
            updateAutoFields.run();
        });
        addAppPill(activity, rightPills, theme, menuDensity, "🟣 Twitch", "https://www.twitch.tv", false, (n, u) -> {
            rightName[0] = "Twitch";
            rightIcon[0] = "🟣";
            rightUrlInput.setText(u);
            updateAutoFields.run();
        });
        addAppPill(activity, rightPills, theme, menuDensity, "👽 Reddit", "https://www.reddit.com/r/chess", false, (n, u) -> {
            rightName[0] = "Reddit";
            rightIcon[0] = "👽";
            rightUrlInput.setText(u);
            updateAutoFields.run();
        });
        addAppPill(activity, rightPills, theme, menuDensity, "🦚 Peacock", "https://www.peacocktv.com", false, (n, u) -> {
            rightName[0] = "Peacock";
            rightIcon[0] = "🦚";
            rightUrlInput.setText(u);
            updateAutoFields.run();
        });
        addAppPill(activity, rightPills, theme, menuDensity, "🎧 DJ Decks", "https://buttonbass.com/DJ.html", false, (n, u) -> {
            rightName[0] = "DJ Turntables";
            rightIcon[0] = "🎧";
            rightUrlInput.setText(u);
            updateAutoFields.run();
        });

        rightPillsScroll.addView(rightPills);
        root.addView(rightPillsScroll);
        root.addView(rightUrlInput);

        View spacer2 = new View(activity);
        spacer2.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (12 * menuDensity)));
        root.addView(spacer2);

        // --- DUO DETAILS SECTION ---
        LinearLayout detailsRow = new LinearLayout(activity);
        detailsRow.setOrientation(LinearLayout.HORIZONTAL);
        detailsRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout nameCol = new LinearLayout(activity);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        nameCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.72f));

        TextView nameLabel = new TextView(activity);
        nameLabel.setText("DUO PRESET NAME");
        nameLabel.setTextColor(Color.parseColor("#E2E8F0"));
        nameLabel.setTextSize(9f * MenuOverlayView.MENU_SCALE);
        nameLabel.setTypeface(Typeface.DEFAULT_BOLD);
        nameCol.addView(nameLabel);
        nameCol.addView(duoNameInput);

        LinearLayout iconCol = new LinearLayout(activity);
        iconCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.28f);
        iconParams.setMargins((int) (8 * menuDensity), 0, 0, 0);
        iconCol.setLayoutParams(iconParams);

        TextView iconLabel = new TextView(activity);
        iconLabel.setText("ICON / EMOJI");
        iconLabel.setTextColor(Color.parseColor("#E2E8F0"));
        iconLabel.setTextSize(9f * MenuOverlayView.MENU_SCALE);
        iconLabel.setTypeface(Typeface.DEFAULT_BOLD);
        iconCol.addView(iconLabel);
        iconCol.addView(duoIconInput);

        detailsRow.addView(nameCol);
        detailsRow.addView(iconCol);
        root.addView(detailsRow);

        // Buttons Bar
        LinearLayout btnBar = new LinearLayout(activity);
        btnBar.setOrientation(LinearLayout.HORIZONTAL);
        btnBar.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        btnBar.setPadding(0, (int) (18 * menuDensity), 0, 0);

        Button resetBtn = new Button(activity);
        resetBtn.setText("↺ Reset Defaults");
        resetBtn.setTextColor(Color.parseColor("#EF4444"));
        resetBtn.setTextSize(9.5f * MenuOverlayView.MENU_SCALE);
        resetBtn.setBackgroundColor(Color.TRANSPARENT);
        resetBtn.setOnClickListener(v -> {
            AppCatalog.resetDuos(activity);
            dismiss();
            if (listener != null) {
                listener.onDuoCreated(null);
            }
        });
        btnBar.addView(resetBtn);

        View btnSpacer = new View(activity);
        btnSpacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1.0f));
        btnBar.addView(btnSpacer);

        Button cancelBtn = new Button(activity);
        cancelBtn.setText("Cancel");
        cancelBtn.setTextColor(Color.parseColor("#94A3B8"));
        cancelBtn.setTextSize(9.5f * MenuOverlayView.MENU_SCALE);
        cancelBtn.setBackgroundColor(Color.TRANSPARENT);
        cancelBtn.setOnClickListener(v -> dismiss());
        btnBar.addView(cancelBtn);

        GradientDrawable addBtnBg = new GradientDrawable();
        addBtnBg.setShape(GradientDrawable.RECTANGLE);
        addBtnBg.setColor(theme.accentColor);
        addBtnBg.setCornerRadius(10 * menuDensity);

        Button addBtn = new Button(activity);
        addBtn.setText("➕ Add Duo");
        addBtn.setTextColor(Color.parseColor("#0F172A"));
        addBtn.setTypeface(Typeface.DEFAULT_BOLD);
        addBtn.setTextSize(10f * MenuOverlayView.MENU_SCALE);
        addBtn.setBackground(addBtnBg);
        addBtn.setPadding((int) (16 * menuDensity), (int) (8 * menuDensity), (int) (16 * menuDensity), (int) (8 * menuDensity));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addParams.setMargins((int) (10 * menuDensity), 0, 0, 0);
        addBtn.setLayoutParams(addParams);

        addBtn.setOnClickListener(v -> {
            String name = duoNameInput.getText().toString().trim();
            String pUrl = leftUrlInput.getText().toString().trim();
            String sUrl = rightUrlInput.getText().toString().trim();
            String icon = duoIconInput.getText().toString().trim();

            if (name.isEmpty() || pUrl.isEmpty() || sUrl.isEmpty()) {
                Toast.makeText(activity, "Please fill in Duo name and both URLs", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!pUrl.startsWith("http://") && !pUrl.startsWith("https://")) {
                pUrl = "https://" + pUrl;
            }
            if (!sUrl.startsWith("http://") && !sUrl.startsWith("https://")) {
                sUrl = "https://" + sUrl;
            }
            if (icon.isEmpty()) {
                icon = "◫";
            }

            AppCatalog.SiteEntry newDuo = new AppCatalog.SiteEntry(
                    name, pUrl, sUrl, icon, "Custom Duo", "DUO");
            AppCatalog.addDuo(activity, newDuo);
            dismiss();

            if (listener != null) {
                listener.onDuoCreated(newDuo);
            }
        });

        btnBar.addView(addBtn);
        root.addView(btnBar);

        scrollView.addView(root);
        builder.setView(scrollView);

        currentDialog = builder.create();
        if (currentDialog.getWindow() != null) {
            currentDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        currentDialog.show();

        int dialogWidth = (int) (480 * menuDensity);
        if (currentDialog.getWindow() != null) {
            currentDialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static EditText createStyledEditText(Activity activity, ThemeManager.Theme theme, float menuDensity, String defaultText) {
        EditText et = new EditText(activity);
        et.setText(defaultText);
        et.setTextColor(Color.WHITE);
        et.setTextSize(9.5f * MenuOverlayView.MENU_SCALE);
        et.setSingleLine(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(Color.parseColor("#1A202C"));
        bg.setCornerRadius(8 * menuDensity);
        bg.setStroke((int) (1 * menuDensity), Color.parseColor("#2D3748"));
        et.setBackground(bg);
        int padH = (int) (10 * menuDensity);
        int padV = (int) (8 * menuDensity);
        et.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, (int) (3 * menuDensity), 0, (int) (4 * menuDensity));
        et.setLayoutParams(params);
        return et;
    }

    private interface PillSelectCallback {
        void onSelected(String name, String url);
    }

    private static void addAppPill(Activity activity, LinearLayout container, ThemeManager.Theme theme,
                                   float menuDensity, String label, String url, boolean isDefault, PillSelectCallback callback) {
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setShape(GradientDrawable.RECTANGLE);
        pillBg.setColor(isDefault ? theme.primaryColor : Color.parseColor("#1E2433"));
        pillBg.setCornerRadius(12 * menuDensity);
        pillBg.setStroke((int) (1 * menuDensity), isDefault ? theme.accentColor : Color.parseColor("#334155"));

        TextView pill = new TextView(activity);
        pill.setText(label);
        pill.setTextColor(isDefault ? Color.WHITE : Color.parseColor("#94A3B8"));
        pill.setTextSize(8.5f * MenuOverlayView.MENU_SCALE);
        pill.setTypeface(Typeface.DEFAULT_BOLD);
        pill.setBackground(pillBg);
        int padH = (int) (10 * menuDensity);
        int padV = (int) (5 * menuDensity);
        pill.setPadding(padH, padV, padH, padV);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, (int) (6 * menuDensity), 0);
        pill.setLayoutParams(params);

        pill.setOnClickListener(v -> {
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                if (child instanceof TextView) {
                    GradientDrawable bg = new GradientDrawable();
                    bg.setShape(GradientDrawable.RECTANGLE);
                    bg.setColor(Color.parseColor("#1E2433"));
                    bg.setCornerRadius(12 * menuDensity);
                    bg.setStroke((int) (1 * menuDensity), Color.parseColor("#334155"));
                    child.setBackground(bg);
                    ((TextView) child).setTextColor(Color.parseColor("#94A3B8"));
                }
            }
            GradientDrawable activeBg = new GradientDrawable();
            activeBg.setShape(GradientDrawable.RECTANGLE);
            activeBg.setColor(theme.primaryColor);
            activeBg.setCornerRadius(12 * menuDensity);
            activeBg.setStroke((int) (1 * menuDensity), theme.accentColor);
            pill.setBackground(activeBg);
            pill.setTextColor(Color.WHITE);

            if (callback != null) {
                callback.onSelected(label, url);
            }
        });

        container.addView(pill);
    }
}

