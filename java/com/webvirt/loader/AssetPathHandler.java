package com.webvirt.loader;

import android.content.Context;
import android.content.res.AssetManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
* PathHandler que sirve assets desde la carpeta assets del APK.
*
* @since 1.0.0
*/
public class AssetPathHandler implements PathHandler {
	
	private final AssetManager assetManager;
	private final String basePath;
	private final boolean usePrefix;
	
	/**
	* Constructor con Context.
	*
	* @param context  Contexto de aplicación
	* @param basePath Ruta base en assets (ej: "www" o "www/")
	*/
	public AssetPathHandler(Context context, String basePath) {
		this.assetManager = context.getApplicationContext().getAssets();
		this.basePath = normalizeBasePath(basePath);
		this.usePrefix = this.basePath.isEmpty() || this.basePath.endsWith("/");
	}
	
	/**
	* Constructor con AssetManager directamente.
	*
	* @param assetManager AssetManager de la aplicación
	* @param basePath     Ruta base en assets (ej: "www" o "www/")
	*/
	public AssetPathHandler(AssetManager assetManager, String basePath) {
		this.assetManager = assetManager;
		this.basePath = normalizeBasePath(basePath);
		this.usePrefix = this.basePath.isEmpty() || this.basePath.endsWith("/");
	}
	
	/**
	* Constructor con flag explícito de prefijo.
	*
	* @param context   Contexto de aplicación
	* @param basePath  Ruta base en assets
	* @param usePrefix true si basePath es un prefijo de directorio
	*/
	public AssetPathHandler(Context context, String basePath, boolean usePrefix) {
		this.assetManager = context.getApplicationContext().getAssets();
		this.basePath = normalizeBasePath(basePath);
		this.usePrefix = usePrefix;
	}
	
	private static String normalizeBasePath(String path) {
		if (path == null) return "";
		path = path.trim();
		// Eliminar slash inicial
		if (path.startsWith("/")) path = path.substring(1);
		return path;
	}
	
	@Override
	public WebResourceResponse handle(String path, WebResourceRequest request) {
		try {
			String assetPath = resolveAssetPath(path);
			if (assetPath == null) return null;
			
			InputStream is = assetManager.open(assetPath);
			if (is == null) return null;
			
			String mimeType = MimeTypeResolver.resolve(path);
			String encoding = getEncodingForMimeType(mimeType);
			
			byte[] data = readFully(is);
			is.close();
			
			Map<String, String> headers = new HashMap<>();
			headers.put("Content-Type", mimeType + (encoding != null ? "; charset=" + encoding : ""));
			
			return new WebResourceResponse(
			mimeType, encoding, 200, "OK", headers,
			new ByteArrayInputStream(data));
			
			} catch (IOException e) {
			return null;
		}
	}
	
	/**
	* Maneja peticiones Range (Partial Content).
	*/
	public WebResourceResponse handleRange(String path, String rangeHeader, WebResourceRequest request) {
		try {
			String assetPath = resolveAssetPath(path);
			if (assetPath == null) return null;
			
			InputStream is = assetManager.open(assetPath);
			byte[] data = readFully(is);
			is.close();
			
			long fileSize = data.length;
			long[] range = RangeParser.parse(rangeHeader, fileSize);
			if (range == null) {
				Map<String, String> headers = new HashMap<>();
				headers.put("Content-Range", "bytes */" + fileSize);
				return new WebResourceResponse("text/plain", "UTF-8", 416,
				"Range Not Satisfiable", headers, new ByteArrayInputStream(new byte[0]));
			}
			
			long start = range[0], end = range[1];
			int contentLength = (int) (end - start + 1);
			byte[] partial = new byte[contentLength];
			System.arraycopy(data, (int) start, partial, 0, contentLength);
			
			String mimeType = MimeTypeResolver.resolve(path);
			Map<String, String> headers = new HashMap<>();
			headers.put("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
			headers.put("Content-Length", String.valueOf(contentLength));
			headers.put("Content-Type", mimeType);
			
			return new WebResourceResponse(mimeType, "UTF-8", 206,
			"Partial Content", headers, new ByteArrayInputStream(partial));
			
			} catch (IOException e) {
			return null;
		}
	}
	
	private String resolveAssetPath(String requestPath) {
		if (requestPath == null) return null;
		
		// Quitar slash inicial para assets
		String relativePath = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;
		
		if (usePrefix) {
			// Si basePath está vacío, usar el path completo
			if (basePath.isEmpty()) {
				return relativePath;
			}
			// Si basePath termina con /, concatenar
			if (basePath.endsWith("/")) {
				return basePath + relativePath;
			}
			// Si basePath no termina con /, es un archivo específico
			return basePath;
			} else {
			// Archivo específico: devolver siempre el mismo
			return basePath;
		}
	}
	
	private static byte[] readFully(InputStream is) throws IOException {
		java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = is.read(buffer)) != -1) {
			baos.write(buffer, 0, read);
		}
		return baos.toByteArray();
	}
	
	private static String getEncodingForMimeType(String mimeType) {
		if (mimeType == null) return null;
		if (mimeType.startsWith("text/")) return "UTF-8";
		if (mimeType.equals("application/javascript")) return "UTF-8";
		if (mimeType.equals("application/json")) return "UTF-8";
		if (mimeType.equals("application/xml")) return "UTF-8";
		if (mimeType.equals("image/svg+xml")) return "UTF-8";
		return null;
	}
}