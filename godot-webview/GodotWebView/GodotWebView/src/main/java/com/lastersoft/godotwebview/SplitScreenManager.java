package com.lastersoft.godotwebview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.net.Uri;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import java.util.Collections;
import java.util.Locale;

/**
 * Manages Split-Screen Dual-App Tabletop Mode:
 * - Two live, concurrent hardware-accelerated WebViews (Primary & Secondary).
 * - Draggable center divider with snap presets (50/50, 70/30, 30/70).
 * - Per-pane audio mute/unmute toggling via client-side DOM media bridge.
 * - Swap panes (Left <-> Right) and clean teardown on exit.
 * - Respects Board OS hardware corner cutout and tabletop margins.
 */
public class SplitScreenManager {

    private static final String TAG = "ChessBoard.SplitScreen";

    public interface SplitScreenCallback {
        void onSplitClosed();
        default void onPanesSwapped(boolean isPrimaryOnLeft) {}
        default void onSplitRatioChanged(float ratio) {}
        default void onPickSecondaryApp() {}
        default void onPaneMarginChanged(boolean isPrimary, int marginMode, int marginDp) {}
    }

    private final Handler handler = new Handler(Looper.getMainLooper());

    public enum AudioFocusPane { PRIMARY, SECONDARY, NONE }

    private boolean isSplitActive = false;
    private float splitRatio = 0.50f; // 0.20f to 0.80f
    private boolean isPrimaryOnLeft = true;

    // Audio mutual exclusivity states
    private AudioFocusPane activeAudio = AudioFocusPane.SECONDARY;
    private boolean isPrimaryMuted = true;
    private boolean isSecondaryMuted = false;

    // Per-pane margin states & dp
    private int primaryMarginMode = SiteSettingsDialogHelper.MARGIN_MODE_NONE;
    private int primaryMarginDp = SiteSettingsDialogHelper.DEFAULT_GUTTER_DP;
    private int secondaryMarginMode = SiteSettingsDialogHelper.MARGIN_MODE_NONE;
    private int secondaryMarginDp = SiteSettingsDialogHelper.DEFAULT_GUTTER_DP;

    // Views
    private FrameLayout secondaryContainer = null;
    private WebView secondaryWebView = null;
    private ProgressBar secondaryProgressBar = null;
    private View secondaryCustomView = null;
    private WebChromeClient.CustomViewCallback secondaryCustomViewCallback = null;
    private LinearLayout dividerView = null;
    private View dividerHandle = null;
    private GradientDrawable dividerHandleBg = null;

    // Themed Drag Overlay View (covers WebViews during slider drag with live-scaling app cards)
    private static class DragCardHolder {
        FrameLayout cardContainer;
        LinearLayout cardContent;
        TextView iconView;
        TextView titleView;
        TextView percentView;
        LinearLayout skeletonLayout;
    }
    private FrameLayout dragOverlayView = null;
    private DragCardHolder leftDragHolder = null;
    private DragCardHolder rightDragHolder = null;

    // Integrated Split Control Bar (docked horizontally at top center, visible only when Browser Controls are open)
    private HorizontalScrollView splitControlBarWrapper = null;
    private LinearLayout splitControlBar = null;
    private GradientDrawable splitControlBarBg = null;
    private Button leftAudioBtn = null;
    private Button leftMarginBtn = null;
    private Button swapBtn = null;
    private Button presetBtn = null;
    private Button playPauseBtn = null;
    private Button rightMarginBtn = null;
    private Button rightAudioBtn = null;
    private Button changeAppBtn = null;
    private Button secBackBtn = null;
    private Button secReloadBtn = null;
    private Button flipBtn = null;
    private Button exitBtn = null;
    private boolean areControlsVisible = false;
    private boolean isSecondaryFlipped = false;

    private SplitScreenCallback callback = null;

    // Cached references for layout updates
    private Activity activity = null;
    private FrameLayout rootLayout = null;
    private WebView primaryWebView = null;
    private ProgressBar primaryProgressBar = null;
    private UserAgentManager uaManager = null;
    private String secondarySiteUrl = "";
    private String secondarySiteKey = "";

    public boolean isSplitActive() {
        return isSplitActive;
    }

    public float getSplitRatio() {
        return splitRatio;
    }

    public boolean isPrimaryOnLeft() {
        return isPrimaryOnLeft;
    }

    public boolean areControlsVisible() {
        return areControlsVisible;
    }

