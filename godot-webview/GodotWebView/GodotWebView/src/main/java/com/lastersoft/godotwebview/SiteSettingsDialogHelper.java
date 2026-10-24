package com.lastersoft.godotwebview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Tabletop dialog allowing users to configure per-site User-Agent mode (Desktop vs Mobile)
 * and custom Right Margin values (Edge-to-Edge 0dp, 106dp Gutter, or fine-tuned custom dp)
 * via long-press on any card in the Launcher.
 */
public class SiteSettingsDialogHelper {

    public static final String PREF_SITE_MARGIN_DP_PREFIX = "site_margin_dp_";
    public static final String PREF_SITE_MARGIN_MODE_PREFIX = "site_margin_mode_";
    public static final int DEFAULT_GUTTER_DP = 106;

    public static final int MARGIN_MODE_NONE = 0;
    public static final int MARGIN_MODE_RIGHT = 1;
    public static final int MARGIN_MODE_ALL = 2;

    public interface OnSettingsSavedListener {
        void onSaved(String siteKey, boolean isDesktop, int marginDp, int marginMode);
    }

    public static int getSiteDefaultMarginMode(Context context, String siteKey) {
        if (context != null && siteKey != null && !siteKey.isEmpty()) {
            SharedPreferences prefs = context.getSharedPreferences(UserAgentManager.PREFS_NAME, Context.MODE_PRIVATE);
            String modeKey = PREF_SITE_MARGIN_MODE_PREFIX + siteKey;
            if (prefs.contains(modeKey)) {
                return prefs.getInt(modeKey, MARGIN_MODE_NONE);
            }
            int dp = getSiteDefaultRightMarginDp(context, siteKey);
            if (dp > 0) {
                return MARGIN_MODE_RIGHT;
            }
        }
        return MARGIN_MODE_NONE;
    }

