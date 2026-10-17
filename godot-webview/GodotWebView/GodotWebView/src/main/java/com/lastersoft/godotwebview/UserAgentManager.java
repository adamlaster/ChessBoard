package com.lastersoft.godotwebview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Manages Desktop vs. Mobile User-Agent state, persistent defaults via SharedPreferences,
 * and YouTube query parameter adjustments. Supports per-site persistent defaults.
 */
public class UserAgentManager {

    public static final String PREFS_NAME = "chessboard_prefs";
    public static final String LEGACY_PREFS_NAME = "chess4board_prefs";
    public static final String PREF_DEFAULT_DESKTOP_MODE = "default_desktop_mode";
    public static final String PREF_SITE_UA_PREFIX = "site_ua_";
    public static final String PREF_SITE_MARGIN_PREFIX = "site_margin_";

    public static final String DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    public static final String MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";

    public static boolean isAuthUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.contains("accounts.google.com") ||
               lower.contains("accounts.youtube.com") ||
               lower.contains("myaccount.google.com") ||
               lower.contains("appleid.apple.com") ||
               lower.contains("challenges.cloudflare.com") ||
               lower.contains("turnstile") ||
               lower.contains("recaptcha") ||
               lower.contains("hcaptcha") ||
               lower.contains("/signin") ||
               lower.contains("/login") ||
               lower.contains("/signup") ||
               lower.contains("/register") ||
               lower.contains("/oauth") ||
               lower.contains("/auth");
    }

    public static String sanitizeUserAgent(String raw) {
        if (raw == null) return null;
        return raw.replaceAll(";\\s*wv", "").replaceAll("Version/\\d+\\.\\d+\\s*", "");
    }

    private String defaultUserAgent = null;
    private boolean defaultDesktopMode = true;
    private boolean isDesktopMode = true;
    private AlertDialog currentAckDialog = null;

    /**
     * Extracts a clean domain/host key (e.g. "chess.com", "youtube.com") from a URL.
     * Returns empty string for auth/login URLs to prevent false site-switch triggers.
     */
    public static String getSiteKey(String url) {
        if (url == null || url.trim().isEmpty() || isAuthUrl(url)) return "";
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host != null) {
                host = host.toLowerCase().trim();
                if (host.startsWith("www.")) {
                    host = host.substring(4);
                } else if (host.startsWith("m.")) {
                    host = host.substring(2);
                }
                return host;
            }
        } catch (Exception ignored) {}
        return "";
    }

    public void init(Context context, String systemDefaultUserAgent) {
        if (defaultUserAgent == null && systemDefaultUserAgent != null) {
            defaultUserAgent = sanitizeUserAgent(systemDefaultUserAgent);
        }
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
            if (prefs.contains(PREF_DEFAULT_DESKTOP_MODE)) {
                defaultDesktopMode = prefs.getBoolean(PREF_DEFAULT_DESKTOP_MODE, true);
            } else {
                defaultDesktopMode = legacyPrefs.getBoolean(PREF_DEFAULT_DESKTOP_MODE, true);
            }
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

    public boolean getSiteDefaultDesktopMode(Context context, String siteKey) {
        if (context != null && siteKey != null && !siteKey.isEmpty()) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String key = PREF_SITE_UA_PREFIX + siteKey;
            if (prefs.contains(key)) {
                return prefs.getBoolean(key, defaultDesktopMode);
            }
            SharedPreferences legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
            if (legacyPrefs.contains(key)) {
                return legacyPrefs.getBoolean(key, defaultDesktopMode);
            }
        }
        return defaultDesktopMode;
    }

    public void saveSiteDefaultDesktopMode(Context context, String siteKey, boolean desktopMode) {
        if (context != null && siteKey != null && !siteKey.isEmpty()) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(PREF_SITE_UA_PREFIX + siteKey, desktopMode)
                    .apply();
        }
    }

    public void applySiteDefault(Context context, String siteKey) {
        this.isDesktopMode = getSiteDefaultDesktopMode(context, siteKey);
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

    public String getCleanMobileUserAgent() {
        if (defaultUserAgent != null && !defaultUserAgent.isEmpty()) {
            return defaultUserAgent;
        }
        return MOBILE_USER_AGENT;
    }

    public String getDesktopUserAgent() {
        if (defaultUserAgent != null && !defaultUserAgent.isEmpty()) {
            return defaultUserAgent.replaceAll("\\(Linux;[^)]*\\)", "(X11; Linux x86_64)")
                    .replace(" Mobile", "");
        }
        return DESKTOP_USER_AGENT;
    }

    public void applyAuthUserAgent(WebSettings settings) {
        if (settings == null) return;
        String authUa = getCleanMobileUserAgent();
        if (!authUa.equals(settings.getUserAgentString())) {
            settings.setUserAgentString(authUa);
        }
    }

    public void configureWebSettings(WebSettings settings) {
        configureWebSettings(settings, null);
    }

    public void configureWebSettings(WebSettings settings, String url) {
        if (settings == null) return;
        if (defaultUserAgent == null) {
            defaultUserAgent = sanitizeUserAgent(settings.getUserAgentString());
        }
        String targetUa;
        if (url != null && isAuthUrl(url)) {
            targetUa = getCleanMobileUserAgent();
        } else {
            targetUa = isDesktopMode ? getDesktopUserAgent() : getCleanMobileUserAgent();
        }
        if (!targetUa.equals(settings.getUserAgentString())) {
            settings.setUserAgentString(targetUa);
        }
    }

    public void applyUserAgent(WebView webView, boolean reloadPage) {
        if (webView == null) return;
        configureWebSettings(webView.getSettings());

        if (reloadPage) {
            String currentUrl = webView.getUrl();
            if (currentUrl != null && !isAuthUrl(currentUrl)) {
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
        if (url == null || !url.contains("youtube.com") || isAuthUrl(url)) return url;
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

    public void showAckDialog(Activity activity, String title, String message, float menuDensity) {
        if (activity == null || activity.isFinishing()) return;

        activity.runOnUiThread(() -> {
            if (activity.isFinishing()) return;
            try {
                if (currentAckDialog != null && currentAckDialog.isShowing()) {
                    currentAckDialog.dismiss();
                }
            } catch (Exception ignored) {}

            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            int cardWidth = Math.min((int) (340 * menuDensity), (int) (screenWidth * 0.85f));

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
            titleView.setText(title);
            titleView.setTextColor(Color.WHITE);
            titleView.setTextSize(16 * 2.0f);
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            titleView.setGravity(Gravity.CENTER);
            dialogCard.addView(titleView);

            TextView msgView = new TextView(activity);
            msgView.setText(message);
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
            dialog.setOnDismissListener(d -> {
                if (currentAckDialog == dialog) {
                    currentAckDialog = null;
                }
            });
            currentAckDialog = dialog;

            dialog.show();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dialog.getWindow().setLayout(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
    }

    public void showSiteUaAckDialog(Activity activity, String siteKey, boolean isDesktop, float menuDensity) {
        String msg = isDesktop
                ? "🖥  Desktop Mode is now the default for " + siteKey + "."
                : "📱  Mobile Mode is now the default for " + siteKey + ".";
        showAckDialog(activity, "Default User-Agent Saved", msg, menuDensity);
    }

    public void showSiteMarginAckDialog(Activity activity, String siteKey, boolean isMarginEnabled, float menuDensity) {
        String msg = isMarginEnabled
                ? "◧  Right Margin is now ON by default for " + siteKey + "."
                : "◧  Right Margin is now OFF by default for " + siteKey + ".";
        showAckDialog(activity, "Default Right Margin Saved", msg, menuDensity);
    }

    public void showDefaultUaAckDialog(Activity activity, float menuDensity) {
        String msg = defaultDesktopMode
                ? "🖥  Desktop Mode is now the default for all sites."
                : "📱  Mobile Mode is now the default for all sites.";
        showAckDialog(activity, "Default User-Agent Saved", msg, menuDensity);
    }
}

