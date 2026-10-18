package com.lastersoft.godotwebview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;
import org.godotengine.godot.plugin.UsedByGodot;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * ChessBoard WebView Plugin for Godot 4.
 * Coordinates web browsing, Board OS hardware integration, multi-window secondary tab popups,
 * floating dismiss button, and 2x scaled tabletop menu overlay.
 */
public class GodotWebView extends GodotPlugin {

    private static final String TAG = "GodotWebView";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final UserAgentManager uaManager = new UserAgentManager();
    private final DisplayController displayController = new DisplayController();
    private final SecondaryTabManager secondaryTabManager = new SecondaryTabManager();
    private final FloatingDismissButton floatingDismissButton = new FloatingDismissButton();
    private final MenuOverlayView menuOverlayView = new MenuOverlayView();

    private FrameLayout layout = null;
    private WebView webView = null;
    private ProgressBar progressBar = null;
    private View customView = null;
    private WebChromeClient.CustomViewCallback customViewCallback = null;
    private String currentSiteKey = "";
    private int currentSiteMarginDp = 0;
    private boolean isExplicitNavigation = false;

    private String getActiveSiteKey() {
        String currentUrl = null;
        if (secondaryTabManager.isOpen() && secondaryTabManager.getWebView() != null) {
            currentUrl = secondaryTabManager.getWebView().getUrl();
        }
        if ((currentUrl == null || currentUrl.isEmpty()) && webView != null) {
            currentUrl = webView.getUrl();
        }
        String siteKey = UserAgentManager.getSiteKey(currentUrl);
        if (siteKey.isEmpty()) {
            siteKey = currentSiteKey;
        }
        return siteKey.isEmpty() ? "this site" : siteKey;
    }

    public GodotWebView(Godot godot) {
        super(godot);
        prepareBoardTouchPassthrough();
    }

    @Override
    public View onMainCreate(Activity activity) {
        prepareBoardTouchPassthrough();
        return super.onMainCreate(activity);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "GodotWebView";
    }

    @NonNull
    @Override
    public Set getPluginSignals() {
        Set signals = new HashSet<>();
        signals.add(new SignalInfo("quit_app_requested"));
        return signals;
    }

    @UsedByGodot
    public void prepareBoardTouchPassthrough() {
        BoardBridge.prepareBoardTouchPassthrough(
                getActivity(),
                this::terminateBoardAndActivity,
                this::showLauncher,
                this::showBrowserControls,
                this::showHelp,
                mainHandler
        );
    }

    @UsedByGodot
    public void show_launcher() {
        runOnUiThread(this::showLauncher);
    }

    @UsedByGodot
    public void open_launcher() {
        runOnUiThread(this::showLauncher);
    }

    @UsedByGodot
    public void show_browser_controls() {
        runOnUiThread(this::showBrowserControls);
    }

    @UsedByGodot
    public void open_browser_controls() {
        runOnUiThread(this::showBrowserControls);
    }

    @UsedByGodot
    public void show_menu() {
        runOnUiThread(this::showLauncher);
    }

    @UsedByGodot
    public void open_menu() {
        runOnUiThread(this::showLauncher);
    }