    public static void saveSiteDefaultMarginMode(Context context, String siteKey, int marginMode) {
        if (context != null && siteKey != null && !siteKey.isEmpty()) {
            context.getSharedPreferences(UserAgentManager.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(PREF_SITE_MARGIN_MODE_PREFIX + siteKey, marginMode)
                    .commit();
        }
    }

    public static int getSiteDefaultRightMarginDp(Context context, String siteKey) {
        if (context != null && siteKey != null && !siteKey.isEmpty()) {
            SharedPreferences prefs = context.getSharedPreferences(UserAgentManager.PREFS_NAME, Context.MODE_PRIVATE);
            String dpKey = PREF_SITE_MARGIN_DP_PREFIX + siteKey;
            if (prefs.contains(dpKey)) {
                return prefs.getInt(dpKey, 0);
            }
            // Check legacy boolean preference
            String boolKey = "site_margin_" + siteKey;
            if (prefs.contains(boolKey)) {
                return prefs.getBoolean(boolKey, false) ? DEFAULT_GUTTER_DP : 0;
            }
            SharedPreferences legacyPrefs = context.getSharedPreferences(UserAgentManager.LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
            if (legacyPrefs.contains(dpKey)) {
                return legacyPrefs.getInt(dpKey, 0);
            }
            if (legacyPrefs.contains(boolKey)) {
                return legacyPrefs.getBoolean(boolKey, false) ? DEFAULT_GUTTER_DP : 0;
            }
        }
        return 0; // Default edge-to-edge
    }

    public static void saveSiteDefaultRightMarginDp(Context context, String siteKey, int marginDp) {
        if (context != null && siteKey != null && !siteKey.isEmpty()) {
            context.getSharedPreferences(UserAgentManager.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(PREF_SITE_MARGIN_DP_PREFIX + siteKey, marginDp)
                    .commit();
        }
    }

    public static void show(Activity activity, AppCatalog.SiteEntry site, UserAgentManager uaManager,
                            float menuDensity, OnSettingsSavedListener listener) {
        if (activity == null || activity.isFinishing() || site == null) return;

        String rawKey = UserAgentManager.getSiteKey(site.url);
        final String siteKey = (!rawKey.isEmpty())
                ? rawKey
                : site.name.toLowerCase().replaceAll("[^a-z0-9._-]", "");
        final boolean currentDesktop = (uaManager != null)
                ? uaManager.getSiteDefaultDesktopMode(activity, siteKey)
                : false;
        final int currentMarginDp = getSiteDefaultRightMarginDp(activity, siteKey);
        final int currentMarginMode = getSiteDefaultMarginMode(activity, siteKey);

        // Mutable state holders
        final boolean[] selectedDesktop = new boolean[]{currentDesktop};
        final int[] selectedMarginMode = new int[]{currentMarginMode};
        final int[] selectedMarginDp = new int[]{(currentMarginDp > 0) ? currentMarginDp : (currentMarginMode == MARGIN_MODE_NONE ? 0 : DEFAULT_GUTTER_DP)};

        // Root container
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * menuDensity);
        root.setPadding(pad, pad, pad, pad);

        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setShape(GradientDrawable.RECTANGLE);
        rootBg.setColor(Color.parseColor("#F5121622"));
        rootBg.setCornerRadius(18 * menuDensity);
        rootBg.setStroke((int) (1.2f * menuDensity), Color.parseColor("#28324A"));
        root.setBackground(rootBg);

        // 1. Header: Icon + Site Name + URL
        LinearLayout headerLayout = new LinearLayout(activity);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        headerLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Icon Box
        GradientDrawable iconBoxBg = new GradientDrawable();
        iconBoxBg.setShape(GradientDrawable.RECTANGLE);
        iconBoxBg.setColor(Color.parseColor("#22293C"));
        iconBoxBg.setCornerRadius(8 * menuDensity);

        TextView iconView = new TextView(activity);
        iconView.setText(site.icon);
        iconView.setTextSize(13 * MenuOverlayView.MENU_SCALE);
        iconView.setGravity(Gravity.CENTER);
        iconView.setBackground(iconBoxBg);
        int iconBoxSize = (int) (32 * menuDensity);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconBoxSize, iconBoxSize);
        iconParams.setMargins(0, 0, (int) (10 * menuDensity), 0);
        iconView.setLayoutParams(iconParams);
        headerLayout.addView(iconView);

        // Titles
        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView nameView = new TextView(activity);
        nameView.setText(site.name + " Settings");
        nameView.setTextColor(Color.WHITE);
        nameView.setTextSize(12.5f * MenuOverlayView.MENU_SCALE);
        nameView.setTypeface(Typeface.DEFAULT_BOLD);
        titleCol.addView(nameView);

        TextView urlView = new TextView(activity);
        urlView.setText(site.url);
        urlView.setTextColor(Color.parseColor("#8E9BB5"));
        urlView.setTextSize(8.5f * MenuOverlayView.MENU_SCALE);
        urlView.setSingleLine(true);
        urlView.setEllipsize(TextUtils.TruncateAt.END);
        titleCol.addView(urlView);

        headerLayout.addView(titleCol);
        root.addView(headerLayout);

        // Divider
        addDivider(root, activity, menuDensity);

        // 2. User-Agent Section
        addSectionTitle(root, activity, "🖥 / 📱  User-Agent Mode", menuDensity);

        TextView uaDesc = new TextView(activity);
        uaDesc.setText("Select Desktop for full tabletop web layout, or Mobile for responsive touch interfaces.");
        uaDesc.setTextColor(Color.parseColor("#94A3B8"));
        uaDesc.setTextSize(8 * MenuOverlayView.MENU_SCALE);
        LinearLayout.LayoutParams uaDescParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        uaDescParams.setMargins(0, 0, 0, (int) (8 * menuDensity));
        uaDesc.setLayoutParams(uaDescParams);
        root.addView(uaDesc);

        LinearLayout uaToggleRow = new LinearLayout(activity);
        uaToggleRow.setOrientation(LinearLayout.HORIZONTAL);
        uaToggleRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (32 * menuDensity)));

        Button desktopBtn = new Button(activity);
        desktopBtn.setText("🖥  Desktop");
        desktopBtn.setAllCaps(false);
        desktopBtn.setTextSize(9.5f * MenuOverlayView.MENU_SCALE);
        desktopBtn.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams dParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        dParams.setMargins(0, 0, (int) (4 * menuDensity), 0);
        desktopBtn.setLayoutParams(dParams);

