package com.webvirt.loader;

import java.util.Map;

/**
 * Enriquece respuestas con cabeceras de seguridad HTTP.
 * CSP, X-Content-Type-Options, X-Frame-Options, X-XSS-Protection, CORS.
 * 
 * @since 3.6.0
 */
public class SecurityHeaderEnricher {

    private final String cspPolicy;

    /**
     * @param cspPolicy Política Content-Security-Policy completa
     */
    public SecurityHeaderEnricher(String cspPolicy) {
        this.cspPolicy = cspPolicy;
    }

    /**
     * Añade cabeceras de seguridad a un mapa de headers existente.
     * No sobrescribe cabeceras ya presentes.
     *
     * @param headers Mapa de headers a enriquecer (modificado in-place)
     */
    public void enrich(Map<String, String> headers) {
        if (!headers.containsKey("Content-Security-Policy")) {
            headers.put("Content-Security-Policy", cspPolicy);
        }
        if (!headers.containsKey("X-Content-Type-Options")) {
            headers.put("X-Content-Type-Options", "nosniff");
        }
        if (!headers.containsKey("X-Frame-Options")) {
            headers.put("X-Frame-Options", "DENY");
        }
        if (!headers.containsKey("X-XSS-Protection")) {
            headers.put("X-XSS-Protection", "1; mode=block");
        }
        if (!headers.containsKey("Access-Control-Allow-Origin")) {
            headers.put("Access-Control-Allow-Origin", "*");
        }
    }
}