package com.nexus.adapters;

import androidx.annotation.NonNull;

import com.nexus.NexusException;
import com.nexus.NexusHandler;
import com.nexus.NexusLog;
import com.nexus.handlers.WebViewSearchEngine;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter unificado para búsqueda en WebView.
 * 
 * Expone una función 'search' con un parámetro 'action':
 * - search: Buscar texto (requiere 'term')
 * - next: Siguiente resultado
 * - previous: Resultado anterior
 * - clear: Limpiar búsqueda
 * - status: Obtener estado actual completo
 * 
 * Integración con Nexus:
 * - Recibe WebViewSearchEngine por constructor (inyección de dependencias)
 * - Usa NexusException para errores tipados
 * - Validación segura de tipos desde JavaScript (instanceof)
 * - Compatible con ciclo de vida Nexus (onDestroy → engine.destroy())
 * - No depende de WebViewSearchBridge (comunicación directa con el engine)
 * 
 * JavaScript usage:
 * Nexus.call('search', { action: 'search', term: 'texto' })
 * Nexus.call('search', { action: 'next' })
 * Nexus.call('search', { action: 'previous' })
 * Nexus.call('search', { action: 'clear' })
 * Nexus.call('search', { action: 'status' })
 * 
 * @author FouzStack
 * @version 5.0.0 - Production
 */
public class SearchHandlerAdapter implements NexusHandler {

    private static final String TAG = "SearchHandler";
    
    /** Motor de búsqueda inyectado (no lo construye el adapter) */
    private final WebViewSearchEngine searchEngine;

    /**
     * Constructor que recibe las dependencias ya construidas.
     * El adapter solo adapta, no construye dependencias.
     * 
     * @param searchEngine Motor de búsqueda ya configurado con su WebView
     */
    public SearchHandlerAdapter(@NonNull WebViewSearchEngine searchEngine) {
        this.searchEngine = searchEngine;
        NexusLog.d(TAG, "Adapter creado");
    }

    @NonNull
    @Override
    public String getName() {
        return "search";
    }

    @NonNull
    @Override
    public Object handle(@NonNull Map<String, Object> params) throws Exception {
        // Validación segura de tipo para 'action' (previene ClassCastException)
        Object rawAction = params.get("action");
        String action = (rawAction instanceof String) ? (String) rawAction : "search";
        
        NexusLog.d(TAG, "handle: action=" + action);

        Map<String, Object> result = new HashMap<>();
        result.put("action", action);

        switch (action) {
            case "search": {
                // Validación segura de tipo para 'term'
                Object rawTerm = params.get("term");
                if (!(rawTerm instanceof String) || ((String) rawTerm).isEmpty()) {
                    throw new NexusException(
                        "INVALID_PARAMS",
                        "The 'term' parameter is required and must be a non-empty string"
                    );
                }
                
                String term = (String) rawTerm;
                searchEngine.search(term);
                
                result.put("status", "searching");
                result.put("term", term);
                NexusLog.d(TAG, "Búsqueda iniciada: '" + term + "'");
                break;
            }

            case "next":
                searchEngine.findNext();
                result.put("status", "ok");
                result.put("current", searchEngine.getCurrentMatchIndex());
                result.put("total", searchEngine.getTotalMatches());
                break;

            case "previous":
                searchEngine.findPrevious();
                result.put("status", "ok");
                result.put("current", searchEngine.getCurrentMatchIndex());
                result.put("total", searchEngine.getTotalMatches());
                break;

            case "clear":
                searchEngine.clear();
                result.put("status", "cleared");
                NexusLog.d(TAG, "Búsqueda limpiada");
                break;

            case "status":
                // Estado completo desde el engine
                result.putAll(searchEngine.getSearchStatusMap());
                result.put("status", "ok");
                break;

            default:
                throw new NexusException(
                    "UNKNOWN_ACTION",
                    "Unknown action: " + action + 
                    ". Valid actions: search, next, previous, clear, status"
                );
        }

        return result;
    }

    /**
     * Destruye el adapter y el engine subyacente.
     * Llamado por Nexus cuando se destruye la instancia.
     */
    @Override
    public void onDestroy() {
        NexusLog.d(TAG, "Destruyendo adapter y engine");
        searchEngine.destroy();
    }
}