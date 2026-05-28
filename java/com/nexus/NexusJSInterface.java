package com.nexus;

import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.*;

/**
 * JavaScript interface exposed to the WebView.
 * Handles incoming calls from JavaScript with thread pool execution
 * and true timeout cancellation.
 */
final class NexusJSInterface {

    private final NexusHandlerRegistry handlerRegistry;
    private final NexusCallbackManager callbackManager;
    private final NexusInterceptorChain interceptorChain;
    private final NexusSerializer serializer;
    private final NexusEventBus eventBus;
    private final WeakReference<WebView> webViewRef;
    private final NexusConfig config;
    private final Handler mainHandler;
    private final ExecutorService executor;
    private final ScheduledExecutorService timeoutScheduler;

    NexusJSInterface(
            @NonNull NexusHandlerRegistry handlerRegistry,
            @NonNull NexusCallbackManager callbackManager,
            @NonNull NexusInterceptorChain interceptorChain,
            @NonNull NexusSerializer serializer,
            @NonNull NexusEventBus eventBus,
            @NonNull WeakReference<WebView> webViewRef,
            @NonNull NexusConfig config
    ) {
        this.handlerRegistry = handlerRegistry;
        this.callbackManager = callbackManager;
        this.interceptorChain = interceptorChain;
        this.serializer = serializer;
        this.eventBus = eventBus;
        this.webViewRef = webViewRef;
        this.config = config;
        this.mainHandler = new Handler(Looper.getMainLooper());

        // Thread pool para handlers
        this.executor = new ThreadPoolExecutor(
                2, 4,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                r -> {
                    Thread t = new Thread(r, "Nexus-Worker");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // Scheduler global para timeouts - un solo thread reutilizado
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Nexus-Timeout");
            t.setDaemon(true);
            return t;
        });
    }

    @JavascriptInterface
    public void call(@NonNull String method, @NonNull String paramsJson, @NonNull String callbackId) {
        try {
            WebView webView = webViewRef.get();
            if (webView == null) {
                rejectOnMainThread(callbackId, "DESTROYED", "WebView no longer exists");
                return;
            }

            callbackManager.addCallback(callbackId, webView);

            NexusHandler handler = handlerRegistry.get(method);
            if (handler == null) {
                rejectOnMainThread(callbackId, "HANDLER_NOT_FOUND", "Handler does not exist: " + method);
                return;
            }

            Map<String, Object> params;
            try {
                params = serializer.deserialize(paramsJson);
            } catch (Exception e) {
                rejectOnMainThread(callbackId, "SERIALIZATION_ERROR", "Invalid parameters: " + e.getMessage());
                return;
            }

            try {
                interceptorChain.execute(method, params);
            } catch (Exception e) {
                rejectOnMainThread(callbackId, "BLOCKED", "Call blocked: " + e.getMessage());
                return;
            }

            long timeout = handler.getTimeoutMs() > 0 ? handler.getTimeoutMs() : config.getTimeoutMs();
            executeHandler(method, handler, params, callbackId, timeout);

        } catch (Exception e) {
            rejectOnMainThread(callbackId, "INTERNAL_ERROR", "Internal error: " + e.getMessage());
        }
    }

    private void executeHandler(
            @NonNull String method,
            @NonNull NexusHandler handler,
            @NonNull Map<String, Object> params,
            @NonNull String callbackId,
            long timeoutMs
    ) {
        Future<?> future = executor.submit(() -> {
            try {
                Object result = handler.handle(params);

                String resultJson;
                try {
                    resultJson = serializer.serialize(result);
                } catch (Exception e) {
                    rejectOnMainThread(callbackId, "SERIALIZATION_ERROR", "Error serializing result: " + e.getMessage());
                    return;
                }

                final String finalResultJson = resultJson;
                mainHandler.post(() -> callbackManager.resolveCallback(callbackId, finalResultJson));

            } catch (InterruptedException e) {
                // El futuro fue cancelado por timeout
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : "Unknown error";
                String code = (e instanceof NexusException) ? ((NexusException) e).getCode() : "HANDLER_ERROR";

                final String finalCode = code;
                final String finalMessage = message;
                mainHandler.post(() -> callbackManager.rejectCallback(callbackId, finalCode, finalMessage));
            }
        });

        // Timeout real usando el scheduler global reutilizable
        if (timeoutMs > 0) {
            timeoutScheduler.schedule(() -> {
                if (!future.isDone()) {
                    future.cancel(true);
                    rejectOnMainThread(callbackId, "TIMEOUT", "Operation timed out after " + timeoutMs + "ms");
                }
            }, timeoutMs, TimeUnit.MILLISECONDS);
        }
    }

    private void rejectOnMainThread(@NonNull String callbackId, @NonNull String code, @NonNull String message) {
        mainHandler.post(() -> callbackManager.rejectCallback(callbackId, code, message));
    }

    @JavascriptInterface
    public void on(@NonNull String event, @NonNull String callbackId) {
        eventBus.subscribe(event, callbackId);
    }

    @JavascriptInterface
    public void off(@NonNull String event, @NonNull String callbackId) {
        eventBus.unsubscribe(event, callbackId);
    }

    /**
     * Shuts down both thread pools. Called during Nexus destruction.
     */
    void shutdown() {
        executor.shutdownNow();
        timeoutScheduler.shutdownNow();
    }
}