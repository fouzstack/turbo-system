package com.nexus.handlers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.webkit.WebView;

import com.nexus.utils.JsonNormalizer;

import java.io.IOException;
import java.util.function.Consumer;

public class ImportHandler {
    private static final String TAG = "ImportHandler";
    public static final int FILE_PICKER_REQUEST_CODE = 1001;
    
    private final Context context;
    private final Handler mainHandler;
    private final JsonNormalizer jsonNormalizer;
    private WebView webView;
    private Consumer<String> toastCallback;
    private Consumer<String> onJsonImportedCallback;
    private Runnable fileSelectorLauncher;
    
    public ImportHandler(Context context, Handler mainHandler) {
        this.context = context;
        this.mainHandler = mainHandler;
        this.jsonNormalizer = new JsonNormalizer(context);
    }
    
    public void setWebView(WebView webView) {
        this.webView = webView;
    }
    
    public void setToastCallback(Consumer<String> toastCallback) {
        this.toastCallback = toastCallback;
    }
    
    public void setOnJsonImportedCallback(Consumer<String> callback) {
        this.onJsonImportedCallback = callback;
    }
    
    public void setFileSelectorLauncher(Runnable launcher) {
        this.fileSelectorLauncher = launcher;
    }
    
    /**
     * Abre el selector de archivos para importar JSON
     */
    public void openFileSelector() {
        Log.d(TAG, "openFileSelector() llamado");
        
        if (fileSelectorLauncher == null) {
            Log.e(TAG, "FileSelectorLauncher no configurado");
            sendToast("Error: Selector de archivos no configurado");
            return;
        }
        
        if (!(context instanceof Activity)) {
            Log.e(TAG, "Context no es una Activity");
            sendToast("Error: Contexto inválido");
            return;
        }
        
        mainHandler.post(() -> {
            try {
                fileSelectorLauncher.run();
                Log.d(TAG, "Selector de archivos lanzado");
            } catch (Exception e) {
                Log.e(TAG, "Error abriendo selector de archivos", e);
                sendToast("Error abriendo selector: " + e.getMessage());
            }
        });
    }
    
    /**
     * Importa JSON directamente desde un string
     */
    public void importJsonString(String jsonString) {
        Log.d(TAG, "importJsonString() iniciado, longitud: " + 
                (jsonString != null ? jsonString.length() : 0));
        
        if (webView == null) {
            Log.e(TAG, "WebView es null");
            sendToast("Error: WebView no disponible");
            return;
        }
        
        // Usar JsonNormalizer para validar
        if (!jsonNormalizer.isValidJson(jsonString)) {
            Log.e(TAG, "JSON inválido recibido");
            
            // Diagnóstico detallado
            String diagnosis = jsonNormalizer.diagnoseJson(jsonString);
            Log.e(TAG, "Diagnóstico: " + diagnosis);
            
            sendToast("Error: JSON inválido o corrupto");
            return;
        }
        
        mainHandler.post(() -> performJsonImport(jsonString));
    }
    
