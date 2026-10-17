package com.lastersoft.godotwebview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
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

/**
 * Manages the isolated secondary tab / popup WebView lifecycle.
 * Protects active chess games in the primary WebView by loading external links and popups
 * in an independent overlay container with its own progress bar, fullscreen video, and teardown.
 */
public class SecondaryTabManager {

    private static final String TAG = "GodotWebView.Secondary";

    public interface SecondaryTabCallback {
        void onTabClosed();
        void onPageFinished();
    }

    private WebView popupWebView = null;
    private FrameLayout popupContainer = null;
    private ProgressBar popupProgressBar = null;
    private View popupCustomView = null;
    private WebChromeClient.CustomViewCallback popupCustomViewCallback = null;

    public boolean isOpen() {
        return popupWebView != null;
    }

    public WebView getWebView() {
        return popupWebView;
    }

    public View getCustomView() {
        return popupCustomView;
    }

    public boolean canGoBack() {
        return (popupCustomView != null) || (popupWebView != null && popupWebView.canGoBack());
    }

    public void goBack() {
        if (popupCustomView != null) {
            exitPopupCustomFullscreen();
        } else if (popupWebView != null && popupWebView.canGoBack()) {
            popupWebView.goBack();
        }
    }

    public void reload() {
        if (popupWebView != null) {
            popupWebView.reload();
        }
    }

    public void applyUserAgent(UserAgentManager uaManager, boolean reload) {
        if (popupWebView != null && uaManager != null) {
            uaManager.configureWebSettings(popupWebView.getSettings());
            if (reload) {
                popupWebView.reload();
            }
        }
    }

    public void exitPopupCustomFullscreen() {
        if (popupCustomView != null) {
            if (popupContainer != null) {
                popupContainer.removeView(popupCustomView);
            }
            popupCustomView = null;
        }
        if (popupWebView != null) {
            popupWebView.setVisibility(View.VISIBLE);
        }
        if (popupCustomViewCallback != null) {
            popupCustomViewCallback.onCustomViewHidden();
            popupCustomViewCallback = null;
        }
    }

    public void closeSecondTab(FrameLayout rootLayout, FloatingDismissButton floatingDismissButton,
                               SecondaryTabCallback callback) {
        exitPopupCustomFullscreen();
        if (popupWebView != null) {
            popupWebView.stopLoading();
            popupWebView.onPause();
            popupWebView.loadUrl("about:blank");
            if (popupContainer != null) {
                popupContainer.removeView(popupWebView);
            }
            popupWebView.destroy();
            popupWebView = null;
        }
        if (popupProgressBar != null && popupContainer != null) {
            popupContainer.removeView(popupProgressBar);
            popupProgressBar = null;
        }
        if (popupContainer != null && rootLayout != null) {
            rootLayout.removeView(popupContainer);
            popupContainer = null;
        }
        if (floatingDismissButton != null) {
            floatingDismissButton.hide();
        }
        if (callback != null) {
            callback.onTabClosed();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    public boolean createSecondTabInternal(Activity activity,
                                           FrameLayout rootLayout,
                                           UserAgentManager uaManager,
                                           DisplayController displayController,
                                           FloatingDismissButton floatingDismissButton,
                                           View scrimView,
                                           View menuCard,
                                           WebView.WebViewTransport transport,
                                           String initialUrl,
                                           SecondaryTabCallback callback) {
        if (activity == null || rootLayout == null) return false;

        closeSecondTab(rootLayout, floatingDismissButton, callback);

        float density = activity.getResources().getDisplayMetrics().density;

        popupContainer = new FrameLayout(activity);
        popupContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        popupContainer.setBackgroundColor(Color.BLACK);

        popupWebView = new WebView(activity);
        popupWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        popupWebView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        popupWebView.setVerticalScrollBarEnabled(false);
        popupWebView.setHorizontalScrollBarEnabled(false);

        WebSettings popupSettings = popupWebView.getSettings();
        if (uaManager != null) {
            uaManager.configureWebSettings(popupSettings);
        }
        popupSettings.setJavaScriptEnabled(true);
        popupSettings.setSupportMultipleWindows(true);
        popupSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        popupSettings.setDomStorageEnabled(true);
        popupSettings.setDatabaseEnabled(true);
        popupSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        popupSettings.setUseWideViewPort(true);
        popupSettings.setLoadWithOverviewMode(true);
        popupSettings.setMediaPlaybackRequiresUserGesture(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            popupSettings.setOffscreenPreRaster(true);
        }

        popupSettings.setSupportZoom(true);
        popupSettings.setBuiltInZoomControls(true);
        popupSettings.setDisplayZoomControls(false);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(popupWebView, true);
        }

        popupProgressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        int progressHeight = (int) (4 * density);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, progressHeight);
        progressParams.gravity = Gravity.TOP;
        popupProgressBar.setLayoutParams(progressParams);
        popupProgressBar.setMax(100);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popupProgressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#43A047")));
        }

