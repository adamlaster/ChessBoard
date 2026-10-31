package com.lastersoft.godotwebview;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight embedded HTTP daemon for autonomous remote navigation and automated screenshot tours.
 * Runs on port 8990 and accepts HTTP GET requests to control ChessBoard remotely over LAN:
 *
 * Examples:
 *   curl "http://10.0.0.65:8990/status"
 *   curl "http://10.0.0.65:8990/nav?action=launcher"
 *   curl "http://10.0.0.65:8990/nav?action=controls"
 *   curl "http://10.0.0.65:8990/nav?action=help"
 *   curl "http://10.0.0.65:8990/nav?action=theme_picker"
 *   curl "http://10.0.0.65:8990/nav?action=theme&id=michigan"
 *   curl "http://10.0.0.65:8990/nav?action=open&url=https%3A%2F%2Flichess.org%2Fanalysis"
 *   curl "http://10.0.0.65:8990/nav?action=duo&primary=https%3A%2F%2Fwww.chess.com%2Fplay&secondary=https%3A%2F%2Fwww.youtube.com"
 *   curl "http://10.0.0.65:8990/nav?action=split_preset"
 *   curl "http://10.0.0.65:8990/nav?action=split_swap"
 *   curl "http://10.0.0.65:8990/nav?action=split_close"
 *   curl "http://10.0.0.65:8990/nav?action=close"
 */
public final class RemoteNavigationServer {

    private static final String TAG = "GodotWebView.RemoteNav";
    public static final int DEFAULT_PORT = 8990;

    private static ServerSocket serverSocket = null;
    private static Thread serverThread = null;
    private static volatile boolean running = false;
    private static RemoteNavigationDelegate delegate = null;
    private static Handler mainHandler = null;

    public interface RemoteNavigationDelegate {
        void onRemoteOpenUrl(String url);
        void onRemoteOpenDuo(String primaryUrl, String secondaryUrl);
        void onRemoteShowLauncher();
        void onRemoteShowBrowserControls();
        void onRemoteShowHelp();
        void onRemoteHideMenu();
        void onRemoteShowThemePicker();
        void onRemoteSetTheme(String themeId);
        void onRemoteSplitPreset();
        void onRemoteSplitSwap();
        void onRemoteSplitClose();
        void onRemoteRotateScreen(boolean resetToLandscape);
        void onRemoteToggleMargin();
        void onRemoteEvalJs(String script);
        void onRemoteAddDuo(String name, String primaryUrl, String secondaryUrl, String icon, String subtitle);
        void onRemoteRemoveDuo(String name);
        void onRemoteResetDuos();
        String onRemoteGetStatusJson();
    }