    private void performJsonImport(String jsonString) {
        try {
            // Verificar si tiene formato de metadatos
            boolean hasMetadata = jsonNormalizer.hasMetadataFormat(jsonString);
            Log.d(TAG, "¿Tiene formato de metadatos?: " + hasMetadata);
            
            String jsCode;
            
            if (hasMetadata) {
                // Usar método inteligente que extrae las tablas
                Log.d(TAG, "Usando generateSmartImportCode para extraer tablas");
                jsCode = jsonNormalizer.generateSmartImportCode(jsonString);
            } else {
                // Método normal para JSON directo
                Log.d(TAG, "Usando generateImportJavascriptCode para JSON directo");
                jsCode = jsonNormalizer.generateImportJavascriptCode(jsonString);
            }
            
            Log.d(TAG, "Código JS generado: " + jsCode.substring(0, Math.min(100, jsCode.length())) + "...");
            
            // Intentar con evaluateJavascript (método principal)
            webView.evaluateJavascript(jsCode, result -> {
                Log.d(TAG, "Resultado de evaluateJavascript: " + result);
                
                if (result == null || result.contains("error") || result.contains("Error") || "null".equals(result)) {
                    Log.w(TAG, "Método evaluateJavascript no tuvo éxito, intentando debug");
                    tryDebugImport(jsonString);
                } else {
                    Log.d(TAG, "✅ JSON importado exitosamente vía evaluateJavascript");
                    sendToast("✅ Inventario importado correctamente");
                    notifyImportSuccess();
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error en performJsonImport", e);
            tryDebugImport(jsonString);
        }
    }
    
    private void tryDebugImport(String jsonString) {
        try {
            Log.d(TAG, "Intentando método debug...");
            
            // Usar el método debug que incluye logs detallados
            String debugCode = jsonNormalizer.generateDebugImportCode(jsonString);
            webView.loadUrl(debugCode);
            
            sendToast("✅ Inventario importado (modo debug)");
            notifyImportSuccess();
            
        } catch (Exception e) {
            Log.e(TAG, "Error en método debug", e);
            
            // Último intento con método alternativo directo
            tryLastResortImport(jsonString);
        }
    }
    
    private void tryLastResortImport(String jsonString) {
        try {
            Log.d(TAG, "Intentando último recurso...");
            
            // Intentar con loadUrl directo usando el método smart
            String smartCode = jsonNormalizer.generateSmartImportCode(jsonString);
            webView.loadUrl(smartCode);
            
            sendToast("Inventario importado (método alternativo)");
            notifyImportSuccess();
            
        } catch (Exception e) {
            Log.e(TAG, "Error crítico en todos los métodos de importación", e);
            sendToast("Error crítico importando inventario");
        }
    }
    
    /**
     * Procesa el archivo seleccionado del file picker usando JsonNormalizer
     */
    public void processSelectedFile(Uri fileUri) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Procesando archivo con JsonNormalizer: " + fileUri);
                
                // Usar JsonNormalizer para leer y validar el JSON
                String jsonContent = jsonNormalizer.readAndValidateJson(fileUri);
                
                if (jsonContent == null || jsonContent.trim().isEmpty()) {
                    sendToast("Archivo vacío o JSON inválido");
                    return;
                }
                
                Log.d(TAG, "JSON leído correctamente, longitud: " + jsonContent.length());
                
                // Diagnóstico del JSON leído
                String diagnosis = jsonNormalizer.diagnoseJson(jsonContent);
                Log.d(TAG, "Diagnóstico del archivo:\n" + diagnosis);
                
                // Importar el JSON
                importJsonString(jsonContent);
                
            } catch (IOException e) {
                Log.e(TAG, "Error leyendo archivo con JsonNormalizer", e);
                sendToast("Error leyendo archivo: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Error procesando archivo", e);
                sendToast("Error procesando archivo: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Maneja el resultado del file picker
     */
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, String.format("handleActivityResult: requestCode=%d, resultCode=%d", 
                requestCode, resultCode));
        
        if (requestCode != FILE_PICKER_REQUEST_CODE) {
            return false;
        }
        
        if (resultCode != Activity.RESULT_OK) {
            Log.d(TAG, "Usuario canceló la selección");
            sendToast("Selección de archivo cancelada");
            return true;
        }
        
        if (data == null || data.getData() == null) {
            Log.e(TAG, "No se recibió URI del archivo");
            sendToast("Error: No se pudo obtener el archivo seleccionado");
            return true;
        }
        
        Uri fileUri = data.getData();
        Log.d(TAG, "Archivo seleccionado: " + fileUri);
        
        processSelectedFile(fileUri);
        return true;
    }
    
    private void notifyImportSuccess() {
        if (onJsonImportedCallback != null) {
            onJsonImportedCallback.accept("success");
        }
    }
    
    private void sendToast(String message) {
        if (toastCallback != null) {
            mainHandler.post(() -> toastCallback.accept(message));
        }
    }
    
    public int getFilePickerRequestCode() {
        return FILE_PICKER_REQUEST_CODE;
    }
	
	// En ImportHandler.java, agrega:
	public Context getContext() {
		return context;
	}
}