package com.nexus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central handler registry.
 * Stores and retrieves handlers by name.
 */
final class NexusHandlerRegistry {

    private final Map<String, NexusHandler> handlers = new ConcurrentHashMap<>();

    /**
     * Registers a handler.
     *
     * @throws IllegalArgumentException if a handler with that name already exists
     */
    void register(@NonNull String name, @NonNull NexusHandler handler) {
        if (handlers.containsKey(name)) {
            throw new IllegalArgumentException(
                    "A handler is already registered with the name: " + name);
        }
        handlers.put(name, handler);
    }

    /**
     * Gets a handler by name.
     *
     * @return The handler or null if it doesn't exist
     */
    @Nullable
    NexusHandler get(@NonNull String name) {
        return handlers.get(name);
    }

    /**
     * Gets all registered handlers.
     */
    @NonNull
    Collection<NexusHandler> getAll() {
        return handlers.values();
    }

    /**
     * Checks if a handler is registered.
     */
    boolean exists(@NonNull String name) {
        return handlers.containsKey(name);
    }

    /**
     * Gets the number of registered handlers.
     */
    int count() {
        return handlers.size();
    }

    /**
     * Removes all handlers.
     */
    void clear() {
        handlers.clear();
    }
}