    @UsedByGodot
    public void show_help() {
        runOnUiThread(this::showHelp);
    }

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @UsedByGodot
    public void open(@NonNull String url) {
        prepareBoardTouchPassthrough();
        runOnUiThread(() -> {
            Activity activity = getActivity();
            if (activity == null) return;
            if (activity.getCurrentFocus() != null) {
                activity.getCurrentFocus().setOnTouchListener(null);
            }

            uaManager.init(activity, null);
            displayController.resetZoom();

            if (layout == null) {
                layout = new FrameLayout(activity);
                layout.setBackgroundColor(Color.BLACK);
                activity.addContentView(layout, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            } else {
                layout.removeAllViews();
                layout.setBackgroundColor(Color.BLACK);
            }

            webView = new WebView(activity);
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            webView.setVerticalScrollBarEnabled(false);
            webView.setHorizontalScrollBarEnabled(false);

            currentSiteKey = UserAgentManager.getSiteKey(url);
            currentSiteMarginDp = SiteSettingsDialogHelper.getSiteDefaultRightMarginDp(activity, currentSiteKey);
            WebSettings settings = webView.getSettings();
            uaManager.init(activity, settings.getUserAgentString());
            uaManager.applySiteDefault(activity, currentSiteKey);
            uaManager.configureWebSettings(settings, url);

            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, Collections.emptySet());
            }

            settings.setJavaScriptEnabled(true);
            settings.setSupportMultipleWindows(true);
            settings.setJavaScriptCanOpenWindowsAutomatically(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setMediaPlaybackRequiresUserGesture(false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                settings.setOffscreenPreRaster(true);
            }

            settings.setSupportZoom(true);
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);

            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.setAcceptThirdPartyCookies(webView, true);
                settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            }

            // Web client
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    WebResourceResponse wasmResp = WasmPatchHelper.shouldInterceptRequest(activity, request);
                    if (wasmResp != null) {
                        return wasmResp;
                    }
                    return super.shouldInterceptRequest(view, request);
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && request != null && request.getUrl() != null) {
                        android.net.Uri targetUri = request.getUrl();
                        String scheme = targetUri.getScheme();
                        if (scheme != null && !scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                            String fullUri = targetUri.toString();
                            if (fullUri.startsWith("intent://")) {
                                try {
                                    android.content.Intent intent = android.content.Intent.parseUri(fullUri, android.content.Intent.URI_INTENT_SCHEME);
                                    String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                                    if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                                        view.loadUrl(fallbackUrl);
                                    }
                                } catch (Exception ignored) {}
                            }
                            return true;
                        }

                        // If explicit site navigation from Launcher/Menu, never intercept as external popup
                        if (isExplicitNavigation) {
                            return false;
                        }

                        // Allow all auth URLs in primary WebView; NEVER divert auth to second tab
                        String fullTargetUrl = targetUri.toString();
                        if (UserAgentManager.isAuthUrl(fullTargetUrl)) {
                            uaManager.applyAuthUserAgent(view.getSettings());
                            return false;
                        }

