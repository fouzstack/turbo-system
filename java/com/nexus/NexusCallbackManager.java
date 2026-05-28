package com.nexus;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages pending JavaScript callbacks.
 * Thread-safe with proper cleanup for the thread pool.
 */
final class NexusCallbackManager {

    private final Map<String, PendingCallback> pending = new ConcurrentHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final long timeoutMs;

    NexusCallbackManager(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    void addCallback(@NonNull String callbackId, @NonNull WebView webView) {
        PendingCallback pc = new PendingCallback(callbackId, webView, mainHandler);
        pending.put(callbackId, pc);

        if (timeoutMs > 0) {
            mainHandler.postDelayed(() -> {
                if (pending.containsKey(callbackId)) {
                    rejectCallback(callbackId, "TIMEOUT", "Operation timed out after " + timeoutMs + "ms");
                }
            }, timeoutMs);
        }
    }

    void resolveCallback(@NonNull String callbackId, @NonNull String resultJson) {
        PendingCallback pendingCallback = pending.remove(callbackId);

        if (pendingCallback != null) {
            pendingCallback.resolve(resultJson);
        }
    }

    void rejectCallback(@NonNull String callbackId, @NonNull String code, @NonNull String message) {
        PendingCallback pendingCallback = pending.remove(callbackId);

        if (pendingCallback != null) {
            pendingCallback.reject(code, message);
        }
    }

    void cancelAll(@NonNull String reason) {
        for (PendingCallback pc : pending.values()) {
            pc.reject("CANCELLED", reason);
        }
        pending.clear();
    }

    void handleActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        // Override in subclasses if needed
    }

    private static class PendingCallback {
        private final String id;
        private final WebView webView;
        private final Handler mainHandler;

        PendingCallback(@NonNull String id, @NonNull WebView webView, @NonNull Handler mainHandler) {
            this.id = id;
            this.webView = webView;
            this.mainHandler = mainHandler;
        }

        void resolve(@NonNull String resultJson) {
            mainHandler.post(() -> {
                String escapedId = escapeJs(id);
                String js = "(function(){" +
                        "if(typeof window.Nexus!=='undefined'&&typeof window.Nexus._res==='function'){" +
                        "window.Nexus._res('" + escapedId + "'," + resultJson + ");" +
                        "}" +
                        "})()";

                webView.evaluateJavascript(js, null);
            });
        }

        void reject(@NonNull String code, @NonNull String message) {
            mainHandler.post(() -> {
                try {
                    JSONObject error = new JSONObject();
                    error.put("code", code);
                    error.put("message", message);
                    String errorJson = error.toString();
                    String escapedId = escapeJs(id);

                    String js = "(function(){" +
                            "if(typeof window.Nexus!=='undefined'&&typeof window.Nexus._rej==='function'){" +
                            "window.Nexus._rej('" + escapedId + "'," + errorJson + ");" +
                            "}" +
                            "})()";

                    webView.evaluateJavascript(js, null);
                } catch (Exception e) {
                    NexusLog.e("CallbackManager", "Error rejecting callback", e);
                }
            });
        }

        private String escapeJs(@NonNull String value) {
            return value.replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
        }
    }
}