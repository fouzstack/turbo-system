package com.webvirt.loader;

import android.webkit.WebResourceResponse;
import static com.webvirt.loader.StreamUtils.MAX_CACHEABLE_READ_BYTES;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Abstracción de caché HTTP sobre CacheManager.
 * Centraliza:
 * - Validación condicional (If-Modified-Since / If-None-Match)
 * - Serialización cacheada con headers precalculados
 * - ETag, 304, métricas de caché
 * - Gestión de memoria (trimMemory)
 * 
 * @since 3.6.0
 */
public class ResponseCache {

    private static final String TAG = "WebVirtResponseCache";

    private final CacheManager cacheManager;
    private final ResponseEnricher enricher;

    public ResponseCache(CacheManager cacheManager, ResponseEnricher enricher) {
        this.cacheManager = cacheManager;
        this.enricher = enricher;
    }

    /**
     * Busca en caché considerando validación condicional HTTP.
     *
     * @param path           Path del recurso
     * @param ifNoneMatch    Cabecera If-None-Match (puede ser null)
     * @param ifModifiedSince Cabecera If-Modified-Since (puede ser null)
     * @return CacheResult con hit/miss/notModified
     */
    public CacheResult lookup(String path, String ifNoneMatch, String ifModifiedSince) {
        CacheManager.CacheEntry entry = cacheManager.getEntry(path);

        if (entry == null) {
            return CacheResult.miss();
        }

        // Evaluar validación condicional → 304
        if (ConditionalRequestEvaluator.isNotModified(entry, ifNoneMatch, ifModifiedSince)) {
            return CacheResult.notModified(entry);
        }

        return CacheResult.hit(entry);
    }

    /**
     * Almacena una respuesta en caché con headers precalculados.
     * Solo cachea ByteArrayInputStream con tamaño dentro de límites.
     *
     * @param path          Path del recurso
     * @param response      Respuesta original del handler
     * @param integrityHash Hash SRI (puede ser null)
     */
    public void cacheResponseWithHeaders(
            String path, WebResourceResponse response, String integrityHash) {

        try {
            InputStream is = response.getData();
            if (!(is instanceof ByteArrayInputStream)) return;

            ByteArrayInputStream bais = (ByteArrayInputStream) is;
            int available = bais.available();

            if (available == 0 || available > MAX_CACHEABLE_READ_BYTES) {
                if (bais.markSupported()) bais.reset();
                return;
            }

            byte[] data = StreamUtils.readFullyLimited(bais, MAX_CACHEABLE_READ_BYTES);
            if (data.length == 0) {
                if (bais.markSupported()) bais.reset();
                return;
            }

            String etag = CacheManager.generateETag(data);
            String mimeType = response.getMimeType();
            String encoding = response.getEncoding();
            long lastModified = System.currentTimeMillis();

            Map<String, String> headers = enricher.buildResponseHeaders(
                path, mimeType, etag, integrityHash);

            cacheManager.put(path, data, mimeType, encoding, headers, etag, lastModified);

            // Restaurar stream para posible uso posterior
            if (bais.markSupported()) {
                bais.reset();
            }

        } catch (IOException e) {
            Log.e(TAG, "Cache limit exceeded: " + path + " - " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Cache error: " + path + " - " + e.getMessage());
        }
    }

    // ==================== GESTIÓN DE CACHÉ ====================

    /**
     * Invalida una entrada de caché específica.
     */
    public void invalidate(String path) {
        cacheManager.remove(path);
    }

    /**
     * Limpia completamente la caché.
     */
    public void clear() {
        cacheManager.clear();
    }

    /**
     * Recorta la caché según nivel de presión de memoria de Android.
     *
     * @param level           Nivel TRIM_MEMORY_* de ComponentCallbacks2
     * @param maxCacheSizeBytes Tamaño máximo configurado
     */
    public void trimMemory(int level, long maxCacheSizeBytes) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            cacheManager.clear();
        } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            cacheManager.trimToSize(maxCacheSizeBytes / 4);
        } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE) {
            cacheManager.trimToSize(maxCacheSizeBytes / 2);
        }
    }

    // ==================== MÉTRICAS ====================

    public int getEntryCount() {
        return cacheManager.getEntryCount();
    }

    public long getCurrentSizeBytes() {
        return cacheManager.getCurrentSizeBytes();
    }

    public double getHitRate() {
        return cacheManager.getHitRate();
    }

    public long getHitCount() {
        return cacheManager.getHitCount();
    }

    public long getMissCount() {
        return cacheManager.getMissCount();
    }

    public long getEvictionCount() {
        return cacheManager.getEvictionCount();
    }

    // ==================== CACHE RESULT ====================

    /**
     * Resultado de búsqueda en caché con información de validación condicional.
     */
    public static class CacheResult {
        public final CacheManager.CacheEntry entry;
        public final boolean hit;
        public final boolean notModified;

        private CacheResult(CacheManager.CacheEntry entry, boolean hit, boolean notModified) {
            this.entry = entry;
            this.hit = hit;
            this.notModified = notModified;
        }

        static CacheResult hit(CacheManager.CacheEntry entry) {
            return new CacheResult(entry, true, false);
        }

        static CacheResult miss() {
            return new CacheResult(null, false, false);
        }

        static CacheResult notModified(CacheManager.CacheEntry entry) {
            return new CacheResult(entry, true, true);
        }
    }
}