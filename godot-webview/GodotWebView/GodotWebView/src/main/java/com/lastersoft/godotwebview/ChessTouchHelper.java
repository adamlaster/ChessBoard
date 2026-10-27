package com.lastersoft.godotwebview;

import android.os.Build;
import android.util.Log;
import android.webkit.WebView;

/**
 * Enhances touch navigation ergonomics and tabletop gestures on Chess.com (and Lichess)
 * when operating in Desktop User-Agent mode.
 * 
 * 1. Desktop Sidebar Navigation:
 *    On desktop browsers, Chess.com's left navigation sidebar uses mouseenter hover events to expand
 *    flyout submenus (e.g. Play -> Play Online, Computer, Tournaments). On touchscreens, tapping a
 *    primary menu item normally triggers an immediate synthesized click on the underlying <a> tag,
 *    which hotlinks directly to the category landing page without giving tabletop players a chance
 *    to view or select from the submenu. This helper intercepts the first tap to open and stabilize
 *    the flyout submenu until a sub-item is chosen or dismissed.
 * 
 * 2. Double-Tap Right-Click Gesture (Arrow Drawing & Square Highlighting):
 *    Chess.com allows desktop players to draw tactical arrows (right-click and drag) and highlight
 *    squares (right-click tap). On large tabletop touchscreen displays, multi-touch two-finger
 *    gestures suffer from driver contact merging and palm skew. This helper translates single-finger
 *    double-tap interactions on the chessboard into synthetic right mouse events (button: 2, buttons: 2,
 *    which: 3):
 *    - Double-tap and drag: Draws live tactical arrows from the start square to the release square.
 *    - Double-tap and release: Toggles square color highlight without dragging.
 *    - Native piece drag (> 10px on tap 1) and single-tap move retain 100% native responsiveness.
 */
public final class ChessTouchHelper {

    private static final String TAG = "GodotWebView.Touch";

    private static final String HARDWARE_CONCURRENCY_CAP_JS =
        "(function() {" +
        "    try {" +
        "        if (window.navigator && (!window.navigator.hardwareConcurrency || window.navigator.hardwareConcurrency > 2)) {" +
        "            Object.defineProperty(navigator, 'hardwareConcurrency', {" +
        "                get: function() { return 2; }," +
        "                configurable: true" +
        "            });" +
        "        }" +
        "    } catch (e) {}" +
        "})();";

    private static final String CHESSKID_RESPONSIVE_FIX_JS =
        "(function() {" +
        "    if (window.__chesskid_responsive_fix_init) return;" +
        "    window.__chesskid_responsive_fix_init = true;" +
        "    var s = document.createElement('style');" +
        "    s.id = '__chesskid_responsive_fix_style';" +
        "    s.textContent = '@media (max-width: 1050px) { ' +" +
        "        '.puzzles-page__pawn-background { display: none !important; } ' +" +
        "        '.chess-board { max-width: calc(100vh - 120px) !important; margin: 0 auto !important; } ' +" +
        "        '.content-sidebar-interactive { overflow-y: auto !important; } ' +" +
        "        ' }';" +
        "    document.head.appendChild(s);" +
        "})();";

    private static final String CHESS_TOUCH_FIX_JS =
        "(function() {" +
        "    if (window.__chessboard_touch_init) return;" +
        "    window.__chessboard_touch_init = true;" +
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

