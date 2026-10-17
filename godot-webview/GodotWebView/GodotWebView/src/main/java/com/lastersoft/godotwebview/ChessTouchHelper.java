package com.lastersoft.godotwebview;

import android.os.Build;
import android.util.Log;
import android.webkit.WebView;

/**
 * Enhances touch navigation ergonomics on Chess.com when operating in Desktop User-Agent mode.
 * 
 * On desktop browsers, Chess.com's left navigation sidebar uses mouseenter hover events to expand
 * flyout submenus (e.g. Play -> Play Online, Computer, Tournaments). On touchscreens, tapping a
 * primary menu item normally triggers an immediate synthesized click on the underlying <a> tag,
 * which hotlinks directly to the category landing page without giving tabletop players a chance
 * to view or select from the submenu.
 * 
 * This helper injects a lightweight client-side bridge that:
 * 1. Intercepts the first tap on a desktop sidebar category, preventing the immediate hotlink.
 * 2. Programmatically opens the flyout submenu via mouseenter (buttons: 0) and focus management.
 * 3. Keeps the flyout submenu stable until a sub-item is selected, the same category is tapped again,
 *    or the player taps outside the menu to dismiss it.
 */
public final class ChessTouchHelper {

    private static final String TAG = "GodotWebView.Touch";

