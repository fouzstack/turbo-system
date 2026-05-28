package com.webvirt.loader.policies;

/**
 * Política para sitios estáticos multi-página (MPA).
 * Cualquier .html o .htm es considerado navegación.
 * 
 * @since 3.6.0
 */
public class StaticSiteNavigationPolicy implements NavigationRequestPolicy {

    @Override
    public boolean isNavigationRequest(String path) {
        if (path.equals("/")) {
            return true;
        }
        if (path.endsWith(".html") || path.endsWith(".htm")) {
            return true;
        }
        return false;
    }
}