package com.nexus;

import androidx.annotation.NonNull;
import java.util.Map;

/**
 * Interface for Nexus interceptors.
 * Interceptors execute in a chain before each handler.
 * They can validate, log, authenticate, or block calls.
 */
public interface NexusInterceptor {

    /**
     * Intercepts a call before it reaches the handler.
     *
     * @param method Handler name being invoked
     * @param params Call parameters (mutable)
     * @throws NexusException To block the call with a specific error
     */
    void intercept(@NonNull String method, @NonNull Map<String, Object> params) throws NexusException;
}