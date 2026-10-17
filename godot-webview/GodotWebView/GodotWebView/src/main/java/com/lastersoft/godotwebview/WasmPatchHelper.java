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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Intercepts WebAssembly requests (such as Chess.com's explanation-engine.wasm) that declare
 * excessively large initial memory footprints (e.g. 386 MB initial / 2048 MB max), which cause
 * Android WebView's V8 engine to crash with:
 * "RangeError: WebAssembly.instantiate(): Out of memory: Cannot allocate Wasm memory for new instance"
 * and subsequently trigger "Couldn't load the bot engine" on Chess.com.
 *
 * This helper downloads the .wasm binary, rewrites the Section 5 memory definition to use a safe
 * 512 pages (32 MB) initial memory and 8192 pages (512 MB) max memory, and caches the patched
 * binary on disk for instant future loads.
 */
public final class WasmPatchHelper {

    private static final String TAG = "GodotWebView.Wasm";

    public static WebResourceResponse shouldInterceptRequest(Context context, WebResourceRequest request) {
        if (request == null || request.getUrl() == null) return null;
        Uri uri = request.getUrl();
        String path = uri.getPath();
        if (path == null) return null;

        // Target Chess.com's explanation-engine.wasm
        if (path.contains("explanation-engine") && path.endsWith(".wasm")) {
            return handleExplanationEngineWasm(context, request);
        }

        return null;
    }

    private static WebResourceResponse handleExplanationEngineWasm(Context context, WebResourceRequest request) {
        try {
            String urlStr = request.getUrl().toString();
            File cacheDir = new File(context.getCacheDir(), "wasm_cache");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }

            // Generate deterministic cache file name
            String cacheKey = "cee_" + Integer.toHexString(urlStr.hashCode()) + ".wasm";
            File cachedFile = new File(cacheDir, cacheKey);

            if (cachedFile.exists() && cachedFile.length() > 1000000) {
                Log.i(TAG, "Serving patched Wasm from disk cache: " + cachedFile.getAbsolutePath());
                return buildWasmResponse(new FileInputStream(cachedFile));
            }

            Log.i(TAG, "Downloading and patching Wasm: " + urlStr);
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);

            // Forward request headers
            Map<String, String> reqHeaders = request.getRequestHeaders();
            if (reqHeaders != null) {
                for (Map.Entry<String, String> entry : reqHeaders.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
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

            InputStream in = conn.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[65536];
            int bytesRead;
            while ((bytesRead = in.read(chunk, 0, chunk.length)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }
            in.close();
            conn.disconnect();

            byte[] wasmBytes = buffer.toByteArray();
            boolean patched = patchMemorySection(wasmBytes);

            if (patched) {
                try (FileOutputStream fos = new FileOutputStream(cachedFile)) {
                    fos.write(wasmBytes);
                }
                Log.i(TAG, "Successfully patched and cached Wasm (" + wasmBytes.length + " bytes)");
            } else {
                Log.w(TAG, "Wasm Section 5 not found or already patched");
            }

            return buildWasmResponse(new ByteArrayInputStream(wasmBytes));

        } catch (Exception e) {
            Log.e(TAG, "Error intercepting/patching Wasm: " + e.getMessage(), e);
            return null; // Fall back to default WebView loader
        }
    }

    private static boolean patchMemorySection(byte[] wasm) {
        if (wasm == null || wasm.length < 8) return false;
        // Verify Wasm magic '\0asm'
        if (wasm[0] != 0 || wasm[1] != 'a' || wasm[2] != 's' || wasm[3] != 'm') return false;

        int idx = 8;
        while (idx < wasm.length) {
            int secId = wasm[idx++];
            int size = 0;
            int shift = 0;
            while (idx < wasm.length) {
                byte b = wasm[idx++];
                size |= (b & 0x7f) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
            }

            if (secId == 5) { // Section 5: Memory Section
                if (size == 7) {
                    // Original: count=1, flags=1, init=6176 (0xa0, 0x30), max=32768 (0x80, 0x80, 0x02)
                    // Patched:  count=1, flags=1, init=512 (0x80, 0x04), max=8192 (0x80, 0xc0, 0x00)
                    wasm[idx]     = 1;          // count
                    wasm[idx + 1] = 1;          // has_max
                    wasm[idx + 2] = (byte)0x80; // init = 512 pages (32 MB)
                    wasm[idx + 3] = (byte)0x04;
                    wasm[idx + 4] = (byte)0x80; // max = 8192 pages (512 MB)
                    wasm[idx + 5] = (byte)0xc0;
                    wasm[idx + 6] = (byte)0x00;
                    return true;
                }
                break;
            }
            idx += size;
        }
        return false;
    }

    private static WebResourceResponse buildWasmResponse(InputStream data) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Cross-Origin-Resource-Policy", "cross-origin");
        headers.put("Cross-Origin-Embedder-Policy", "require-corp");
        headers.put("Content-Type", "application/wasm");
        headers.put("Cache-Control", "public, max-age=31536000");

        return new WebResourceResponse("application/wasm", null, 200, "OK", headers, data);
    }

    private WasmPatchHelper() {}
}

