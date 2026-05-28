package com.nexus.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class JsonNormalizer {
	private static final String TAG = "JsonNormalizer";
	
	private final Context context;
	
	public JsonNormalizer(Context context) {
		this.context = context;
	}
	
	/**
	* Valida JSON sin modificar la estructura original
	* @param jsonString String JSON a validar
	* @return El mismo JSON si es válido, null si es inválido
	*/
	public String validateJson(String jsonString) {
		if (jsonString == null) {
			Log.e(TAG, "JSON string es null");
			return null;
		}
		
		String trimmed = jsonString.trim();
		if (trimmed.isEmpty()) {
			Log.e(TAG, "JSON string está vacío");
			return null;
		}
		
		try {
			new JSONObject(trimmed);
			return trimmed;
			} catch (JSONException e1) {
			try {
				new JSONArray(trimmed);
				return trimmed;
				} catch (JSONException e2) {
				Log.e(TAG, "JSON inválido - no es objeto ni array");
				return null;
			}
		}
	}
	
	/**
	* Prepara JSON para exportación (solo valida, mantiene estructura original)
	* @param jsonString JSON a exportar
	* @return JSON validado o null si es inválido
	*/
	public String prepareForExport(String jsonString) {
		return validateJson(jsonString);
	}
	
	/**
	* Lee JSON desde un Uri y lo valida
	* @param uri Uri del archivo JSON
	* @return Contenido del archivo JSON validado
	* @throws IOException Si hay error al leer el archivo
	*/
	public String readAndValidateJson(Uri uri) throws IOException {
		if (uri == null) {
			throw new IllegalArgumentException("Uri no puede ser null");
		}
		
		try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
			if (inputStream == null) {
				throw new IOException("No se pudo abrir InputStream para Uri: " + uri);
			}
			
			return readAndValidateJson(inputStream);
		}
	}
	
	/**
	* Lee JSON desde InputStream y lo valida
	* @param inputStream InputStream con el contenido JSON
	* @return Contenido JSON validado
	* @throws IOException Si hay error al leer el stream
	*/
	public String readAndValidateJson(InputStream inputStream) throws IOException {
		if (inputStream == null) {
			throw new IllegalArgumentException("InputStream no puede ser null");
		}
		
		try (BufferedReader reader = new BufferedReader(
		new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			
			StringBuilder jsonBuilder = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				jsonBuilder.append(line);
			}
			
			if (jsonBuilder.length() == 0) {
				Log.e(TAG, "InputStream vacío");
				return null;
			}
			
			return validateJson(jsonBuilder.toString());
		}
	}
	
	/**
	* Genera código JavaScript para importación
	* Envía el JSON como OBJETO JavaScript (no como string)
	* @param jsonString JSON a enviar al componente React
	* @return Código JavaScript para ejecutar en WebView
	*/
	public String generateImportJavascriptCode(String jsonString) {
		String validatedJson = validateJson(jsonString);
		if (validatedJson == null) {
			Log.e(TAG, "JSON inválido para generar código JS");
			return "javascript:console.error('JSON inválido recibido')";
		}
		
		try {
			// Intentar parsear como objeto
			JSONObject jsonObject = new JSONObject(validatedJson);
			// Enviar como OBJETO JavaScript, no como string
			return "javascript:useJsonData(" + jsonObject.toString() + ")";
			
			} catch (JSONException e1) {
			try {
				// Intentar como array
				JSONArray jsonArray = new JSONArray(validatedJson);
				// Enviar como ARRAY JavaScript, no como string
				return "javascript:useJsonData(" + jsonArray.toString() + ")";
				
				} catch (JSONException e2) {
				Log.e(TAG, "Error al generar código JS");
				return "javascript:console.error('Error procesando JSON: formato inválido')";
			}
		}
	}
	
	/**
	* Versión con logging detallado para debugging
	* @param jsonString JSON a enviar
	* @return Código JavaScript con logs de depuración
	*/
	public String generateDebugImportCode(String jsonString) {
		String validatedJson = validateJson(jsonString);
		if (validatedJson == null) {
			return "javascript:console.error('❌ JSON inválido para importación')";
		}
		
		try {
			JSONObject jsonObject = new JSONObject(validatedJson);
			return "javascript:(function(){" +
			"try{" +
			"  console.log('📦 JSON recibido:', " + jsonObject.toString() + ");" +
			"  console.log('📊 Tipo de dato:', typeof " + jsonObject.toString() + ");" +
			"  if(typeof window.useJsonData === 'function'){" +
			"    window.useJsonData(" + jsonObject.toString() + ");" +
			"    console.log('✅ useJsonData ejecutado correctamente');" +
			"  }else{" +
			"    console.error('❌ window.useJsonData no está definido');" +
			"    console.log('🔍 window disponible:', Object.keys(window));" +
			"  }" +
			"}catch(e){" +
			"  console.error('❌ Error en importación:', e.message);" +
			"  console.error('📚 Stack:', e.stack);" +
			"}" +
			"})()";
			
			} catch (JSONException e) {
			try {
				JSONArray jsonArray = new JSONArray(validatedJson);
				return "javascript:(function(){" +
				"try{" +
				"  console.log('📦 JSON Array recibido:', " + jsonArray.toString() + ");" +
				"  console.log('📊 Longitud del array:', " + jsonArray.length() + ");" +
				"  if(typeof window.useJsonData === 'function'){" +
				"    window.useJsonData(" + jsonArray.toString() + ");" +
				"    console.log('✅ useJsonData ejecutado correctamente');" +
				"  }else{" +
				"    console.error('❌ window.useJsonData no está definido');" +
				"  }" +
				"}catch(e){" +
				"  console.error('❌ Error en importación:', e.message);" +
				"}" +
				"})()";
				
				} catch (JSONException e2) {
				return "javascript:console.error('❌ Error procesando JSON: formato inválido')";
			}
		}
	}
	
	/**
	* Verifica si un string es JSON válido
	* @param jsonString String a validar
	* @return true si es JSON válido, false en caso contrario
	*/
	public boolean isValidJson(String jsonString) {
		return validateJson(jsonString) != null;
	}
	
	/**
	* Verifica si el JSON tiene formato de exportación con metadatos
	* @param jsonString JSON a verificar
	* @return true si tiene estructura de metadatos (version, exportedAt, tables)
	*/
	public boolean hasMetadataFormat(String jsonString) {
		String validatedJson = validateJson(jsonString);
		if (validatedJson == null) {
			return false;
		}
		
		try {
			JSONObject json = new JSONObject(validatedJson);
			return json.has("version") && json.has("exportedAt") && json.has("tables");
			} catch (JSONException e) {
			return false;
		}
	}
	
	/**
	* Extrae solo las tablas del formato con metadatos
	* @param jsonString JSON con metadatos
	* @return JSON de las tablas o el original si no tiene metadatos
	*/
	public String extractTablesFromMetadata(String jsonString) {
		String validatedJson = validateJson(jsonString);
		if (validatedJson == null) {
			return null;
		}
		
		try {
			JSONObject json = new JSONObject(validatedJson);
			if (json.has("tables")) {
				return json.getJSONObject("tables").toString();
			}
			return validatedJson;
			} catch (JSONException e) {
			Log.e(TAG, "Error extrayendo tablas de metadatos: " + e.getMessage());
			return validatedJson;
		}
	}
	
	/**
	* Genera código JavaScript con extracción automática de metadatos
	* @param jsonString JSON (puede tener o no metadatos)
	* @return Código JavaScript para importación
	*/
	public String generateSmartImportCode(String jsonString) {
		String validatedJson = validateJson(jsonString);
		if (validatedJson == null) {
			return "javascript:console.error('JSON inválido')";
		}
		
		String tablesJson;
		if (hasMetadataFormat(validatedJson)) {
			tablesJson = extractTablesFromMetadata(validatedJson);
			Log.d(TAG, "Detectado formato con metadatos, extrayendo tablas");
			} else {
			tablesJson = validatedJson;
			Log.d(TAG, "Formato directo de tablas");
		}
		
		try {
			JSONObject jsonObject = new JSONObject(tablesJson);
			return "javascript:useJsonData(" + jsonObject.toString() + ")";
			} catch (JSONException e) {
			try {
				JSONArray jsonArray = new JSONArray(tablesJson);
				return "javascript:useJsonData(" + jsonArray.toString() + ")";
				} catch (JSONException e2) {
				return "javascript:console.error('Error procesando JSON')";
			}
		}
	}
	
	/**
	* Diagnóstico detallado del JSON
	* @param jsonString JSON a diagnosticar
	* @return String con información de diagnóstico
	*/
	public String diagnoseJson(String jsonString) {
		if (jsonString == null) {
			return "❌ JSON: null";
		}
		
		StringBuilder diagnosis = new StringBuilder();
		diagnosis.append("📏 Longitud: ").append(jsonString.length()).append("\n");
		diagnosis.append("✅ Válido: ").append(isValidJson(jsonString) ? "Sí" : "No").append("\n");
		diagnosis.append("📦 Formato metadatos: ").append(hasMetadataFormat(jsonString) ? "Sí" : "No").append("\n");
		
		String trimmed = jsonString.trim();
		if (trimmed.length() > 100) {
			diagnosis.append("🔍 Preview: ").append(trimmed.substring(0, 100)).append("...");
			} else {
			diagnosis.append("🔍 Preview: ").append(trimmed);
		}
		
		if (isValidJson(jsonString)) {
			try {
				JSONObject json = new JSONObject(trimmed);
				diagnosis.append("\n📑 Tablas encontradas: ");
				if (json.has("tables")) {
					JSONObject tables = json.getJSONObject("tables");
					diagnosis.append(tables.length());
					diagnosis.append(" (");
					JSONArray keys = tables.names();
					for (int i = 0; i < keys.length(); i++) {
						if (i > 0) diagnosis.append(", ");
						diagnosis.append(keys.getString(i));
					}
					diagnosis.append(")");
					} else {
					diagnosis.append(json.length());
					diagnosis.append(" (");
					JSONArray keys = json.names();
					for (int i = 0; i < keys.length(); i++) {
						if (i > 0) diagnosis.append(", ");
						diagnosis.append(keys.getString(i));
					}
					diagnosis.append(")");
				}
				} catch (JSONException e) {
				// Ignorar errores de diagnóstico
			}
		}
		
		return diagnosis.toString();
	}
}