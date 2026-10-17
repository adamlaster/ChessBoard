package com.lastersoft.godotwebview;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Handles reflection-based communication with the native Board SDK (co.harrishill.board.*).
 * This ensures Board system touches are not swallowed, hooks the Board system pause menu,
 * registers the "Chess 4 Board Menu" custom action, and handles clean application termination.
 */
public final class BoardBridge {

    private static final String TAG = "GodotWebView.Board";
    private static boolean boardPauseListenerHooked = false;
    private static int boardContextRetryCount = 0;

    public static void prepareBoardTouchPassthrough(Activity activity, Runnable onExit, Runnable onShowMenu, Handler handler) {
        try {
            Class<?> boardNativeClass = Class.forName("co.harrishill.board.core.BoardNativePlugin");
            Method setSwallow = boardNativeClass.getMethod("setSwallowSystemTouches", boolean.class);
            setSwallow.invoke(null, false);

            Field nativeLibLoadedField = boardNativeClass.getField("nativeLibLoaded");
            nativeLibLoadedField.setBoolean(null, false);

            Log.i(TAG, "Configured BoardNativePlugin for standard Android touch passthrough");
        } catch (Throwable t) {
            Log.w(TAG, "BoardNativePlugin touch passthrough setup skipped: " + t.getMessage());
        }

        hookBoardPauseListener(activity, onExit, onShowMenu);
        ensureBoardPauseContextRegistered(handler);

        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (activity.getCurrentFocus() != null) {
                    activity.getCurrentFocus().setOnTouchListener(null);
                }
            });
        }
    }

    public static void hookBoardPauseListener(Activity activity, Runnable onExit, Runnable onShowMenu) {
        if (boardPauseListenerHooked) return;
        try {
            Class<?> callbackClass = Class.forName("co.harrishill.board.pausescreen.PauseResultCallbackImpl");
            Class<?> listenerInterface = Class.forName("co.harrishill.board.pausescreen.PauseResultCallbackImpl$PauseResultListener");
            Field listenerField = callbackClass.getDeclaredField("listener");
            listenerField.setAccessible(true);
            final Object originalListener = listenerField.get(null);
            final Method getActionTypeMethod = callbackClass.getMethod("getActionType");
            final Method getCustomButtonIdMethod = callbackClass.getMethod("getCustomButtonId");

            Object proxyListener = Proxy.newProxyInstance(
                    listenerInterface.getClassLoader(),
                    new Class<?>[]{listenerInterface},
                    (proxy, method, args) -> {
                        if ("onPauseResult".equals(method.getName())) {
                            try {
                                String actionType = (String) getActionTypeMethod.invoke(null);
                                String customButtonId = (String) getCustomButtonIdMethod.invoke(null);
                                Log.i(TAG, "Board pause menu action received in Java: " + actionType + " (customButtonId=" + customButtonId + ")");
                                if ("EXIT_GAME_UNSAVED".equals(actionType) || "EXIT_GAME_SAVED".equals(actionType)) {
                                    if (activity != null && onExit != null) {
                                        activity.runOnUiThread(onExit);
                                    }
                                } else if ("CUSTOM_ACTION".equals(actionType) && "open_menu".equals(customButtonId)) {
                                    if (activity != null && onShowMenu != null) {
                                        activity.runOnUiThread(onShowMenu);
                                    }
                                }
                            } catch (Throwable inner) {
                                Log.e(TAG, "Error handling Board pause action in Java", inner);
                            }
                            if (originalListener != null) {
                                return method.invoke(originalListener, args);
                            }
                            return null;
                        }
                        if (originalListener != null) {
                            return method.invoke(originalListener, args);
                        }
                        return null;
                    }
            );

            Method setListenerMethod = callbackClass.getMethod("setListener", listenerInterface);
            setListenerMethod.invoke(null, proxyListener);
            boardPauseListenerHooked = true;
            Log.i(TAG, "Hooked Board PauseResultCallbackImpl listener in Java");
        } catch (Throwable t) {
            Log.w(TAG, "Could not hook PauseResultCallbackImpl listener: " + t.getMessage());
        }
    }

    public static void ensureBoardPauseContextRegistered(Handler handler) {
        if (handler == null) return;
        boardContextRetryCount = 0;
        Runnable checkRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    Class<?> boardNativeClass = Class.forName("co.harrishill.board.core.BoardNativePlugin");
                    Method areServicesReadyMethod = boardNativeClass.getMethod("areServicesReady");
                    boolean ready = (boolean) areServicesReadyMethod.invoke(null);
                    if (ready) {
                        Field callbackField = boardNativeClass.getDeclaredField("pauseResultCallback");
                        callbackField.setAccessible(true);
                        if (callbackField.get(null) == null) {
                            Method setPauseContextMethod = boardNativeClass.getMethod(
                                    "setPauseContext",
                                    String.class, String.class, boolean.class,
                                    String[].class, String[].class, String[].class,
                                    String[].class, String[].class, int[].class
                            );
                            setPauseContextMethod.invoke(
                                    null,
                                    "00000000-0000-0000-0000-000000000000",
                                    "Chess 4 Board",
                                    false,
                                    new String[]{"open_menu"},
                                    new String[]{"Chess 4 Board Menu"},
                                    new String[]{"square"},
                                    null,
                                    null,
                                    null
                            );
                            Log.i(TAG, "Registered Board pause context with Chess 4 Board Menu button after SystemOverlayService bound");
                        }
                        return;
                    }
                } catch (Throwable ignored) {}

                boardContextRetryCount++;
                if (boardContextRetryCount < 50) {
                    handler.postDelayed(this, 200);
                }
            }
        };
        handler.postDelayed(checkRunnable, 200);
    }

    public static void terminateBoardAndActivity(Activity activity, Runnable onSignalEmit, Handler handler) {
        if (onSignalEmit != null) {
            try {
                onSignalEmit.run();
            } catch (Throwable ignored) {}
        }

        try {
            Class<?> bridgeClass = Class.forName("co.harrishill.board.session.SessionManagerBridge");
            Method terminateMethod = bridgeClass.getMethod("terminateApplication");
            terminateMethod.invoke(null);
            Log.i(TAG, "Called SessionManagerBridge.terminateApplication()");
        } catch (Throwable t) {
            Log.w(TAG, "SessionManagerBridge.terminateApplication() fallback: " + t.getMessage());
        }

        if (handler != null) {
            handler.postDelayed(() -> {
                if (activity != null && !activity.isFinishing()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        activity.finishAndRemoveTask();
                    } else {
                        activity.finish();
                    }
                }
            }, 300);
        }
    }

    private BoardBridge() {}
}