    private static final String CHESS_ARROW_TOUCH_JS =
        "(function() {" +
        "    if (window.__chessboard_arrow_bridge_init) return;" +
        "    function initBridge() {" +
        "        if (window.__chessboard_arrow_bridge_init) return true;" +
        "        var found = document.querySelector('wc-chess-board, chess-board, cg-board, .chess-board, cm-chessboard, .cm-chessboard, #board-layout-chessboard, #board-single, .board, piece, cg-wrap, .board-pieces-wrapper, .game-board-preview');" +
        "        if (!found) return false;" +
        "        window.__chessboard_arrow_bridge_init = true;" +
        "        var DOUBLE_TAP_MAX_GAP = 280;" +
        "        var TAP_MAX_HOLD_TIME = 350;" +
        "        var DRAG_MIN_DISTANCE = 16;" +
        "        var PIECE_DRAG_THRESHOLD = 10;" +
        "        var pendingTap1 = null;" +
        "        var pendingTapTimer = null;" +
        "        var isPieceDragging = false;" +
        "        var pieceDragPointerId = null;" +
        "        var isDoubleTapActive = false;" +
        "        var doubleTapPointerId = null;" +
        "        var isArrowDrag = false;" +
        "        var startSquare = null;" +
        "        var startX = 0, startY = 0;" +
        "        var lastX = 0, lastY = 0;" +
        "        var activeBoard = null;" +
        "        var lastGestureEndTime = 0;" +
        "        var activeModifiers = { shiftKey: false, altKey: false, ctrlKey: false, metaKey: false };" +
        "        function getBoardElement(node) {" +
        "            if (!node) return null;" +
        "            var el = (node.nodeType === 1) ? node : node.parentElement;" +
        "            if (!el || typeof el.closest !== 'function') return null;" +
        "            var specific = el.closest('wc-chess-board, chess-board, cg-board, .chess-board, cm-chessboard, .cm-chessboard, .chessboard, .board-pieces-wrapper, .game-board-preview');" +
        "            if (specific) return specific;" +
        "            var container = el.closest('#board-layout-chessboard, #board-single, .board-layout-chessboard, .chessboard-component, cg-wrap, .main-board, .board-container');" +
        "            if (container) {" +
        "                var childBoard = container.querySelector('wc-chess-board, chess-board, cg-board, .chess-board, cm-chessboard, .cm-chessboard, .chessboard, .board-pieces-wrapper, .game-board-preview');" +
        "                return childBoard || container;" +
        "            }" +
        "            return null;" +
        "        }" +
        "        function getSquareAtPoint(x, y, board) {" +
        "            if (!board) return null;" +
        "            try {" +
        "                var elements = document.elementsFromPoint ? document.elementsFromPoint(x, y) : [document.elementFromPoint(x, y)];" +
        "                for (var i = 0; i < elements.length; i++) {" +
        "                    var el = elements[i];" +
        "                    if (!el || !board.contains(el)) continue;" +
        "                    var ds = el.getAttribute ? (el.getAttribute('data-square') || el.getAttribute('data-sq')) : null;" +
        "                    if (!ds && typeof el.closest === 'function') {" +
        "                        var dsEl = el.closest('[data-square], [data-sq]');" +
        "                        if (dsEl && dsEl.getAttribute) ds = dsEl.getAttribute('data-square') || dsEl.getAttribute('data-sq');" +
        "                    }" +
        "                    if (ds) {" +
        "                        ds = ds.trim().toLowerCase();" +
        "                        if (/^[a-h][1-8]$/.test(ds)) return ds;" +
        "                        var mNum = ds.match(/^([1-8])([1-8])$/);" +
        "                        if (mNum) {" +
        "                            var files = ['', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'];" +
        "                            return files[parseInt(mNum[1], 10)] + mNum[2];" +
        "                        }" +
        "                    }" +
        "                    var cgk = el.getAttribute ? el.getAttribute('cg-key') : null;" +
        "                    if (!cgk && typeof el.closest === 'function') {" +
        "                        var cgkEl = el.closest('[cg-key]');" +
        "                        if (cgkEl && cgkEl.getAttribute) cgk = cgkEl.getAttribute('cg-key');" +
        "                    }" +
        "                    if (cgk && /^[a-h][1-8]$/i.test(cgk.trim())) return cgk.trim().toLowerCase();" +
        "                    var rawCls = el.className;" +
        "                    var cls = (typeof rawCls === 'string') ? rawCls : (rawCls && rawCls.baseVal ? rawCls.baseVal : '');" +
        "                    if (cls) {" +
        "                        var m = cls.match(/square-([1-8])([1-8])/);" +
        "                        if (m) {" +
        "                            var f = parseInt(m[1], 10);" +
        "                            var r = parseInt(m[2], 10);" +
        "                            var files = ['', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'];" +
        "                            if (f >= 1 && f <= 8 && r >= 1 && r <= 8) {" +
        "                                return files[f] + r;" +
        "                            }" +
        "                        }" +
        "                        var mAlg = cls.match(/\\bsquare-([a-h][1-8])\\b/i);" +
        "                        if (mAlg) return mAlg[1].toLowerCase();" +
        "                    }" +
        "                }" +
        "            } catch (e) {}" +
        "            try {" +
        "                var rect = board.getBoundingClientRect();" +
        "                var margin = 25;" +
        "                if (x < (rect.left - margin) || x > (rect.right + margin) || y < (rect.top - margin) || y > (rect.bottom + margin)) return null;" +
        "                var clampedX = Math.max(rect.left, Math.min(rect.right - 1, x));" +
        "                var clampedY = Math.max(rect.top, Math.min(rect.bottom - 1, y));" +
        "                var col = Math.floor((clampedX - rect.left) / (rect.width / 8));" +
        "                var row = Math.floor((clampedY - rect.top) / (rect.height / 8));" +
        "                if (col < 0 || col > 7 || row < 0 || row > 7) return null;" +
        "                var isFlipped = board.classList.contains('flipped') ||" +
        "                                board.getAttribute('orientation') === 'black' ||" +
        "                                (board.closest && (board.closest('.orientation-black') || board.closest('cg-wrap.orientation-black') || board.closest('[orientation=\"black\"]')));" +
        "                if (!isFlipped && board.querySelector) {" +
        "                    var a1 = board.querySelector('[data-square=\"a1\"], .square-11, .square-a1');" +
        "                    if (a1 && a1.getBoundingClientRect) {" +
        "                        var a1r = a1.getBoundingClientRect();" +
        "                        if (a1r.top < (rect.top + rect.height / 2)) {" +
        "                            isFlipped = true;" +
        "                        }" +
        "                    }" +
        "                }" +
        "                var fileIdx = isFlipped ? 7 - col : col;" +
        "                var rankIdx = isFlipped ? row : 7 - row;" +
        "                var letters = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'];" +
        "                return letters[fileIdx] + (rankIdx + 1);" +
        "            } catch (e) {}" +
        "            return null;" +
        "        }" +
        "        function isSameSquareOrClose(x1, y1, sq1, x2, y2, sq2) {" +
        "            if (sq1 && sq2 && sq1 === sq2) return true;" +
        "            var dist = Math.hypot(x2 - x1, y2 - y1);" +
        "            return dist < 55;" +
        "        }" +
        "        function dispatchRightEvent(type, x, y, button, buttons, which, target) {" +
        "            var opts = {" +
        "                bubbles: true," +
        "                cancelable: true," +
        "                composed: true," +
        "                view: window," +
        "                clientX: x," +
        "                clientY: y," +
        "                screenX: x," +
        "                screenY: y," +
        "                pageX: x + (window.scrollX || window.pageXOffset || 0)," +
        "                pageY: y + (window.scrollY || window.pageYOffset || 0)," +
        "                button: button," +
        "                buttons: buttons," +
        "                which: which," +
        "                pointerId: 1," +
        "                pointerType: 'mouse'," +
        "                isPrimary: true," +
        "                shiftKey: activeModifiers.shiftKey," +
        "                altKey: activeModifiers.altKey," +
        "                ctrlKey: activeModifiers.ctrlKey," +
        "                metaKey: activeModifiers.metaKey" +
        "            };" +
        "            var pe;" +
        "            try {" +
        "                pe = new PointerEvent(type, opts);" +
        "            } catch (e) {" +
        "                pe = new MouseEvent(type, opts);" +
        "            }" +
        "            try {" +
        "                if (pe.which !== which) Object.defineProperty(pe, 'which', { value: which, configurable: true });" +
        "                if (pe.button !== button) Object.defineProperty(pe, 'button', { value: button, configurable: true });" +
        "                if (pe.buttons !== buttons) Object.defineProperty(pe, 'buttons', { value: buttons, configurable: true });" +
        "            } catch (e) {}" +
        "            var elUnder = null;" +
        "            try { elUnder = document.elementFromPoint(x, y); } catch (e) {}" +
        "            var sqEl = (elUnder && typeof elUnder.closest === 'function') ? elUnder.closest('[data-square], [cg-key], square, .square') : null;" +
        "            var dest = target || sqEl || elUnder || activeBoard || document;" +
        "            dest.dispatchEvent(pe);" +
        "            var mouseType = null;" +
        "            if (type === 'pointerdown') mouseType = 'mousedown';" +
        "            else if (type === 'pointermove') mouseType = 'mousemove';" +
        "            else if (type === 'pointerup') mouseType = 'mouseup';" +
        "            if (mouseType) {" +
        "                var me = new MouseEvent(mouseType, opts);" +
        "                try {" +
        "                    if (me.which !== which) Object.defineProperty(me, 'which', { value: which, configurable: true });" +
        "                    if (me.button !== button) Object.defineProperty(me, 'button', { value: button, configurable: true });" +
        "                    if (me.buttons !== buttons) Object.defineProperty(me, 'buttons', { value: buttons, configurable: true });" +
        "                } catch (e) {}" +
        "                dest.dispatchEvent(me);" +
        "            }" +
        "        }" +
        "        function dispatchContextMenu(x, y, target) {" +
        "            var opts = {" +
        "                bubbles: true," +
        "                cancelable: true," +
        "                composed: true," +
        "                view: window," +
        "                clientX: x," +
        "                clientY: y," +
        "                screenX: x," +
        "                screenY: y," +
        "                pageX: x + (window.scrollX || window.pageXOffset || 0)," +
        "                pageY: y + (window.scrollY || window.pageYOffset || 0)," +
        "                button: 2," +
        "                buttons: 0," +
        "                which: 3" +
        "            };" +
        "            var ce = new MouseEvent('contextmenu', opts);" +
        "            try {" +
        "                if (ce.which !== 3) Object.defineProperty(ce, 'which', { value: 3, configurable: true });" +
        "            } catch (e) {}" +
        "            var elUnder = null;" +
        "            try { elUnder = document.elementFromPoint(x, y); } catch (e) {}" +
        "            var sqEl = (elUnder && typeof elUnder.closest === 'function') ? elUnder.closest('[data-square], [cg-key], square, .square') : null;" +
        "            var dest = target || sqEl || elUnder || activeBoard || document;" +
        "            dest.dispatchEvent(ce);" +
        "        }" +
        "        function dispatchPointerCancel(target) {" +
        "            var opts = {" +
        "                bubbles: true," +
        "                cancelable: true," +
        "                composed: true," +
        "                view: window," +
        "                clientX: -1000," +
        "                clientY: -1000," +
        "                screenX: -1000," +
        "                screenY: -1000," +
        "                pageX: -1000," +
        "                pageY: -1000," +
        "                button: 2," +
        "                buttons: 0," +
        "                which: 3," +
        "                pointerId: 1," +
        "                pointerType: 'mouse'," +
        "                isPrimary: true" +
        "            };" +
        "            var dest = target || activeBoard || document;" +
        "            try {" +
        "                var peCancel = new PointerEvent('pointercancel', opts);" +
        "                dest.dispatchEvent(peCancel);" +
        "            } catch (e) {}" +
        "            try {" +
        "                var peUp = new PointerEvent('pointerup', opts);" +
        "                dest.dispatchEvent(peUp);" +
        "            } catch (e) {}" +
        "        }" +
        "        function flushSingleTap(tap) {" +
        "            if (!tap) return;" +
        "            var el = document.elementFromPoint(tap.x, tap.y) || tap.target || document;" +
        "            var opts = {" +
        "                bubbles: true," +
        "                cancelable: true," +
        "                composed: true," +
        "                view: window," +
        "                clientX: tap.x," +
        "                clientY: tap.y," +
        "                pageX: tap.x + (window.scrollX || window.pageXOffset || 0)," +
        "                pageY: tap.y + (window.scrollY || window.pageYOffset || 0)," +
        "                button: 0," +
        "                buttons: 1," +
        "                which: 1," +
        "                pointerId: tap.pointerId || 1," +
        "                pointerType: 'touch'," +
        "                isPrimary: true" +
        "            };" +
        "            try { el.dispatchEvent(new PointerEvent('pointerdown', opts)); } catch (e) {}" +
        "            try { el.dispatchEvent(new MouseEvent('mousedown', opts)); } catch (e) {}" +
        "            opts.buttons = 0;" +
        "            try { el.dispatchEvent(new PointerEvent('pointerup', opts)); } catch (e) {}" +
        "            try { el.dispatchEvent(new MouseEvent('mouseup', opts)); } catch (e) {}" +
        "            try { el.dispatchEvent(new MouseEvent('click', opts)); } catch (e) {}" +
        "        }" +
        "        function flushPieceDrag(tap) {" +
        "            if (!tap) return;" +
        "            var el = document.elementFromPoint(tap.x, tap.y) || tap.target || document;" +
        "            var opts = {" +
        "                bubbles: true," +
        "                cancelable: true," +
        "                composed: true," +
        "                view: window," +
        "                clientX: tap.x," +
        "                clientY: tap.y," +
        "                pageX: tap.x + (window.scrollX || window.pageXOffset || 0)," +
        "                pageY: tap.y + (window.scrollY || window.pageYOffset || 0)," +
        "                button: 0," +
        "                buttons: 1," +
        "                which: 1," +
        "                pointerId: tap.pointerId," +
        "                pointerType: 'touch'," +
        "                isPrimary: true" +
        "            };" +
        "            try { el.dispatchEvent(new PointerEvent('pointerdown', opts)); } catch (e) {}" +
        "            try { el.dispatchEvent(new MouseEvent('mousedown', opts)); } catch (e) {}" +
        "        }" +
        "        function clearPendingTapTimer() {" +
        "            if (pendingTapTimer) {" +
        "                clearTimeout(pendingTapTimer);" +
        "                pendingTapTimer = null;" +
        "            }" +
        "        }" +
        "        window.addEventListener('pointerdown', function(e) {" +
        "            if (!e.isTrusted) return;" +
        "            if (Date.now() - lastGestureEndTime < 450) {" +
        "                e.stopPropagation();" +
        "                e.stopImmediatePropagation();" +
        "                e.preventDefault();" +
        "                return;" +
        "            }" +
        "            if (e.pointerType !== 'touch') return;" +
        "            var board = getBoardElement(e.target);" +
        "            if (!board) {" +
        "                if (pendingTap1) {" +
        "                    clearPendingTapTimer();" +
        "                    var t = pendingTap1;" +
        "                    pendingTap1 = null;" +
        "                    flushSingleTap(t);" +
        "                }" +
        "                return;" +
        "            }" +
        "            if (pendingTap1 && pendingTap1.released) {" +
        "                var curSquare = getSquareAtPoint(e.clientX, e.clientY, board);" +
        "                var isSame = isSameSquareOrClose(pendingTap1.x, pendingTap1.y, pendingTap1.square, e.clientX, e.clientY, curSquare);" +
        "                if (isSame) {" +
        "                    clearPendingTapTimer();" +
        "                    var originTap = pendingTap1;" +
        "                    pendingTap1 = null;" +
        "                    e.stopPropagation();" +
        "                    e.stopImmediatePropagation();" +
        "                    e.preventDefault();" +
        "                    isDoubleTapActive = true;" +
        "                    doubleTapPointerId = e.pointerId;" +
        "                    isArrowDrag = false;" +
        "                    startSquare = originTap.square || curSquare;" +
        "                    startX = e.clientX;" +
        "                    startY = e.clientY;" +
        "                    lastX = e.clientX;" +
        "                    lastY = e.clientY;" +
        "                    activeBoard = board;" +
        "                    var elUnder = null;" +
        "                    try { elUnder = document.elementFromPoint(e.clientX, e.clientY); } catch (err) {}" +
        "                    var sqEl = (elUnder && typeof elUnder.closest === 'function') ? elUnder.closest('[data-square], [cg-key], square, .square') : null;" +
        "                    dispatchRightEvent('pointerdown', e.clientX, e.clientY, 2, 2, 3, sqEl || elUnder || board);" +
        "                    dispatchRightEvent('pointermove', e.clientX, e.clientY, 2, 2, 3, sqEl || elUnder || board);" +
        "                    return;" +
        "                } else {" +
        "                    clearPendingTapTimer();" +
        "                    var prevTap = pendingTap1;" +
        "                    pendingTap1 = null;" +
        "                    flushSingleTap(prevTap);" +
        "                }" +
        "            }" +
        "            e.stopPropagation();" +
        "            e.stopImmediatePropagation();" +
        "            e.preventDefault();" +
        "            clearPendingTapTimer();" +
        "            pendingTap1 = {" +
        "                pointerId: e.pointerId," +
        "                x: e.clientX," +
        "                y: e.clientY," +
        "                target: e.target," +
        "                square: getSquareAtPoint(e.clientX, e.clientY, board)," +
        "                board: board," +
        "                downTime: Date.now()," +
        "                released: false" +
        "            };" +
        "            pendingTapTimer = setTimeout(function() {" +
        "                if (pendingTap1 && !pendingTap1.released && pendingTap1.pointerId === e.pointerId) {" +
        "                    var t = pendingTap1;" +
        "                    pendingTap1 = null;" +
        "                    isPieceDragging = true;" +
        "                    pieceDragPointerId = t.pointerId;" +
        "                    flushPieceDrag(t);" +
        "                }" +
        "            }, TAP_MAX_HOLD_TIME);" +
        "        }, true);" +
        "        window.addEventListener('pointermove', function(e) {" +
        "            if (!e.isTrusted) return;" +
        "            if (isDoubleTapActive && e.pointerId === doubleTapPointerId) {" +
        "                e.stopPropagation();" +
        "                e.stopImmediatePropagation();" +
        "                e.preventDefault();" +
        "                lastX = e.clientX;" +
        "                lastY = e.clientY;" +
        "                var dist = Math.hypot(e.clientX - startX, e.clientY - startY);" +
        "                var curSquare = getSquareAtPoint(e.clientX, e.clientY, activeBoard);" +
        "                if (dist >= DRAG_MIN_DISTANCE || (curSquare && startSquare && curSquare !== startSquare)) {" +
        "                    isArrowDrag = true;" +
        "                }" +
        "                var elUnder = null;" +
        "                try { elUnder = document.elementFromPoint(e.clientX, e.clientY); } catch (err) {}" +
        "                var sqEl = (elUnder && typeof elUnder.closest === 'function') ? elUnder.closest('[data-square], [cg-key], square, .square') : null;" +
        "                dispatchRightEvent('pointermove', e.clientX, e.clientY, 2, 2, 3, sqEl || elUnder || activeBoard || document);" +
        "                return;" +
        "            }" +
        "            if (isPieceDragging && e.pointerId === pieceDragPointerId) {" +
        "                return;" +
        "            }" +
        "            if (pendingTap1 && !pendingTap1.released && e.pointerId === pendingTap1.pointerId) {" +
        "                var dist = Math.hypot(e.clientX - pendingTap1.x, e.clientY - pendingTap1.y);" +
        "                if (dist > PIECE_DRAG_THRESHOLD) {" +
        "                    clearPendingTapTimer();" +
        "                    var t = pendingTap1;" +
        "                    pendingTap1 = null;" +
        "                    isPieceDragging = true;" +
        "                    pieceDragPointerId = t.pointerId;" +
        "                    flushPieceDrag(t);" +
        "                    return;" +
        "                }" +
        "                e.stopPropagation();" +
        "                e.stopImmediatePropagation();" +
        "                e.preventDefault();" +
        "                return;" +
        "            }" +
        "            if (Date.now() - lastGestureEndTime < 450) {" +
        "                e.stopPropagation();" +
        "                e.stopImmediatePropagation();" +
        "                e.preventDefault();" +
        "                return;" +
        "            }" +
        "        }, true);" +
        "        window.addEventListener('pointerup', function(e) {" +
        "            if (!e.isTrusted) return;" +
        "            if (isDoubleTapActive && e.pointerId === doubleTapPointerId) {" +
        "                e.stopPropagation();" +
        "                e.stopImmediatePropagation();" +
        "                e.preventDefault();" +
        "                isDoubleTapActive = false;" +
        "                doubleTapPointerId = null;" +
        "                lastGestureEndTime = Date.now();" +
        "                var boardRect = activeBoard ? activeBoard.getBoundingClientRect() : null;" +
        "                var clampedEndX = lastX;" +
        "                var clampedEndY = lastY;" +
        "                if (boardRect) {" +
        "                    clampedEndX = Math.max(boardRect.left + 5, Math.min(boardRect.right - 5, lastX));" +
        "                    clampedEndY = Math.max(boardRect.top + 5, Math.min(boardRect.bottom - 5, lastY));" +
        "                }" +
        "                var endSquare = getSquareAtPoint(clampedEndX, clampedEndY, activeBoard);" +
        "                var elUnder = null;" +
        "                try { elUnder = document.elementFromPoint(clampedEndX, clampedEndY); } catch (err) {}" +
        "                var sqEl = (elUnder && typeof elUnder.closest === 'function') ? elUnder.closest('[data-square], [cg-key], square, .square') : null;" +
        "                var dest = sqEl || elUnder || activeBoard || document;" +
        "                if (isArrowDrag) {" +
        "                    if (endSquare && startSquare && endSquare !== startSquare) {" +
        "                        dispatchRightEvent('pointermove', clampedEndX, clampedEndY, 2, 2, 3, dest);" +
        "                        dispatchRightEvent('pointerup', clampedEndX, clampedEndY, 2, 0, 3, dest);" +
        "                        dispatchContextMenu(clampedEndX, clampedEndY, dest);" +
        "                    } else {" +
        "                        dispatchPointerCancel(activeBoard);" +
        "                    }" +
        "                } else {" +
        "                    dispatchRightEvent('pointermove', startX, startY, 2, 2, 3, dest);" +
        "                    dispatchRightEvent('pointerup', startX, startY, 2, 0, 3, dest);" +
        "                    dispatchContextMenu(startX, startY, dest);" +
        "                }" +
        "                activeBoard = null;" +
        "                startSquare = null;" +
        "                return;" +
        "            }" +
        "            if (isPieceDragging && e.pointerId === pieceDragPointerId) {" +
        "                isPieceDragging = false;" +
        "                pieceDragPointerId = null;" +
        "                return;" +
        "            }" +
        "            if (pendingTap1 && !pendingTap1.released && e.pointerId === pendingTap1.pointerId) {" +
        "                e.stopPropagation();" +
        "                e.stopImmediatePropagation();" +
        "                e.preventDefault();" +
        "                clearPendingTapTimer();" +
        "                var holdDuration = Date.now() - pendingTap1.downTime;" +
        "                if (holdDuration > TAP_MAX_HOLD_TIME) {" +
        "                    var t = pendingTap1;" +
        "                    pendingTap1 = null;" +
        "                    flushSingleTap(t);" +
        "                    return;" +
        "                }" +
        "                pendingTap1.released = true;" +
        "                pendingTap1.upTime = Date.now();" +
        "                pendingTapTimer = setTimeout(function() {" +
        "                    if (pendingTap1 && pendingTap1.released) {" +
        "                        var t = pendingTap1;" +
        "                        pendingTap1 = null;" +
        "                        flushSingleTap(t);" +
        "                    }" +
        "                }, DOUBLE_TAP_MAX_GAP);" +
        "                return;" +
        "            }" +
        "            if (Date.now() - lastGestureEndTime < 450) {" +
        "                e.stopPropagation();" +
        "                e.stopImmediatePropagation();" +
        "                e.preventDefault();" +
        "                return;" +
        "            }" +
        "        }, true);" +
        "        window.addEventListener('pointercancel', function(e) {" +
        "            if (!e.isTrusted) return;" +
        "            if (isDoubleTapActive && e.pointerId === doubleTapPointerId) {" +
        "                isDoubleTapActive = false;" +
        "                doubleTapPointerId = null;" +
        "                lastGestureEndTime = Date.now();" +
        "                dispatchPointerCancel(activeBoard);" +
        "                activeBoard = null;" +
        "                startSquare = null;" +
        "            }" +
        "            if (pendingTap1 && e.pointerId === pendingTap1.pointerId) {" +
        "                clearPendingTapTimer();" +
        "                pendingTap1 = null;" +
        "            }" +
        "            if (isPieceDragging && e.pointerId === pieceDragPointerId) {" +
        "                isPieceDragging = false;" +
        "                pieceDragPointerId = null;" +
        "            }" +
        "        }, true);" +
        "        ['click', 'mousedown', 'mouseup', 'touchstart', 'touchmove', 'touchend', 'touchcancel', 'contextmenu'].forEach(function(evt) {" +
        "            document.addEventListener(evt, function(e) {" +
        "                if (!e.isTrusted) return;" +
        "                if (evt === 'contextmenu') {" +
        "                    if (isDoubleTapActive || (Date.now() - lastGestureEndTime < 450)) {" +
        "                        e.preventDefault();" +
        "                        e.stopPropagation();" +
        "                        if (typeof e.stopImmediatePropagation === 'function') e.stopImmediatePropagation();" +
        "                        return false;" +
        "                    }" +
        "                    var b = getBoardElement(e.target);" +
        "                    if (b) {" +
        "                        e.preventDefault();" +
        "                        e.stopPropagation();" +
        "                        return false;" +
        "                    }" +
        "                    return;" +
        "                }" +
        "                if (isDoubleTapActive || (Date.now() - lastGestureEndTime < 450)) {" +
        "                    var b = getBoardElement(e.target);" +
        "                    if (b || isDoubleTapActive) {" +
        "                        e.preventDefault();" +
        "                        e.stopPropagation();" +
        "                        if (typeof e.stopImmediatePropagation === 'function') e.stopImmediatePropagation();" +
        "                        return false;" +
        "                    }" +
        "                }" +
        "            }, { capture: true, passive: false });" +
        "        });" +
        "        window.addEventListener('keydown', function(e) {" +
        "            if (e.key === 'Shift') activeModifiers.shiftKey = true;" +
        "            if (e.key === 'Alt') activeModifiers.altKey = true;" +
        "            if (e.key === 'Control') activeModifiers.ctrlKey = true;" +
        "            if (e.key === 'Meta') activeModifiers.metaKey = true;" +
        "        }, true);" +
        "        window.addEventListener('keyup', function(e) {" +
        "            if (e.key === 'Shift') activeModifiers.shiftKey = false;" +
        "            if (e.key === 'Alt') activeModifiers.altKey = false;" +
        "            if (e.key === 'Control') activeModifiers.ctrlKey = false;" +
        "            if (e.key === 'Meta') activeModifiers.metaKey = false;" +
        "        }, true);" +
        "        return true;" +
        "    }" +
        "    if (!initBridge()) {" +
        "        try {" +
        "            var obs = new MutationObserver(function() {" +
        "                if (initBridge()) obs.disconnect();" +
        "            });" +
        "            var root = document.body || document.documentElement || document;" +
        "            obs.observe(root, { childList: true, subtree: true });" +
        "            setTimeout(function() { obs.disconnect(); }, 12000);" +
        "        } catch (e) {}" +
        "    }" +
        "})();";

