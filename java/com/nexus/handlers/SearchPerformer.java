package com.nexus.handlers;

import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Abstracción de las operaciones de búsqueda sobre un WebView.
 * 
 * Permite desacoplar el motor de búsqueda del WebView real,
 * facilitando tests unitarios y simulaciones deterministas.
 * 
 * Implementaciones:
 * - {@link WebViewSearchPerformer}: Implementación real con WebView
 * - MockSearchPerformer: Para tests unitarios
 */
public interface SearchPerformer {
    
    /**
     * Inicia búsqueda asíncrona de texto en el WebView.
     * 
     * @param query Texto a buscar (no nulo, no vacío)
     */
    void findAllAsync(@NonNull String query);
    
    /**
     * Navega entre resultados de búsqueda.
     * 
     * @param forward true = siguiente resultado, false = anterior
     */
    void findNext(boolean forward);
    
    /**
     * Limpia los resaltados visuales de búsqueda.
     */
    void clearMatches();
    
    /**
     * Registra un listener para recibir resultados de búsqueda.
     * Solo puede haber un listener activo a la vez.
     * 
     * @param listener Listener de resultados, o null para remover
     */
    void setFindListener(@Nullable WebView.FindListener listener);
    
    /**
     * Verifica si el WebView subyacente sigue disponible.
     * 
     * @return true si el WebView existe y está operativo
     */
    boolean isValid();
}