package com.nexus;

import androidx.annotation.NonNull;

import java.util.Map;

/**
 * Interface that all Nexus handlers must implement.
 * Each handler represents a native functionality invocable
 * from JavaScript through Nexus.call('handlerName', params).
 * <p>
 * For simple cases, use {@link Nexus.NexusHandlerLogic} with a lambda instead.
 *
 * <pre>{@code
 * // Traditional approach (full control)
 * public class ExportHandler implements NexusHandler {
 *     public String getName() { return "export"; }
 *     public Object handle(Map<String, Object> params) { ... }
 * }
 *
 * // Lambda approach (simpler)
 * nexus.registerHandler("export", params -> {
 *     String data = (String) params.get("data");
 *     exportHandler.exportJsonToFile(data);
 *     return Map.of("status", "ok");
 * });
 * }</pre>
 */
public interface NexusHandler {

    /**
     * Public name that JavaScript will use to invoke this handler.
     * Must be unique within the Nexus instance.
     *
     * @return Handler name (e.g., "pdf", "import", "search")
     */
    @NonNull
    String getName();

    /**
     * Executes the handler logic.
     *
     * @param params Parameters deserialized from JSON (may be empty map, never null)
     * @return Result to be serialized to JSON and sent to JavaScript
     * @throws Exception If an error occurs, Nexus converts it to a promise rejection
     */
    @NonNull
    Object handle(@NonNull Map<String, Object> params) throws Exception;

    /**
     * Custom timeout in milliseconds.
     * Returns 0 to use Nexus global timeout.
     *
     * @return Timeout in ms, 0 = use global
     */
    default long getTimeoutMs() {
        return 0;
    }

    /**
     * Log level for this handler.
     * Useful to silence very verbose handlers.
     *
     * @return Log level
     */
    @NonNull
    default NexusLogLevel getLogLevel() {
        return NexusLogLevel.INFO;
    }

    /**
     * Called when Nexus is initialized.
     * Useful for preparing resources.
     */
    default void onInitialize() {
    }

    /**
     * Called when Nexus is destroyed.
     * Release resources here.
     */
    default void onDestroy() {
    }
}