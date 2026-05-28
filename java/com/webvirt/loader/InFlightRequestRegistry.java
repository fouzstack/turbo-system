package com.webvirt.loader;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro de peticiones en vuelo para coalescencia.
 * Evita cargas duplicadas del mismo recurso en requests concurrentes.
 * 
 * @since 3.6.0
 */
public class InFlightRequestRegistry {

    private final ConcurrentHashMap<String, InFlightRequest> inFlightRequests = new ConcurrentHashMap<>();

    /**
     * Registra u obtiene una petición en vuelo para un path.
     * Si ya existe una completada, la limpia y crea una nueva.
     *
     * @param path Path del recurso
     * @return InFlightRequest nueva o existente
     */
    public InFlightRequest register(String path) {
        InFlightRequest inFlight = inFlightRequests.get(path);

        if (inFlight != null && inFlight.isCompleted()) {
            // Limpiar petición completada stale
            inFlightRequests.remove(path, inFlight);
            inFlight = null;
        }

        if (inFlight == null) {
            inFlight = new InFlightRequest();
            InFlightRequest existing = inFlightRequests.putIfAbsent(path, inFlight);
            if (existing != null) {
                inFlight = existing;
            }
        }

        return inFlight;
    }

    /**
     * Marca una petición como completada y la remueve del registro.
     *
     * @param path    Path del recurso
     * @param request InFlightRequest a completar
     */
    public void complete(String path, InFlightRequest request) {
        request.markCompleted();
        inFlightRequests.remove(path, request);
    }

    /**
     * Verifica si una petición se completó y tiene resultado cacheado.
     */
    public boolean isCompletedAndCached(InFlightRequest request) {
        return request.isCompleted() && request.isCached();
    }

    /**
     * Limpia todas las peticiones en vuelo.
     */
    public void clear() {
        inFlightRequests.clear();
    }

    // ==================== INNER CLASS ====================

    /**
     * Representa una petición en vuelo con su lock de sincronización.
     */
    public static class InFlightRequest {
        private final Object lock = new Object();
        private volatile boolean completed = false;
        private volatile boolean cached = false;

        public Object getLock() {
            return lock;
        }

        public boolean isCompleted() {
            return completed;
        }

        public void markCompleted() {
            this.completed = true;
        }

        public boolean isCached() {
            return cached;
        }

        public void markCached() {
            this.cached = true;
        }
    }
}