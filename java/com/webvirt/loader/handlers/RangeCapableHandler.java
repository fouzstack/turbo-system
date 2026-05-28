package com.webvirt.loader.handlers;

import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import com.webvirt.loader.PathHandler;

/**
 * Handler capaz de servir peticiones HTTP Range (Partial Content).
 * 
 * Extiende PathHandler para que los handlers que soportan Range
 * puedan ser detectados polimórficamente sin instanceof.
 * 
 * @since 3.6.0
 */
public interface RangeCapableHandler extends PathHandler {

    /**
     * Maneja una petición Range para este handler.
     *
     * @param path        Path del recurso
     * @param rangeHeader Valor de la cabecera Range (ej: "bytes=0-1023")
     * @param request     Petición original (puede ser null)
     * @return Respuesta 206 Partial Content, o null si no se puede servir
     */
    WebResourceResponse handleRange(
        String path, String rangeHeader, WebResourceRequest request);
}