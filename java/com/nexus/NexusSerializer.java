package com.nexus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Map;

/**
 * Serialization interface for Nexus.
 * Implement to use other formats (Gson, Moshi, MessagePack).
 */
public interface NexusSerializer {

    /**
     * Deserializes a JSON parameter string into a map.
     *
     * @param json JSON received from JavaScript
     * @return Parameter map (never null)
     * @throws Exception if JSON is invalid
     */
    @NonNull
    Map<String, Object> deserialize(@NonNull String json) throws Exception;

    /**
     * Serializes a result to JSON for sending to JavaScript.
     *
     * @param object Handler result (may be null)
     * @return JSON string
     * @throws Exception if the object cannot be serialized
     */
    @NonNull
    String serialize(@Nullable Object object) throws Exception;
}