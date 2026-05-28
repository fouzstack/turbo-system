package com.nexus;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bidirectional event system.
 * Allows the native side to emit events that JavaScript listens to.
 */
final class NexusEventBus {

    private final Map<String, List<String>> subscriptions = new ConcurrentHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Subscribes a JavaScript callback to an event.
     */
    void subscribe(@NonNull String event, @NonNull String callbackId) {
        subscriptions
                .computeIfAbsent(event, k -> new CopyOnWriteArrayList<>())
                .add(callbackId);
        NexusLog.d("EventBus", "Subscribed to: " + event + " (total: "
                + subscriptions.get(event).size() + ")");
    }

    /**
     * Unsubscribes a callback from an event.
     */
    void unsubscribe(@NonNull String event, @NonNull String callbackId) {
        List<String> list = subscriptions.get(event);
        if (list != null) {
            list.remove(callbackId);
            if (list.isEmpty()) {
                subscriptions.remove(event);
            }
        }
    }

    /**
     * Emits an event to all subscribed listeners in JavaScript.
     */
    void emit(@NonNull String event, @Nullable Object data,
              @NonNull NexusSerializer serializer, @NonNull WebView webView) {
        List<String> list = subscriptions.get(event);
        if (list == null || list.isEmpty()) {
            NexusLog.d("EventBus", "Event with no subscribers: " + event);
            return;
        }

        String dataJson;
        try {
            dataJson = serializer.serialize(data);
        } catch (Exception e) {
            NexusLog.e("EventBus", "Error serializing event data: " + event, e);
            return;
        }

        String escapedData = escapeJs(dataJson);

        for (String callbackId : list) {
            mainHandler.post(() -> {
                String js = String.format(
                        "if (typeof window.Nexus !== 'undefined' && window.Nexus._emit) {" +
                                "window.Nexus._emit('%s', '%s', %s);" +
                                "}",
                        escapeJs(event),
                        escapeJs(callbackId),
                        escapedData
                );
                webView.evaluateJavascript(js, null);
            });
        }

        NexusLog.d("EventBus", "Emitted '" + event + "' to " + list.size() + " subscriber(s)");
    }

    /**
     * Removes all subscriptions.
     */
    void clear() {
        subscriptions.clear();
    }

    private String escapeJs(@NonNull String value) {
        return value.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}