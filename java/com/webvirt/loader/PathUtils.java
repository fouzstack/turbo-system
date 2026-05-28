package com.webvirt.loader;

import java.net.URLDecoder;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
* Operaciones seguras de path.
*
* Clase utilitaria sin estado, sin dependencias.
* Accesible desde cualquier clase del paquete.
*
* @since 3.6.0
*/
public final class PathUtils {
	
	private static final Set<String> CACHEABLE_EXTENSIONS;
	
	static {
		Set<String> set = new HashSet<>();
		set.add("css");
		set.add("js");
		set.add("mjs");
		set.add("json");
		set.add("map");
		set.add("woff");
		set.add("woff2");
		set.add("ttf");
		set.add("otf");
		set.add("png");
		set.add("svg");
		set.add("ico");
		set.add("webmanifest");
		set.add("html");
		set.add("htm");
		CACHEABLE_EXTENSIONS = Collections.unmodifiableSet(set);
	}
	
	private PathUtils() {
		throw new AssertionError("No instances");
	}
	
	/**
	* Extrae y sanitiza un path seguro desde una URI.
	*
	* @param path Raw path desde la URI
	* @return Path sanitizado o null si es inseguro
	*/
	public static String extractSafePath(String path) {
		if (path == null || path.isEmpty()) return "/";
		try {
			path = URLDecoder.decode(path, "UTF-8");
			path = sanitizePath(path);
			return path;
			} catch (Exception e) {
			return null;
		}
	}
	
	/**
	* Sanitiza un path eliminando path traversal y normalizando.
	*
	* @param path Path crudo
	* @return Path sanitizado o null si contiene '..'
	*/
	public static String sanitizePath(String path) {
		if (path == null) return null;
		path = path.replace('\\', '/');
		while (path.contains("//")) {
			path = path.replace("//", "/");
		}
		if (path.contains("..")) return null;
		if (!path.startsWith("/")) path = "/" + path;
		return path;
	}
	
	/**
	* Obtiene la extensión de un archivo en minúsculas.
	*
	* @param path Ruta del archivo
	* @return Extensión en minúsculas, o null si no tiene
	*/
	public static String getExtension(String path) {
		if (path == null) return null;
		int lastDot = path.lastIndexOf('.');
		return lastDot > 0 ? path.substring(lastDot + 1).toLowerCase() : null;
	}
	
	/**
	* Determina si un path corresponde al HTML principal de una SPA.
	*
	* @param path Path a evaluar
	* @return true si es el HTML principal
	*/
	public static boolean isMainHtml(String path) {
		return path.equals("/") ||
		path.equals("/index.html") ||
		path.equals("/index.htm") ||
		(path.endsWith(".html") && !path.contains("/assets/"));
	}
	
	/**
	* Determina si un path es cacheable estáticamente.
	*
	* @param path Ruta del archivo
	* @return true si la extensión es cacheable
	*/
	public static boolean isCacheableStatic(String path) {
		String ext = getExtension(path);
		return ext != null && CACHEABLE_EXTENSIONS.contains(ext);
	}
	
	/**
	* Obtiene el nombre del archivo (último segmento del path).
	*
	* @param path Ruta completa
	* @return Nombre del archivo
	*/
	public static String getFileName(String path) {
		if (path == null) return "";
		int lastSlash = path.lastIndexOf('/');
		return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
	}
}