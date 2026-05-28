package com.nexus;

import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Nexus - Simplified WebView-to-Native communication.
 * <p>
 * Main entry point. Acts as a mediator between JavaScript and native handlers.
 * Designed to complement (not compete with) WebVirt.
 * <p>
 * Uses WebViewLifecycleObserver to coexist with other libraries (like WebVirt)
 * that also need to set a WebViewClient on the same WebView.
 * <p>
 * Smart re-injection: Only re-injects the JavaScript runtime on actual page
 * navigations, not on SPA hash changes (e.g., React Router).
 * <p>
 * FilePicker integration: Built-in universal file picker that doesn't require
 * Android permissions. Uses Storage Access Framework (SAF).
 *
 * <pre>{@code
 * // 1. Configure WebVirt first
 * WebVirt.with(context).host("app.local").bind(webView);
 *
 * // 2. Initialize Nexus with FilePicker
 * Nexus nexus = Nexus.installOn(webView)
 *     .withDebugMode(false)
 *     .withGlobalTimeout(30_000)
 *     .registerHandler("export", new ExportHandlerAdapter(handler))
 *     .registerHandler("import", new ImportHandlerAdapter(context))
 *     .initialize()
 *     .withFilePicker(activity);
 *
 * // 3. Attach Nexus to WebView lifecycle (after WebVirt)
 * nexus.attachToWebViewLifecycle();
 * }</pre>
 *
 * @author FouzStack
 * @version 2.1.0
 */
public class Nexus {

    private final WeakReference<WebView> webViewRef;
    private final NexusConfig config;
    private final NexusHandlerRegistry handlerRegistry;
    private final NexusCallbackManager callbackManager;
    private final NexusInterceptorChain interceptorChain;
    private final NexusEventBus eventBus;
    private final NexusSerializer serializer;
    private final NexusRuntimeInjector runtimeInjector;

    private NexusJSInterface jsInterface;
    private NexusFilePickerListener filePickerListener;
    private WebViewLifecycleObserver lifecycleObserver;
    private NexusFilePicker filePicker;
    private boolean initialized = false;
    private volatile boolean destroyed = false;

    private Nexus(@NonNull WebView webView, @NonNull NexusConfig config) {
        this.webViewRef = new WeakReference<>(webView);
        this.config = config;
        this.handlerRegistry = new NexusHandlerRegistry();
        this.callbackManager = new NexusCallbackManager(config.getTimeoutMs());
        this.interceptorChain = new NexusInterceptorChain();
        this.eventBus = new NexusEventBus();
        this.serializer = config.getSerializer() != null
                ? config.getSerializer()
                : new NexusJsonSerializer();
        this.runtimeInjector = new NexusRuntimeInjector(webView, config.getJsName());
    }

    // ==================== STATIC FACTORY ====================

    @NonNull
    public static Builder installOn(@NonNull WebView webView) {
        if (!webView.getSettings().getJavaScriptEnabled()) {
            NexusLog.w("Nexus", "JavaScript is not enabled in the WebView. Nexus requires it.");
        }
        return new Builder(webView);
    }

    @NonNull
    public static Nexus quick(@NonNull WebView webView, @NonNull NexusConfigurator configurator) {
        Builder builder = installOn(webView);
        configurator.configure(builder);
        return builder.initialize();
    }

    // ==================== HANDLER REGISTRATION ====================

    @NonNull
    public Nexus registerHandler(@NonNull String name, @NonNull NexusHandler handler) {
        checkNotInitialized();
        validateHandlerName(name);
        handlerRegistry.register(name, handler);
        return this;
    }

    @NonNull
    public Nexus registerHandler(@NonNull String name, @NonNull NexusHandlerLogic logic) {
        checkNotInitialized();
        validateHandlerName(name);
        handlerRegistry.register(name, new NexusHandler() {
            @NonNull
            @Override
            public String getName() {
                return name;
            }

            @NonNull
            @Override
            public Object handle(@NonNull Map<String, Object> params) throws Exception {
                return logic.handle(params);
            }
        });
        return this;
    }

    @NonNull
    public Nexus registerInterceptor(@NonNull NexusInterceptor interceptor) {
        checkNotInitialized();
        interceptorChain.register(interceptor);
        return this;
    }

    // ==================== FILE PICKER (Legacy) ====================

