package com.webvirt.loader;

import com.webvirt.extensions.cache.CachePolicy;

import java.util.Map;

/**
 * Enriquece respuestas con cabeceras de caché HTTP.
 * Cache-Control y Vary.
 * 
 * @since 3.6.0
 */
public class CacheHeaderEnricher {

    private final CachePolicy cachePolicy;

    /**
     * @param cachePolicy Política de caché para generar headers Cache-Control
     */
    public CacheHeaderEnricher(CachePolicy cachePolicy) {
        this.cachePolicy = cachePolicy;
    }

    /**
     * Añade cabeceras de caché basadas en el MIME type.
     * No sobrescribe cabeceras ya presentes.
     *
     * @param headers  Mapa de headers a enriquecer (modificado in-place)
     * @param mimeType MIME type del recurso (puede ser null)
     */
    public void enrich(Map<String, String> headers, String mimeType) {
        if (mimeType != null && !headers.containsKey("Cache-Control")) {
            headers.put("Cache-Control", cachePolicy.getCacheControlHeader(mimeType));
        }
        if (!headers.containsKey("Vary")) {
            headers.put("Vary", "Accept-Encoding");
        }
    }
}