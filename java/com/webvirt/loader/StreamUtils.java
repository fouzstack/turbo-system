package com.webvirt.loader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utilidades de lectura segura de streams.
 * Protección OOM centralizada con límites configurables.
 * 
 * @since 3.6.0
 */
public final class StreamUtils {

    /**
     * Límite máximo de lectura para assets cacheables.
     */
    public static final int MAX_CACHEABLE_READ_BYTES = 5 * 1024 * 1024; // 5 MB

    private StreamUtils() {
        throw new AssertionError("No instances");
    }

    /**
     * Lee completamente un InputStream con el límite por defecto (5 MB).
     *
     * @param is InputStream a leer
     * @return Array de bytes leídos
     * @throws IOException Si ocurre error de lectura o se excede el límite
     */
    public static byte[] readFully(InputStream is) throws IOException {
        return readFullyLimited(is, MAX_CACHEABLE_READ_BYTES);
    }

    /**
     * Lee un InputStream con un límite máximo de bytes.
     * Lanza IOException si se excede el límite para prevenir OOM.
     *
     * @param is       InputStream a leer
     * @param maxBytes Límite máximo en bytes
     * @return Array de bytes leídos
     * @throws IOException Si ocurre error de lectura o se excede el límite
     */
    public static byte[] readFullyLimited(InputStream is, int maxBytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;

        while ((read = is.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException(
                    "Stream exceeds limit: " + maxBytes + " bytes (read: " + total + ")");
            }
            baos.write(buffer, 0, read);
        }

        return baos.toByteArray();
    }
}