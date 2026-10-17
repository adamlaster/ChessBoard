package com.lastersoft.godotwebview;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

/**
 * Manages display zoom stepping (75% to 200%) and 4-way 90° tabletop orientation cycling.
 */
public class DisplayController {

    public static final int[] ZOOM_LEVELS = {75, 100, 125, 150, 200};
    public static final int DEFAULT_ZOOM_INDEX = 1; // 100%

    public static final int[] ORIENTATION_CYCLE = {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
    };

    private int currentZoomIndex = DEFAULT_ZOOM_INDEX;
    private int currentOrientationIndex = -1;

    public int getCurrentZoomPercent() {
        return ZOOM_LEVELS[currentZoomIndex];
    }

    public int getOrientationDegrees() {
        if (currentOrientationIndex <= 0) return 0;
        return currentOrientationIndex * 90;
    }

    public void resetZoom() {
        currentZoomIndex = DEFAULT_ZOOM_INDEX;
    }

    public boolean zoomIn(WebView mainWebView, WebView popupWebView) {
        if (currentZoomIndex < ZOOM_LEVELS.length - 1) {
            currentZoomIndex++;
            applyZoom(mainWebView, popupWebView);
            return true;
        }
        return false;
    }

    public boolean zoomOut(WebView mainWebView, WebView popupWebView) {
        if (currentZoomIndex > 0) {
            currentZoomIndex--;
            applyZoom(mainWebView, popupWebView);
            return true;
        }
        return false;
    }

    public void applyZoom(WebView mainWebView, WebView popupWebView) {
        int zoomPercent = ZOOM_LEVELS[currentZoomIndex];
        applyZoomToWebView(mainWebView, zoomPercent);
        applyZoomToWebView(popupWebView, zoomPercent);
    }

    private void applyZoomToWebView(WebView webView, int zoomPercent) {
        if (webView == null) return;
        webView.getSettings().setTextZoom(zoomPercent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && zoomPercent != 100) {
            String js = "document.documentElement.style.zoom = '" + zoomPercent + "%';";
            webView.evaluateJavascript(js, null);
        }
    }

    public void cycleOrientation(Activity activity, Runnable postRotateRefresh) {
        if (activity == null) return;
        if (currentOrientationIndex == -1) {
            int currentOrientation = activity.getResources().getConfiguration().orientation;
            currentOrientationIndex = (currentOrientation == Configuration.ORIENTATION_PORTRAIT) ? 1 : 0;
        }
        currentOrientationIndex = (currentOrientationIndex + 1) % ORIENTATION_CYCLE.length;
        activity.setRequestedOrientation(ORIENTATION_CYCLE[currentOrientationIndex]);

        if (postRotateRefresh != null) {
            postRotateRefresh.run();
        }
    }

    public void resetOrientationToLandscape(Activity activity, Runnable postRotateRefresh) {
        if (activity == null) return;
        currentOrientationIndex = 0;
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        if (postRotateRefresh != null) {
            postRotateRefresh.run();
        }
    }

    public void schedulePostRotateRefresh(WebView webView, View floatingButton, ViewGroup layout, int buttonSize, int margin) {
        if (webView == null) return;
        webView.postDelayed(() -> {
            webView.requestLayout();
            webView.invalidate();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                webView.evaluateJavascript("window.dispatchEvent(new Event('resize'));", null);
            }
            if (floatingButton != null && layout != null) {
                float targetX = layout.getWidth() - buttonSize - margin;
                float targetY = layout.getHeight() - buttonSize - margin;
                if (targetX > 0 && targetY > 0) {
                    floatingButton.setX(targetX);
                    floatingButton.setY(targetY);
                }
            }
        }, 350);
    }
}