    /**
     * @deprecated Use {@link #withFilePicker(FragmentActivity)} instead.
     */
    @Deprecated
    public void setFilePickerListener(@Nullable NexusFilePickerListener listener) {
        this.filePickerListener = listener;
    }

    /**
     * @deprecated Use {@link #getFilePicker()} instead.
     */
    @Deprecated
    @Nullable
    public NexusFilePickerListener getFilePickerListener() {
        return filePickerListener;
    }

    // ==================== FILE PICKER (Universal) ====================

    @NonNull
    public Nexus withFilePicker(@NonNull FragmentActivity activity) {
        checkInitialized();
        this.filePicker = new NexusFilePicker(activity);
        NexusLog.i("Nexus", "FilePicker initialized successfully");
        return this;
    }

    @Nullable
    public NexusFilePicker getFilePicker() {
        return filePicker;
    }

    public boolean hasFilePicker() {
        return filePicker != null;
    }

    // ==================== INITIALIZATION ====================

    @NonNull
    public Nexus initialize() {
        if (initialized) {
            throw new IllegalStateException("Nexus is already initialized");
        }

        WebView webView = webViewRef.get();
        if (webView == null) {
            throw new IllegalStateException("WebView has been garbage collected");
        }

        config.lock();

        jsInterface = new NexusJSInterface(
                handlerRegistry, callbackManager, interceptorChain,
                serializer, eventBus, webViewRef, config
        );

        webView.addJavascriptInterface(jsInterface, config.getJsName());
        runtimeInjector.inject();

        for (NexusHandler handler : handlerRegistry.getAll()) {
            try {
                handler.onInitialize();
            } catch (Exception e) {
                NexusLog.e("Nexus", "Error initializing handler: " + handler.getName(), e);
            }
        }

        initialized = true;
        NexusLog.i("Nexus", "Nexus initialized successfully");
        return this;
    }

    // ==================== WEBVIEW LIFECYCLE INTEGRATION ====================

    public void attachToWebViewLifecycle() {
        if (!initialized || lifecycleObserver != null) return;

        WebView webView = getWebViewOrThrow();

        lifecycleObserver = new WebViewLifecycleObserver(webView);

        lifecycleObserver.addListener(new WebViewLifecycleObserver.Listener() {

            private String previousBaseUrl = "";

            @Override
            public void onPageFinished(@NonNull WebView webView, @NonNull String url) {
                String baseUrl = url.contains("#") ? url.substring(0, url.indexOf("#")) : url;

                if (!baseUrl.equals(previousBaseUrl)) {
                    previousBaseUrl = baseUrl;
                    notifyPageLoaded();
                }
            }

            @Override
            public void onPageStarted(@NonNull WebView webView, @NonNull String url) {
                // No action needed
            }

            @Override
            public void onReceivedError(@NonNull WebView webView, int errorCode,
                                         @NonNull String description, @NonNull String failingUrl) {
                NexusLog.e("Nexus", "WebView error: " + description + " - " + failingUrl);
            }

            @Override
            public void onWebViewDestroy(@NonNull WebView webView) {
                destroy();
            }
        });

        lifecycleObserver.install();
    }

    public void detachFromWebViewLifecycle() {
        if (lifecycleObserver != null) {
            lifecycleObserver.uninstall();
            lifecycleObserver = null;
        }
    }

    // ==================== RUNTIME MANAGEMENT ====================

    public void notifyPageLoaded() {
        if (initialized && runtimeInjector != null) {
            runtimeInjector.inject();
        }
    }

    // ==================== EVENT EMISSION ====================

    public void emitEvent(@NonNull String eventName, @Nullable Object data) {
        checkInitialized();
        WebView webView = webViewRef.get();
        if (webView != null) {
            eventBus.emit(eventName, data, serializer, webView);
        }
    }

    // ==================== ACTIVITY RESULT HANDLING ====================

