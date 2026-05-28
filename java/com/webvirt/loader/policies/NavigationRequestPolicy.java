package com.webvirt.loader.policies;

/**
 * Política para determinar si un path es una petición de navegación
 * que debe devolver HTML principal.
 * 
 * Permite diferentes estrategias:
 * - SPA clásica (todo va a index.html)
 * - SSR híbrido
 * - Multi-página estática
 * - Micro-frontends
 * 
 * @since 3.6.0
 */
public interface NavigationRequestPolicy {

    /**
     * Determina si un path es una petición de navegación.
     *
     * @param path Path normalizado (debe empezar con "/")
     * @return true si debe tratarse como navegación HTML
     */
    boolean isNavigationRequest(String path);
}