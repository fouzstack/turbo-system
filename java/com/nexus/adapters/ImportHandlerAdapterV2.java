package com.nexus.adapters;

import android.net.Uri;
import android.content.Context;
import androidx.annotation.NonNull;

import com.nexus.utils.JsonNormalizer;
import com.nexus.utils.LoggingUtil;
import com.nexus.NexusException;
import com.nexus.FileHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
* Adapter para importación que usa el FilePicker universal de Nexus.
*
* Flujo:
* 1. React → Nexus.call('import', { action: 'pickFile' })
* 2. Java → Abre FilePicker universal
* 3. Usuario selecciona archivo
* 4. Java → Lee, valida y devuelve el JSON en la misma llamada
*
* Acciones soportadas:
* - "data": Importa desde un string JSON proporcionado directamente
* - "pickFile": Abre el file picker y devuelve el contenido del archivo
* - "getFileData": Devuelve el JSON cacheado de una selección anterior
*/
public class ImportHandlerAdapterV2 extends FileHandler {
	
	private final Context context;
	private final JsonNormalizer jsonNormalizer;
	private String cachedJsonData;
	
	public ImportHandlerAdapterV2(@NonNull Context context) {
		this.context = context;
		this.jsonNormalizer = new JsonNormalizer(context);
		LoggingUtil.logToFile(context, "ImportHandlerAdapterV2: Creado");
	}
	
	@NonNull
	@Override
	public String getName() {
		return "import";
	}
	
	@NonNull
	@Override
	public Object handle(@NonNull Map<String, Object> params) throws Exception {
		String action = (String) params.getOrDefault("action", "data");
		LoggingUtil.logToFile(context, "ImportHandlerV2.handle: action=" + action);
		
		Map<String, Object> result = new HashMap<>();
		
		switch (action) {
			case "data": {
				// Importar desde string JSON proporcionado directamente
				String data = (String) params.get("data");
				if (data == null || data.isEmpty()) {
					throw new NexusException("INVALID_PARAMS", "Se requiere el parámetro 'data'");
				}
				
				validateJson(data);
				
				result.put("status", "ok");
				result.put("jsonData", data);
				result.put("length", data.length());
				break;
			}
			
			case "pickFile": {
				// Usar el FilePicker universal
				LoggingUtil.logToFile(context, "ImportHandlerV2: Abriendo FilePicker universal...");
				
				try {
					Uri fileUri = pickFile("application/json");
					
					LoggingUtil.logToFile(context, "ImportHandlerV2: Archivo seleccionado: " + fileUri);
					
					// Leer el contenido del archivo
					String content = readFileContent(fileUri);
					
					LoggingUtil.logToFile(context, "ImportHandlerV2: Contenido leído, longitud=" +
					(content != null ? content.length() : 0));
					
					// Validar el JSON
					String validated = validateAndReturn(content, fileUri);
					cachedJsonData = validated;
					
					String fileName = getFileName(fileUri);
					
					result.put("status", "ok");
					result.put("message", "Archivo seleccionado exitosamente");
					result.put("jsonData", validated);
					result.put("length", validated.length());
					result.put("fileName", fileName);
					
					LoggingUtil.logToFile(context, "ImportHandlerV2: JSON validado, fileName=" + fileName);
					
					} catch (NexusException e) {
					// Si el usuario cancela, no es un error
					if ("USER_CANCELLED".equals(e.getCode())) {
						result.put("status", "cancelled");
						result.put("message", "Usuario canceló la selección");
						} else {
						throw e;
					}
				}
				break;
			}
			
			case "getFileData": {
				// Devolver datos cacheados de una selección anterior
				LoggingUtil.logToFile(context, "ImportHandlerV2: getFileData llamado. Cache=" +
				(cachedJsonData != null ? "presente" : "vacio"));
				
				if (cachedJsonData == null) {
					throw new NexusException("NO_DATA",
					"No hay datos de archivo disponibles. Selecciona un archivo primero con 'pickFile'.");
				}
				
				result.put("status", "ok");
				result.put("jsonData", cachedJsonData);
				result.put("length", cachedJsonData.length());
				
				// Limpiar cache después de leer
				cachedJsonData = null;
				break;
			}
			
			default:
			throw new NexusException("UNKNOWN_ACTION",
			"Acción desconocida: " + action + ". Válidas: data, pickFile, getFileData");
		}
		
		return result;
	}
	
	/**
	* Valida que un string sea JSON válido.
	*/
	private void validateJson(String jsonString) throws NexusException {
		if (!jsonNormalizer.isValidJson(jsonString)) {
			String diagnosis = jsonNormalizer.diagnoseJson(jsonString);
			LoggingUtil.logToFile(context, "ImportHandlerV2: JSON inválido. Diagnóstico: " + diagnosis);
			throw new NexusException("INVALID_JSON",
			"JSON inválido: " + diagnosis);
		}
	}
	
	/**
	* Valida el JSON y lo devuelve normalizado.
	* Maneja las excepciones de readAndValidateJson internamente.
	*/
	private String validateAndReturn(String jsonContent, Uri uri) throws NexusException {
		try {
			// Intentar validar con el JsonNormalizer
			String validated = jsonNormalizer.readAndValidateJson(uri);
			
			if (validated == null || validated.trim().isEmpty()) {
				throw new NexusException("INVALID_JSON",
				"El archivo no contiene un JSON válido");
			}
			
			return validated;
			
			} catch (IOException e) {
			LoggingUtil.logToFile(context, "ImportHandlerV2: Error IOException: " + e.getMessage());
			throw new NexusException("FILE_READ_ERROR",
			"Error al leer el archivo: " + e.getMessage());
			
			} catch (Exception e) {
			LoggingUtil.logToFile(context, "ImportHandlerV2: Error validando: " + e.getMessage());
			
			// Si falla readAndValidateJson, intentar validación manual
			if (jsonNormalizer.isValidJson(jsonContent)) {
				LoggingUtil.logToFile(context, "ImportHandlerV2: JSON válido (validación manual)");
				return jsonContent;
			}
			
			String diagnosis = jsonNormalizer.diagnoseJson(jsonContent);
			throw new NexusException("INVALID_JSON",
			"El archivo no contiene un JSON válido: " + diagnosis);
		}
	}
	
	/**
	* Obtiene el nombre del archivo desde su URI.
	*/
	private String getFileName(Uri uri) {
		try {
			String path = uri.getPath();
			if (path != null) {
				String decodedPath = android.net.Uri.decode(path);
				int lastSlash = decodedPath.lastIndexOf('/');
				if (lastSlash >= 0 && lastSlash < decodedPath.length() - 1) {
					return decodedPath.substring(lastSlash + 1);
				}
			}
			} catch (Exception e) {
			LoggingUtil.logToFile(context, "ImportHandlerV2: Error obteniendo fileName: " + e.getMessage());
		}
		return "archivo.json";
	}
}