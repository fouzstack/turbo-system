package com.webvirt.loader;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolvedor de tipos MIME basado en extensión de archivo.
 *
 * @since 1.0.0
 */
public class MimeTypeResolver {

    private static final Map<String, String> MIME_TYPES = new HashMap<>();

    static {
        // Texto
        MIME_TYPES.put("html", "text/html");
        MIME_TYPES.put("htm", "text/html");
        MIME_TYPES.put("css", "text/css");
        MIME_TYPES.put("txt", "text/plain");
        MIME_TYPES.put("xml", "application/xml");

        // JavaScript
        MIME_TYPES.put("js", "application/javascript");
        MIME_TYPES.put("mjs", "application/javascript");
        MIME_TYPES.put("json", "application/json");
        MIME_TYPES.put("map", "application/json");

        // Imágenes
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("gif", "image/gif");
        MIME_TYPES.put("svg", "image/svg+xml");
        MIME_TYPES.put("ico", "image/x-icon");
        MIME_TYPES.put("webp", "image/webp");

        // Fuentes
        MIME_TYPES.put("woff", "font/woff");
        MIME_TYPES.put("woff2", "font/woff2");
        MIME_TYPES.put("ttf", "font/ttf");
        MIME_TYPES.put("otf", "font/otf");
        MIME_TYPES.put("eot", "application/vnd.ms-fontobject");

        // Audio/Video
        MIME_TYPES.put("mp3", "audio/mpeg");
        MIME_TYPES.put("mp4", "video/mp4");
        MIME_TYPES.put("webm", "video/webm");
        MIME_TYPES.put("ogg", "audio/ogg");

        // Otros
        MIME_TYPES.put("pdf", "application/pdf");
        MIME_TYPES.put("zip", "application/zip");
        MIME_TYPES.put("wasm", "application/wasm");
        MIME_TYPES.put("webmanifest", "application/manifest+json");
    }

    /**
     * Resuelve el MIME type para un path.
     *
     * @param path Ruta del archivo
     * @return MIME type o "application/octet-stream" si no se reconoce
     */
    public static String resolve(String path) {
        if (path == null) return "application/octet-stream";

        String extension = getExtension(path);
        if (extension == null) return "application/octet-stream";

        String mime = MIME_TYPES.get(extension);
        return mime != null ? mime : "application/octet-stream";
    }

    private static String getExtension(String path) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot < 0) return null;
        return path.substring(lastDot + 1).toLowerCase();
    }
}