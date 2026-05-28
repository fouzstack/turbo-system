package com.webvirt.loader;

import java.util.Map;

/**
 * Enriquece respuestas con cabeceras de integridad (Subresource Integrity).
 * 
 * @since 3.6.0
 */
public class IntegrityHeaderEnricher {

    /**
     * Añade cabecera Content-Integrity si hay hash SRI disponible.
     * No sobrescribe si ya existe.
     *
     * @param headers       Mapa de headers a enriquecer (modificado in-place)
     * @param integrityHash Hash de integridad (puede ser null)
     */
    public void enrich(Map<String, String> headers, String integrityHash) {
        if (integrityHash != null && !headers.containsKey("Content-Integrity")) {
            headers.put("Content-Integrity", integrityHash);
        }
    }
}