package com.nexus.adapters;

import androidx.annotation.NonNull;

import com.nexus.handlers.PdfExportHandler;
import com.nexus.NexusHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter that exposes PdfExportHandler through Nexus.
 */
public class PdfExportHandlerAdapter implements NexusHandler {

    private final PdfExportHandler pdfExportHandler;

    public PdfExportHandlerAdapter(@NonNull PdfExportHandler pdfExportHandler) {
        this.pdfExportHandler = pdfExportHandler;
    }

    @NonNull
    @Override
    public String getName() {
        return "pdf";
    }

    @NonNull
    @Override
    public Object handle(@NonNull Map<String, Object> params) throws Exception {
        String jsonString = (String) params.get("data");
        if (jsonString == null || jsonString.isEmpty()) {
            throw new Exception("The 'data' parameter is required to generate PDF");
        }

        pdfExportHandler.export(jsonString);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("message", "PDF generated successfully");
        return result;
    }

    @Override
    public long getTimeoutMs() {
        return 60_000; // PDF may take longer
    }

    @Override
    public void onDestroy() {
        pdfExportHandler.cleanup();
    }
}