    public static boolean isEnabled(android.content.Context context) {
        if (context == null) return false;
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences(UserAgentManager.PREFS_NAME, android.content.Context.MODE_PRIVATE);
            if (prefs.getBoolean("enable_remote_nav", false)) {
                return true;
            }
        } catch (Exception ignored) {}
        try {
            context.getAssets().open("enable_remote_nav").close();
            return true;
        } catch (Exception ignored) {}
        try {
            if (new java.io.File("/data/local/tmp/enable_chess_remote_nav").exists()) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static synchronized void start(android.content.Context context, int port, RemoteNavigationDelegate cmdDelegate, Handler handler) {
        delegate = cmdDelegate;
        mainHandler = handler;
        if (!isEnabled(context)) {
            Log.d(TAG, "RemoteNavigationServer is disabled (zero resource usage on Board).");
            return;
        }
        if (running && serverSocket != null && !serverSocket.isClosed()) {
            return;
        }

        running = true;
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                Log.i(TAG, "Remote navigation HTTP server started on port " + port);
                while (running && !serverSocket.isClosed()) {
                    Socket client = serverSocket.accept();
                    handleClient(client);
                }
            } catch (Exception e) {
                if (running) {
                    Log.w(TAG, "Server socket error: " + e.getMessage());
                }
            } finally {
                stop();
            }
        }, "ChessBoard-RemoteNav");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    public static synchronized void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception ignored) {}
            serverSocket = null;
        }
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread = null;
        }
        Log.i(TAG, "Remote navigation HTTP server stopped");
    }

    private static void handleClient(Socket socket) {
        new Thread(() -> {
            try (socket;
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 OutputStream os = socket.getOutputStream()) {

                String requestLine = reader.readLine();
                if (requestLine == null || requestLine.isEmpty()) return;

                // Read and discard remaining HTTP headers
                String header;
                while ((header = reader.readLine()) != null && !header.isEmpty()) {}

                String[] parts = requestLine.split("\\s+");
                if (parts.length < 2) return;
                String method = parts[0];
                String rawUri = parts[1];

                if (!method.equalsIgnoreCase("GET")) {
                    sendResponse(os, 405, "application/json", "{\"ok\":false,\"error\":\"Method Not Allowed\"}");
                    return;
                }

                String path = rawUri;
                Map<String, String> queryParams = new HashMap<>();
                int qIdx = rawUri.indexOf('?');
                if (qIdx >= 0) {
                    path = rawUri.substring(0, qIdx);
                    String queryString = rawUri.substring(qIdx + 1);
                    String[] pairs = queryString.split("&");
                    for (String pair : pairs) {
                        int eqIdx = pair.indexOf('=');
                        if (eqIdx >= 0) {
                            String k = URLDecoder.decode(pair.substring(0, eqIdx), "UTF-8");
                            String v = URLDecoder.decode(pair.substring(eqIdx + 1), "UTF-8");
                            queryParams.put(k, v);
                        } else if (!pair.isEmpty()) {
                            queryParams.put(URLDecoder.decode(pair, "UTF-8"), "");
                        }
                    }
                }

                if (path.equals("/status") || path.equals("/api/status")) {
                    String statusJson = "{\"ok\":true,\"status\":\"ready\"}";
                    if (delegate != null && mainHandler != null) {
                        final String[] holder = new String[1];
                        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                        mainHandler.post(() -> {
                            try {
                                holder[0] = delegate.onRemoteGetStatusJson();
                            } catch (Exception e) {
                                holder[0] = "{\"ok\":false,\"error\":\"" + e.getMessage() + "\"}";
                            } finally {
                                latch.countDown();
                            }
                        });
                        try {
                            latch.await(2, java.util.concurrent.TimeUnit.SECONDS);
                            if (holder[0] != null) statusJson = holder[0];
                        } catch (Exception ignored) {}
                    }
                    sendResponse(os, 200, "application/json", statusJson);
                    return;
                }

                if (path.equals("/nav") || path.equals("/api/nav")) {
                    String action = queryParams.get("action");
                    if (action == null || action.isEmpty()) {
                        sendResponse(os, 400, "application/json", "{\"ok\":false,\"error\":\"Missing action query parameter\"}");
                        return;
                    }

                    executeAction(action, queryParams);
                    sendResponse(os, 200, "application/json", "{\"ok\":true,\"action\":\"" + action + "\"}");
                    return;
                }

                sendResponse(os, 404, "application/json", "{\"ok\":false,\"error\":\"Not Found\"}");
            } catch (Exception e) {
                Log.w(TAG, "Error handling client: " + e.getMessage());
            }
        }, "RemoteNav-Client").start();
    }

    private static void executeAction(String action, Map<String, String> params) {
        if (delegate == null || mainHandler == null) return;

        mainHandler.post(() -> {
            try {
                switch (action.toLowerCase()) {
                    case "launcher":
                        delegate.onRemoteShowLauncher();
                        break;
                    case "controls":
                        delegate.onRemoteShowBrowserControls();
                        break;
                    case "help":
                        delegate.onRemoteShowHelp();
                        break;
                    case "close":
                        delegate.onRemoteHideMenu();
                        break;
                    case "theme_picker":
                        delegate.onRemoteShowThemePicker();
                        break;
                    case "theme":
                        String themeId = params.get("id");
                        if (themeId != null) delegate.onRemoteSetTheme(themeId);
                        break;
                    case "open":
                        String url = params.get("url");
                        if (url != null) delegate.onRemoteOpenUrl(url);
                        break;
                    case "duo":
                        String primary = params.get("primary");
                        String secondary = params.get("secondary");
                        if (primary != null && secondary != null) {
                            delegate.onRemoteOpenDuo(primary, secondary);
                        }
                        break;
                    case "split_preset":
                        delegate.onRemoteSplitPreset();
                        break;
                    case "split_swap":
                        delegate.onRemoteSplitSwap();
                        break;
                    case "split_close":
                        delegate.onRemoteSplitClose();
                        break;
                    case "rotate":
                        delegate.onRemoteRotateScreen(false);
                        break;
                    case "rotate_reset":
                        delegate.onRemoteRotateScreen(true);
                        break;
                    case "margin":
                        delegate.onRemoteToggleMargin();
                        break;
                    case "eval":
                        String script = params.get("js");
                        if (script != null) delegate.onRemoteEvalJs(script);
                        break;
                    case "add_duo":
                        String dName = params.get("name");
                        String dPrimary = params.get("primary");
                        String dSecondary = params.get("secondary");
                        String dIcon = params.get("icon");
                        String dSub = params.get("subtitle");
                        if (dName != null && dPrimary != null && dSecondary != null) {
                            delegate.onRemoteAddDuo(dName, dPrimary, dSecondary,
                                    dIcon != null ? dIcon : "◫",
                                    dSub != null ? dSub : "Custom Duo");
                        }
                        break;
                    case "remove_duo":
                        String remName = params.get("name");
                        if (remName != null) {
                            delegate.onRemoteRemoveDuo(remName);
                        }
                        break;
                    case "reset_duos":
                        delegate.onRemoteResetDuos();
                        break;
                    default:
                        Log.w(TAG, "Unknown action: " + action);
                }
            } catch (Exception e) {
                Log.e(TAG, "Action execution failed for " + action + ": " + e.getMessage(), e);
            }
        });
    }

    private static void sendResponse(OutputStream os, int statusCode, String contentType, String body) throws Exception {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String statusText = (statusCode == 200) ? "OK" : (statusCode == 404) ? "Not Found" : (statusCode == 400) ? "Bad Request" : "Internal Server Error";
        String header = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                "Content-Type: " + contentType + "; charset=utf-8\r\n" +
                "Content-Length: " + bodyBytes.length + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, OPTIONS\r\n" +
                "Connection: close\r\n\r\n";
        os.write(header.getBytes(StandardCharsets.US_ASCII));
        os.write(bodyBytes);
        os.flush();
    }

    private RemoteNavigationServer() {}
}