        Button mobileBtn = new Button(activity);
        mobileBtn.setText("📱  Mobile");
        mobileBtn.setAllCaps(false);
        mobileBtn.setTextSize(9.5f * MenuOverlayView.MENU_SCALE);
        mobileBtn.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams mParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        mParams.setMargins((int) (4 * menuDensity), 0, 0, 0);
        mobileBtn.setLayoutParams(mParams);

        Runnable updateUaStyles = () -> {
            applyOptionStyle(desktopBtn, selectedDesktop[0], menuDensity);
            applyOptionStyle(mobileBtn, !selectedDesktop[0], menuDensity);
        };
        updateUaStyles.run();

        desktopBtn.setOnClickListener(v -> {
            selectedDesktop[0] = true;
            updateUaStyles.run();
        });
        mobileBtn.setOnClickListener(v -> {
            selectedDesktop[0] = false;
            updateUaStyles.run();
        });

        uaToggleRow.addView(desktopBtn);
        uaToggleRow.addView(mobileBtn);
        root.addView(uaToggleRow);

        // Divider
        addDivider(root, activity, menuDensity);

        // 3. Margin Section
        addSectionTitle(root, activity, "◧  Margin & Tabletop Padding", menuDensity);

        TextView marginDesc = new TextView(activity);
        marginDesc.setText("Configure margin padding: 0 dp edge-to-edge, 106 dp right gutter for Board button clearance, or 106 dp on all 4 sides for inset tabletop gaming.");
        marginDesc.setTextColor(Color.parseColor("#94A3B8"));
        marginDesc.setTextSize(8 * MenuOverlayView.MENU_SCALE);
        LinearLayout.LayoutParams mDescParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mDescParams.setMargins(0, 0, 0, (int) (8 * menuDensity));
        marginDesc.setLayoutParams(mDescParams);
        root.addView(marginDesc);

