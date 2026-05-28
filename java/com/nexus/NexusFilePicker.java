package com.nexus;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;

/**
* Universal File Picker for Nexus.
* <p>
* No requiere permisos de Android.
* Usa Storage Access Framework (SAF) para acceder a archivos.
* <p>
* Características:
* <ul>
*   <li>No necesita permisos en el manifiesto</li>
*   <li>Soporta cualquier tipo MIME</li>
*   <li>API basada en CompletableFuture para mejor manejo asíncrono</li>
*   <li>Lectura de archivos como texto o bytes</li>
*   <li>Compatible con Android API 21+</li>
* </ul>
*
* Uso típico:
* <pre>{@code
	* NexusFilePicker picker = new NexusFilePicker(activity);
	*
	* // Seleccionar archivo JSON
	* picker.pickFile("application/json")
	*     .thenAccept(uri -> {
		*         String content = picker.readFileContent(uri);
		*         // Procesar contenido...
	*     })
	*     .exceptionally(error -> {
		*         // Manejar error...
		*         return null;
	*     });
* }</pre>
*
* @author FouzStack
* @version 1.0.1
*/
public class NexusFilePicker {
	
	private final FragmentActivity activity;
	private final ActivityResultLauncher<String[]> filePickerLauncher;
	private CompletableFuture<Uri> pendingResult;
	
	/**
	* Crea una nueva instancia del FilePicker.
	*
	* @param activity La actividad que hospeda el picker (debe ser FragmentActivity)
	*/
	public NexusFilePicker(@NonNull FragmentActivity activity) {
		this.activity = activity;
		this.filePickerLauncher = activity.registerForActivityResult(
		new ActivityResultContracts.OpenDocument(),
		uri -> {
			if (pendingResult != null) {
				if (uri != null) {
					NexusLog.d("FilePicker", "Archivo seleccionado: " + uri);
					pendingResult.complete(uri);
					} else {
					NexusLog.d("FilePicker", "Usuario canceló la selección");
					pendingResult.completeExceptionally(
					new NexusException("USER_CANCELLED", "Usuario canceló la selección de archivo")
					);
				}
				pendingResult = null;
			}
		}
		);
	}
	
	
	public CompletableFuture<Uri> pickFile(@NonNull String... mimeTypes) {
		if (pendingResult != null) {
			NexusLog.w("FilePicker", "Ya hay un file picker en progreso");
			pendingResult.completeExceptionally(
			new NexusException("ALREADY_OPEN", "Ya hay un selector de archivos abierto")
			);
		}
		
		pendingResult = new CompletableFuture<>();
		String[] types = mimeTypes.length > 0 ? mimeTypes : new String[]{"*/*"};
		
		try {
			NexusLog.d("FilePicker", "Abriendo selector para: " + String.join(", ", types));
			filePickerLauncher.launch(types);
			} catch (Exception e) {
			NexusLog.e("FilePicker", "Error al abrir selector", e);
			pendingResult.completeExceptionally(
			new NexusException("LAUNCH_ERROR", "Error al abrir selector: " + e.getMessage())
			);
			pendingResult = null;
		}
		
		return pendingResult;
	}
	
	/**
	* Lee el contenido de un archivo como texto (String).
	* <p>
	* Útil para archivos JSON, XML, TXT, CSV, etc.
	*
	* @param uri URI del archivo a leer
	* @return Contenido del archivo como String
	* @throws Exception si hay error al leer el archivo
	*/
	@NonNull
	public String readFileContent(@NonNull Uri uri) throws Exception {
		StringBuilder stringBuilder = new StringBuilder();
		
		try (InputStream inputStream = activity.getContentResolver().openInputStream(uri);
		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
			
			String line;
			boolean firstLine = true;
			while ((line = reader.readLine()) != null) {
				if (!firstLine) {
					stringBuilder.append("\n");
				}
				stringBuilder.append(line);
				firstLine = false;
			}
		}
		
		String content = stringBuilder.toString();
		NexusLog.d("FilePicker", "Archivo leído: " + content.length() + " caracteres");
		return content;
	}
	
	/**
	* Lee el contenido de un archivo como bytes.
	* <p>
	* Útil para archivos binarios, imágenes, PDFs, etc.
	* <p>
	* Compatible con Android API 21+ (no usa readAllBytes() que requiere API 33+).
	*
	* @param uri URI del archivo a leer
	* @return Contenido del archivo como array de bytes
	* @throws Exception si hay error al leer el archivo
	*/
	@NonNull
	public byte[] readFileBytes(@NonNull Uri uri) throws Exception {
		try (InputStream inputStream = activity.getContentResolver().openInputStream(uri)) {
			if (inputStream == null) {
				throw new NexusException("READ_ERROR", "No se pudo abrir el archivo");
			}
			
			// Método compatible con API 21+ en lugar de readAllBytes()
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			byte[] data = new byte[8192]; // Buffer de 8KB
			int bytesRead;
			
			while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
				buffer.write(data, 0, bytesRead);
			}
			
			buffer.flush();
			byte[] bytes = buffer.toByteArray();
			
			NexusLog.d("FilePicker", "Archivo leído: " + bytes.length + " bytes");
			return bytes;
		}
	}
	
	/**
	* Obtiene el nombre del archivo desde su URI.
	*
	* @param uri URI del archivo
	* @return Nombre del archivo o "unknown" si no se puede determinar
	*/
	@NonNull
	public String getFileName(@NonNull Uri uri) {
		// Intentar obtener el display name del content resolver
		String fileName = null;
		try {
			android.database.Cursor cursor = activity.getContentResolver().query(
			uri, null, null, null, null);
			if (cursor != null) {
				int nameIndex = cursor.getColumnIndex(
				android.provider.OpenableColumns.DISPLAY_NAME);
				if (nameIndex >= 0 && cursor.moveToFirst()) {
					fileName = cursor.getString(nameIndex);
				}
				cursor.close();
			}
			} catch (Exception e) {
			NexusLog.w("FilePicker", "Error al obtener nombre del archivo" + e);
		}
		
		// Fallback: extraer de la ruta
		if (fileName == null) {
			try {
				String path = uri.getPath();
				if (path != null) {
					// Decodificar la URL para obtener el nombre real
					String decodedPath = android.net.Uri.decode(path);
					int lastSlash = decodedPath.lastIndexOf('/');
					if (lastSlash >= 0 && lastSlash < decodedPath.length() - 1) {
						fileName = decodedPath.substring(lastSlash + 1);
					}
				}
			} catch (Exception ignored) {}
		}
		
		return fileName != null ? fileName : "unknown";
	}
	
	/**
	* Obtiene el tamaño del archivo en bytes.
	*
	* @param uri URI del archivo
	* @return Tamaño en bytes, o -1 si no se puede determinar
	*/
	public long getFileSize(@NonNull Uri uri) {
		try {
			android.database.Cursor cursor = activity.getContentResolver().query(
			uri, null, null, null, null);
			if (cursor != null) {
				int sizeIndex = cursor.getColumnIndex(
				android.provider.OpenableColumns.SIZE);
				if (sizeIndex >= 0 && cursor.moveToFirst()) {
					long size = cursor.getLong(sizeIndex);
					cursor.close();
					return size;
				}
				cursor.close();
			}
			} catch (Exception e) {
			NexusLog.w("FilePicker", "Error al obtener tamaño del archivo" + e);
		}
		return -1;
	}
}