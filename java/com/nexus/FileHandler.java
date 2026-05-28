package com.nexus;

import android.net.Uri;
import androidx.annotation.NonNull;
import com.nexus.Nexus;
import com.nexus.NexusFilePicker;
import com.nexus.NexusHandler;
import com.nexus.NexusException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Handler base para operaciones que requieren selección de archivos.
 * Proporciona métodos convenientes para usar el FilePicker.
 */
public abstract class FileHandler implements NexusHandler {
    
    private Nexus nexus;
    
    public void setNexus(@NonNull Nexus nexus) {
        this.nexus = nexus;
    }
    
    /**
     * Abre el file picker y espera la selección del usuario.
     * 
     * @param mimeTypes Tipos MIME permitidos
     * @return URI del archivo seleccionado
     * @throws NexusException si el usuario cancela o hay error
     */
    protected Uri pickFile(@NonNull String... mimeTypes) throws NexusException {
        NexusFilePicker picker = nexus.getFilePicker();
        if (picker == null) {
            throw new NexusException("NO_FILE_PICKER", 
                "FilePicker no inicializado. Llama a Nexus.withFilePicker(activity)");
        }
        
        try {
            CompletableFuture<Uri> future = picker.pickFile(mimeTypes);
            // Esperar con timeout de 5 minutos
            return future.get(5, TimeUnit.MINUTES);
        }  catch (Exception e) {
            throw new NexusException("FILE_PICKER_ERROR", 
                "Error al seleccionar archivo: " + e.getMessage());
        }
    }
    
    /**
     * Lee el contenido de un archivo como texto.
     */
    protected String readFileContent(@NonNull Uri uri) throws NexusException {
        try {
            NexusFilePicker picker = nexus.getFilePicker();
            if (picker == null) {
                throw new NexusException("NO_FILE_PICKER", "FilePicker no inicializado");
            }
            return picker.readFileContent(uri);
        } catch (NexusException e) {
            throw e;
        } catch (Exception e) {
            throw new NexusException("FILE_READ_ERROR", 
                "Error al leer archivo: " + e.getMessage());
        }
    }
}