package com.nexus.handlers;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.nexus.builtin.pdf.JsonToPdfHelper;
import com.nexus.builtin.pdf.PdfLogCallback;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Handler dedicado exclusivamente a la exportación de PDF
 * Responsabilidad única: generar PDFs desde JSON
 */
public class PdfExportHandler {

    private static final String TAG = "PdfExportHandler";

    private final Context context;
    private final Handler mainHandler;
    private final ExecutorService executor;

    private Consumer<String> toastCallback;

    public PdfExportHandler(Context context, Handler mainHandler) {
        this.context = context;
        this.mainHandler = mainHandler;
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void setToastCallback(Consumer<String> toastCallback) {
        this.toastCallback = toastCallback;
    }

    /**
     * Método principal para exportar JSON a PDF
     */
    public void export(String jsonString) {
        Log.d(TAG, "export() iniciado");

        if (jsonString == null || jsonString.trim().isEmpty()) {
            sendToast("Error: JSON vacío");
            return;
        }

        executor.execute(() -> {
            try {
                PdfLogCallback pdfLogCallback = message -> Log.d(TAG, message);

                JsonToPdfHelper.generarPdfDesdeJson(
                        jsonString,
                        context,
                        mainHandler,
                        pdfLogCallback
                );

                sendToast("✅ PDF generado correctamente");

            } catch (Exception e) {
                Log.e(TAG, "Error generando PDF", e);
                sendToast("Error generando PDF: " + e.getMessage());
            }
        });
    }

    /**
     * Limpieza de recursos (importante si escalas la app)
     */
    public void cleanup() {
        try {
            executor.shutdown();
        } catch (Exception e) {
            Log.e(TAG, "Error cerrando executor", e);
        }
    }

    private void sendToast(String message) {
        if (toastCallback != null) {
            mainHandler.post(() -> toastCallback.accept(message));
        }
    }
}