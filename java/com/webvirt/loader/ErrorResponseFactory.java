package com.webvirt.loader;

import static com.webvirt.WebVirtResponses.escapeHtml;
import static com.webvirt.WebVirtVersion.FULL;

import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generación de respuestas de error HTML.
 * Aísla la UI de error del runtime principal.
 * 
 * @since 3.6.0
 */
public final class ErrorResponseFactory {

    private ErrorResponseFactory() {
        throw new AssertionError("No instances");
    }

    /**
     * Crea una respuesta de error HTML estilizada.
     *
     * @param code    Código HTTP de error (ej: 404, 500)
     * @param message Mensaje descriptivo para el usuario
     * @return WebResourceResponse con HTML de error
     */
    public static WebResourceResponse createErrorResponse(int code, String message) {
        String html = buildErrorHtml(code, message);
        byte[] data = html.getBytes(StandardCharsets.UTF_8);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "text/html; charset=utf-8");
        headers.put("Content-Length", String.valueOf(data.length));

        return new WebResourceResponse(
            "text/html",
            "UTF-8",
            code,
            "Error",
            headers,
            new ByteArrayInputStream(data)
        );
    }

    private static String buildErrorHtml(int code, String message) {
        return "<!DOCTYPE html>\n"
            + "<html lang=\"en\">\n"
            + "<head>\n"
            + "<meta charset=\"UTF-8\">\n"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
            + "<title>Error " + code + "</title>\n"
            + "<style>\n"
            + "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;"
            + "display:flex;align-items:center;justify-content:center;height:100vh;margin:0;"
            + "background:#1a1a2e;color:#eee}\n"
            + ".error-container{text-align:center;padding:2rem}\n"
            + ".error-code{font-size:6rem;font-weight:700;color:#e74c3c;line-height:1;margin:0}\n"
            + ".error-message{font-size:1rem;color:#999;margin-top:1rem}\n"
            + ".error-footer{margin-top:2rem;padding-top:1rem;border-top:1px solid #333;"
            + "font-size:.8rem;color:#555}\n"
            + "</style>\n"
            + "</head>\n"
            + "<body>\n"
            + "<div class=\"error-container\">\n"
            + "<h1 class=\"error-code\">" + code + "</h1>\n"
            + "<p class=\"error-message\">" + escapeHtml(message) + "</p>\n"
            + "<div class=\"error-footer\">" + FULL + "</div>\n"
            + "</div>\n"
            + "</body>\n"
            + "</html>";
    }
}