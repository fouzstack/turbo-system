package com.webvirt.loader;

import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
* PathHandler que sirve archivos desde el sistema de archivos.
*
* Útil para:
* - Archivos descargados en cache
* - Archivos generados dinámicamente
* - Recursos fuera del APK
*
* @since 1.0.0
*/
public class FilePathHandler implements PathHandler {
	
	private final File baseDirectory;
	
	/**
	* @param baseDirectory Directorio base para resolver paths relativos
	*/
	public FilePathHandler(File baseDirectory) {
		this.baseDirectory = baseDirectory;
	}
	
	@Override
	public WebResourceResponse handle(String path, WebResourceRequest request) {
		try {
			// Sanitizar path
			String relativePath = path.startsWith("/") ? path.substring(1) : path;
			
			// Prevenir path traversal
			if (relativePath.contains("..")) return null;
			
			File file = new File(baseDirectory, relativePath);
			
			// Verificar que el archivo está dentro del directorio base
			if (!file.getCanonicalPath().startsWith(baseDirectory.getCanonicalPath())) {
				return null;
			}
			
			if (!file.exists() || !file.isFile()) return null;
			
			FileInputStream fis = new FileInputStream(file);
			byte[] data = readFully(fis);
			fis.close();
			
			String mimeType = MimeTypeResolver.resolve(path);
			String encoding = getEncodingForMimeType(mimeType);
			
			Map<String, String> headers = new HashMap<>();
			headers.put("Content-Type", mimeType + (encoding != null ? "; charset=" + encoding : ""));
			headers.put("Content-Length", String.valueOf(data.length));
			
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
			String relativePath = path.startsWith("/") ? path.substring(1) : path;
			if (relativePath.contains("..")) return null;
			
			File file = new File(baseDirectory, relativePath);
			if (!file.getCanonicalPath().startsWith(baseDirectory.getCanonicalPath())) {
				return null;
			}
			if (!file.exists() || !file.isFile()) return null;
			
			long fileSize = file.length();
			long[] range = RangeParser.parse(rangeHeader, fileSize);
			if (range == null) {
				Map<String, String> headers = new HashMap<>();
				headers.put("Content-Range", "bytes */" + fileSize);
				return new WebResourceResponse("text/plain", "UTF-8", 416,
				"Range Not Satisfiable", headers, new ByteArrayInputStream(new byte[0]));
			}
			
			long start = range[0], end = range[1];
			int contentLength = (int) (end - start + 1);
			
			FileInputStream fis = new FileInputStream(file);
			fis.skip(start);
			byte[] partial = new byte[contentLength];
			fis.read(partial);
			fis.close();
			
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