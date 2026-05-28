package com.webvirt.loader.policies;

/**
 * Política por defecto para Single Page Applications.
 * 
 * Considera navegación:
 * - "/" (raíz)
 * - "/index.html" (entry point explícito)
 * - Cualquier .html fuera de /assets/ (rutas de la SPA)
 * 
 * @since 3.6.0
 */
public class SpaNavigationPolicy implements NavigationRequestPolicy {

    @Override
    public boolean isNavigationRequest(String path) {
        if (path.equals("/") || path.equals("/index.html")) {
            return true;
        }
        if (path.endsWith(".html") && !path.contains("/assets/")) {
            return true;
        }
        return false;
    }
}