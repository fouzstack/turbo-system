package com.webvirt.loader;

import com.webvirt.extensions.manifest.AssetManifest;

/**
* Estrategia de precarga basada en AssetManifest.
*
* Determina si un asset es crítico para precarga.
* Interfaz funcional simple — sin duplicar lógica del PrecacheManager.
*
* @since 3.6.0
*/
@FunctionalInterface
public interface ManifestBasedPrecacheStrategy {
	
	/**
	* Determina si un path lógico corresponde a un asset crítico.
	*
	* @param path Path lógico del asset (ej: "/assets/main.js")
	* @return true si el asset debe precargarse con prioridad
	*/
	boolean isCritical(String path);
	
	// ==================== IMPLEMENTACIONES POR DEFECTO ====================
	
	/**
	* Estrategia por defecto para SPAs.
	* Considera críticos: HTML principal, bundles (index-, main-, vendor-, runtime-),
	* CSS/JS global (excluye chunks/ y pages/).
	*/
	static ManifestBasedPrecacheStrategy spaDefault(AssetManifest manifest) {
		return path -> {
			if (path == null) return false;
			
			// HTML principal
			if (path.equals("/") || path.equals("/index.html")) return true;
			
			// Bundles principales
			if (path.contains("/assets/index-") ||
			path.contains("/assets/vendor-") ||
			path.contains("/assets/main-") ||
			path.contains("/assets/runtime-")) return true;
			
			// CSS global (no chunks, no pages)
			if (path.endsWith(".css") &&
			!path.contains("/chunks/") &&
			!path.contains("/pages/")) {
				String filename = path.substring(path.lastIndexOf('/') + 1);
				if (filename.startsWith("index") ||
				filename.startsWith("main") ||
				filename.startsWith("app") ||
				filename.startsWith("global")) return true;
			}
			
			// JS global (no chunks, no pages)
			if (path.endsWith(".js") &&
			!path.contains("/chunks/") &&
			!path.contains("/pages/")) {
				String filename = path.substring(path.lastIndexOf('/') + 1);
				if (filename.startsWith("index") ||
				filename.startsWith("main") ||
				filename.startsWith("app")) return true;
			}
			
			return false;
		};
	}
	
	/**
	* Estrategia que marca todo como crítico (precarga agresiva).
	*/
	static ManifestBasedPrecacheStrategy all() {
		return path -> true;
	}
	
	/**
	* Estrategia que solo marca paths explícitos.
	*/
	static ManifestBasedPrecacheStrategy explicit(String... paths) {
		java.util.Set<String> set = new java.util.HashSet<>(java.util.Arrays.asList(paths));
		return set::contains;
	}
	
	/**
	* Estrategia vacía (nada es crítico).
	*/
	static ManifestBasedPrecacheStrategy none() {
		return path -> false;
	}
}