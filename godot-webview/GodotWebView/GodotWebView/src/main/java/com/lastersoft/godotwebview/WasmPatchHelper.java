package com.lastersoft.godotwebview;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Intercepts WebAssembly requests and engine scripts (such as Chess.com's explanation-engine.wasm
 * and Stockfish scripts/binaries) to prevent Android WebView renderer crashes caused by excessive
 * memory declarations (e.g. 2 GB max / 386 MB initial) and unsupported streaming MIME constraints.
 *
 * Downloads wasm binaries directly to disk with zero heap pressure, patches Section 5 linear
 * memory to safe 32 MB initial / 256 MB max, patches JS scripts to cap thread concurrency and
 * fall back to arrayBuffer instantiation, and caches patched assets on disk for instant future loads.
 */
public final class WasmPatchHelper {

    private static final String TAG = "GodotWebView.Wasm";

    public static WebResourceResponse shouldInterceptRequest(Context context, WebResourceRequest request) {
        if (request == null || request.getUrl() == null) return null;
        Uri uri = request.getUrl();
        String path = uri.getPath();
        if (path == null) return null;

        // Block oversized explanation-engine (27.7 MB wasm binary) which exhausts sandboxed renderer cgroup memory
        if (path.contains("explanation-engine")) {
            Log.i(TAG, "Bypassing oversized explanation-engine to prevent renderer OOM crash: " + uri);
            return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, null);
        }

        // Target WebAssembly binaries: Stockfish and chess engines
        if (path.endsWith(".wasm") && (
                path.toLowerCase().contains("stockfish") ||
                path.contains("assets-chess-engine"))) {
            return handleWasmBinary(context, request);
        }

        // Target Stockfish and analysis engine worker scripts
        if (path.endsWith(".js") && (
                path.toLowerCase().contains("stockfish") ||
                path.contains("assets-chess-engine"))) {
            return handleStockfishJs(context, request);
        }

