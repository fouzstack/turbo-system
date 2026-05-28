package com.webvirt.loader;

import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import com.webvirt.loader.handlers.RangeCapableHandler;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maneja peticiones HTTP Range (Partial Content).
 * Usa polimorfismo vía RangeCapableHandler en lugar de instanceof.
 * 
 * @since 3.6.0
 */
public class RangeRequestHandler {

    private final RequestRouter requestRouter;

    public RangeRequestHandler(RequestRouter requestRouter) {
        this.requestRouter = requestRouter;
    }

    /**
     * Maneja una petición Range para un path.
     *
     * @param path        Path del recurso
     * @param rangeHeader Valor de la cabecera Range (ej: "bytes=0-1023")
     * @param request     Petición original
     * @return Respuesta 206 Partial Content, 416, o null si no se puede servir
     */
    public WebResourceResponse handleRangeRequest(
            String path, String rangeHeader, WebResourceRequest request) {

        PathHandler handler = requestRouter.resolve(path);
        if (handler == null) return null;

        try {
            // Delegación polimórfica a handlers que soportan Range nativamente
            if (handler instanceof RangeCapableHandler) {
                return ((RangeCapableHandler) handler).handleRange(path, rangeHeader, request);
            }

            // Fallback genérico: cargar recurso completo y extraer el rango
            return handleRangeFallback(path, rangeHeader, handler, request);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fallback para handlers sin soporte nativo de Range.
     * Carga el recurso completo y extrae el rango solicitado.
     */
    private WebResourceResponse handleRangeFallback(
            String path, String rangeHeader, PathHandler handler, WebResourceRequest request) {

        WebResourceResponse fullResponse = handler.handle(path, request);
        if (fullResponse == null || fullResponse.getData() == null) return null;

        byte[] data;
        try {
            data = StreamUtils.readFully(fullResponse.getData());
        } catch (OutOfMemoryError oom) {
            // Demasiado grande para cargar en memoria → devolver recurso completo
            return fullResponse;
        } catch (Exception e) {
            return null;
        }

        long fileSize = data.length;
        long[] range = RangeParser.parse(rangeHeader, fileSize);
        if (range == null) {
            return create416Response(fileSize);
        }

        long start = range[0];
        long end = range[1];
        int contentLength = (int) (end - start + 1);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
        headers.put("Content-Length", String.valueOf(contentLength));
        headers.put("Accept-Ranges", "bytes");
        headers.put("Content-Type", fullResponse.getMimeType());

        byte[] partialData = Arrays.copyOfRange(data, (int) start, (int) end + 1);

        return new WebResourceResponse(
            fullResponse.getMimeType(),
            fullResponse.getEncoding(),
            206,
            "Partial Content",
            headers,
            new ByteArrayInputStream(partialData)
        );
    }

    /**
     * Crea una respuesta 416 Range Not Satisfiable.
     */
    public static WebResourceResponse create416Response(long fileSize) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Range", "bytes */" + fileSize);
        headers.put("Content-Length", "0");

        return new WebResourceResponse(
            "text/plain",
            "UTF-8",
            416,
            "Range Not Satisfiable",
            headers,
            new ByteArrayInputStream(new byte[0])
        );
    }
}