package com.lastersoft.godotwebview;

import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;
import org.godotengine.godot.plugin.UsedByGodot;

import java.util.HashSet;
import java.util.Set;

/**
 * Chess 4 Board WebView Plugin for Godot 4.
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
                this::showMenu,
                mainHandler
        );
    }

    @UsedByGodot
    public void show_menu() {
        runOnUiThread(this::showMenu);
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
                activity.addContentView(layout, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            } else {
                layout.removeAllViews();
            }

            webView = new WebView(activity);
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            webView.setVerticalScrollBarEnabled(false);
            webView.setHorizontalScrollBarEnabled(false);

            WebSettings settings = webView.getSettings();
            uaManager.init(activity, settings.getUserAgentString());
            uaManager.configureWebSettings(settings);

            settings.setJavaScriptEnabled(true);
            settings.setSupportMultipleWindows(true);
            settings.setJavaScriptCanOpenWindowsAutomatically(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
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
            }

            // Web client
            webView.setWebViewClient(new WebViewClient() {
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

                        // Protect active chess game: route external site links in chess.com to second tab
                        if (request.isForMainFrame()) {
                            String host = targetUri.getHost();
                            String currentUrl = view.getUrl();
                            if (currentUrl != null && currentUrl.contains("chess.com") && host != null) {
                                if (!host.endsWith("chess.com") && !host.endsWith("chesskids.com")) {
                                    openUrlInSecondTab(targetUri.toString());
                                    return true;
                                }
                            }
                        }
                    }
                    return false;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    displayController.applyZoom(webView, secondaryTabManager.getWebView());
                    menuOverlayView.updateBackButtonState(canGoBack());
                    ChessTouchHelper.injectSidebarTouchFix(view, url);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        CookieManager.getInstance().flush();
                    }
                }
            });

            // Loading progress bar
            float density = activity.getResources().getDisplayMetrics().density;
            progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            int progressHeight = (int) (4 * density);
            FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, progressHeight);
            progressParams.gravity = Gravity.TOP;
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

            layout.addView(webView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            layout.addView(progressBar);

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
                    hideMenu();
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
                        uaManager.toggleAndSaveDefaultMode(activity);
                        hideMenu();
                        applyUserAgent(true);
                        uaManager.showDefaultUaAckDialog(activity, menuDensity);
                    } else {
                        uaManager.toggleTemporaryMode();
                        hideMenu();
                        applyUserAgent(true);
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
                public void onExitRequested() {
                    terminateBoardAndActivity();
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
                    floatingDismissButton.pinToBottomRight(w, h);
                    if (menuOverlayView.isMenuOpen()) {
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
                if (webView != null) {
                    webView.requestFocus();
                }
            }

            @Override
            public void onPageFinished() {
                menuOverlayView.updateBackButtonState(canGoBack());
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
            hideMenu();
        } else if (customView != null) {
            exitCustomFullscreen();
            hideMenu();
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
            hideMenu();
        }
    }

    private void showMenu() {
        floatingDismissButton.hide();
        menuOverlayView.showMenu(layout, canGoBack());
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
        secondaryTabManager.applyUserAgent(uaManager, reloadPage);
        uaManager.applyUserAgent(webView, reloadPage);
    }

    private void navigateToSite(String siteUrl) {
        closeSecondTab();
        exitCustomFullscreen();
        Activity activity = getActivity();
        float menuDensity = (activity != null)
                ? activity.getResources().getDisplayMetrics().density * MenuOverlayView.MENU_SCALE
                : 2.0f;
        if (uaManager.isDesktopMode() != uaManager.isDefaultDesktopMode()) {
            uaManager.resetToDefaultMode();
            applyUserAgent(false);
        }
        if (webView != null) {
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