                        // Protect active chess game: route external site links in chess.com to second tab
                        if (request.isForMainFrame()) {
                            String host = targetUri.getHost();
                            String currentUrl = view.getUrl();
                            if (currentUrl != null && currentUrl.contains("chess.com") && host != null) {
                                if (!host.endsWith("chess.com") && !host.endsWith("chesskids.com")) {
                                    openUrlInSecondTab(fullTargetUrl);
                                    return true;
                                }
                            }
                        }
                    }
                    return false;
                }

                @Override
                public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                    if (UserAgentManager.isAuthUrl(url)) {
                        uaManager.applyAuthUserAgent(view.getSettings());
                    } else {
                        String siteKey = UserAgentManager.getSiteKey(url);
                        if (!siteKey.isEmpty() && !siteKey.equals(currentSiteKey)) {
                            currentSiteKey = siteKey;
                            Activity act = getActivity();
                            if (act != null) {
                                float d = act.getResources().getDisplayMetrics().density;
                                float md = d * MenuOverlayView.MENU_SCALE;

                                boolean siteDesktop = uaManager.getSiteDefaultDesktopMode(act, siteKey);
                                if (siteDesktop != uaManager.isDesktopMode()) {
                                    uaManager.setDesktopMode(siteDesktop);
                                    menuOverlayView.updateDesktopButtonStyle(uaManager, md);
                                }
                            }
                        }
                    }
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    isExplicitNavigation = false;
                    displayController.applyZoom(webView, secondaryTabManager.getWebView());
                    menuOverlayView.updateBackButtonState(canGoBack());
                    ChessTouchHelper.injectTouchEnhancements(view, url);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        CookieManager.getInstance().flush();
                    }
                }
            });

            // Loading progress bar
            float density = activity.getResources().getDisplayMetrics().density;
            int marginPx = (int) (currentSiteMarginDp * density);
            progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            int progressHeight = (int) (4 * density);
            FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, progressHeight);
            progressParams.gravity = Gravity.TOP;
            progressParams.rightMargin = marginPx;
            progressBar.setLayoutParams(progressParams);
            progressBar.setMax(100);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#43A047")));
            }

            // Web Chrome client
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    if (progressBar != null) {
                        if (newProgress == 100) {
                            progressBar.setVisibility(View.GONE);
                        } else {
                            progressBar.setVisibility(View.VISIBLE);
                            progressBar.setProgress(newProgress);
                        }
                    }
                }

                @Override
                public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                    // Reject automated/background window requests (e.g. background bot checks)
                    if (!isUserGesture) {
                        return false;
                    }
                    // Handle auth URLs in-place so Google/OAuth flows remain in primary session
                    WebView.HitTestResult hitTest = view.getHitTestResult();
                    String extraUrl = (hitTest != null) ? hitTest.getExtra() : null;
                    if (UserAgentManager.isAuthUrl(extraUrl)) {
                        if (extraUrl != null && !extraUrl.isEmpty()) {
                            uaManager.applyAuthUserAgent(view.getSettings());
                            view.loadUrl(extraUrl);
                        }
                        return false;
                    }
                    return secondaryTabManager.handleCreateWindow(
                            activity, layout, uaManager, displayController, floatingDismissButton,
                            menuOverlayView.getScrimView(), menuOverlayView.getMenuCard(),
                            resultMsg, createSecondaryTabCallback()
                    );
                }

                @Override
                public void onCloseWindow(WebView window) {
                    closeSecondTab();
                }

                @Override
                public void onPermissionRequest(final android.webkit.PermissionRequest request) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        String[] resources = request.getResources();
                        for (String r : resources) {
                            if (android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(r)) {
                                request.grant(new String[]{android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID});
                                return;
                            }
                        }
                    }
                    super.onPermissionRequest(request);
                }

                @Override
                public void onShowCustomView(View view, CustomViewCallback callback) {
                    if (customView != null) {
                        callback.onCustomViewHidden();
                        return;
                    }
                    customView = view;
                    customView.setBackgroundColor(Color.BLACK);
                    customViewCallback = callback;
                    if (webView != null) {
                        webView.setVisibility(View.GONE);
                    }
                    if (layout != null) {
                        layout.addView(customView, new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT));
                        menuOverlayView.bringToFront();
                        floatingDismissButton.bringToFront();
                    }
                }

                @Override
                public void onHideCustomView() {
                    exitCustomFullscreen();
                }
            });

            FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            webParams.rightMargin = marginPx;
            layout.addView(webView, webParams);
            layout.addView(progressBar);
            secondaryTabManager.applyRightMargin(marginPx);

            ViewGroup parentView = layout;
            while (parentView != null) {
                parentView.setClipChildren(false);
                parentView.setClipToPadding(false);
                if (parentView.getParent() instanceof ViewGroup) {
                    parentView = (ViewGroup) parentView.getParent();
                } else {
                    break;
                }
            }

            // Setup Menu Overlay
            menuOverlayView.setup(activity, layout, uaManager, new MenuOverlayView.MenuActionListener() {
                @Override
                public void onSiteSelected(String siteUrl) {
                    navigateToSite(siteUrl);
                }

                @Override
                public void onGoBack() {
                    handleBackNavigation();
                }

                @Override
                public void onZoomIn() {
                    displayController.zoomIn(webView, secondaryTabManager.getWebView());
                }

                @Override
                public void onZoomOut() {
                    displayController.zoomOut(webView, secondaryTabManager.getWebView());
                }

                @Override
                public void onRefresh() {
                    if (secondaryTabManager.isOpen()) {
                        secondaryTabManager.reload();
                    } else if (webView != null) {
                        webView.reload();
                    }
                }

                @Override
                public void onToggleDesktopMode(boolean isLongPressSaveDefault) {
                    float menuDensity = density * MenuOverlayView.MENU_SCALE;
                    if (isLongPressSaveDefault) {
                        String siteKey = getActiveSiteKey();
                        boolean savedDefault = uaManager.getSiteDefaultDesktopMode(activity, siteKey);
                        boolean currentMode = uaManager.isDesktopMode();
                        boolean targetMode = (currentMode != savedDefault) ? currentMode : !currentMode;

                        uaManager.saveSiteDefaultDesktopMode(activity, siteKey, targetMode);
                        uaManager.setDesktopMode(targetMode);
                        hideMenu();
                        applyUserAgent(true);
                        mainHandler.postDelayed(() -> {
                            Activity act = getActivity();
                            if (act != null && !act.isFinishing()) {
                                uaManager.showSiteUaAckDialog(act, siteKey, targetMode, menuDensity);
                            }
                        }, 150);
                    } else {
                        uaManager.toggleTemporaryMode();
                        applyUserAgent(true);
                    }
                }

                @Override
                public void onToggleRightMargin(boolean isLongPressSaveDefault) {
                    Activity act = getActivity();
                    if (act == null) return;
                    float density = act.getResources().getDisplayMetrics().density;
                    float menuDensity = density * MenuOverlayView.MENU_SCALE;
                    String rawKey = UserAgentManager.getSiteKey(webView != null ? webView.getUrl() : null);
                    final String siteKey = rawKey.isEmpty() ? currentSiteKey : rawKey;

                    int targetDp = (currentSiteMarginDp > 0) ? 0 : SiteSettingsDialogHelper.DEFAULT_GUTTER_DP;
                    applyRightMarginDp(targetDp);
                    menuOverlayView.updateRightMarginButtonStyle(targetDp > 0, menuDensity);

                    if (isLongPressSaveDefault) {
                        SiteSettingsDialogHelper.saveSiteDefaultRightMarginDp(act, siteKey, targetDp);
                        mainHandler.postDelayed(() -> {
                            if (!act.isFinishing()) {
                                SiteSettingsDialogHelper.showSaveAckDialog(act, siteKey, uaManager.isDesktopMode(), targetDp, menuDensity);
                            }
                        }, 150);
                    }
                }

                @Override
                public void onRotateScreen(boolean isLongPressResetLandscape) {
                    hideMenu();
                    Runnable postRotate = () -> displayController.schedulePostRotateRefresh(
                            webView,
                            floatingDismissButton.getButton(),
                            layout,
                            floatingDismissButton.getButtonSize(),
                            floatingDismissButton.getMargin()
                    );
                    if (isLongPressResetLandscape) {
                        displayController.resetOrientationToLandscape(activity, postRotate);
                    } else {
                        displayController.cycleOrientation(activity, postRotate);
                    }
                }

                @Override
                public void onCloseMenu() {
                    hideMenu();
                }

                @Override
                public void onOpenBrowserControls() {
                    showBrowserControls();
                }

                @Override
                public void onOpenLauncher() {
                    showLauncher();
                }

                @Override
                public void onSiteLongPressed(AppCatalog.SiteEntry site) {
                    Activity act = getActivity();
                    if (act == null || act.isFinishing()) return;
                    float menuDensity = density * MenuOverlayView.MENU_SCALE;
                    SiteSettingsDialogHelper.show(act, site, uaManager, menuDensity, (siteKey, isDesktop, marginDp) -> {
                        onSiteSettingsSaved(siteKey, isDesktop, marginDp);
                    });
                }
            });

            // Setup Floating Dismiss Button
            View fab = floatingDismissButton.createButton(activity, layout, this::closeSecondTab);
            layout.addView(fab);

            // Re-pin floating button whenever layout dimensions / orientation change
            layout.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                int w = right - left;
                int h = bottom - top;
                int oldW = oldRight - oldLeft;
                int oldH = oldBottom - oldTop;
                if (w > 0 && h > 0 && (w != oldW || h != oldH)) {
                    int currentMarginPx = (int) (currentSiteMarginDp * density);
                    floatingDismissButton.pinToBottomRight(w - currentMarginPx, h);
                    if (oldW > 0 && oldH > 0 && menuOverlayView.isMenuOpen()) {
                        hideMenu();
                    }
                }
            });

            webView.loadUrl(url);
        });
    }

    private SecondaryTabManager.SecondaryTabCallback createSecondaryTabCallback() {
        return new SecondaryTabManager.SecondaryTabCallback() {
            @Override
            public void onTabClosed() {
                menuOverlayView.updateBackButtonState(canGoBack());
                Activity act = getActivity();
                if (act != null) {
                    float density = act.getResources().getDisplayMetrics().density;
                    float md = density * MenuOverlayView.MENU_SCALE;
                    String mainKey = UserAgentManager.getSiteKey(webView != null ? webView.getUrl() : null);
                    if (mainKey.isEmpty()) mainKey = currentSiteKey;
                    uaManager.applySiteDefault(act, mainKey);
                    menuOverlayView.updateDesktopButtonStyle(uaManager, md);
                    int marginDp = SiteSettingsDialogHelper.getSiteDefaultRightMarginDp(act, mainKey);
                    applyRightMarginDp(marginDp);
                    menuOverlayView.updateRightMarginButtonStyle(marginDp > 0, md);
                }
                if (webView != null) {
                    webView.requestFocus();
                }
            }

            @Override
            public void onPageFinished() {
                menuOverlayView.updateBackButtonState(canGoBack());
            }

            @Override
            public void onAuthRedirect(String authUrl) {
                if (webView != null && authUrl != null && !authUrl.isEmpty()) {
                    uaManager.applyAuthUserAgent(webView.getSettings());
                    webView.loadUrl(authUrl);
                }
            }
        };
    }

    private void openUrlInSecondTab(String targetUrl) {
        Activity activity = getActivity();
        if (activity == null || layout == null) return;
        secondaryTabManager.openUrlInSecondTab(
                activity, layout, uaManager, displayController, floatingDismissButton,
                menuOverlayView.getScrimView(), menuOverlayView.getMenuCard(),
                targetUrl, createSecondaryTabCallback()
        );
    }

    private void closeSecondTab() {
        secondaryTabManager.closeSecondTab(layout, floatingDismissButton, createSecondaryTabCallback());
    }

    private boolean canGoBack() {
        if (secondaryTabManager.isOpen()) {
            return secondaryTabManager.canGoBack();
        }
        return (customView != null) || (webView != null && webView.canGoBack());
    }

    private void handleBackNavigation() {
        if (secondaryTabManager.isOpen()) {
            secondaryTabManager.goBack();
        } else if (customView != null) {
            exitCustomFullscreen();
            hideMenu();
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        }
    }

    private void showLauncher() {
        HelpDialogHelper.dismiss();
        floatingDismissButton.hide();
        menuOverlayView.showLauncher(layout);
    }

    private void showBrowserControls() {
        HelpDialogHelper.dismiss();
        floatingDismissButton.hide();
        Activity activity = getActivity();
        if (activity != null) {
            float density = activity.getResources().getDisplayMetrics().density;
            float menuDensity = density * MenuOverlayView.MENU_SCALE;
            menuOverlayView.updateDesktopButtonStyle(uaManager, menuDensity);
            menuOverlayView.updateRightMarginButtonStyle(currentSiteMarginDp > 0, menuDensity);
        }
        menuOverlayView.showBrowserControls(layout, canGoBack());
    }

    private void showMenu() {
        showLauncher();
    }

    private void showHelp() {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) return;
        hideMenu();
        float density = activity.getResources().getDisplayMetrics().density;
        HelpDialogHelper.show(activity, density * MenuOverlayView.MENU_SCALE);
    }

    private void hideMenu() {
        menuOverlayView.hideMenu();
        if (secondaryTabManager.isOpen()) {
            floatingDismissButton.showDismissStyle();
        }
    }

    private void applyUserAgent(boolean reloadPage) {
        Activity activity = getActivity();
        float menuDensity = (activity != null)
                ? activity.getResources().getDisplayMetrics().density * MenuOverlayView.MENU_SCALE
                : 2.0f;
        menuOverlayView.updateDesktopButtonStyle(uaManager, menuDensity);
        if (secondaryTabManager.isOpen()) {
            secondaryTabManager.applyUserAgent(uaManager, reloadPage);
        } else {
            uaManager.applyUserAgent(webView, reloadPage);
        }
    }

    private void applyRightMarginDp(int marginDp) {
        this.currentSiteMarginDp = marginDp;
        Activity activity = getActivity();
        if (activity == null || layout == null) return;
        float density = activity.getResources().getDisplayMetrics().density;
        int marginPx = (int) (marginDp * density);

        if (webView != null) {
            ViewGroup.LayoutParams lp = webView.getLayoutParams();
            if (lp instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                flp.rightMargin = marginPx;
                webView.setLayoutParams(flp);
            }
        }

        if (progressBar != null) {
            ViewGroup.LayoutParams lp = progressBar.getLayoutParams();
            if (lp instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                flp.rightMargin = marginPx;
                progressBar.setLayoutParams(flp);
            }
        }

        secondaryTabManager.applyRightMargin(marginPx);

        int w = layout.getWidth();
        int h = layout.getHeight();
        if (w > 0 && h > 0) {
            floatingDismissButton.pinToBottomRight(w - marginPx, h);
        }
    }

    private void onSiteSettingsSaved(String siteKey, boolean isDesktop, int marginDp) {
        Activity act = getActivity();
        if (act == null || act.isFinishing()) return;
        float menuDensity = act.getResources().getDisplayMetrics().density * MenuOverlayView.MENU_SCALE;

        if (siteKey != null && siteKey.equals(currentSiteKey)) {
            uaManager.setDesktopMode(isDesktop);
            applyUserAgent(true);
            applyRightMarginDp(marginDp);
            menuOverlayView.updateRightMarginButtonStyle(marginDp > 0, menuDensity);
        }

        SiteSettingsDialogHelper.showSaveAckDialog(act, siteKey, isDesktop, marginDp, menuDensity);
    }

    private void navigateToSite(String siteUrl) {
        closeSecondTab();
        exitCustomFullscreen();
        Activity activity = getActivity();

        currentSiteKey = UserAgentManager.getSiteKey(siteUrl);

        if (activity != null) {
            float density = activity.getResources().getDisplayMetrics().density;
            float menuDensity = density * MenuOverlayView.MENU_SCALE;
            uaManager.applySiteDefault(activity, currentSiteKey);
            applyUserAgent(false);
            int marginDp = SiteSettingsDialogHelper.getSiteDefaultRightMarginDp(activity, currentSiteKey);
            applyRightMarginDp(marginDp);
            menuOverlayView.updateRightMarginButtonStyle(marginDp > 0, menuDensity);
        }

        if (webView != null) {
            isExplicitNavigation = true;
            String targetUrl = uaManager.adjustYouTubeTargetUrl(siteUrl);
            webView.loadUrl(targetUrl);
        }
        hideMenu();
    }

    private void exitCustomFullscreen() {
        if (customView != null) {
            if (layout != null) {
                layout.removeView(customView);
            }
            customView = null;
        }
        if (webView != null) {
            webView.setVisibility(View.VISIBLE);
        }
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
    }

    private void terminateBoardAndActivity() {
        closeSecondTab();
        BoardBridge.terminateBoardAndActivity(
                getActivity(),
                () -> emitSignal("quit_app_requested"),
                mainHandler
        );
    }

    @UsedByGodot
    public void close() {
        runOnUiThread(() -> {
            closeSecondTab();
            floatingDismissButton.destroy();
            exitCustomFullscreen();
            if (layout != null) {
                menuOverlayView.destroy(layout);
                layout.removeAllViews();
                ViewGroup parent = (ViewGroup) layout.getParent();
                if (parent != null) {
                    parent.removeView(layout);
                }
                layout = null;
                webView = null;
                progressBar = null;
            }
        });
    }
}