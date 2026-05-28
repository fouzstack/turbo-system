package com.nexus;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chain of interceptors that execute before each handler.
 * Allows validating, logging, authenticating, or transforming calls.
 */
final class NexusInterceptorChain {

    private final List<NexusInterceptor> interceptors = new ArrayList<>();

    /**
     * Registers an interceptor.
     */
    void register(@NonNull NexusInterceptor interceptor) {
        interceptors.add(interceptor);
    }

    /**
     * Executes all interceptors in order.
     *
     * @throws NexusException if any interceptor rejects the call
     */
    void execute(@NonNull String method, @NonNull Map<String, Object> params) throws NexusException {
        for (NexusInterceptor interceptor : interceptors) {
            interceptor.intercept(method, params);
        }
    }

    /**
     * Removes all interceptors.
     */
    void clear() {
        interceptors.clear();
    }
}