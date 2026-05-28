package com.webvirt.loader;

import android.content.Context;
import android.content.res.AssetManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.File;

/**
* Interfaz para handlers que sirven recursos bajo un prefijo de path.
*
* @since 1.0.0
*/
public interface PathHandler {
	
	/**
	* Maneja una petición para un path específico.
	*
	* @param path    Path normalizado y sanitizado
	* @param request Petición original del WebView (puede ser null en precache)
	* @return WebResourceResponse con el contenido, o null si no puede servir
	*/
	WebResourceResponse handle(String path, WebResourceRequest request);
	
	// ==================== FACTORY METHODS ====================
	
	/**
	* Crea un PathHandler que sirve archivos desde la carpeta assets del APK.
	*
	* @param context        Contexto de aplicación
	* @param assetSubfolder Subcarpeta dentro de assets (ej: "www"), puede ser "" o null
	* @return AssetPathHandler configurado
	*/
	static PathHandler fromAssets(Context context, String assetSubfolder) {
		String basePath = (assetSubfolder != null && !assetSubfolder.isEmpty())
		? assetSubfolder
		: "";
		return new AssetPathHandler(context, basePath);
	}
	
	/**
	* Crea un PathHandler que sirve archivos desde la carpeta assets del APK.
	*
	* @param assetManager   AssetManager de la aplicación
	* @param assetSubfolder Subcarpeta dentro de assets (ej: "www"), puede ser "" o null
	* @return AssetPathHandler configurado
	*/
	static PathHandler fromAssets(AssetManager assetManager, String assetSubfolder) {
		String basePath = (assetSubfolder != null && !assetSubfolder.isEmpty())
		? assetSubfolder
		: "";
		return new AssetPathHandler(assetManager, basePath);
	}
	
	/**
	* Crea un PathHandler que sirve archivos desde un directorio del sistema.
	*
	* @param directory Directorio base (ej: context.getCacheDir())
	* @return FilePathHandler configurado
	*/
	static PathHandler fromFile(File directory) {
		return new FilePathHandler(directory);
	}
	
	/**
	* Crea un PathHandler que sirve archivos desde un directorio del sistema.
	*
	* @param directoryPath Ruta del directorio base
	* @return FilePathHandler configurado
	*/
	static PathHandler fromFile(String directoryPath) {
		return new FilePathHandler(new File(directoryPath));
	}
}