    private static final String CHESS_TOUCH_FIX_JS =
        "(function() {" +
        "    if (window.__chess4board_touch_init) return;" +
        "    window.__chess4board_touch_init = true;" +
        "    var SUBMENU_KEYS = new Set(['play', 'puzzles', 'learn', 'watch', 'community', 'other', 'train', 'more', 'news', 'social']);" +
        "    var currentOpenKey = null;" +
        "    var allowUnmount = false;" +
        "    function openSubmenu(wrapper, key, link) {" +
        "        if (link) {" +
        "            if (link.tabIndex < 0) link.tabIndex = 0;" +
        "            try { link.focus({ preventScroll: true }); } catch (e) { try { link.focus(); } catch (e2) {} }" +
        "        }" +
        "        try {" +
        "            wrapper.dispatchEvent(new MouseEvent('mouseenter', { bubbles: true, cancelable: true, view: window, buttons: 0 }));" +
        "        } catch (e) {}" +
        "        try {" +
        "            wrapper.dispatchEvent(new FocusEvent('focusin', { bubbles: true, cancelable: true, view: window }));" +
        "        } catch (e) {}" +
        "        currentOpenKey = key;" +
        "    }" +
        "    function closeSubmenu(wrapper) {" +
        "        if (!wrapper) return;" +
        "        allowUnmount = true;" +
        "        try {" +
        "            if (document.activeElement && wrapper.contains(document.activeElement)) {" +
        "                try { document.activeElement.blur(); } catch (e) {}" +
        "            }" +
        "            try {" +
        "                wrapper.dispatchEvent(new MouseEvent('mouseleave', { bubbles: false, cancelable: true }));" +
        "            } catch (e) {}" +
        "            try {" +
        "                wrapper.dispatchEvent(new FocusEvent('focusout', { bubbles: true, cancelable: true }));" +
        "            } catch (e) {}" +
        "        } finally {" +
        "            allowUnmount = false;" +
        "        }" +
        "    }" +
        "    document.addEventListener('mouseleave', function(e) {" +
        "        if (!allowUnmount && currentOpenKey) {" +
        "            var target = e.target;" +
        "            if (target && typeof target.closest === 'function' && target.closest('#sidebar-main-menu')) {" +
        "                e.stopImmediatePropagation();" +
        "            }" +
        "        }" +
        "    }, true);" +
        "    document.addEventListener('focusout', function(e) {" +
        "        if (!allowUnmount && currentOpenKey) {" +
        "            var target = e.target;" +
        "            if (target && typeof target.closest === 'function' && target.closest('#sidebar-main-menu')) {" +
        "                e.stopImmediatePropagation();" +
        "            }" +
        "        }" +
        "    }, true);" +
        "    document.addEventListener('click', function(e) {" +
        "        var target = e.target;" +
        "        if (!target) return;" +
        "        var mountPoint = (typeof target.closest === 'function') ? target.closest('[data-interaction-mount-point]') : null;" +
        "        if (mountPoint) {" +
        "            var subLink = (typeof target.closest === 'function') ? target.closest('a, button') : null;" +
        "            if (subLink) currentOpenKey = null;" +
        "            return;" +
        "        }" +
        "        var wrapper = (typeof target.closest === 'function') ? target.closest('#sidebar-main-menu .sidebar-link-desktop-wrapper, #sidebar-main-menu [data-interaction]:not([data-interaction^=\"mobile\"])') : null;" +
        "        if (!wrapper) {" +
        "            if (currentOpenKey) {" +
        "                var openWrapper = document.querySelector('[data-interaction=\"' + currentOpenKey + '\"]');" +
        "                if (openWrapper && !openWrapper.contains(target)) {" +
        "                    closeSubmenu(openWrapper);" +
        "                    currentOpenKey = null;" +
        "                }" +
        "            }" +
        "            return;" +
        "        }" +
        "        var key = wrapper.getAttribute('data-interaction');" +
        "        if (!key || !SUBMENU_KEYS.has(key)) {" +
        "            if (currentOpenKey) {" +
        "                var prev = document.querySelector('[data-interaction=\"' + currentOpenKey + '\"]');" +
        "                if (prev && prev !== wrapper) {" +
        "                    closeSubmenu(prev);" +
        "                    currentOpenKey = null;" +
        "                }" +
        "            }" +
        "            return;" +
        "        }" +
        "        e.preventDefault();" +
        "        e.stopPropagation();" +
        "        if (typeof e.stopImmediatePropagation === 'function') e.stopImmediatePropagation();" +
        "        if (currentOpenKey === key) {" +
        "            closeSubmenu(wrapper);" +
        "            currentOpenKey = null;" +
        "        } else {" +
        "            if (currentOpenKey) {" +
        "                var prevOpen = document.querySelector('[data-interaction=\"' + currentOpenKey + '\"]');" +
        "                if (prevOpen) closeSubmenu(prevOpen);" +
        "            }" +
        "            var link = wrapper.querySelector('a.sidebar-link') || wrapper.querySelector('a');" +
        "            openSubmenu(wrapper, key, link);" +
        "        }" +
        "    }, true);" +
        "    document.addEventListener('touchstart', function(e) {" +
        "        var target = e.target;" +
        "        if (!target || !currentOpenKey) return;" +
        "        var mountPoint = (typeof target.closest === 'function') ? target.closest('[data-interaction-mount-point]') : null;" +
        "        var inSidebar = (typeof target.closest === 'function') ? target.closest('#sidebar-main-menu') : null;" +
        "        if (!mountPoint && !inSidebar) {" +
        "            var openWrapper = document.querySelector('[data-interaction=\"' + currentOpenKey + '\"]');" +
        "            if (openWrapper) closeSubmenu(openWrapper);" +
        "            currentOpenKey = null;" +
        "        }" +
        "    }, { passive: true });" +
        "    window.addEventListener('popstate', function() { currentOpenKey = null; allowUnmount = false; });" +
        "})();";

    public static void injectSidebarTouchFix(WebView webView, String url) {
        if (webView == null) return;
        String targetUrl = (url != null) ? url : webView.getUrl();
        if (targetUrl == null || !targetUrl.contains("chess.com")) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(CHESS_TOUCH_FIX_JS, null);
        } else {
            webView.loadUrl("javascript:" + CHESS_TOUCH_FIX_JS);
        }
        Log.i(TAG, "Injected Chess.com desktop sidebar touch fix into " + targetUrl);
    }

    private ChessTouchHelper() {}
}

