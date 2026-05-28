package com.nexus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Default JSON serializer.
 * Uses org.json (included in Android) without external dependencies.
 */
public class NexusJsonSerializer implements NexusSerializer {

    @NonNull
    @Override
    public Map<String, Object> deserialize(@NonNull String json) throws Exception {
        JSONObject jsonObject = new JSONObject(json);
        return toMap(jsonObject);
    }

    @NonNull
    @Override
    public String serialize(@Nullable Object object) throws Exception {
        if (object == null) {
            return "{}";
        }
        Object converted = convert(object);
        if (converted instanceof JSONObject || converted instanceof JSONArray) {
            return converted.toString();
        }
        JSONObject wrapper = new JSONObject();
        wrapper.put("result", converted);
        return wrapper.toString();
    }

    private Map<String, Object> toMap(@NonNull JSONObject jsonObject) throws Exception {
        Map<String, Object> map = new HashMap<>();
        Iterator<String> keys = jsonObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = jsonObject.get(key);
            if (value instanceof JSONObject) {
                map.put(key, toMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                map.put(key, toList((JSONArray) value));
            } else if (value == JSONObject.NULL) {
                map.put(key, null);
            } else {
                map.put(key, value);
            }
        }
        return map;
    }

    private List<Object> toList(@NonNull JSONArray jsonArray) throws Exception {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            Object value = jsonArray.get(i);
            if (value instanceof JSONObject) {
                list.add(toMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                list.add(toList((JSONArray) value));
            } else if (value == JSONObject.NULL) {
                list.add(null);
            } else {
                list.add(value);
            }
        }
        return list;
    }

    private Object convert(@Nullable Object object) {
        if (object == null) return JSONObject.NULL;
        if (object instanceof String || object instanceof Number || object instanceof Boolean) {
            return object;
        }
        if (object instanceof Map) return new JSONObject((Map) object);
        if (object instanceof List) return new JSONArray((List) object);
        if (object instanceof JSONObject || object instanceof JSONArray) return object;
        return object.toString();
    }
}