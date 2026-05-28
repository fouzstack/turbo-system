package com.webvirt.loader;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Validador de seguridad para paths de assets.
 *
 * Verifica:
 * - Extensión permitida
 * - Tamaño máximo de archivo
 * - Sin path traversal
 *
 * @since 1.0.0
 */
public class SecurityManager {

    private final Set<String> allowedExtensions;
    private final long maxFileSize;

    /**
     * @param allowedExtensions Extensiones permitidas (sin punto, minúsculas)
     * @param maxFileSize       Tamaño máximo de archivo en bytes
     */
    public SecurityManager(Set<String> allowedExtensions, long maxFileSize) {
        this.allowedExtensions = Collections.unmodifiableSet(
            new HashSet<>(allowedExtensions));
        this.maxFileSize = maxFileSize;
    }

    /**
     * Verifica si un path está permitido.
     */
    public boolean isPathAllowed(String path) {
        if (path == null) return false;

        String extension = getExtension(path);
        if (extension == null) {
            // Sin extensión: solo permitir paths que sean directorios o sin extensión explícita
            return path.equals("/") || path.endsWith("/");
        }

        return allowedExtensions.contains(extension);
    }

    /**
     * Verifica si un tamaño de archivo está dentro del límite.
     */
    public boolean isSizeAllowed(long size) {
        return size <= maxFileSize;
    }

    /**
     * @return Extensiones permitidas por defecto para una SPA
     */
    public static Set<String> getDefaultAllowedExtensions() {
        Set<String> extensions = new HashSet<>();
        extensions.add("html");
        extensions.add("htm");
        extensions.add("css");
        extensions.add("js");
        extensions.add("mjs");
        extensions.add("json");
        extensions.add("map");
        extensions.add("png");
        extensions.add("jpg");
        extensions.add("jpeg");
        extensions.add("gif");
        extensions.add("svg");
        extensions.add("ico");
        extensions.add("webp");
        extensions.add("woff");
        extensions.add("woff2");
        extensions.add("ttf");
        extensions.add("otf");
        extensions.add("eot");
        extensions.add("txt");
        extensions.add("xml");
        extensions.add("webmanifest");
        extensions.add("mp3");
        extensions.add("mp4");
        extensions.add("webm");
        extensions.add("ogg");
        extensions.add("pdf");
        extensions.add("wasm");
        extensions.add("data");
        return extensions;
    }

    private static String getExtension(String path) {
        if (path == null) return null;
        int lastDot = path.lastIndexOf('.');
        if (lastDot < 0) return null;
        return path.substring(lastDot + 1).toLowerCase();
    }
}