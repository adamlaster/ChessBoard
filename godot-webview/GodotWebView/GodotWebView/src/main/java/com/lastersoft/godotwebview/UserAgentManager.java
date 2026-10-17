package com.lastersoft.godotwebview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Manages Desktop vs. Mobile User-Agent state, persistent defaults via SharedPreferences,
 * and YouTube query parameter adjustments.
 */
public class UserAgentManager {

    public static final String PREFS_NAME = "chess4board_prefs";
    public static final String PREF_DEFAULT_DESKTOP_MODE = "default_desktop_mode";
    public static final String DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private String defaultUserAgent = null;
    private boolean defaultDesktopMode = true;
    private boolean isDesktopMode = true;

    public void init(Context context, String systemDefaultUserAgent) {
        if (defaultUserAgent == null && systemDefaultUserAgent != null) {
            defaultUserAgent = systemDefaultUserAgent;
        }
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            defaultDesktopMode = prefs.getBoolean(PREF_DEFAULT_DESKTOP_MODE, true);
            isDesktopMode = defaultDesktopMode;
        }
    }

    public boolean isDesktopMode() {
        return isDesktopMode;
    }

    public boolean isDefaultDesktopMode() {
        return defaultDesktopMode;
    }

    public void setDesktopMode(boolean desktopMode) {
        this.isDesktopMode = desktopMode;
    }

    public void resetToDefaultMode() {
        this.isDesktopMode = defaultDesktopMode;
    }

    public void toggleTemporaryMode() {
        this.isDesktopMode = !this.isDesktopMode;
    }

    public void toggleAndSaveDefaultMode(Context context) {
        this.defaultDesktopMode = !this.defaultDesktopMode;
        this.isDesktopMode = this.defaultDesktopMode;
        if (context != null) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(PREF_DEFAULT_DESKTOP_MODE, this.defaultDesktopMode)
                    .apply();
        }
    }

    public void configureWebSettings(WebSettings settings) {
        if (settings == null) return;
        if (defaultUserAgent == null) {
            defaultUserAgent = settings.getUserAgentString();
        }
        if (isDesktopMode) {
            settings.setUserAgentString(DESKTOP_USER_AGENT);
        } else if (defaultUserAgent != null) {
            settings.setUserAgentString(defaultUserAgent);
        }
    }

    public void applyUserAgent(WebView webView, boolean reloadPage) {
        if (webView == null) return;
        configureWebSettings(webView.getSettings());

        if (reloadPage) {
            String currentUrl = webView.getUrl();
            if (currentUrl != null) {
                if (isDesktopMode && currentUrl.contains("youtube.com")) {
                    String desktopUrl = currentUrl.replace("://m.youtube.com", "://www.youtube.com")
                            .replace("?app=mobile", "")
                            .replace("&app=mobile", "");
                    if (!desktopUrl.contains("app=desktop")) {
                        desktopUrl += (desktopUrl.contains("?") ? "&" : "?") + "app=desktop";
                    }
                    webView.loadUrl(desktopUrl);
                    return;
                } else if (!isDesktopMode && currentUrl.contains("youtube.com")) {
                    String mobileUrl = currentUrl.replace("://www.youtube.com", "://m.youtube.com")
                            .replace("?app=desktop", "")
                            .replace("&app=desktop", "");
                    if (!mobileUrl.contains("app=mobile")) {
                        mobileUrl += (mobileUrl.contains("?") ? "&" : "?") + "app=mobile";
                    }
                    webView.loadUrl(mobileUrl);
                    return;
                }
            }
            webView.reload();
        }
    }

    public String adjustYouTubeTargetUrl(String url) {
        if (url == null || !url.contains("youtube.com")) return url;
        String targetUrl = url;
        if (isDesktopMode && !targetUrl.contains("app=desktop")) {
            targetUrl += (targetUrl.contains("?") ? "&" : "?") + "app=desktop";
        } else if (!isDesktopMode && !targetUrl.contains("app=mobile")) {
            targetUrl += (targetUrl.contains("?") ? "&" : "?") + "app=mobile";
        }
        return targetUrl;
    }

    public void updateDesktopButtonStyle(Button button, GradientDrawable circle, float density) {
        if (button == null || circle == null) return;
        if (isDesktopMode) {
            button.setText("🖥");
            button.setTextColor(Color.parseColor("#91A7FF"));
            circle.setColor(Color.parseColor("#1E2433"));
            circle.setStroke((int) (1.5f * density), Color.parseColor("#5C7CFA"));
        } else {
            button.setText("📱");
            button.setTextColor(Color.WHITE);
            circle.setColor(Color.parseColor("#2E2E2E"));
            circle.setStroke(0, Color.TRANSPARENT);
        }
    }

    public void showDefaultUaAckDialog(Activity activity, float menuDensity) {
        if (activity == null || activity.isFinishing()) return;

        LinearLayout dialogCard = new LinearLayout(activity);
        dialogCard.setOrientation(LinearLayout.VERTICAL);
        dialogCard.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (24 * menuDensity);
        dialogCard.setPadding(pad, pad, pad, pad);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setColor(Color.parseColor("#1A1A1A"));
        cardBg.setCornerRadius(16 * menuDensity);
        cardBg.setStroke((int) (1.5f * menuDensity), Color.parseColor("#33FFFFFF"));
        dialogCard.setBackground(cardBg);

        TextView titleView = new TextView(activity);
        titleView.setText("Default User-Agent Saved");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(16 * 2.0f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);
        dialogCard.addView(titleView);

        TextView msgView = new TextView(activity);
        msgView.setText(defaultDesktopMode
                ? "🖥  Desktop Mode is now the default for all sites."
                : "📱  Mobile Mode is now the default for all sites.");
        msgView.setTextColor(Color.parseColor("#D9FFFFFF"));
        msgView.setTextSize(13 * 2.0f);
        msgView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        msgParams.setMargins(0, (int) (12 * menuDensity), 0, (int) (20 * menuDensity));
        msgView.setLayoutParams(msgParams);
        dialogCard.addView(msgView);

        Button okBtn = new Button(activity);
        okBtn.setText("OK");
        okBtn.setAllCaps(false);
        okBtn.setTextColor(Color.WHITE);
        okBtn.setTextSize(14 * 2.0f);
        okBtn.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable okBg = new GradientDrawable();
        okBg.setShape(GradientDrawable.RECTANGLE);
        okBg.setColor(Color.parseColor("#5C7CFA"));
        okBg.setCornerRadius(10 * menuDensity);
        okBtn.setBackground(okBg);

        LinearLayout.LayoutParams okParams = new LinearLayout.LayoutParams(
                (int) (140 * menuDensity), (int) (42 * menuDensity));
        okBtn.setLayoutParams(okParams);
        dialogCard.addView(okBtn);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogCard)
                .setCancelable(true)
                .create();

        okBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout((int) (320 * menuDensity), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}