    public void handleActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        checkInitialized();
        callbackManager.handleActivityResult(requestCode, resultCode, data);
    }

    public void deliverFileResult(@NonNull String callbackId, @Nullable Object result) {
        checkInitialized();

        try {
            String json = serializer.serialize(result);
            callbackManager.resolveCallback(callbackId, json);
        } catch (Exception e) {
            NexusLog.e("Nexus", "Error delivering file result", e);
            callbackManager.rejectCallback(callbackId, "SERIALIZATION_ERROR",
                    "Failed to serialize file result: " + e.getMessage());
        }
    }

    // ==================== DESTRUCTION ====================

    public void destroy() {
        if (destroyed) return;
        destroyed = true;

        if (lifecycleObserver != null) {
            lifecycleObserver.uninstall();
            lifecycleObserver = null;
        }

        callbackManager.cancelAll("Nexus destroyed");

        for (NexusHandler handler : handlerRegistry.getAll()) {
            try {
                handler.onDestroy();
            } catch (Exception e) {
                NexusLog.e("Nexus", "Error destroying handler: " + handler.getName(), e);
            }
        }

        if (jsInterface != null) {
            jsInterface.shutdown();
            jsInterface = null;
        }

        WebView webView = webViewRef.get();
        if (webView != null && initialized) {
            webView.removeJavascriptInterface(config.getJsName());
        }

        handlerRegistry.clear();
        interceptorChain.clear();
        eventBus.clear();

        filePicker = null;
        filePickerListener = null;

        initialized = false;
    }

    // ==================== INTERNAL METHODS ====================

    private void checkNotInitialized() {
        if (initialized) {
            throw new IllegalStateException(
                    "Nexus is already initialized. Register handlers before calling initialize().");
        }
    }

    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("Nexus is not initialized. Call initialize() first.");
        }
    }

    @NonNull
    private WebView getWebViewOrThrow() {
        WebView webView = webViewRef.get();
        if (webView == null) {
            throw new IllegalStateException("WebView has been garbage collected");
        }
        return webView;
    }

    private void validateHandlerName(@NonNull String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Handler name cannot be empty");
        }

        Set<String> reserved = new HashSet<>(Arrays.asList(
                "_res", "_rej", "_emt", "_c", "_e", "_id", "_gid", "_init",
                "call", "on", "off", "v", "constructor", "prototype", "__proto__"
        ));

        if (reserved.contains(name)) {
            throw new IllegalArgumentException(
                    "'" + name + "' is a reserved name and cannot be used as a handler");
        }
    }

    // ==================== FUNCTIONAL INTERFACES ====================

    @FunctionalInterface
    public interface NexusHandlerLogic {
        @NonNull Object handle(@NonNull Map<String, Object> params) throws Exception;
    }

    @FunctionalInterface
    public interface NexusConfigurator {
        void configure(@NonNull Builder builder);
    }

    // ==================== BUILDER ====================

    public static class Builder {
        private final WebView webView;
        private final NexusConfig config;

        private Builder(@NonNull WebView webView) {
            this.webView = webView;
            this.config = new NexusConfig();
        }

        @NonNull
        public Builder withJsName(@NonNull String name) {
            config.setJsName(name);
            return this;
        }

        @NonNull
        public Builder withDebugMode(boolean enabled) {
            config.setDebugMode(enabled);
            return this;
        }

        @NonNull
        public Builder withGlobalTimeout(long timeoutMs) {
            config.setTimeoutMs(timeoutMs);
            return this;
        }

        @NonNull
        public Builder withSerializer(@NonNull NexusSerializer serializer) {
            config.setSerializer(serializer);
            return this;
        }

        @NonNull
        public Builder withLogLevel(@NonNull NexusLogLevel level) {
            config.setLogLevel(level);
            return this;
        }

        @NonNull
        public Builder registerHandler(@NonNull String name, @NonNull NexusHandler handler) {
            config.registerHandler(name, handler);
            return this;
        }

        @NonNull
        public Builder registerHandler(@NonNull String name, @NonNull NexusHandlerLogic logic) {
            config.registerHandler(name, new NexusHandler() {
                @NonNull
                @Override
                public String getName() {
                    return name;
                }

                @NonNull
                @Override
                public Object handle(@NonNull Map<String, Object> params) throws Exception {
                    return logic.handle(params);
                }
            });
            return this;
        }

        @NonNull
        public Builder registerInterceptor(@NonNull NexusInterceptor interceptor) {
            config.registerInterceptor(interceptor);
            return this;
        }

        @NonNull
        public Nexus initialize() {
            Nexus nexus = new Nexus(webView, config);

            for (Map.Entry<String, NexusHandler> entry : config.getHandlers().entrySet()) {
                nexus.registerHandler(entry.getKey(), entry.getValue());
            }

            for (NexusInterceptor interceptor : config.getInterceptors()) {
                nexus.registerInterceptor(interceptor);
            }

            return nexus.initialize();
        }
    }
}