        // Quick Presets Row: [ None (0 dp) ] [ Right (106 dp) ] [ All 4 (106 dp) ]
        LinearLayout presetRow = new LinearLayout(activity);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (30 * menuDensity)));

        Button noneBtn = new Button(activity);
        noneBtn.setText("None (0 dp)");
        noneBtn.setAllCaps(false);
        noneBtn.setTextSize(8.0f * MenuOverlayView.MENU_SCALE);
        noneBtn.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams nParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        nParams.setMargins(0, 0, (int) (3 * menuDensity), 0);
        noneBtn.setLayoutParams(nParams);

        Button rightBtn = new Button(activity);
        rightBtn.setText("Right (106 dp)");
        rightBtn.setAllCaps(false);
        rightBtn.setTextSize(8.0f * MenuOverlayView.MENU_SCALE);
        rightBtn.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams rParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        rParams.setMargins((int) (3 * menuDensity), 0, (int) (3 * menuDensity), 0);
        rightBtn.setLayoutParams(rParams);

        Button allSidesBtn = new Button(activity);
        allSidesBtn.setText("All 4 (106 dp)");
        allSidesBtn.setAllCaps(false);
        allSidesBtn.setTextSize(8.0f * MenuOverlayView.MENU_SCALE);
        allSidesBtn.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams aParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        aParams.setMargins((int) (3 * menuDensity), 0, 0, 0);
        allSidesBtn.setLayoutParams(aParams);

        // Stepper Row: [ － ]  [ 106 dp ]  [ ＋ ]
        LinearLayout stepperRow = new LinearLayout(activity);
        stepperRow.setOrientation(LinearLayout.HORIZONTAL);
        stepperRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams stepRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (32 * menuDensity));
        stepRowParams.setMargins(0, (int) (6 * menuDensity), 0, 0);
        stepperRow.setLayoutParams(stepRowParams);

        Button minusBtn = new Button(activity);
        minusBtn.setText("－");
        minusBtn.setTextSize(12 * MenuOverlayView.MENU_SCALE);
        minusBtn.setTypeface(Typeface.DEFAULT_BOLD);
        int stepBtnSize = (int) (32 * menuDensity);
        minusBtn.setLayoutParams(new LinearLayout.LayoutParams(stepBtnSize, stepBtnSize));
        applyRoundActionStyle(minusBtn, menuDensity);

        TextView marginValueText = new TextView(activity);
        marginValueText.setTextSize(10.5f * MenuOverlayView.MENU_SCALE);
        marginValueText.setTypeface(Typeface.MONOSPACE);
        marginValueText.setTextColor(Color.WHITE);
        marginValueText.setGravity(Gravity.CENTER);
        marginValueText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        Button plusBtn = new Button(activity);
        plusBtn.setText("＋");
        plusBtn.setTextSize(12 * MenuOverlayView.MENU_SCALE);
        plusBtn.setTypeface(Typeface.DEFAULT_BOLD);
        plusBtn.setLayoutParams(new LinearLayout.LayoutParams(stepBtnSize, stepBtnSize));
        applyRoundActionStyle(plusBtn, menuDensity);

        Runnable updateMarginDisplay = () -> {
            int mode = selectedMarginMode[0];
            int val = selectedMarginDp[0];
            applyOptionStyle(noneBtn, mode == MARGIN_MODE_NONE, menuDensity);
            applyOptionStyle(rightBtn, mode == MARGIN_MODE_RIGHT, menuDensity);
            applyOptionStyle(allSidesBtn, mode == MARGIN_MODE_ALL, menuDensity);

            if (mode == MARGIN_MODE_NONE) {
                marginValueText.setText("0 dp  (Edge-to-Edge)");
            } else if (mode == MARGIN_MODE_RIGHT) {
                marginValueText.setText(val + " dp  (Right Gutter)");
            } else {
                marginValueText.setText(val + " dp  (All 4 Sides)");
            }
        };
        updateMarginDisplay.run();

        noneBtn.setOnClickListener(v -> {
            selectedMarginMode[0] = MARGIN_MODE_NONE;
            selectedMarginDp[0] = 0;
            updateMarginDisplay.run();
        });
        rightBtn.setOnClickListener(v -> {
            selectedMarginMode[0] = MARGIN_MODE_RIGHT;
            if (selectedMarginDp[0] <= 0) selectedMarginDp[0] = DEFAULT_GUTTER_DP;
            updateMarginDisplay.run();
        });
        allSidesBtn.setOnClickListener(v -> {
            selectedMarginMode[0] = MARGIN_MODE_ALL;
            if (selectedMarginDp[0] <= 0) selectedMarginDp[0] = DEFAULT_GUTTER_DP;
            updateMarginDisplay.run();
        });

        minusBtn.setOnClickListener(v -> {
            selectedMarginDp[0] = Math.max(0, selectedMarginDp[0] - 10);
            if (selectedMarginDp[0] == 0) {
                selectedMarginMode[0] = MARGIN_MODE_NONE;
            }
            updateMarginDisplay.run();
        });
        plusBtn.setOnClickListener(v -> {
            selectedMarginDp[0] = Math.min(200, selectedMarginDp[0] + 10);
            if (selectedMarginMode[0] == MARGIN_MODE_NONE) {
                selectedMarginMode[0] = MARGIN_MODE_RIGHT;
            }
            updateMarginDisplay.run();
        });

        presetRow.addView(noneBtn);
        presetRow.addView(rightBtn);
        presetRow.addView(allSidesBtn);
        root.addView(presetRow);

        stepperRow.addView(minusBtn);
        stepperRow.addView(marginValueText);
        stepperRow.addView(plusBtn);
        root.addView(stepperRow);

        // Divider
        addDivider(root, activity, menuDensity);

        // 4. Action Buttons (Cancel / Save)
        LinearLayout actionRow = new LinearLayout(activity);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (36 * menuDensity));
        actionParams.setMargins(0, (int) (4 * menuDensity), 0, 0);
        actionRow.setLayoutParams(actionParams);

        Button cancelBtn = new Button(activity);
        cancelBtn.setText("Cancel");
        cancelBtn.setAllCaps(false);
        cancelBtn.setTextColor(Color.parseColor("#94A3B8"));
        cancelBtn.setTextSize(9.5f * MenuOverlayView.MENU_SCALE);
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setShape(GradientDrawable.RECTANGLE);
        cancelBg.setColor(Color.parseColor("#1C2130"));
        cancelBg.setCornerRadius(14 * menuDensity);
        cancelBtn.setBackground(cancelBg);
        LinearLayout.LayoutParams cParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        cParams.setMargins(0, 0, (int) (6 * menuDensity), 0);
        cancelBtn.setLayoutParams(cParams);

        Button saveBtn = new Button(activity);
        saveBtn.setText("Save Settings");
        saveBtn.setAllCaps(false);
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setTextSize(9.5f * MenuOverlayView.MENU_SCALE);
        saveBtn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setShape(GradientDrawable.RECTANGLE);
        saveBg.setColor(Color.parseColor("#4C6EF5"));
        saveBg.setCornerRadius(14 * menuDensity);
        saveBtn.setBackground(saveBg);
        LinearLayout.LayoutParams sParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        sParams.setMargins((int) (6 * menuDensity), 0, 0, 0);
        saveBtn.setLayoutParams(sParams);

        actionRow.addView(cancelBtn);
        actionRow.addView(saveBtn);
        root.addView(actionRow);

        // Build AlertDialog
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(root)
                .setCancelable(true)
                .create();

        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        saveBtn.setOnClickListener(v -> {
            boolean isDesktop = selectedDesktop[0];
            int marginDp = selectedMarginDp[0];
            int marginMode = selectedMarginMode[0];

            if (uaManager != null) {
                uaManager.saveSiteDefaultDesktopMode(activity, siteKey, isDesktop);
            }
            saveSiteDefaultRightMarginDp(activity, siteKey, marginDp);
            saveSiteDefaultMarginMode(activity, siteKey, marginMode);

            if (listener != null) {
                listener.onSaved(siteKey, isDesktop, marginDp, marginMode);
            }
            dialog.dismiss();
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int dialogWidth = (int) (260 * menuDensity);
            dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static void addDivider(LinearLayout parent, Activity activity, float menuDensity) {
        View div = new View(activity);
        div.setBackgroundColor(Color.parseColor("#22293C"));
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * menuDensity));
        divParams.setMargins(0, (int) (10 * menuDensity), 0, (int) (10 * menuDensity));
        div.setLayoutParams(divParams);
        parent.addView(div);
    }

    private static void addSectionTitle(LinearLayout parent, Activity activity, String title, float menuDensity) {
        TextView tv = new TextView(activity);
        tv.setText(title);
        tv.setTextColor(Color.parseColor("#F1F5F9"));
        tv.setTextSize(10 * MenuOverlayView.MENU_SCALE);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, (int) (2 * menuDensity));
        tv.setLayoutParams(lp);
        parent.addView(tv);
    }

    private static void applyOptionStyle(Button btn, boolean isSelected, float menuDensity) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(10 * menuDensity);
        if (isSelected) {
            bg.setColor(Color.parseColor("#1E284A"));
            bg.setStroke((int) (1.5f * menuDensity), Color.parseColor("#5C7CFA"));
            btn.setTextColor(Color.parseColor("#91A7FF"));
        } else {
            bg.setColor(Color.parseColor("#181D2A"));
            bg.setStroke((int) (1 * menuDensity), Color.parseColor("#2A3348"));
            btn.setTextColor(Color.parseColor("#94A3B8"));
        }
        btn.setBackground(bg);
    }

    private static void applyRoundActionStyle(Button btn, float menuDensity) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#202636"));
        bg.setStroke((int) (1 * menuDensity), Color.parseColor("#34405A"));
        btn.setBackground(bg);
        btn.setTextColor(Color.WHITE);
        btn.setPadding(0, 0, 0, 0);
        btn.setIncludeFontPadding(false);
        btn.setGravity(Gravity.CENTER);
    }

    public static void showSaveAckDialog(Activity activity, String siteKey, boolean isDesktop, int marginDp, float menuDensity) {
        int mode = (marginDp > 0) ? MARGIN_MODE_RIGHT : MARGIN_MODE_NONE;
        showSaveAckDialog(activity, siteKey, isDesktop, mode, marginDp, menuDensity);
    }

    public static void showSaveAckDialog(Activity activity, String siteKey, boolean isDesktop, int marginMode, int marginDp, float menuDensity) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> {
            if (activity.isFinishing()) return;
            String uaStr = isDesktop ? "🖥  Desktop Mode" : "📱  Mobile Mode";
            String marginStr;
            int effectiveDp = (marginDp > 0) ? marginDp : DEFAULT_GUTTER_DP;
            switch (marginMode) {
                case MARGIN_MODE_RIGHT:
                    marginStr = effectiveDp + " dp  (Right Gutter)";
                    break;
                case MARGIN_MODE_ALL:
                    marginStr = effectiveDp + " dp  (All 4 Sides)";
                    break;
                case MARGIN_MODE_NONE:
                default:
                    marginStr = "0 dp  (Edge-to-Edge)";
                    break;
            }

            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            int pad = (int) (18 * menuDensity);
            root.setPadding(pad, pad, pad, pad);

            GradientDrawable rootBg = new GradientDrawable();
            rootBg.setShape(GradientDrawable.RECTANGLE);
            rootBg.setColor(Color.parseColor("#F5121622"));
            rootBg.setCornerRadius(18 * menuDensity);
            rootBg.setStroke((int) (1.2f * menuDensity), Color.parseColor("#28324A"));
            root.setBackground(rootBg);

            TextView titleView = new TextView(activity);
            titleView.setText("⚙️ Site Defaults Saved");
            titleView.setTextColor(Color.WHITE);
            titleView.setTextSize(13 * MenuOverlayView.MENU_SCALE);
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            titleView.setGravity(Gravity.CENTER);
            root.addView(titleView);

            TextView siteView = new TextView(activity);
            siteView.setText(siteKey);
            siteView.setTextColor(Color.parseColor("#91A7FF"));
            siteView.setTextSize(10.5f * MenuOverlayView.MENU_SCALE);
            siteView.setTypeface(Typeface.DEFAULT_BOLD);
            siteView.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams svParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            svParams.setMargins(0, (int) (4 * menuDensity), 0, (int) (8 * menuDensity));
            siteView.setLayoutParams(svParams);
            root.addView(siteView);

            addDivider(root, activity, menuDensity);

            TextView uaView = new TextView(activity);
            uaView.setText("• Mode: " + uaStr);
            uaView.setTextColor(Color.parseColor("#E2E8F0"));
            uaView.setTextSize(9.5f * MenuOverlayView.MENU_SCALE);
            LinearLayout.LayoutParams uaParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            uaParams.setMargins(0, (int) (4 * menuDensity), 0, 0);
            uaView.setLayoutParams(uaParams);
            root.addView(uaView);

            TextView marginView = new TextView(activity);
            marginView.setText("• Margin: " + marginStr);
            marginView.setTextColor(Color.parseColor("#E2E8F0"));
            marginView.setTextSize(9.5f * MenuOverlayView.MENU_SCALE);
            LinearLayout.LayoutParams mvParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mvParams.setMargins(0, (int) (4 * menuDensity), 0, (int) (12 * menuDensity));
            marginView.setLayoutParams(mvParams);
            root.addView(marginView);

            Button okBtn = new Button(activity);
            okBtn.setText("OK");
            okBtn.setAllCaps(false);
            okBtn.setTextColor(Color.WHITE);
            okBtn.setTextSize(9.5f * MenuOverlayView.MENU_SCALE);
            okBtn.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable okBg = new GradientDrawable();
            okBg.setShape(GradientDrawable.RECTANGLE);
            okBg.setColor(Color.parseColor("#4C6EF5"));
            okBg.setCornerRadius(14 * menuDensity);
            okBtn.setBackground(okBg);
            LinearLayout.LayoutParams okParams = new LinearLayout.LayoutParams(
                    (int) (90 * menuDensity), (int) (32 * menuDensity));
            okBtn.setLayoutParams(okParams);
            root.addView(okBtn);

            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setView(root)
                    .setCancelable(true)
                    .create();

            okBtn.setOnClickListener(v -> dialog.dismiss());

            dialog.show();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                int dialogWidth = (int) (220 * menuDensity);
                dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
    }
}