    public void setControlsVisible(boolean visible) {
        this.areControlsVisible = visible;
        if (splitControlBarWrapper != null) {
            splitControlBarWrapper.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible) {
                splitControlBarWrapper.bringToFront();
                updateControlBarButtons();
            }
        }
    }

    public boolean isSecondaryFlipped() {
        return isSecondaryFlipped;
    }

    public WebView getSecondaryWebView() {
        return secondaryWebView;
    }

    public boolean isPrimaryMuted() {
        return isPrimaryMuted;
    }

    public boolean isSecondaryMuted() {
        return isSecondaryMuted;
    }

    public AudioFocusPane getActiveAudio() {
        return activeAudio;
    }

    public int getPrimaryMarginMode() {
        return primaryMarginMode;
    }

    public int getPrimaryMarginDp() {
        return primaryMarginDp;
    }

    public int getSecondaryMarginMode() {
        return secondaryMarginMode;
    }

    public int getSecondaryMarginDp() {
        return secondaryMarginDp;
    }

    public int getActivePaneMarginMode() {
        if (activeAudio == AudioFocusPane.PRIMARY) {
            return primaryMarginMode;
        } else if (activeAudio == AudioFocusPane.SECONDARY) {
            return secondaryMarginMode;
        } else {
            return isPrimaryOnLeft ? primaryMarginMode : secondaryMarginMode;
        }
    }

    public boolean openSplit(Activity act,
                              FrameLayout root,
                              WebView primaryView,
                              ProgressBar primaryProgress,
                              String targetUrl,
                              UserAgentManager userAgentMgr,
                              float density,
                              SplitScreenCallback cb) {
        return openSplit(act, root, primaryView, primaryProgress, targetUrl, userAgentMgr, density,
                SiteSettingsDialogHelper.MARGIN_MODE_NONE, SiteSettingsDialogHelper.DEFAULT_GUTTER_DP,
                SiteSettingsDialogHelper.MARGIN_MODE_NONE, SiteSettingsDialogHelper.DEFAULT_GUTTER_DP, cb);
    }

    @SuppressLint("SetJavaScriptEnabled")
    public boolean openSplit(Activity act,
                              FrameLayout root,
                              WebView primaryView,
                              ProgressBar primaryProgress,
                              String targetUrl,
                              UserAgentManager userAgentMgr,
                              float density,
                              int primMarginMode,
                              int primMarginDp,
                              int secMarginMode,
                              int secMarginDp,
                              SplitScreenCallback cb) {
        if (act == null || root == null || primaryView == null) return false;

        this.activity = act;
        this.rootLayout = root;
        this.primaryWebView = primaryView;
        this.primaryProgressBar = primaryProgress;
        this.uaManager = userAgentMgr;
        this.secondarySiteUrl = targetUrl;
        this.secondarySiteKey = UserAgentManager.getSiteKey(targetUrl);
        this.callback = cb;
        this.isSplitActive = true;
        this.primaryMarginMode = primMarginMode;
        this.primaryMarginDp = (primMarginDp > 0) ? primMarginDp : SiteSettingsDialogHelper.DEFAULT_GUTTER_DP;
        this.secondaryMarginMode = secMarginMode;
        this.secondaryMarginDp = (secMarginDp > 0) ? secMarginDp : SiteSettingsDialogHelper.DEFAULT_GUTTER_DP;

        String primKey = (primaryView != null) ? UserAgentManager.getSiteKey(primaryView.getUrl()) : "";
        if ("chesskids.com".equals(primKey)) {
            this.splitRatio = isPrimaryOnLeft ? 0.70f : 0.30f;
        } else if ("chesskids.com".equals(this.secondarySiteKey)) {
            this.splitRatio = isPrimaryOnLeft ? 0.30f : 0.70f;
        } else {
            this.splitRatio = 0.50f;
        }

        float menuDensity = density * MenuOverlayView.MENU_SCALE;

        // 1. Create Secondary Container
        secondaryContainer = new FrameLayout(act);
        secondaryContainer.setBackgroundColor(Color.BLACK);

        // 2. Create Secondary WebView
        secondaryWebView = new WebView(act);
        secondaryWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        secondaryWebView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        secondaryWebView.setVerticalScrollBarEnabled(false);
        secondaryWebView.setHorizontalScrollBarEnabled(false);

        WebSettings settings = secondaryWebView.getSettings();
        if (uaManager != null) {
            uaManager.applySiteDefault(act, secondarySiteKey);
            uaManager.configureWebSettings(settings, targetUrl);
        } else {
            settings.setUserAgentString(UserAgentManager.DESKTOP_USER_AGENT);
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, Collections.emptySet());
        }

        settings.setJavaScriptEnabled(true);
        settings.setSupportMultipleWindows(false);
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

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(secondaryWebView, true);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        secondaryWebView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                WebResourceResponse wasmResp = WasmPatchHelper.shouldInterceptRequest(act, request);
                if (wasmResp != null) return wasmResp;
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                ChessTouchHelper.injectTouchEnhancements(view, url);
                applyAudioMuteState(secondaryWebView, isSecondaryMuted);
                updateControlBarButtons();
            }
        });

        // 3. Secondary Progress Bar
        secondaryProgressBar = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
        int progressHeight = (int) (4 * density);
        FrameLayout.LayoutParams pbParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, progressHeight, Gravity.TOP);
        secondaryProgressBar.setLayoutParams(pbParams);
        secondaryProgressBar.setMax(100);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            secondaryProgressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#22B8CF")));
        }

        secondaryWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (secondaryProgressBar != null) {
                    if (newProgress == 100) {
                        secondaryProgressBar.setVisibility(View.GONE);
                    } else {
                        secondaryProgressBar.setVisibility(View.VISIBLE);
                        secondaryProgressBar.setProgress(newProgress);
                    }
                }
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    request.grant(request.getResources());
                } else {
                    super.onPermissionRequest(request);
                }
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d(TAG, "[Secondary] " + consoleMessage.message() + " ["
                        + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber() + "]");
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (secondaryCustomView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                secondaryCustomView = view;
                secondaryCustomView.setBackgroundColor(Color.BLACK);
                secondaryCustomViewCallback = callback;
                if (secondaryWebView != null) {
                    secondaryWebView.setVisibility(View.GONE);
                }
                if (secondaryContainer != null) {
                    secondaryContainer.addView(secondaryCustomView, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                }
            }

            @Override
            public void onHideCustomView() {
                if (secondaryCustomView != null) {
                    if (secondaryContainer != null) {
                        secondaryContainer.removeView(secondaryCustomView);
                    }
                    secondaryCustomView = null;
                }
                if (secondaryWebView != null) {
                    secondaryWebView.setVisibility(View.VISIBLE);
                }
                if (secondaryCustomViewCallback != null) {
                    secondaryCustomViewCallback.onCustomViewHidden();
                    secondaryCustomViewCallback = null;
                }
            }
        });

        secondaryContainer.addView(secondaryWebView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        secondaryContainer.addView(secondaryProgressBar);

        // 4. Create Center Divider
        createDividerView(act, density, menuDensity);

        // 5. Create Themed Drag Overlay View
        createDragOverlay(act, menuDensity);

        // 6. Create Integrated Split Control Bar (hidden by default)
        createSplitControlBar(act, menuDensity);

        // 7. Insert Views into root hierarchy
        int insertIndex = 1;
        if (root.indexOfChild(primaryWebView) >= 0) {
            insertIndex = root.indexOfChild(primaryWebView) + 1;
        }
        root.addView(secondaryContainer, insertIndex);
        root.addView(dragOverlayView, insertIndex + 1);
        root.addView(dividerView, insertIndex + 2);
        root.addView(splitControlBarWrapper, insertIndex + 3);

        // Set audio focus to secondary by default (secondary unmuted, primary muted)
        setAudioFocus(AudioFocusPane.SECONDARY);
        setControlsVisible(false);

        // 7. Load Target URL in Secondary
        String finalUrl = (uaManager != null) ? uaManager.adjustYouTubeTargetUrl(targetUrl) : targetUrl;
        secondaryWebView.loadUrl(finalUrl);

        // 8. Apply layout
        applyLayoutInternal(root.getWidth(), root.getHeight(), primaryMarginMode, primaryMarginDp);

        return true;
    }

    private void createDividerView(Activity act, float density, float menuDensity) {
        ThemeManager.Theme theme = (act != null) ? ThemeManager.getActiveTheme(act) : ThemeManager.Theme.MICHIGAN;
        dividerView = new LinearLayout(act);
        dividerView.setOrientation(LinearLayout.VERTICAL);
        dividerView.setGravity(Gravity.CENTER);
        dividerView.setBackgroundColor(theme.dividerBgColor);

        dividerHandle = new View(act);
        int handleWidth = (int) (6 * menuDensity);
        int handleHeight = (int) (48 * menuDensity);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(handleWidth, handleHeight);
        dividerHandle.setLayoutParams(handleParams);

        dividerHandleBg = new GradientDrawable();
        dividerHandleBg.setShape(GradientDrawable.RECTANGLE);
        dividerHandleBg.setColor(theme.dividerHandleColor);
        dividerHandleBg.setCornerRadius(3 * menuDensity);
        dividerHandle.setBackground(dividerHandleBg);
        dividerView.addView(dividerHandle);

        dividerView.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private float startRatio;
            private float pendingRatio;
            private boolean isDragging = false;
            private long lastTapTime = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (rootLayout == null || rootLayout.getWidth() <= 0) return false;
                int totalW = rootLayout.getWidth();
                float d = (activity != null) ? activity.getResources().getDisplayMetrics().density : 1.0f;
                float md = d * MenuOverlayView.MENU_SCALE;
                int dividerW = (int) (12 * md);
                int availW = Math.max(1, totalW - dividerW);

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startRatio = splitRatio;
                        pendingRatio = splitRatio;
                        isDragging = false;

                        long now = System.currentTimeMillis();
                        if (now - lastTapTime < 300) {
                            // Double-tap resets to 50/50
                            v.setTranslationX(0);
                            splitRatio = 0.50f;
                            applyLayoutUpdate();
                            updateControlBarButtons();
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            if (callback != null) callback.onSplitRatioChanged(splitRatio);
                            lastTapTime = 0;
                            return true;
                        }
                        lastTapTime = now;
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - downX;
                        if (!isDragging && Math.abs(deltaX) > 10) {
                            isDragging = true;
                            // Visual feedback: highlight handle and divider bar while dragging
                            if (dividerHandle != null) {
                                GradientDrawable activeBg = new GradientDrawable();
                                activeBg.setShape(GradientDrawable.RECTANGLE);
                                activeBg.setColor(Color.parseColor("#22B8CF"));
                                activeBg.setCornerRadius(3 * md);
                                dividerHandle.setBackground(activeBg);
                            }
                            dividerView.setBackgroundColor(Color.parseColor("#1E2638"));

                            if (dragOverlayView != null) {
                                dragOverlayView.setVisibility(View.VISIBLE);
                                dragOverlayView.bringToFront();
                                dividerView.bringToFront();
                                if (splitControlBarWrapper != null && areControlsVisible) {
                                    splitControlBarWrapper.bringToFront();
                                }
                            }
                        }

                        if (isDragging) {
                            float newRatio = isPrimaryOnLeft
                                    ? (startRatio + (deltaX / availW))
                                    : (startRatio - (deltaX / availW));
                            newRatio = Math.max(0.20f, Math.min(0.80f, newRatio));
                            pendingRatio = newRatio;

                            // Calculate visual pixel translation for divider thumb (GPU-only, no WebView relayout!)
                            int startLeft = isPrimaryOnLeft
                                    ? (int) (availW * startRatio)
                                    : (int) (availW * (1.0f - startRatio));
                            int targetLeft = isPrimaryOnLeft
                                    ? (int) (availW * pendingRatio)
                                    : (int) (availW * (1.0f - pendingRatio));
                            float visualDeltaX = targetLeft - startLeft;

                            v.setTranslationX(visualDeltaX);

                            int totalH = (rootLayout != null) ? rootLayout.getHeight() : 0;
                            updateDragOverlay(totalW, totalH, pendingRatio, md);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // Restore divider handle styling
                        if (dividerHandle != null) {
                            GradientDrawable normalBg = new GradientDrawable();
                            normalBg.setShape(GradientDrawable.RECTANGLE);
                            normalBg.setColor(Color.parseColor("#4C6EF5"));
                            normalBg.setCornerRadius(3 * md);
                            dividerHandle.setBackground(normalBg);
                        }
                        dividerView.setBackgroundColor(Color.parseColor("#151A28"));
                        v.setTranslationX(0);

                        if (dragOverlayView != null) {
                            dragOverlayView.setVisibility(View.GONE);
                        }

                        if (isDragging && event.getActionMasked() == MotionEvent.ACTION_UP) {
                            // Snap to 50/50 if within 5%
                            if (Math.abs(pendingRatio - 0.50f) < 0.05f) {
                                pendingRatio = 0.50f;
                            } else if (Math.abs(pendingRatio - 0.70f) < 0.03f) {
                                pendingRatio = 0.70f;
                            } else if (Math.abs(pendingRatio - 0.30f) < 0.03f) {
                                pendingRatio = 0.30f;
                            }

                            splitRatio = pendingRatio;
                            // Redraw WebViews ONLY once thumb has been let go!
                            applyLayoutUpdate();
                            updateControlBarButtons();
                            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                            if (callback != null) callback.onSplitRatioChanged(splitRatio);
                        }
                        isDragging = false;
                        return true;
                }
                return false;
            }
        });
    }

    private static class AppInfo {
        final String name;
        final String icon;

        AppInfo(String name, String icon) {
            this.name = name;
            this.icon = icon;
        }
    }

    private AppInfo resolveAppInfo(String url) {
        if (url == null || url.isEmpty()) {
            return new AppInfo("Browser", "🌐");
        }
        AppCatalog.SiteEntry entry = AppCatalog.findSiteEntryByUrl(url);
        if (entry != null) {
            return new AppInfo(entry.name, entry.icon);
        }
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host != null) {
                host = host.replace("www.", "").replace("m.", "");
                int dot = host.indexOf('.');
                if (dot > 0) host = host.substring(0, dot);
                if (host.length() > 0) {
                    String title = Character.toUpperCase(host.charAt(0)) + host.substring(1);
                    return new AppInfo(title, "🌐");
                }
            }
        } catch (Exception ignored) {}
        return new AppInfo("Browser", "🌐");
    }

    private void createSplitControlBar(Activity act, float menuDensity) {
        ThemeManager.Theme theme = (act != null) ? ThemeManager.getActiveTheme(act) : ThemeManager.Theme.MICHIGAN;
        splitControlBarWrapper = new HorizontalScrollView(act);
        splitControlBarWrapper.setHorizontalScrollBarEnabled(false);
        splitControlBarWrapper.setOverScrollMode(View.OVER_SCROLL_NEVER);
        splitControlBarWrapper.setClipToPadding(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            splitControlBarWrapper.setElevation(18 * menuDensity);
        }
        splitControlBarWrapper.setVisibility(View.GONE); // Hidden by default!

        splitControlBar = new LinearLayout(act);
        splitControlBar.setOrientation(LinearLayout.HORIZONTAL);
        splitControlBar.setGravity(Gravity.CENTER_VERTICAL);

        splitControlBarBg = new GradientDrawable();
        splitControlBarBg.setShape(GradientDrawable.RECTANGLE);
        splitControlBarBg.setColor(theme.splitControlBarBgColor);
        splitControlBarBg.setCornerRadius(18 * menuDensity);
        splitControlBarBg.setStroke((int) (1.5f * menuDensity), theme.controlsRailBorderColor);
        splitControlBar.setBackground(splitControlBarBg);

        int padH = (int) (8 * menuDensity);
        int padV = (int) (5 * menuDensity);
        splitControlBar.setPadding(padH, padV, padH, padV);

        // 1. Left Audio Button
        leftAudioBtn = createBarButton(act, menuDensity);
        leftAudioBtn.setOnClickListener(v -> toggleAudio(isPrimaryOnLeft));
        leftAudioBtn.setOnLongClickListener(v -> {
            setAudioFocus(AudioFocusPane.NONE);
            if (activity != null) activity.getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            return true;
        });
        splitControlBar.addView(leftAudioBtn);

        // 1B. Left Pane Margin / Padding Button
        leftMarginBtn = createBarButton(act, menuDensity);
        leftMarginBtn.setOnClickListener(v -> cycleLeftPaneMargin());
        leftMarginBtn.setOnLongClickListener(v -> {
            savePaneMarginDefault(true);
            return true;
        });
        splitControlBar.addView(leftMarginBtn);

        // 2. Swap Button
        swapBtn = createBarButton(act, menuDensity);
        swapBtn.setText("⇄ Swap");
        swapBtn.setTextColor(Color.parseColor("#CBD5E1"));
        swapBtn.setBackground(createButtonPillBackground(menuDensity, false, null));
        swapBtn.setOnClickListener(v -> swapPanes());
        splitControlBar.addView(swapBtn);

        // 3. Preset Button
        presetBtn = createBarButton(act, menuDensity);
        presetBtn.setText(String.format(Locale.US, "◫ %d/%d", Math.round(splitRatio * 100), 100 - Math.round(splitRatio * 100)));
        presetBtn.setTextColor(theme.accentColor);
        presetBtn.setBackground(createButtonPillBackground(menuDensity, false, null));
        presetBtn.setOnClickListener(v -> cycleSplitPreset());
        splitControlBar.addView(presetBtn);

        // 4. Play/Pause Media Button
        playPauseBtn = createBarButton(act, menuDensity);
        playPauseBtn.setText("⏯");
        playPauseBtn.setTextColor(theme.accentColor);
        playPauseBtn.setBackground(createButtonPillBackground(menuDensity, false, null));
        playPauseBtn.setOnClickListener(v -> toggleActiveMediaPlayback());
        splitControlBar.addView(playPauseBtn);

        // 4B. Right Pane Margin / Padding Button
        rightMarginBtn = createBarButton(act, menuDensity);
        rightMarginBtn.setOnClickListener(v -> cycleRightPaneMargin());
        rightMarginBtn.setOnLongClickListener(v -> {
            savePaneMarginDefault(false);
            return true;
        });
        splitControlBar.addView(rightMarginBtn);

        // 5. Right Audio Button
        rightAudioBtn = createBarButton(act, menuDensity);
        rightAudioBtn.setOnClickListener(v -> toggleAudio(!isPrimaryOnLeft));
        rightAudioBtn.setOnLongClickListener(v -> {
            setAudioFocus(AudioFocusPane.NONE);
            if (activity != null) activity.getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            return true;
        });
        splitControlBar.addView(rightAudioBtn);

        // 6. Secondary App Hot-Swap Button
        changeAppBtn = createBarButton(act, menuDensity);
        changeAppBtn.setText("🔀 App");
        changeAppBtn.setTextColor(theme.accentColor);
        changeAppBtn.setBackground(createButtonPillBackground(menuDensity, false, null));
        changeAppBtn.setOnClickListener(v -> pickSecondaryApp());
        splitControlBar.addView(changeAppBtn);

        // 7. Secondary Back Button
        secBackBtn = createBarButton(act, menuDensity);
        secBackBtn.setText("◂");
        secBackBtn.setTextColor(Color.parseColor("#CBD5E1"));
        secBackBtn.setBackground(createButtonPillBackground(menuDensity, false, null));
        secBackBtn.setOnClickListener(v -> navigateSecondaryBack());
        splitControlBar.addView(secBackBtn);

        // 8. Secondary Reload Button
        secReloadBtn = createBarButton(act, menuDensity);
        secReloadBtn.setText("↺");
        secReloadBtn.setTextColor(Color.parseColor("#CBD5E1"));
        secReloadBtn.setBackground(createButtonPillBackground(menuDensity, false, null));
        secReloadBtn.setOnClickListener(v -> reloadSecondary());
        splitControlBar.addView(secReloadBtn);

        // 9. Secondary 180° Inversion Button (Head-to-head mode)
        flipBtn = createBarButton(act, menuDensity);
        flipBtn.setText("🔄 180°");
        flipBtn.setTextColor(Color.parseColor("#CBD5E1"));
        flipBtn.setBackground(createButtonPillBackground(menuDensity, false, null));
        flipBtn.setOnClickListener(v -> toggleSecondaryFlip());
        splitControlBar.addView(flipBtn);

        // 10. Exit Split Button
        exitBtn = createBarButton(act, menuDensity);
        exitBtn.setText("✕ Exit");
        exitBtn.setTextColor(Color.parseColor("#FFA8A8"));
        GradientDrawable exitBg = new GradientDrawable();
        exitBg.setShape(GradientDrawable.RECTANGLE);
        exitBg.setColor(Color.parseColor("#38181E"));
        exitBg.setCornerRadius(14 * menuDensity);
        exitBg.setStroke((int) (1.5f * menuDensity), Color.parseColor("#E03131"));
        exitBtn.setBackground(exitBg);
        exitBtn.setOnClickListener(v -> closeSplit());
        splitControlBar.addView(exitBtn);

        splitControlBarWrapper.addView(splitControlBar);
        updateControlBarButtons();
    }

    private Button createBarButton(Activity act, float menuDensity) {
        Button btn = new Button(act);
        btn.setTextColor(Color.parseColor("#CBD5E1"));
        btn.setTextSize(10.0f * MenuOverlayView.MENU_SCALE);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        int height = (int) (32 * menuDensity);
        int padH = (int) (8 * menuDensity);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, height);
        lp.setMargins((int) (2.5f * menuDensity), 0, (int) (2.5f * menuDensity), 0);
        btn.setLayoutParams(lp);
        btn.setPadding(padH, 0, padH, 0);
        btn.setIncludeFontPadding(false);
        btn.setGravity(Gravity.CENTER);
        btn.setMinWidth(0);
        btn.setMinHeight(0);
        btn.setMinimumWidth(0);
        btn.setMinimumHeight(0);
        btn.setAllCaps(false);
        return btn;
    }

    private GradientDrawable createButtonPillBackground(float menuDensity, boolean isActive, Integer activeColor) {
        ThemeManager.Theme theme = (activity != null) ? ThemeManager.getActiveTheme(activity) : ThemeManager.Theme.MICHIGAN;
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(theme.cardBgColor);
        bg.setCornerRadius(14 * menuDensity);
        if (isActive && activeColor != null) {
            bg.setStroke((int) (1.5f * menuDensity), activeColor);
        } else {
            bg.setStroke((int) (1 * menuDensity), theme.cardBorderColor);
        }
        return bg;
    }

    public void updateControlBarButtons() {
        if (splitControlBar == null || activity == null) return;
        float density = activity.getResources().getDisplayMetrics().density;
        float menuDensity = density * MenuOverlayView.MENU_SCALE;
        ThemeManager.Theme theme = ThemeManager.getActiveTheme(activity);

        AppInfo primInfo = resolveAppInfo(primaryWebView != null ? primaryWebView.getUrl() : null);
        AppInfo secInfo = resolveAppInfo(secondarySiteUrl);

        AppInfo leftInfo = isPrimaryOnLeft ? primInfo : secInfo;
        AppInfo rightInfo = isPrimaryOnLeft ? secInfo : primInfo;

        boolean isLeftMuted = isPrimaryOnLeft ? isPrimaryMuted : isSecondaryMuted;
        boolean isRightMuted = isPrimaryOnLeft ? isSecondaryMuted : isPrimaryMuted;

        int activeAccent = theme.accentColor;

        if (leftAudioBtn != null) {
            leftAudioBtn.setText((isLeftMuted ? "🔇 " : "🔊 ") + leftInfo.name);
            leftAudioBtn.setTextColor(isLeftMuted ? Color.parseColor("#FF6B6B") : activeAccent);
            leftAudioBtn.setBackground(createButtonPillBackground(menuDensity, !isLeftMuted, activeAccent));
        }

        int leftMode = isPrimaryOnLeft ? primaryMarginMode : secondaryMarginMode;
        int rightMode = isPrimaryOnLeft ? secondaryMarginMode : primaryMarginMode;

        if (leftMarginBtn != null) {
            String label;
            if (leftMode == SiteSettingsDialogHelper.MARGIN_MODE_RIGHT) {
                label = "◧ L-Pad: Left";
            } else if (leftMode == SiteSettingsDialogHelper.MARGIN_MODE_ALL) {
                label = "◧ L-Pad: All 4";
            } else {
                label = "◧ L-Pad: Off";
            }
            leftMarginBtn.setText(label);
            boolean isPadActive = (leftMode != SiteSettingsDialogHelper.MARGIN_MODE_NONE);
            leftMarginBtn.setTextColor(isPadActive ? activeAccent : Color.parseColor("#CBD5E1"));
            leftMarginBtn.setBackground(createButtonPillBackground(menuDensity, isPadActive, activeAccent));
        }

        if (rightMarginBtn != null) {
            String label;
            if (rightMode == SiteSettingsDialogHelper.MARGIN_MODE_RIGHT) {
                label = "◨ R-Pad: Right";
            } else if (rightMode == SiteSettingsDialogHelper.MARGIN_MODE_ALL) {
                label = "◨ R-Pad: All 4";
            } else {
                label = "◨ R-Pad: Off";
            }
            rightMarginBtn.setText(label);
            boolean isPadActive = (rightMode != SiteSettingsDialogHelper.MARGIN_MODE_NONE);
            rightMarginBtn.setTextColor(isPadActive ? activeAccent : Color.parseColor("#CBD5E1"));
            rightMarginBtn.setBackground(createButtonPillBackground(menuDensity, isPadActive, activeAccent));
        }

        if (rightAudioBtn != null) {
            rightAudioBtn.setText((isRightMuted ? "🔇 " : "🔊 ") + rightInfo.name);
            rightAudioBtn.setTextColor(isRightMuted ? Color.parseColor("#FF6B6B") : activeAccent);
            rightAudioBtn.setBackground(createButtonPillBackground(menuDensity, !isRightMuted, activeAccent));
        }

        if (presetBtn != null) {
            presetBtn.setText(String.format(Locale.US, "◫ %d/%d", Math.round(splitRatio * 100), 100 - Math.round(splitRatio * 100)));
            presetBtn.setTextColor(theme.accentColor);
            presetBtn.setBackground(createButtonPillBackground(menuDensity, false, null));
        }

        if (swapBtn != null) {
            swapBtn.setBackground(createButtonPillBackground(menuDensity, false, null));
        }

        if (playPauseBtn != null) {
            playPauseBtn.setTextColor(theme.accentColor);
            playPauseBtn.setBackground(createButtonPillBackground(menuDensity, false, null));
        }

        if (changeAppBtn != null) {
            changeAppBtn.setTextColor(theme.accentColor);
            changeAppBtn.setBackground(createButtonPillBackground(menuDensity, false, null));
        }

        if (secBackBtn != null) {
            boolean canBack = (secondaryWebView != null && secondaryWebView.canGoBack());
            secBackBtn.setTextColor(canBack ? Color.parseColor("#F8FAFC") : Color.parseColor("#64748B"));
            secBackBtn.setBackground(createButtonPillBackground(menuDensity, false, null));
        }

        if (secReloadBtn != null) {
            secReloadBtn.setTextColor(Color.parseColor("#CBD5E1"));
            secReloadBtn.setBackground(createButtonPillBackground(menuDensity, false, null));
        }

        if (flipBtn != null) {
            flipBtn.setText(isSecondaryFlipped ? "🙃 180°" : "🔄 180°");
            flipBtn.setTextColor(isSecondaryFlipped ? theme.accentColor : Color.parseColor("#CBD5E1"));
            flipBtn.setBackground(createButtonPillBackground(menuDensity, isSecondaryFlipped, theme.accentColor));
        }
    }

    private void createDragOverlay(Activity act, float menuDensity) {
        ThemeManager.Theme theme = (act != null) ? ThemeManager.getActiveTheme(act) : ThemeManager.Theme.MICHIGAN;
        dragOverlayView = new FrameLayout(act);
        dragOverlayView.setBackgroundColor(theme.dragOverlayCurtainColor);
        dragOverlayView.setVisibility(View.GONE);

        leftDragHolder = createDragCardHolder(act, menuDensity);
        rightDragHolder = createDragCardHolder(act, menuDensity);

        dragOverlayView.addView(leftDragHolder.cardContainer);
        dragOverlayView.addView(rightDragHolder.cardContainer);
    }

    private DragCardHolder createDragCardHolder(Activity act, float menuDensity) {
        ThemeManager.Theme theme = (act != null) ? ThemeManager.getActiveTheme(act) : ThemeManager.Theme.MICHIGAN;
        DragCardHolder holder = new DragCardHolder();
        holder.cardContainer = new FrameLayout(act);

        holder.cardContent = new LinearLayout(act);
        holder.cardContent.setOrientation(LinearLayout.VERTICAL);
        holder.cardContent.setGravity(Gravity.CENTER);

        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        holder.cardContent.setLayoutParams(contentLp);

        // 1. App Icon
        holder.iconView = new TextView(act);
        holder.iconView.setTextSize(36.0f * MenuOverlayView.MENU_SCALE);
        holder.iconView.setGravity(Gravity.CENTER);
        holder.iconView.setIncludeFontPadding(false);
        holder.cardContent.addView(holder.iconView);

        // 2. App Title
        holder.titleView = new TextView(act);
        holder.titleView.setTextSize(16.0f * MenuOverlayView.MENU_SCALE);
        holder.titleView.setTypeface(Typeface.DEFAULT_BOLD);
        holder.titleView.setTextColor(Color.parseColor("#F8FAFC"));
        holder.titleView.setGravity(Gravity.CENTER);
        holder.titleView.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMargins(0, (int) (6 * menuDensity), 0, (int) (8 * menuDensity));
        holder.titleView.setLayoutParams(titleLp);
        holder.cardContent.addView(holder.titleView);

        // 3. Live Percentage
        holder.percentView = new TextView(act);
        holder.percentView.setTextSize(32.0f * MenuOverlayView.MENU_SCALE);
        holder.percentView.setTypeface(Typeface.DEFAULT_BOLD);
        holder.percentView.setGravity(Gravity.CENTER);
        holder.percentView.setIncludeFontPadding(false);
        LinearLayout.LayoutParams pctLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pctLp.setMargins(0, 0, 0, (int) (12 * menuDensity));
        holder.percentView.setLayoutParams(pctLp);
        holder.cardContent.addView(holder.percentView);

        // 4. Wireframe Skeleton Bars
        holder.skeletonLayout = new LinearLayout(act);
        holder.skeletonLayout.setOrientation(LinearLayout.VERTICAL);
        holder.skeletonLayout.setGravity(Gravity.CENTER_HORIZONTAL);

        int barH = (int) (6 * menuDensity);
        int barR = (int) (3 * menuDensity);

        View bar1 = createSkeletonBar(act, (int) (120 * menuDensity), barH, barR, theme.primaryColor);
        LinearLayout.LayoutParams b1Lp = new LinearLayout.LayoutParams((int) (120 * menuDensity), barH);
        b1Lp.setMargins(0, 0, 0, (int) (6 * menuDensity));
        bar1.setLayoutParams(b1Lp);
        holder.skeletonLayout.addView(bar1);

        View bar2 = createSkeletonBar(act, (int) (80 * menuDensity), barH, barR, theme.cardBorderColor);
        LinearLayout.LayoutParams b2Lp = new LinearLayout.LayoutParams((int) (80 * menuDensity), barH);
        b2Lp.setMargins(0, 0, 0, (int) (6 * menuDensity));
        bar2.setLayoutParams(b2Lp);
        holder.skeletonLayout.addView(bar2);

        View bar3 = createSkeletonBar(act, (int) (140 * menuDensity), barH, barR, theme.cardBgColor);
        LinearLayout.LayoutParams b3Lp = new LinearLayout.LayoutParams((int) (140 * menuDensity), barH);
        bar3.setLayoutParams(b3Lp);
        holder.skeletonLayout.addView(bar3);

        holder.cardContent.addView(holder.skeletonLayout);
        holder.cardContainer.addView(holder.cardContent);

        return holder;
    }

    private View createSkeletonBar(Activity act, int width, int height, int radius, int color) {
        View bar = new View(act);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(color);
        bg.setCornerRadius(radius);
        bar.setBackground(bg);
        return bar;
    }

    private void updateDragOverlay(int totalW, int totalH, float ratio, float menuDensity) {
        if (dragOverlayView == null || leftDragHolder == null || rightDragHolder == null) return;
        if (totalW <= 0 || totalH <= 0) return;

        ThemeManager.Theme theme = (activity != null) ? ThemeManager.getActiveTheme(activity) : ThemeManager.Theme.MICHIGAN;
        int activeAccent = theme.accentColor;
        int mutedBorder = theme.cardBorderColor;
        int cardBgColor = theme.dragCardBgColor;

        int dividerW = (int) (12 * menuDensity);
        int availW = Math.max(1, totalW - dividerW);
        int leftPaneW = (int) (availW * ratio);
        int rightPaneW = availW - leftPaneW;
        int cardMargin = (int) (10 * menuDensity);

        AppInfo primInfo = resolveAppInfo(primaryWebView != null ? primaryWebView.getUrl() : null);
        AppInfo secInfo = resolveAppInfo(secondarySiteUrl);

        AppInfo leftInfo = isPrimaryOnLeft ? primInfo : secInfo;
        AppInfo rightInfo = isPrimaryOnLeft ? secInfo : primInfo;

        boolean isLeftMuted = isPrimaryOnLeft ? isPrimaryMuted : isSecondaryMuted;
        boolean isRightMuted = isPrimaryOnLeft ? isSecondaryMuted : isPrimaryMuted;

        int leftPct = Math.round(ratio * 100);
        int rightPct = 100 - leftPct;

        // Position & Style Left Card
        int leftCardW = Math.max(10, leftPaneW - (cardMargin * 2));
        int cardH = Math.max(10, totalH - (cardMargin * 2));
        FrameLayout.LayoutParams leftLp = new FrameLayout.LayoutParams(leftCardW, cardH);
        leftLp.leftMargin = cardMargin;
        leftLp.topMargin = cardMargin;
        leftDragHolder.cardContainer.setLayoutParams(leftLp);

        GradientDrawable leftBg = new GradientDrawable();
        leftBg.setShape(GradientDrawable.RECTANGLE);
        leftBg.setColor(cardBgColor);
        leftBg.setCornerRadius(16 * menuDensity);
        leftBg.setStroke((int) (1.5f * menuDensity), !isLeftMuted ? activeAccent : mutedBorder);
        leftDragHolder.cardContainer.setBackground(leftBg);

        leftDragHolder.iconView.setText(leftInfo.icon);
        leftDragHolder.titleView.setText(leftInfo.name);
        leftDragHolder.percentView.setText(leftPct + "%");
        leftDragHolder.percentView.setTextColor(!isLeftMuted ? activeAccent : Color.parseColor("#CBD5E1"));

        float leftScale = Math.max(0.55f, Math.min(1.35f, ratio / 0.50f));
        leftDragHolder.cardContent.setScaleX(leftScale);
        leftDragHolder.cardContent.setScaleY(leftScale);
        leftDragHolder.cardContainer.setAlpha(Math.max(0.2f, Math.min(1.0f, (ratio - 0.15f) / 0.15f)));

        // Position & Style Right Card
        int rightCardW = Math.max(10, rightPaneW - (cardMargin * 2));
        FrameLayout.LayoutParams rightLp = new FrameLayout.LayoutParams(rightCardW, cardH);
        rightLp.leftMargin = leftPaneW + dividerW + cardMargin;
        rightLp.topMargin = cardMargin;
        rightDragHolder.cardContainer.setLayoutParams(rightLp);

        GradientDrawable rightBg = new GradientDrawable();
        rightBg.setShape(GradientDrawable.RECTANGLE);
        rightBg.setColor(cardBgColor);
        rightBg.setCornerRadius(16 * menuDensity);
        rightBg.setStroke((int) (1.5f * menuDensity), !isRightMuted ? activeAccent : mutedBorder);
        rightDragHolder.cardContainer.setBackground(rightBg);

        rightDragHolder.iconView.setText(rightInfo.icon);
        rightDragHolder.titleView.setText(rightInfo.name);
        rightDragHolder.percentView.setText(rightPct + "%");
        rightDragHolder.percentView.setTextColor(!isRightMuted ? activeAccent : Color.parseColor("#CBD5E1"));

        float rightScale = Math.max(0.55f, Math.min(1.35f, (1.0f - ratio) / 0.50f));
        rightDragHolder.cardContent.setScaleX(rightScale);
        rightDragHolder.cardContent.setScaleY(rightScale);
        rightDragHolder.cardContainer.setAlpha(Math.max(0.2f, Math.min(1.0f, ((1.0f - ratio) - 0.15f) / 0.15f)));
    }

    public void applyTheme(ThemeManager.Theme theme) {
        if (theme == null) return;
        if (dividerView != null) {
            dividerView.setBackgroundColor(theme.dividerBgColor);
        }
        if (dividerHandleBg != null) {
            dividerHandleBg.setColor(theme.dividerHandleColor);
        }
        if (dragOverlayView != null) {
            dragOverlayView.setBackgroundColor(theme.dragOverlayCurtainColor);
        }
        if (splitControlBarBg != null) {
            splitControlBarBg.setColor(theme.splitControlBarBgColor);
            float md = (activity != null) ? activity.getResources().getDisplayMetrics().density * MenuOverlayView.MENU_SCALE : 1.0f;
            splitControlBarBg.setStroke((int) (1.5f * md), theme.controlsRailBorderColor);
        }
        updateControlBarButtons();
        if (isSplitActive && rootLayout != null) {
            applyLayoutUpdate();
        }
    }

    public void swapPanes() {
        if (!isSplitActive) return;
        isPrimaryOnLeft = !isPrimaryOnLeft;
        if (activity != null) {
            activity.getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        }
        applyLayoutUpdate();
        updateControlBarButtons();
        if (callback != null) callback.onPanesSwapped(isPrimaryOnLeft);
    }

    public void cycleSplitPreset() {
        if (!isSplitActive) return;
        if (Math.abs(splitRatio - 0.50f) < 0.05f) {
            splitRatio = 0.70f; // Primary large
        } else if (Math.abs(splitRatio - 0.70f) < 0.05f) {
            splitRatio = 0.30f; // Secondary large
        } else {
            splitRatio = 0.50f; // Balanced
        }
        if (activity != null) {
            activity.getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
        applyLayoutUpdate();
        updateControlBarButtons();
        if (callback != null) callback.onSplitRatioChanged(splitRatio);
    }

    public void toggleAudio(boolean isPrimary) {
        Log.i(TAG, "toggleAudio tapped: isPrimary=" + isPrimary + ", current activeAudio=" + activeAudio);
        if (isPrimary) {
            if (activeAudio == AudioFocusPane.PRIMARY) {
                setAudioFocus(AudioFocusPane.SECONDARY);
            } else {
                setAudioFocus(AudioFocusPane.PRIMARY);
            }
        } else {
            if (activeAudio == AudioFocusPane.SECONDARY) {
                setAudioFocus(AudioFocusPane.PRIMARY);
            } else {
                setAudioFocus(AudioFocusPane.SECONDARY);
            }
        }
        if (activity != null) {
            activity.getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    public void setAudioFocus(AudioFocusPane focus) {
        this.activeAudio = focus;
        this.isPrimaryMuted = (focus != AudioFocusPane.PRIMARY);
        this.isSecondaryMuted = (focus != AudioFocusPane.SECONDARY);

        Log.i(TAG, "setAudioFocus: focus=" + focus + " [PrimaryMuted=" + isPrimaryMuted + ", SecondaryMuted=" + isSecondaryMuted + "]");

        if (primaryWebView != null) {
            applyAudioMuteState(primaryWebView, isPrimaryMuted);
        }
        if (secondaryWebView != null) {
            applyAudioMuteState(secondaryWebView, isSecondaryMuted);
        }

        updateControlBarButtons();
    }

    public void onPrimaryPageFinished(WebView primary) {
        if (isSplitActive && primary != null) {
            applyAudioMuteState(primary, isPrimaryMuted);
            updateControlBarButtons();
        }
    }

    private void applyAudioMuteState(WebView wv, boolean muted) {
        if (wv == null) return;
        String js = String.format(Locale.US,
                "(function() {" +
                "  window.__boardMuted = %b;" +
                "  document.querySelectorAll('video, audio').forEach(function(e) { e.muted = %b; });" +
                "  if (!window.__boardAudioGuard) {" +
                "    window.__boardAudioGuard = true;" +
                "    window.addEventListener('volumechange', function(e) {" +
                "      if (window.__boardMuted && e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) {" +
                "        if (!e.target.muted) e.target.muted = true;" +
                "      }" +
                "    }, true);" +
                "    window.addEventListener('play', function(e) {" +
                "      if (window.__boardMuted && e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) {" +
                "        if (!e.target.muted) e.target.muted = true;" +
                "      }" +
                "    }, true);" +
                "    var obs = new MutationObserver(function() {" +
                "      if (window.__boardMuted) {" +
                "        document.querySelectorAll('video, audio').forEach(function(e) {" +
                "          if (!e.muted) e.muted = true;" +
                "        });" +
                "      }" +
                "    });" +
                "    var target = document.body || document.documentElement;" +
                "    if (target) obs.observe(target, { childList: true, subtree: true });" +
                "  }" +
                "})();",
                muted, muted);
        wv.evaluateJavascript(js, null);
    }

    public void toggleSecondaryFlip() {
        if (secondaryWebView == null) return;
        isSecondaryFlipped = !isSecondaryFlipped;
        secondaryWebView.setRotation(isSecondaryFlipped ? 180.0f : 0.0f);
        if (activity != null) {
            activity.getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
        updateControlBarButtons();
    }

    public void toggleActiveMediaPlayback() {
        WebView targetView = (activeAudio == AudioFocusPane.PRIMARY) ? primaryWebView : secondaryWebView;
        if (targetView == null && secondaryWebView != null) {
            targetView = secondaryWebView;
        }
        if (targetView != null) {
            String js = "(function() {" +
                    "  var media = document.querySelectorAll('video, audio');" +
                    "  if (!media || media.length === 0) return;" +
                    "  var anyPlaying = false;" +
                    "  for (var i = 0; i < media.length; i++) {" +
                    "    if (!media[i].paused && !media[i].ended && media[i].readyState > 2) {" +
                    "      anyPlaying = true; break;" +
                    "    }" +
                    "  }" +
                    "  for (var j = 0; j < media.length; j++) {" +
                    "    if (anyPlaying) { media[j].pause(); } else { media[j].play(); }" +
                    "  }" +
                    "})();";
            targetView.evaluateJavascript(js, null);
            if (activity != null) {
                activity.getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
        }
    }

    public void navigateSecondaryBack() {
        if (secondaryWebView != null && secondaryWebView.canGoBack()) {
            secondaryWebView.goBack();
            if (activity != null) {
                activity.getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
            updateControlBarButtons();
        }
    }

    public void reloadSecondary() {
        if (secondaryWebView != null) {
            secondaryWebView.reload();
            if (activity != null) {
                activity.getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
        }
    }

    public void pickSecondaryApp() {
        if (activity != null) {
            activity.getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
        if (callback != null) {
            callback.onPickSecondaryApp();
        }
    }

    public void loadSecondaryUrl(String targetUrl) {
        if (targetUrl == null || targetUrl.trim().isEmpty() || secondaryWebView == null) return;
        this.secondarySiteUrl = targetUrl;
        this.secondarySiteKey = UserAgentManager.getSiteKey(targetUrl);
        if (activity != null) {
            this.secondaryMarginMode = SiteSettingsDialogHelper.getSiteDefaultMarginMode(activity, secondarySiteKey);
            this.secondaryMarginDp = SiteSettingsDialogHelper.getSiteDefaultRightMarginDp(activity, secondarySiteKey);
            if (this.secondaryMarginDp <= 0) {
                this.secondaryMarginDp = SiteSettingsDialogHelper.DEFAULT_GUTTER_DP;
            }
        }
        if (uaManager != null && activity != null) {
            uaManager.applySiteDefault(activity, secondarySiteKey);
            uaManager.configureWebSettings(secondaryWebView.getSettings(), targetUrl);
        }
        String finalUrl = (uaManager != null) ? uaManager.adjustYouTubeTargetUrl(targetUrl) : targetUrl;
        secondaryWebView.loadUrl(finalUrl);
        applyLayoutUpdate();
        updateControlBarButtons();
    }

    public void closeSplit() {
        if (!isSplitActive) return;
        isSplitActive = false;

        if (secondaryCustomView != null) {
            if (secondaryContainer != null) {
                secondaryContainer.removeView(secondaryCustomView);
            }
            secondaryCustomView = null;
        }
        if (secondaryCustomViewCallback != null) {
            secondaryCustomViewCallback.onCustomViewHidden();
            secondaryCustomViewCallback = null;
        }

        if (secondaryWebView != null) {
            secondaryWebView.stopLoading();
            secondaryWebView.onPause();
            secondaryWebView.setRotation(0.0f);
            secondaryWebView.loadUrl("about:blank");
            if (secondaryContainer != null) {
                secondaryContainer.removeView(secondaryWebView);
            }
            secondaryWebView.destroy();
            secondaryWebView = null;
        }

        if (secondaryProgressBar != null && secondaryContainer != null) {
            secondaryContainer.removeView(secondaryProgressBar);
            secondaryProgressBar = null;
        }

        if (secondaryContainer != null && rootLayout != null) {
            rootLayout.removeView(secondaryContainer);
            secondaryContainer = null;
        }

        if (dividerView != null && rootLayout != null) {
            rootLayout.removeView(dividerView);
            dividerView = null;
        }

        if (dragOverlayView != null && rootLayout != null) {
            rootLayout.removeView(dragOverlayView);
            dragOverlayView = null;
            leftDragHolder = null;
            rightDragHolder = null;
        }

        isSecondaryFlipped = false;
        splitRatio = 0.50f;
        isPrimaryOnLeft = true;
        if (splitControlBarWrapper != null && rootLayout != null) {
            rootLayout.removeView(splitControlBarWrapper);
            splitControlBarWrapper = null;
            splitControlBar = null;
            leftAudioBtn = null;
            leftMarginBtn = null;
            swapBtn = null;
            presetBtn = null;
            playPauseBtn = null;
            rightMarginBtn = null;
            rightAudioBtn = null;
            changeAppBtn = null;
            secBackBtn = null;
            secReloadBtn = null;
            flipBtn = null;
            exitBtn = null;
        }

        // Restore primary webview to full screen and unmuted audio
        if (primaryWebView != null && rootLayout != null) {
            applyAudioMuteState(primaryWebView, false);
            isPrimaryMuted = false;
            activeAudio = AudioFocusPane.PRIMARY;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) primaryWebView.getLayoutParams();
            if (lp != null) {
                lp.width = FrameLayout.LayoutParams.MATCH_PARENT;
                lp.height = FrameLayout.LayoutParams.MATCH_PARENT;
                lp.leftMargin = 0;
                primaryWebView.setLayoutParams(lp);
            }
        }

        if (activity != null) {
            activity.getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        }

        if (callback != null) {
            callback.onSplitClosed();
        }
    }

    public void updateLayout(int rootWidth, int rootHeight) {
        if (!isSplitActive || rootWidth <= 0 || rootHeight <= 0) return;
        applyLayoutInternal(rootWidth, rootHeight, primaryMarginMode, primaryMarginDp);
    }

    public void updateLayout(int rootWidth, int rootHeight, int orientation, int currentMarginState, int currentMarginDp) {
        if (!isSplitActive || rootWidth <= 0 || rootHeight <= 0) return;
        this.primaryMarginMode = currentMarginState;
        this.primaryMarginDp = currentMarginDp;
        applyLayoutInternal(rootWidth, rootHeight, currentMarginState, currentMarginDp);
    }

    private void applyLayoutUpdate() {
        if (rootLayout == null) return;
        int w = rootLayout.getWidth();
        int h = rootLayout.getHeight();
        if (w > 0 && h > 0) {
            applyLayoutInternal(w, h, primaryMarginMode, primaryMarginDp);
        }
    }

    public void cycleLeftPaneMargin() {
        if (!isSplitActive) return;
        if (isPrimaryOnLeft) {
            primaryMarginMode = (primaryMarginMode + 1) % 3;
            if (callback != null) callback.onPaneMarginChanged(true, primaryMarginMode, primaryMarginDp);
        } else {
            secondaryMarginMode = (secondaryMarginMode + 1) % 3;
            if (callback != null) callback.onPaneMarginChanged(false, secondaryMarginMode, secondaryMarginDp);
        }
        if (activity != null) {
            activity.getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
        applyLayoutUpdate();
        updateControlBarButtons();
    }

    public void cycleRightPaneMargin() {
        if (!isSplitActive) return;
        if (isPrimaryOnLeft) {
            secondaryMarginMode = (secondaryMarginMode + 1) % 3;
            if (callback != null) callback.onPaneMarginChanged(false, secondaryMarginMode, secondaryMarginDp);
        } else {
            primaryMarginMode = (primaryMarginMode + 1) % 3;
            if (callback != null) callback.onPaneMarginChanged(true, primaryMarginMode, primaryMarginDp);
        }
        if (activity != null) {
            activity.getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
        applyLayoutUpdate();
        updateControlBarButtons();
    }

    public void cycleActivePaneMargin() {
        if (!isSplitActive) return;
        boolean targetPrimary = (activeAudio == AudioFocusPane.PRIMARY);
        if (activeAudio == AudioFocusPane.NONE) {
            targetPrimary = isPrimaryOnLeft;
        }
        if (targetPrimary) {
            primaryMarginMode = (primaryMarginMode + 1) % 3;
            if (callback != null) callback.onPaneMarginChanged(true, primaryMarginMode, primaryMarginDp);
        } else {
            secondaryMarginMode = (secondaryMarginMode + 1) % 3;
            if (callback != null) callback.onPaneMarginChanged(false, secondaryMarginMode, secondaryMarginDp);
        }
        if (activity != null) {
            activity.getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
        applyLayoutUpdate();
        updateControlBarButtons();
    }

    public void savePaneMarginDefault(boolean isLeft) {
        if (activity == null || !isSplitActive) return;
        boolean isPrimary = isLeft ? isPrimaryOnLeft : !isPrimaryOnLeft;
        String url = isPrimary ? (primaryWebView != null ? primaryWebView.getUrl() : null) : secondarySiteUrl;
        String siteKey = UserAgentManager.getSiteKey(url);
        if (siteKey.isEmpty()) return;

        int mode = isPrimary ? primaryMarginMode : secondaryMarginMode;
        int dp = isPrimary ? primaryMarginDp : secondaryMarginDp;
        if (dp <= 0) dp = SiteSettingsDialogHelper.DEFAULT_GUTTER_DP;
        int savedDp = (mode == SiteSettingsDialogHelper.MARGIN_MODE_NONE) ? 0 : dp;

        SiteSettingsDialogHelper.saveSiteDefaultMarginMode(activity, siteKey, mode);
        SiteSettingsDialogHelper.saveSiteDefaultRightMarginDp(activity, siteKey, savedDp);

        boolean isDesktop = (uaManager != null) ? uaManager.getSiteDefaultDesktopMode(activity, siteKey) : true;
        float menuDensity = activity.getResources().getDisplayMetrics().density * MenuOverlayView.MENU_SCALE;
        SiteSettingsDialogHelper.showSaveAckDialog(activity, siteKey, isDesktop, mode, savedDp, menuDensity);

        if (activity != null) {
            activity.getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        }
    }

    public void saveActivePaneMarginDefault() {
        boolean isLeft = (activeAudio == AudioFocusPane.PRIMARY) ? isPrimaryOnLeft : !isPrimaryOnLeft;
        savePaneMarginDefault(isLeft);
    }

    private void applyLayoutInternal(int rootWidth, int rootHeight, int currentMarginState, int currentMarginDp) {
        if (!isSplitActive || rootLayout == null || primaryWebView == null || secondaryContainer == null) return;

        float density = (activity != null) ? activity.getResources().getDisplayMetrics().density : 1.0f;
        float menuDensity = density * MenuOverlayView.MENU_SCALE;
        int dividerWidth = (int) (12 * menuDensity);

        int totalWidth = rootWidth;
        int totalHeight = rootHeight;

        int primaryWidth = (int) ((totalWidth - dividerWidth) * splitRatio);
        int secondaryWidth = totalWidth - dividerWidth - primaryWidth;

        int leftPaneWidth = isPrimaryOnLeft ? primaryWidth : secondaryWidth;
        int rightPaneWidth = isPrimaryOnLeft ? secondaryWidth : primaryWidth;

        int dividerLeft = leftPaneWidth;

        int leftMarginMode = isPrimaryOnLeft ? primaryMarginMode : secondaryMarginMode;
        int leftMarginDp = isPrimaryOnLeft ? primaryMarginDp : secondaryMarginDp;
        if (leftMarginDp <= 0) leftMarginDp = SiteSettingsDialogHelper.DEFAULT_GUTTER_DP;
        int leftPx = (leftMarginMode != SiteSettingsDialogHelper.MARGIN_MODE_NONE) ? (int) (leftMarginDp * density) : 0;

        int rightMarginMode = isPrimaryOnLeft ? secondaryMarginMode : primaryMarginMode;
        int rightMarginDp = isPrimaryOnLeft ? secondaryMarginDp : primaryMarginDp;
        if (rightMarginDp <= 0) rightMarginDp = SiteSettingsDialogHelper.DEFAULT_GUTTER_DP;
        int rightPx = (rightMarginMode != SiteSettingsDialogHelper.MARGIN_MODE_NONE) ? (int) (rightMarginDp * density) : 0;

        // Apply to Left Pane
        View leftView = isPrimaryOnLeft ? primaryWebView : secondaryContainer;
        FrameLayout.LayoutParams leftLp = (FrameLayout.LayoutParams) leftView.getLayoutParams();
        if (leftLp == null) {
            leftLp = new FrameLayout.LayoutParams(leftPaneWidth, totalHeight);
        }
        if (leftMarginMode == SiteSettingsDialogHelper.MARGIN_MODE_ALL) {
            leftLp.leftMargin = leftPx;
            leftLp.rightMargin = leftPx;
            leftLp.topMargin = leftPx;
            leftLp.bottomMargin = leftPx;
            leftLp.width = Math.max(100, leftPaneWidth - (leftPx * 2));
            leftLp.height = Math.max(100, totalHeight - (leftPx * 2));
        } else if (leftMarginMode == SiteSettingsDialogHelper.MARGIN_MODE_RIGHT) {
            // Outer edge inset for Left Pane
            leftLp.leftMargin = leftPx;
            leftLp.rightMargin = 0;
            leftLp.topMargin = 0;
            leftLp.bottomMargin = 0;
            leftLp.width = Math.max(100, leftPaneWidth - leftPx);
            leftLp.height = totalHeight;
        } else {
            leftLp.leftMargin = 0;
            leftLp.rightMargin = 0;
            leftLp.topMargin = 0;
            leftLp.bottomMargin = 0;
            leftLp.width = leftPaneWidth;
            leftLp.height = totalHeight;
        }
        leftView.setLayoutParams(leftLp);

        // Apply to Right Pane
        View rightView = isPrimaryOnLeft ? secondaryContainer : primaryWebView;
        FrameLayout.LayoutParams rightLp = (FrameLayout.LayoutParams) rightView.getLayoutParams();
        if (rightLp == null) {
            rightLp = new FrameLayout.LayoutParams(rightPaneWidth, totalHeight);
        }
        if (rightMarginMode == SiteSettingsDialogHelper.MARGIN_MODE_ALL) {
            rightLp.leftMargin = dividerLeft + dividerWidth + rightPx;
            rightLp.rightMargin = rightPx;
            rightLp.topMargin = rightPx;
            rightLp.bottomMargin = rightPx;
            rightLp.width = Math.max(100, rightPaneWidth - (rightPx * 2));
            rightLp.height = Math.max(100, totalHeight - (rightPx * 2));
        } else if (rightMarginMode == SiteSettingsDialogHelper.MARGIN_MODE_RIGHT) {
            // Outer edge inset for Right Pane
            rightLp.leftMargin = dividerLeft + dividerWidth;
            rightLp.rightMargin = rightPx;
            rightLp.topMargin = 0;
            rightLp.bottomMargin = 0;
            rightLp.width = Math.max(100, rightPaneWidth - rightPx);
            rightLp.height = totalHeight;
        } else {
            rightLp.leftMargin = dividerLeft + dividerWidth;
            rightLp.rightMargin = 0;
            rightLp.topMargin = 0;
            rightLp.bottomMargin = 0;
            rightLp.width = rightPaneWidth;
            rightLp.height = totalHeight;
        }
        rightView.setLayoutParams(rightLp);

        // Apply to Primary ProgressBar
        if (primaryProgressBar != null) {
            FrameLayout.LayoutParams primLp = (FrameLayout.LayoutParams) primaryWebView.getLayoutParams();
            if (primLp != null) {
                FrameLayout.LayoutParams pbLp = (FrameLayout.LayoutParams) primaryProgressBar.getLayoutParams();
                if (pbLp != null) {
                    pbLp.width = primLp.width;
                    pbLp.leftMargin = primLp.leftMargin;
                    pbLp.topMargin = primLp.topMargin;
                    primaryProgressBar.setLayoutParams(pbLp);
                }
            }
        }

        // Apply to Divider
        if (dividerView != null) {
            FrameLayout.LayoutParams divLp = new FrameLayout.LayoutParams(dividerWidth, totalHeight);
            divLp.leftMargin = dividerLeft;
            divLp.topMargin = 0;
            dividerView.setLayoutParams(divLp);
        }

        // Apply to Integrated Split Control Bar (centered horizontally at top)
        if (splitControlBarWrapper != null) {
            FrameLayout.LayoutParams scbLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            scbLp.topMargin = (int) (14 * menuDensity);
            splitControlBarWrapper.setLayoutParams(scbLp);
        }
    }

    public void destroy(FrameLayout root) {
        closeSplit();
        activity = null;
        rootLayout = null;
        primaryWebView = null;
        primaryProgressBar = null;
        uaManager = null;
    }
}
