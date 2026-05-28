package com.webvirt.loader.policies;

/**
 * Estrategia por defecto optimizada para SPAs con Vite/Webpack/Rollup.
 * 
 * Precarga:
 * - HTML principal ("/", "/index.html")
 * - Bundles principales (/assets/index-*, /assets/vendor-*, etc.)
 * - CSS global (index.css, main.css, app.css, global.css)
 * - JS principal (index.js, main.js, app.js)
 * 
 * Excluye chunks lazy y assets de páginas secundarias.
 * 
 * @since 3.6.0
 */
public class DefaultPrecacheStrategy implements PrecacheStrategy {

    @Override
    public boolean isCriticalAsset(String path) {
        if (path == null) return false;

        // HTML principal
        if (path.equals("/") || path.equals("/index.html")) {
            return true;
        }

        // Bundles principales de Vite/Webpack/Rollup
        if (path.contains("/assets/index-") ||
            path.contains("/assets/vendor-") ||
            path.contains("/assets/main-") ||
            path.contains("/assets/runtime-")) {
            return true;
        }

        // CSS global (no chunks, no pages)
        if (path.endsWith(".css") && !path.contains("/chunks/") && !path.contains("/pages/")) {
            String filename = path.substring(path.lastIndexOf('/') + 1);
            if (filename.startsWith("index") ||
                filename.startsWith("main") ||
                filename.startsWith("app") ||
                filename.startsWith("global")) {
                return true;
            }
        }

        // JS principal (no chunks, no pages)
        if (path.endsWith(".js") && !path.contains("/chunks/") && !path.contains("/pages/")) {
            String filename = path.substring(path.lastIndexOf('/') + 1);
            if (filename.startsWith("index") ||
                filename.startsWith("main") ||
                filename.startsWith("app")) {
                return true;
            }
        }

        return false;
    }
}