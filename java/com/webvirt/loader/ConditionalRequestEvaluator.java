package com.webvirt.loader;

import java.text.ParseException;
import java.util.Date;

/**
 * Evalúa peticiones condicionales HTTP.
 * Separa la semántica del protocolo (If-Modified-Since, If-None-Match)
 * del almacenamiento en caché.
 * 
 * @since 3.6.0
 */
public final class ConditionalRequestEvaluator {

    private ConditionalRequestEvaluator() {
        throw new AssertionError("No instances");
    }

    /**
     * Evalúa si un recurso cacheado no ha sido modificado según
     * las cabeceras condicionales de la petición.
     *
     * @param entry           Entrada de caché con lastModified y etag
     * @param ifNoneMatch     Cabecera If-None-Match (puede ser null)
     * @param ifModifiedSince Cabecera If-Modified-Since (puede ser null)
     * @return true si el recurso NO fue modificado → debe devolverse 304
     */
    public static boolean isNotModified(
            CacheManager.CacheEntry entry,
            String ifNoneMatch,
            String ifModifiedSince) {

        // Validación por fecha de modificación
        if (ifModifiedSince != null) {
            try {
                Date ifModifiedDate = HttpUtils.getHttpDateFormat().parse(ifModifiedSince);
                if (entry.lastModified <= ifModifiedDate.getTime()) {
                    return true;
                }
            } catch (ParseException ignored) {
                // Fecha malformada → ignorar esta validación
            }
        }

        // Validación por ETag
        if (ifNoneMatch != null && ifNoneMatch.equals(entry.etag)) {
            return true;
        }

        return false;
    }
}