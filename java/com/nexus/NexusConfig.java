package com.nexus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nexus configuration.
 * Debug mode is OFF by default for production safety.
 * Configuration becomes immutable after lock() is called.
 */
public class NexusConfig {

    private String jsName = "__nexus";
    private boolean debugMode = false;
    private long timeoutMs = 30_000;
    private NexusSerializer serializer;
    private NexusLogLevel logLevel = NexusLogLevel.INFO;
    private final Map<String, NexusHandler> handlers = new LinkedHashMap<>();
    private final List<NexusInterceptor> interceptors = new ArrayList<>();
    private volatile boolean locked = false;

    // ==================== Getters ====================

    @NonNull
    public String getJsName() {
        return jsName;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    @Nullable
    public NexusSerializer getSerializer() {
        return serializer;
    }

    @NonNull
    public NexusLogLevel getLogLevel() {
        return logLevel;
    }

    @NonNull
    public Map<String, NexusHandler> getHandlers() {
        return new LinkedHashMap<>(handlers);
    }

    @NonNull
    public List<NexusInterceptor> getInterceptors() {
        return new ArrayList<>(interceptors);
    }

    // ==================== Setters ====================

    public void setJsName(@NonNull String jsName) {
        checkNotLocked();
        if (jsName.trim().isEmpty()) {
            throw new IllegalArgumentException("JS name cannot be empty");
        }
        this.jsName = jsName.trim();
    }

    public void setDebugMode(boolean debugMode) {
        checkNotLocked();
        this.debugMode = debugMode;
    }

    public void setTimeoutMs(long timeoutMs) {
        checkNotLocked();
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("Timeout must be >= 0");
        }
        this.timeoutMs = timeoutMs;
    }

    public void setSerializer(@NonNull NexusSerializer serializer) {
        checkNotLocked();
        this.serializer = serializer;
    }

    public void setLogLevel(@NonNull NexusLogLevel logLevel) {
        checkNotLocked();
        this.logLevel = logLevel;
    }

    void registerHandler(@NonNull String name, @NonNull NexusHandler handler) {
        checkNotLocked();
        handlers.put(name, handler);
    }

    void registerInterceptor(@NonNull NexusInterceptor interceptor) {
        checkNotLocked();
        interceptors.add(interceptor);
    }

    // ==================== Locking ====================

    /**
     * Locks the configuration, making it immutable.
     * Called automatically by Nexus.initialize().
     */
    void lock() {
        this.locked = true;
    }

    private void checkNotLocked() {
        if (locked) {
            throw new IllegalStateException(
                    "Configuration is locked. Cannot modify after initialization.");
        }
    }
}