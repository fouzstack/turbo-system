package com.webvirt.loader;

import android.os.SystemClock;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import com.webvirt.WebVirtMetricsCollector;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Orquestador de carga de assets.
 * Coordina: handler → caché → coalescencia → enriquecimiento → métricas.
 * 
 * @since 3.6.0
 */
public class AssetLoader {

    private static final String TAG = "WebVirtAssetLoader";

    private final RequestRouter requestRouter;
    private final ResponseCache responseCache;
    private final ResponseEnricher enricher;
    private final InFlightRequestRegistry inFlightRegistry;
    private final WebVirtMetricsCollector metricsCollector;

    public AssetLoader(
            RequestRouter requestRouter,
            ResponseCache responseCache,
            ResponseEnricher enricher,
            InFlightRequestRegistry inFlightRegistry,
            WebVirtMetricsCollector metricsCollector) {
        this.requestRouter = requestRouter;
        this.responseCache = responseCache;
        this.enricher = enricher;
        this.inFlightRegistry = inFlightRegistry;
        this.metricsCollector = metricsCollector;
    }

    /**
     * Carga un asset con soporte completo de caché y coalescencia.
     *
     * @param path          Path resuelto del asset (normalizado, seguro)
     * @param request       Petición original (puede ser null para precache)
     * @param cacheable     true si el asset es cacheable estáticamente
     * @param integrityHash Hash de integridad SRI (puede ser null)
     * @return WebResourceResponse o null si no se encuentra
     */
    public WebResourceResponse load(
            String path,
            WebResourceRequest request,
            boolean cacheable,
            String integrityHash) {

        long startTime = SystemClock.elapsedRealtime();

        PathHandler handler = requestRouter.resolve(path);
        if (handler == null) return null;

        try {
            // === FLUJO CON CACHÉ ===
            if (cacheable) {
                String ifNoneMatch = request != null
                    ? HttpUtils.getRequestHeader(request, "If-None-Match") : null;
                String ifModifiedSince = request != null
                    ? HttpUtils.getRequestHeader(request, "If-Modified-Since") : null;

                // Buscar en caché con validación condicional
                ResponseCache.CacheResult cacheResult = responseCache.lookup(
                    path, ifNoneMatch, ifModifiedSince);

                if (cacheResult.hit) {
                    long elapsed = SystemClock.elapsedRealtime() - startTime;

                    if (cacheResult.notModified) {
                        // 304 Not Modified
                        metricsCollector.recordAssetLoad(path, elapsed, true, 0);
                        return cacheResult.entry.to304Response();
                    }

                    // Hit de caché completo
                    WebResourceResponse response = cacheResult.entry.toResponse();
                    if (response != null) {
                        metricsCollector.recordAssetLoad(path, elapsed, true, cacheResult.entry.size);
                    }
                    return response;
                }

                // === COALESCENCIA: evitar cargas duplicadas ===
                InFlightRequestRegistry.InFlightRequest inFlight = inFlightRegistry.register(path);

                synchronized (inFlight.getLock()) {
                    try {
                        // Verificar si otro hilo completó la carga mientras esperábamos
                        if (inFlightRegistry.isCompletedAndCached(inFlight)) {
                            ResponseCache.CacheResult freshResult = responseCache.lookup(
                                path, null, null);
                            if (freshResult.hit) {
                                long elapsed = SystemClock.elapsedRealtime() - startTime;
                                WebResourceResponse response = freshResult.entry.toResponse();
                                if (response != null) {
                                    metricsCollector.recordAssetLoad(
                                        path, elapsed, true, freshResult.entry.size);
                                }
                                return response;
                            }
                        }

                        // Cargar desde el handler
                        WebResourceResponse response = loadFromHandler(
                            path, handler, request, startTime, cacheable, integrityHash);

                        inFlight.markCompleted();
                        inFlight.markCached();

                        return response;

                    } finally {
                        inFlightRegistry.complete(path, inFlight);
                    }
                }
            }

            // === FLUJO SIN CACHÉ ===
            return loadFromHandler(path, handler, request, startTime, false, integrityHash);

        } catch (Exception e) {
            metricsCollector.recordHttpError();
            Log.e(TAG, "Error loading: " + path, e);
            return ErrorResponseFactory.createErrorResponse(500, "Internal Server Error");
        }
    }

    /**
     * Carga desde el handler, opcionalmente cachea y enriquece.
     */
    private WebResourceResponse loadFromHandler(
            String path,
            PathHandler handler,
            WebResourceRequest request,
            long startTime,
            boolean cacheable,
            String integrityHash) {

        long fileSize = 0;

        WebResourceResponse response = handler.handle(path, request);
        if (response == null) return null;

        boolean isByteArrayStream = response.getData() instanceof ByteArrayInputStream;
        if (isByteArrayStream) {
            try {
                fileSize = response.getData().available();
            } catch (IOException ignored) {
                // available() puede fallar en algunos streams
            }
        }

        // Cachear si es cacheable y tenemos ByteArrayInputStream
        if (cacheable && isByteArrayStream) {
            responseCache.cacheResponseWithHeaders(path, response, integrityHash);
        }

        // Enriquecer headers HTTP
        response = enricher.enrichResponse(response, path, integrityHash);

        long elapsed = SystemClock.elapsedRealtime() - startTime;
        metricsCollector.recordAssetLoad(path, elapsed, false, fileSize);

        return response;
    }
}