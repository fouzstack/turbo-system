package com.webvirt.loader;

import android.webkit.WebResourceRequest;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Utilidades HTTP mínimas.
 * 
 * @since 3.6.0
 */
public final class HttpUtils {

    private HttpUtils() {
        throw new AssertionError("No instances");
    }

    private static final ThreadLocal<SimpleDateFormat> HTTP_DATE_FORMAT =
        new ThreadLocal<SimpleDateFormat>() {
            @Override
            protected SimpleDateFormat initialValue() {
                SimpleDateFormat sdf = new SimpleDateFormat(
                    "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
                return sdf;
            }
        };

    /**
     * Obtiene una cabecera de una petición WebResourceRequest de forma case-insensitive.
     *
     * @param request    La petición WebView
     * @param headerName Nombre de la cabecera (case-insensitive)
     * @return Valor de la cabecera, o null si no está presente
     */
    public static String getRequestHeader(WebResourceRequest request, String headerName) {
        if (request == null || headerName == null) return null;

        Map<String, String> headers = request.getRequestHeaders();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(headerName)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Obtiene el formateador de fechas HTTP thread-safe.
     * Formato: "EEE, dd MMM yyyy HH:mm:ss z" en GMT.
     *
     * @return SimpleDateFormat thread-local para formato de fecha HTTP
     */
    public static SimpleDateFormat getHttpDateFormat() {
        return HTTP_DATE_FORMAT.get();
    }
}