package com.webvirt.loader;

import android.webkit.WebResourceResponse;

/**
 * Normaliza metadatos de respuestas HTTP.
 * Status code, reason phrase, MIME type, encoding.
 * 
 * @since 3.6.0
 */
public final class ResponseNormalizer {

    private ResponseNormalizer() {
        throw new AssertionError("No instances");
    }

    /**
     * Normaliza los metadatos de una WebResourceResponse.
     *
     * @param response Respuesta original del handler
     * @return NormalizedMetadata con valores garantizados no-nulos y válidos
     */
    public static NormalizedMetadata normalize(WebResourceResponse response) {
        int statusCode = response.getStatusCode();
        if (statusCode < 100) statusCode = 200;

        String reasonPhrase = response.getReasonPhrase();
        if (reasonPhrase == null || reasonPhrase.isEmpty()) {
            reasonPhrase = getDefaultReasonPhrase(statusCode);
        }

        String mimeType = response.getMimeType();
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = "text/plain";
        }

        String encoding = response.getEncoding();
        if (encoding == null || encoding.isEmpty()) {
            encoding = "UTF-8";
        }

        return new NormalizedMetadata(statusCode, reasonPhrase, mimeType, encoding);
    }

    /**
     * Obtiene la frase de razón HTTP estándar para un código de estado.
     */
    public static String getDefaultReasonPhrase(int statusCode) {
        switch (statusCode) {
            case 200: return "OK";
            case 206: return "Partial Content";
            case 304: return "Not Modified";
            case 400: return "Bad Request";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 416: return "Range Not Satisfiable";
            case 500: return "Internal Server Error";
            default:  return "OK";
        }
    }

    /**
     * Metadatos normalizados de una respuesta HTTP.
     */
    public static class NormalizedMetadata {
        public final int statusCode;
        public final String reasonPhrase;
        public final String mimeType;
        public final String encoding;

        NormalizedMetadata(int statusCode, String reasonPhrase, String mimeType, String encoding) {
            this.statusCode = statusCode;
            this.reasonPhrase = reasonPhrase;
            this.mimeType = mimeType;
            this.encoding = encoding;
        }
    }
}