        popupWebView.setWebViewClient(new WebViewClient() {
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
                    String scheme = request.getUrl().getScheme();
                    if (scheme != null && !scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                        String fullUri = request.getUrl().toString();
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
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (displayController != null) {
                    displayController.applyZoom(null, popupWebView);
                }
                ChessTouchHelper.injectTouchEnhancements(view, url);
                if (callback != null) {
                    callback.onPageFinished();
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().flush();
                }
            }
        });

        popupWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (popupProgressBar != null) {
                    if (newProgress == 100) {
                        popupProgressBar.setVisibility(View.GONE);
                    } else {
                        popupProgressBar.setVisibility(View.VISIBLE);
                        popupProgressBar.setProgress(newProgress);
                    }
                }
            }

            @Override
            public void onCloseWindow(WebView window) {
                closeSecondTab(rootLayout, floatingDismissButton, callback);
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message innerMsg) {
                if (innerMsg != null && innerMsg.obj instanceof WebView.WebViewTransport) {
                    WebView.WebViewTransport innerTransport = (WebView.WebViewTransport) innerMsg.obj;
                    innerTransport.setWebView(view);
                    innerMsg.sendToTarget();
                    return true;
                }
                return false;
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
            public void onShowCustomView(View view, CustomViewCallback chromeCallback) {
                if (popupCustomView != null) {
                    chromeCallback.onCustomViewHidden();
                    return;
                }
                popupCustomView = view;
                popupCustomView.setBackgroundColor(Color.BLACK);
                popupCustomViewCallback = chromeCallback;
                if (popupWebView != null) {
                    popupWebView.setVisibility(View.GONE);
                }
                if (popupContainer != null) {
                    popupContainer.addView(popupCustomView, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
                }
                if (floatingDismissButton != null) floatingDismissButton.bringToFront();
                if (scrimView != null) scrimView.bringToFront();
                if (menuCard != null) menuCard.bringToFront();
            }

            @Override
            public void onHideCustomView() {
                exitPopupCustomFullscreen();
            }
        });

        popupContainer.addView(popupWebView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        popupContainer.addView(popupProgressBar);

        int scrimIndex = (scrimView != null) ? rootLayout.indexOfChild(scrimView) : -1;
        if (scrimIndex >= 0) {
            rootLayout.addView(popupContainer, scrimIndex);
        } else {
            rootLayout.addView(popupContainer);
        }

        if (floatingDismissButton != null) {
            floatingDismissButton.showDismissStyle();
        }

        if (transport != null) {
            transport.setWebView(popupWebView);
        }
        if (initialUrl != null) {
            popupWebView.loadUrl(initialUrl);
        }

        if (callback != null) {
            callback.onPageFinished();
        }

        Log.i(TAG, "Secondary tab opened successfully");
        return true;
    }

    public boolean handleCreateWindow(Activity activity,
                                      FrameLayout rootLayout,
                                      UserAgentManager uaManager,
                                      DisplayController displayController,
                                      FloatingDismissButton floatingDismissButton,
                                      View scrimView,
                                      View menuCard,
                                      Message resultMsg,
                                      SecondaryTabCallback callback) {
        if (resultMsg == null || !(resultMsg.obj instanceof WebView.WebViewTransport)) return false;
        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        boolean success = createSecondTabInternal(activity, rootLayout, uaManager, displayController,
                floatingDismissButton, scrimView, menuCard, transport, null, callback);
        if (success) {
            resultMsg.sendToTarget();
        }
        return success;
    }

    public void openUrlInSecondTab(Activity activity,
                                   FrameLayout rootLayout,
                                   UserAgentManager uaManager,
                                   DisplayController displayController,
                                   FloatingDismissButton floatingDismissButton,
                                   View scrimView,
                                   View menuCard,
                                   String url,
                                   SecondaryTabCallback callback) {
        createSecondTabInternal(activity, rootLayout, uaManager, displayController,
                floatingDismissButton, scrimView, menuCard, null, url, callback);
    }
}