    /**
     * Injects both sidebar desktop navigation and two-finger right-click gesture enhancements.
     */
    public static void injectTouchEnhancements(WebView webView, String url) {
        if (webView == null) return;
        String targetUrl = (url != null) ? url : webView.getUrl();
        if (targetUrl == null) return;

        try {
            android.net.Uri uri = android.net.Uri.parse(targetUrl);
            String host = uri.getHost();
            if (host == null) return;
            host = host.toLowerCase().trim();

            // Strict protection: NEVER inject scripts into Google accounts, Cloudflare challenges, or captcha providers
            if (host.equals("accounts.google.com") || host.endsWith(".google.com") ||
                host.equals("challenges.cloudflare.com") || host.endsWith(".cloudflare.com") ||
                host.contains("turnstile") || host.contains("recaptcha") || host.contains("hcaptcha")) {
                return;
            }

            // Exclude authentication, login, registration, and credential pages
            String path = uri.getPath();
            if (path != null) {
                String lowerPath = path.toLowerCase();
                if (lowerPath.contains("/login") || lowerPath.contains("/signin") ||
                    lowerPath.contains("/signup") || lowerPath.contains("/register") ||
                    lowerPath.contains("/auth") || lowerPath.contains("/oauth") ||
                    lowerPath.contains("/password") || lowerPath.contains("/challenge") ||
                    lowerPath.contains("/verify")) {
                    return;
                }
            }

            boolean isChess = host.contains("chess.com") || host.contains("lichess.org") ||
                              host.contains("chesskid.com") || host.contains("chesskids.com") ||
                              host.contains("chessreps.com");

            if (host.equals("chess.com") || host.endsWith(".chess.com")) {
                evaluate(webView, HARDWARE_CONCURRENCY_CAP_JS);
                evaluate(webView, CHESS_TOUCH_FIX_JS);
                Log.i(TAG, "Injected Chess.com desktop sidebar touch fix into " + targetUrl);
            }

            if (isChess) {
                evaluate(webView, CHESS_ARROW_TOUCH_JS);
                Log.i(TAG, "Injected tactical arrow gesture bridge into " + targetUrl);
            }

            if (host.equals("chesskid.com") || host.endsWith(".chesskid.com") ||
                host.equals("chesskids.com") || host.endsWith(".chesskids.com")) {
                evaluate(webView, CHESSKID_RESPONSIVE_FIX_JS);
                Log.i(TAG, "Injected ChessKid responsive split-screen layout bridge into " + targetUrl);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error checking host/path for touch enhancements: " + e.getMessage());
        }
    }

    /**
     * Injects early safeguards (such as hardware concurrency cap) at onPageStarted
     * before engine scripts and workers initialize.
     */
    public static void injectEarlySafeguards(WebView webView, String targetUrl) {
        if (webView == null || targetUrl == null) return;
        try {
            android.net.Uri uri = android.net.Uri.parse(targetUrl);
            String host = uri.getHost();
            if (host == null) return;
            host = host.toLowerCase();
            if (host.contains("chess.com") || host.contains("lichess.org") ||
                host.contains("chesskid.com") || host.contains("chesskids.com") ||
                host.contains("chessreps.com")) {
                evaluate(webView, HARDWARE_CONCURRENCY_CAP_JS);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Backward-compatible alias for injectTouchEnhancements.
     */
    public static void injectSidebarTouchFix(WebView webView, String url) {
        injectTouchEnhancements(webView, url);
    }

    private static void evaluate(WebView webView, String script) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(script, null);
        } else {
            webView.loadUrl("javascript:" + script);
        }
    }

    private ChessTouchHelper() {}
}
