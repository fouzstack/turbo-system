package com.webvirt.loader;

import android.webkit.WebResourceResponse;

import com.webvirt.extensions.cache.CachePolicy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ResponseEnricher v3.6.0 — Compone enrichers especializados.
 * Delega en:
 * - SecurityHeaderEnricher  → CSP, X-Content-Type-Options, X-Frame-Options, CORS
 * - CacheHeaderEnricher     → Cache-Control, Vary
 * - IntegrityHeaderEnricher → Content-Integrity (SRI)
 * - ResponseNormalizer      → Status code, reason phrase, MIME, encoding
 * 
 * @since 3.6.0
 */
public class ResponseEnricher {

    private final SecurityHeaderEnricher securityEnricher;
    private final CacheHeaderEnricher cacheEnricher;
    private final IntegrityHeaderEnricher integrityEnricher;
    private final boolean mergeHeaders;

    public ResponseEnricher(
            String cspPolicy,
            CachePolicy cachePolicy,
            boolean mergeHeaders) {
        this.securityEnricher = new SecurityHeaderEnricher(cspPolicy);
        this.cacheEnricher = new CacheHeaderEnricher(cachePolicy);
        this.integrityEnricher = new IntegrityHeaderEnricher();
        this.mergeHeaders = mergeHeaders;
    }

    /**
     * Construye headers precalculados para almacenar en caché.
     * Incluye Content-Type, ETag, y todas las cabeceras de seguridad/caché/integridad.
     *
     * @param path          Path del recurso
     * @param mimeType      MIME type del recurso
     * @param etag          ETag generado
     * @param integrityHash Hash SRI (puede ser null)
     * @return Mapa de headers listo para serializar en caché
     */
    public Map<String, String> buildResponseHeaders(
            String path, String mimeType, String etag, String integrityHash) {

        Map<String, String> headers = new LinkedHashMap<>();

        headers.put("Content-Type", mimeType != null ? mimeType : "application/octet-stream");
        headers.put("ETag", etag);

        securityEnricher.enrich(headers);
        cacheEnricher.enrich(headers, mimeType);
        integrityEnricher.enrich(headers, integrityHash);

        return headers;
    }

    /**
     * Enriquece una respuesta existente con cabeceras HTTP completas.
     * Respeta los headers originales si mergeHeaders está activo.
     *
     * @param original      Respuesta original del handler
     * @param path          Path del recurso
     * @param integrityHash Hash SRI (puede ser null)
     * @return Respuesta enriquecida con todas las cabeceras
     */
    public WebResourceResponse enrichResponse(
            WebResourceResponse original, String path, String integrityHash) {

        if (original == null) return null;

        // Construir headers enriquecidos
        Map<String, String> enrichedHeaders = new LinkedHashMap<>();
        Map<String, String> originalHeaders = original.getResponseHeaders();

        if (mergeHeaders && originalHeaders != null) {
            enrichedHeaders.putAll(originalHeaders);
        }

        securityEnricher.enrich(enrichedHeaders);
        cacheEnricher.enrich(enrichedHeaders, original.getMimeType());
        integrityEnricher.enrich(enrichedHeaders, integrityHash);

        // Normalizar metadatos HTTP
        ResponseNormalizer.NormalizedMetadata meta = ResponseNormalizer.normalize(original);

        return new WebResourceResponse(
            meta.mimeType,
            meta.encoding,
            meta.statusCode,
            meta.reasonPhrase,
            enrichedHeaders,
            original.getData()
        );
    }
}