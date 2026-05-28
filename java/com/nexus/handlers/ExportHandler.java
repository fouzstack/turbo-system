package com.nexus.handlers;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.nexus.utils.JsonNormalizer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.Consumer;

public class ExportHandler {
    private static final String TAG = "ExportHandler";
    
    private final Context context;
    private final Handler mainHandler;
    private final JsonNormalizer jsonNormalizer;
    private Consumer<String> toastCallback;
    
    public ExportHandler(Context context, Handler mainHandler) {
        this.context = context;
        this.mainHandler = mainHandler;
        this.jsonNormalizer = new JsonNormalizer(context);
    }
    
    public void setToastCallback(Consumer<String> toastCallback) {
        this.toastCallback = toastCallback;
    }
    
    public void exportJsonToFile(String jsonString) {
        Log.d(TAG, "exportJsonToFile() iniciado");
        
        if (jsonString == null || jsonString.trim().isEmpty()) {
            sendToast("Error: JSON vacío recibido");
            return;
        }
        
        // Validar usando JsonNormalizer
        String validatedJson = jsonNormalizer.prepareForExport(jsonString);
        if (validatedJson == null) {
            Log.e(TAG, "JSON inválido para exportar");
            
            // Diagnóstico detallado
            String diagnosis = jsonNormalizer.diagnoseJson(jsonString);
            Log.e(TAG, "Diagnóstico: " + diagnosis);
            
            sendToast("Error: JSON inválido para exportar");
            return;
        }
        
        Log.d(TAG, "JSON validado correctamente");
        
        // Verificar si tiene formato de metadatos (informativo)
        boolean hasMetadata = jsonNormalizer.hasMetadataFormat(validatedJson);
        Log.d(TAG, "¿Formato con metadatos?: " + hasMetadata);
        
        new Thread(() -> {
            try {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        .format(new Date());
                String fileName = "inventario_" + timestamp + ".json";
                
                boolean saved = saveJsonToDocuments(fileName, validatedJson);
                
                if (!saved) {
                    saved = saveToInternalStorage(fileName, validatedJson);
                    if (saved) {
                        sendToast("✅ Inventario exportado (almacenamiento interno)");
                    }
                } else {
                    String successMessage = "✅ Inventario exportado exitosamente\n" +
                            "Ubicación: Documents/fouzstack/" + fileName;
                    sendToast(successMessage);
                }
                
                if (!saved) {
                    sendToast("Error: No se pudo guardar el archivo");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error en exportJsonToFile", e);
                sendToast("Error procesando JSON: " + e.getMessage());
            }
        }).start();
    }
    
    private boolean saveJsonToDocuments(String fileName, String jsonContent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return saveWithMediaStoreModern(fileName, jsonContent);
            } else {
                return saveWithLegacyMethod(fileName, jsonContent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error guardando JSON en Documents", e);
            return false;
        }
    }
    
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private boolean saveWithMediaStoreModern(String fileName, String jsonContent) {
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/fouzstack");
            
            Uri uri = context.getContentResolver().insert(
                    MediaStore.Files.getContentUri("external"),
                    contentValues
            );
            
            if (uri == null) return false;
            
            try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                if (os == null) return false;
                os.write(jsonContent.getBytes(StandardCharsets.UTF_8));
                os.flush();
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error con MediaStore moderno", e);
            return false;
        }
    }
    
    private boolean saveWithLegacyMethod(String fileName, String jsonContent) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(context,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "Sin permisos de escritura en versiones antiguas");
                    return false;
                }
            }
            
            File docsDir = android.os.Environment
                    .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS);
            File inventoryDir = new File(docsDir, "fouzstack");
            
            if (!inventoryDir.exists() && !inventoryDir.mkdirs()) {
                return false;
            }
            
            File jsonFile = new File(inventoryDir, fileName);
            
            try (FileOutputStream fos = new FileOutputStream(jsonFile);
                 OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                 BufferedWriter writer = new BufferedWriter(osw)) {
                
                writer.write(jsonContent);
                writer.flush();
                
                // Escanear el archivo para que aparezca en el sistema
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaScanIntent.setData(Uri.fromFile(jsonFile));
                context.sendBroadcast(mediaScanIntent);
                
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error con método legacy", e);
            return false;
        }
    }
    
    private boolean saveToInternalStorage(String fileName, String jsonContent) {
        try {
            File internalDir = new File(context.getFilesDir(), "inventarios_export");
            if (!internalDir.exists() && !internalDir.mkdirs()) {
                return false;
            }
            
            File jsonFile = new File(internalDir, fileName);
            try (java.io.FileWriter writer = new java.io.FileWriter(jsonFile)) {
                writer.write(jsonContent);
                writer.flush();
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error guardando en almacenamiento interno", e);
            return false;
        }
    }
    
    private void sendToast(String message) {
        if (toastCallback != null) {
            mainHandler.post(() -> toastCallback.accept(message));
        }
    }
}