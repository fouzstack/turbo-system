package com.webvirt.loader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Router de peticiones hacia PathHandlers.
 * Soporta prefijos con snapshot inmutable para lectura lock-free.
 * 
 * @since 3.6.0
 */
public class RequestRouter {

    private final Map<String, PathHandler> handlers = new ConcurrentHashMap<>(4);

    // Snapshot inmutable para lectura lock-free
    private volatile List<String> sortedPrefixes = Collections.emptyList();
    private final Object prefixWriteLock = new Object();

    /**
     * Registra un handler para un prefijo de path.
     *
     * @param pathPrefix Prefijo de ruta (ej: "/assets/")
     * @param handler    Handler que sirve ese prefijo
     */
    public void register(String pathPrefix, PathHandler handler) {
        if (pathPrefix == null || handler == null) return;

        handlers.put(pathPrefix, handler);

        synchronized (prefixWriteLock) {
            List<String> newList = new ArrayList<>(sortedPrefixes);
            if (!newList.contains(pathPrefix)) {
                newList.add(pathPrefix);
                // Ordenar por longitud descendente → prefijos más específicos primero
                newList.sort((a, b) -> Integer.compare(b.length(), a.length()));
                sortedPrefixes = Collections.unmodifiableList(newList);
            }
        }
    }

    /**
     * Resuelve un path al handler correspondiente.
     * Búsqueda O(1) exacta, luego O(n) por prefijos.
     *
     * @param path Path normalizado (debe empezar con "/")
     * @return PathHandler o null si no hay handler para ese path
     */
    public PathHandler resolve(String path) {
        // Búsqueda exacta O(1)
        PathHandler exact = handlers.get(path);
        if (exact != null) return exact;

        // Búsqueda por prefijos — snapshot inmutable, lectura lock-free
        List<String> prefixes = sortedPrefixes;
        for (String prefix : prefixes) {
            if (path.startsWith(prefix)) {
                return handlers.get(prefix);
            }
        }

        return null;
    }

    /**
     * Retorna el mapa de handlers para iteración (ej: precache).
     *
     * @return Mapa inmutable de handlers registrados
     */
    public Map<String, PathHandler> getHandlers() {
        return Collections.unmodifiableMap(handlers);
    }

    /**
     * Retorna los prefijos ordenados (más largo primero).
     */
    public List<String> getSortedPrefixes() {
        return sortedPrefixes;
    }
}