        return null;
    }

    private static void purgeObsoleteCache(File cacheDir) {
        try {
            File[] oldFiles = cacheDir.listFiles((dir, name) ->
                    (name.startsWith("wasm_") && !name.startsWith("wasm_v6_")) ||
                    (name.startsWith("sf_") && !name.startsWith("sf_v4_"))
            );
            if (oldFiles != null) {
                for (File f : oldFiles) {
                    f.delete();
                }
            }
        } catch (Exception ignored) {}
    }

    private static WebResourceResponse handleWasmBinary(Context context, WebResourceRequest request) {
        try {
            String urlStr = request.getUrl().toString();
            File cacheDir = new File(context.getCacheDir(), "wasm_cache");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            purgeObsoleteCache(cacheDir);

            // Generate deterministic cache file name
            String cacheKey = "wasm_v6_" + Integer.toHexString(urlStr.hashCode()) + ".wasm";
            File cachedFile = new File(cacheDir, cacheKey);

            if (cachedFile.exists() && cachedFile.length() > 50000) {
                Log.i(TAG, "Serving Wasm from disk cache: " + urlStr + " -> " + cachedFile.getAbsolutePath());
                return buildWasmResponse(new FileInputStream(cachedFile));
            }

            Log.i(TAG, "Downloading and streaming Wasm directly to disk: " + urlStr);
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(45000);

            // Forward request headers, excluding accept-encoding so HttpURLConnection handles decompression
            boolean hasUa = false;
            Map<String, String> reqHeaders = request.getRequestHeaders();
            if (reqHeaders != null) {
                for (Map.Entry<String, String> entry : reqHeaders.entrySet()) {
                    String key = entry.getKey();
                    if ("user-agent".equalsIgnoreCase(key)) {
                        hasUa = true;
                    }
                    if ("accept-encoding".equalsIgnoreCase(key)) {
                        continue;
                    }
                    conn.setRequestProperty(key, entry.getValue());
                }
            }
            if (!hasUa) {
                conn.setRequestProperty("User-Agent", UserAgentManager.DESKTOP_USER_AGENT);
            }

            // Ensure Cookie is attached
            String cookie = CookieManager.getInstance().getCookie(urlStr);
            if (cookie != null && !cookie.isEmpty()) {
                conn.setRequestProperty("Cookie", cookie);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Failed to download Wasm: HTTP " + responseCode);
                return null;
            }

            // Stream directly to temporary file on disk (peak RAM: 64 KB, avoiding Android OOM)
            File tempFile = new File(cacheDir, "temp_" + System.currentTimeMillis() + "_" + cacheKey);
            try (InputStream in = conn.getInputStream();
                 FileOutputStream fos = new FileOutputStream(tempFile)) {
                byte[] chunk = new byte[65536];
                int bytesRead;
                while ((bytesRead = in.read(chunk, 0, chunk.length)) != -1) {
                    fos.write(chunk, 0, bytesRead);
                }
            }
            conn.disconnect();

            // Patch Section 5 in-place on disk
            boolean patched = patchWasmFile(tempFile);
            if (!tempFile.renameTo(cachedFile)) {
                Log.w(TAG, "Could not rename temp wasm file to " + cachedFile.getName());
            }

            Log.i(TAG, "Successfully cached Wasm (patched=" + patched + ", bytes=" + cachedFile.length() + "): " + urlStr);
            return buildWasmResponse(new FileInputStream(cachedFile));

        } catch (Exception e) {
            Log.e(TAG, "Error intercepting/patching Wasm: " + e.getMessage(), e);
            return null; // Fall back to default WebView loader
        }
    }

    private static WebResourceResponse handleStockfishJs(Context context, WebResourceRequest request) {
        try {
            String urlStr = request.getUrl().toString();
            File cacheDir = new File(context.getCacheDir(), "wasm_cache");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }

            // Deterministic cache file name
            String cacheKey = "sf_v4_" + Integer.toHexString(urlStr.hashCode()) + ".js";
            File cachedFile = new File(cacheDir, cacheKey);

            if (cachedFile.exists() && cachedFile.length() > 500) {
                Log.i(TAG, "Serving patched engine script from disk cache: " + urlStr + " -> " + cachedFile.getAbsolutePath());
                return buildJsResponse(new FileInputStream(cachedFile));
            }

            Log.i(TAG, "Downloading and patching engine script: " + urlStr);
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);

            // Forward request headers, excluding accept-encoding
            boolean hasUa = false;
            Map<String, String> reqHeaders = request.getRequestHeaders();
            if (reqHeaders != null) {
                for (Map.Entry<String, String> entry : reqHeaders.entrySet()) {
                    String key = entry.getKey();
                    if ("user-agent".equalsIgnoreCase(key)) {
                        hasUa = true;
                    }
                    if ("accept-encoding".equalsIgnoreCase(key)) {
                        continue;
                    }
                    conn.setRequestProperty(key, entry.getValue());
                }
            }
            if (!hasUa) {
                conn.setRequestProperty("User-Agent", UserAgentManager.DESKTOP_USER_AGENT);
            }

            // Ensure Cookie is attached
            String cookie = CookieManager.getInstance().getCookie(urlStr);
            if (cookie != null && !cookie.isEmpty()) {
                conn.setRequestProperty("Cookie", cookie);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Failed to download engine script: HTTP " + responseCode);
                return null;
            }

            InputStream in = conn.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[65536];
            int bytesRead;
            while ((bytesRead = in.read(chunk, 0, chunk.length)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }
            in.close();
            conn.disconnect();

            String jsContent = new String(buffer.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);

            // 1. Cap WebAssembly maximum memory allocation in JS to 64 MB (1024 pages)
            boolean memoryPatched = false;
            if (jsContent.contains("maximum")) {
                jsContent = jsContent.replaceAll("maximum:\\s*(32768|16384|8192|4096|65536)", "maximum:1024");
                memoryPatched = true;
            }

            // 2. Cap Stockfish worker thread count to 2 cores
            boolean concurrencyPatched = false;
            if (jsContent.contains("hardwareConcurrency")) {
                jsContent = jsContent.replaceAll("navigator\\.hardwareConcurrency", "(2)");
                concurrencyPatched = true;
            }

            // 3. Fallback from instantiateStreaming to arrayBuffer instantiation to bypass Android WebView MIME type validation
            boolean streamingPatched = false;
            if (jsContent.contains("WebAssembly.instantiateStreaming(e,n)")) {
                jsContent = jsContent.replace("WebAssembly.instantiateStreaming(e,n)",
                        "e.arrayBuffer().then(function(b){return WebAssembly.instantiate(b,n)})");
                streamingPatched = true;
            }
            if (jsContent.contains("WebAssembly.instantiateStreaming(e,o)")) {
                jsContent = jsContent.replace("WebAssembly.instantiateStreaming(e,o)",
                        "e.arrayBuffer().then(function(b){return WebAssembly.instantiate(b,o)})");
                streamingPatched = true;
            }

            byte[] patchedBytes = jsContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            try (FileOutputStream fos = new FileOutputStream(cachedFile)) {
                fos.write(patchedBytes);
            }
            Log.i(TAG, "Successfully patched and cached engine script (memoryPatched=" + memoryPatched
                    + ", concurrencyPatched=" + concurrencyPatched + ", streamingPatched=" + streamingPatched
                    + ", bytes=" + patchedBytes.length + ")");

            return buildJsResponse(new ByteArrayInputStream(patchedBytes));

        } catch (Exception e) {
            Log.e(TAG, "Error intercepting/patching engine script: " + e.getMessage(), e);
            return null; // Fall back to default WebView loader
        }
    }

    private static boolean patchWasmFile(File wasmFile) {
        if (wasmFile == null || !wasmFile.exists() || wasmFile.length() < 8) return false;
        try (RandomAccessFile raf = new RandomAccessFile(wasmFile, "rw")) {
            byte[] magic = new byte[4];
            raf.readFully(magic);
            if (magic[0] != 0 || magic[1] != 'a' || magic[2] != 's' || magic[3] != 'm') return false;

            raf.seek(8);
            long fileLen = wasmFile.length();
            while (raf.getFilePointer() < fileLen) {
                int secId = raf.read();
                if (secId == -1) break;

                int size = 0;
                int shift = 0;
                while (true) {
                    int b = raf.read();
                    if (b == -1) return false;
                    size |= (b & 0x7f) << shift;
                    if ((b & 0x80) == 0) break;
                    shift += 7;
                }

                if (secId == 5) { // Section 5: Memory Section
                    if (size == 7) {
                        long sectionPos = raf.getFilePointer();
                        byte[] secBytes = new byte[7];
                        raf.readFully(secBytes);
                        if (secBytes[0] == 1 && secBytes[1] == 1) {
                            boolean patched = false;
                            int initPages = (secBytes[2] & 0x7f) | ((secBytes[3] & 0x7f) << 7);
                            int maxPages = (secBytes[4] & 0x7f) | ((secBytes[5] & 0x7f) << 7) | ((secBytes[6] & 0x7f) << 14);

                            // Cap initial memory to 512 pages (32 MB) if > 512 pages (e.g. 386 MB in CEE, 128 MB in Stockfish)
                            if (initPages > 512) {
                                secBytes[2] = (byte) 0x80;
                                secBytes[3] = (byte) 0x04;
                                patched = true;
                            }

                            // Cap max memory to 1024 pages (64 MB: 0x80, 0x88, 0x00) if > 1024 pages (e.g. 2 GB in Stockfish/CEE)
                            if (maxPages > 1024) {
                                secBytes[4] = (byte) 0x80;
                                secBytes[5] = (byte) 0x88;
                                secBytes[6] = (byte) 0x00;
                                patched = true;
                            }

                            if (patched) {
                                raf.seek(sectionPos);
                                raf.write(secBytes);
                                return true;
                            }
                        }
                    }
                    break;
                }
                raf.skipBytes(size);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error patching wasm file: " + e.getMessage(), e);
        }
        return false;
    }

    private static WebResourceResponse buildWasmResponse(InputStream data) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Cross-Origin-Resource-Policy", "cross-origin");
        headers.put("Cross-Origin-Embedder-Policy", "require-corp");
        headers.put("Cache-Control", "public, max-age=31536000");

        return new WebResourceResponse("application/wasm", null, 200, "OK", headers, data);
    }

    private static WebResourceResponse buildJsResponse(InputStream data) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Cross-Origin-Resource-Policy", "cross-origin");
        headers.put("Cross-Origin-Embedder-Policy", "require-corp");
        headers.put("Cache-Control", "public, max-age=31536000");

        return new WebResourceResponse("text/javascript", "utf-8", 200, "OK", headers, data);
    }

    private WasmPatchHelper() {}
}
