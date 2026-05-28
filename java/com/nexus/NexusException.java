package com.nexus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Controlled exception for Nexus handler errors.
 * When a handler throws this exception, Nexus automatically
 * rejects the JavaScript promise with the error code and message.
 */
public class NexusException extends Exception {

    private final String code;

    /**
     * @param code    Error code for JavaScript to react (e.g., "PDF_ERROR", "TIMEOUT")
     * @param message Descriptive message for the developer
     */
    public NexusException(@NonNull String code, @NonNull String message) {
        super(message);
        this.code = code;
    }

    /**
     * @param code    Error code
     * @param message Descriptive message
     * @param cause   Original exception
     */
    public NexusException(@NonNull String code, @NonNull String message, @Nullable Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    @NonNull
    public String getCode() {
        return code;
    }
}