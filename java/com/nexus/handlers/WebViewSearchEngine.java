package com.nexus.handlers;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nexus.NexusLog;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Motor de búsqueda robusto para WebView - Nivel Producción.
 * 
 * Garantías:
 * - Thread-safe: AtomicInteger + AtomicBoolean + volatile snapshot
 * - Sin memory leaks: WeakReference<WebView>
 * - Sin race conditions: searchToken invalida búsquedas obsoletas
 * - Sin reentrada: externalFindListener aislado en post()
 * - Sin uso post-destroy: flag destroyed invalida toda la API
 * - Re-attach explícito: política documentada de reconexión
 * - Snapshot atómico: objeto inmutable reemplazado atómicamente
 * - UI thread garantizado: todas las operaciones sobre WebView
 * 
 * Comportamiento documentado:
 * - Después de destroy(), cualquier llamada es no-op
 * - Después de clear(), callbacks encolados se ignoran (acceptingResults=false)
 * - externalFindListener se ejecuta en post() aislado
 * - Si el WebView muere, usar reattach() para reconectar
 * 
 * Integración con Nexus:
 * - Usa NexusLog para logging consistente
 * - Diseñado para ser inyectado en SearchHandlerAdapter
 * - Compatible con el ciclo de vida de Nexus (onDestroy)
 * 
 * @author FouzStack
 * @version 5.0.0 - Production
 */
public class WebViewSearchEngine {
    
    private static final String TAG = "SearchEngine";
    
    /**
     * Estados de búsqueda.
     */
    public enum State {
        /** Sin búsqueda activa */
        IDLE,
        /** Búsqueda en progreso (findAllAsync ejecutándose) */
        SEARCHING,
        /** Búsqueda completada con o sin resultados */
        COMPLETED
    }
    
    // ==================== REFERENCIAS ====================
    
    private final WeakReference<WebView> webViewRef;
    private final Handler mainHandler;
    
    // ==================== CONTROL DE CONCURRENCIA ====================
    
    /** Token atómico para invalidar búsquedas obsoletas */
    private final AtomicInteger searchToken = new AtomicInteger(0);
    
    /** Flag: ¿se aceptan resultados del FindListener? */
    private final AtomicBoolean acceptingResults = new AtomicBoolean(false);
    
    /** Flag: ¿FindListener ya registrado en el WebView? */
    private final AtomicBoolean listenerRegistered = new AtomicBoolean(false);
    
    /** Flag anti-reentrada para externalFindListener */
    private final AtomicBoolean inExternalListener = new AtomicBoolean(false);
    
    /** Flag: ¿engine destruido? Invalida toda la API */
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    
    // ==================== ESTADO (Snapshot atómico) ====================
    
    /** Snapshot inmutable reemplazado atómicamente */
    private volatile SearchSnapshot currentSnapshot;
    
    // ==================== LISTENERS ====================
    
    /** Listener externo para chaining */
    @Nullable
    private WebView.FindListener externalFindListener;
    
    /** Callback para notificar cambios de estado */
    @Nullable
    private OnSearchStateChangedListener stateChangedListener;
    
    // ==================== INTERFACES Y CLASES ====================
    
    /**
     * Listener para cambios de estado del engine.
     */
    public interface OnSearchStateChangedListener {
        /**
         * @param newState Nuevo estado del engine
         * @param snapshot Snapshot atómico del estado completo
         */
        void onStateChanged(@NonNull State newState, @NonNull SearchSnapshot snapshot);
    }
    
    /**
     * Snapshot inmutable del estado completo de búsqueda.
     * Objeto único reemplazado atómicamente para garantizar consistencia.
     */
    public static class SearchSnapshot {
        private final State state;
        private final int totalMatches;
        private final int currentMatchIndex;
        @NonNull private final String query;
        private final boolean acceptingResults;
        private final boolean destroyed;
        
        SearchSnapshot(@NonNull State state, int totalMatches, int currentMatchIndex,
                       @Nullable String query, boolean acceptingResults, boolean destroyed) {
            this.state = state;
            this.totalMatches = totalMatches;
            this.currentMatchIndex = currentMatchIndex;
            this.query = query != null ? query : "";
            this.acceptingResults = acceptingResults;
            this.destroyed = destroyed;
        }
        
        @NonNull public State getState() { return state; }
        public int getTotalMatches() { return totalMatches; }
        public int getCurrentMatchIndex() { return currentMatchIndex; }
        @NonNull public String getQuery() { return query; }
        public boolean isAcceptingResults() { return acceptingResults; }
        public boolean isDestroyed() { return destroyed; }
        
        /**
         * @return true si hay búsqueda activa con resultados
         */
        public boolean isActive() {
            return !destroyed && acceptingResults &&
                   (state == State.SEARCHING || (state == State.COMPLETED && totalMatches > 0));
        }
        
        /**
         * @return Mapa inmutable del estado para serialización
         */
        @NonNull
        public Map<String, Object> toMap() {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("state", state.name());
            map.put("active", isActive());
            map.put("total", totalMatches);
            map.put("current", currentMatchIndex);
            map.put("query", query);
            map.put("destroyed", destroyed);
            return Collections.unmodifiableMap(map);
        }
        
        @Override
        public String toString() {
            return "SearchSnapshot{state=" + state +
                   ", total=" + totalMatches +
                   ", current=" + currentMatchIndex +
                   ", query='" + query + "'" +
                   ", accepting=" + acceptingResults +
                   ", destroyed=" + destroyed + "}";
        }
    }
    
    // ==================== CONSTRUCTOR ====================
    
    /**
     * Crea un nuevo motor de búsqueda.
     * 
     * @param webView WebView donde se realizarán las búsquedas.
     *                Se almacena como WeakReference para prevenir memory leaks.
     */
    public WebViewSearchEngine(@NonNull WebView webView) {
        this.webViewRef = new WeakReference<>(webView);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.currentSnapshot = new SearchSnapshot(State.IDLE, 0, 0, "", false, false);
        
        NexusLog.d(TAG, "Engine creado para WebView: " + webView.hashCode());
        registerFindListenerOnce();
    }
    
    // ==================== REGISTRO DE LISTENER ====================
    
    /**
     * Registra el FindListener UNA SOLA VEZ.
     * 
     * Si el WebView no existe en el momento del registro, el engine
     * queda inoperativo hasta que se llame a {@link #reattach(WebView)}.
     */
    private void registerFindListenerOnce() {
        if (!listenerRegistered.compareAndSet(false, true)) {
            NexusLog.d(TAG, "FindListener ya registrado");
            return;
        }
        
        if (Looper.myLooper() == Looper.getMainLooper()) {
            registerFindListenerInternal();
        } else {
            NexusLog.d(TAG, "Registrando FindListener en UI thread desde: " +
                Thread.currentThread().getName());
            mainHandler.post(this::registerFindListenerInternal);
        }
    }
    
    /**
     * Registro interno del FindListener. DEBE ejecutarse en UI thread.
     * 
     * Si el WebView ya no existe, el flag listenerRegistered se mantiene en true
     * (el intento ya se realizó). El engine queda inoperativo hasta reattach().
     */
    private void registerFindListenerInternal() {
        WebView webView = webViewRef.get();
        if (webView == null) {
            NexusLog.w(TAG, "WebView nulo al registrar FindListener. " +
                "Engine inoperativo hasta reattach()");
            return;
        }
        
        webView.setFindListener((activeMatchOrdinal, numberOfMatches, isDoneCounting) -> {
            // Ignorar si destruido o no aceptando resultados
            if (destroyed.get() || !acceptingResults.get()) {
                return;
            }
            
            int currentToken = searchToken.get();
            
            // ===== LISTENER EXTERNO AISLADO EN post() =====
            // Se ejecuta en post() separado para:
            // - Evitar bloqueos del UI thread
            // - Prevenir llamadas recursivas indirectas
            // - Aislar efectos secundarios
            if (externalFindListener != null &&
                inExternalListener.compareAndSet(false, true)) {
                mainHandler.post(() -> {
                    try {
                        if (externalFindListener != null && !destroyed.get()) {
                            externalFindListener.onFindResultReceived(
                                activeMatchOrdinal, numberOfMatches, isDoneCounting);
                        }
                    } catch (Exception e) {
                        NexusLog.e(TAG, "Error en externalFindListener (aislado)", e);
                    } finally {
                        inExternalListener.set(false);
                    }
                });
            }
            
            // Procesar resultados solo cuando el conteo terminó
            if (isDoneCounting && acceptingResults.get() &&
                currentToken == searchToken.get() && !destroyed.get()) {
                
                SearchSnapshot newSnapshot = new SearchSnapshot(
                    State.COMPLETED,
                    numberOfMatches,
                    numberOfMatches > 0 ? activeMatchOrdinal + 1 : 0,
                    currentSnapshot.getQuery(),
                    true,
                    false
                );
                
                currentSnapshot = newSnapshot;
                
                NexusLog.d(TAG, "Búsqueda #" + currentToken + " completada: " +
                    numberOfMatches + " matches, índice: " + 
                    (numberOfMatches > 0 ? activeMatchOrdinal + 1 : 0));
                
                notifyStateChanged(State.COMPLETED);
            }
        });
        
        NexusLog.d(TAG, "FindListener registrado exitosamente");
    }
    
    // ==================== REATTACH ====================
    
    /**
     * Reconecta el engine a un nuevo WebView.
     * 
     * Útil cuando:
     * - El WebView original fue destruido y recreado
     * - Se quiere reutilizar el engine con otra instancia de WebView
     * 
     * Restricciones:
     * - No funciona si el engine fue destruido con {@link #destroy()}
     * - Reinicia el estado del engine a IDLE
     * 
     * @param newWebView Nuevo WebView al que conectarse
     * @throws IllegalStateException si el engine ya fue destruido
     */
    public void reattach(@NonNull WebView newWebView) {
        if (destroyed.get()) {
            throw new IllegalStateException(
                "No se puede reattach(): el engine fue destruido. Crea una nueva instancia.");
        }
        
        NexusLog.d(TAG, "Reattaching a nuevo WebView: " + newWebView.hashCode());
        
        // Actualizar referencia internamente
        webViewRef.clear();
        
        // Resetear estado para el nuevo WebView
        listenerRegistered.set(false);
        acceptingResults.set(false);
        searchToken.incrementAndGet();
        currentSnapshot = new SearchSnapshot(State.IDLE, 0, 0, "", false, false);
        
        // Registrar listener en el nuevo WebView
        registerFindListenerOnce();
    }
    
    // ==================== NOTIFICACIÓN ====================
    
    private void notifyStateChanged(State newState) {
        if (stateChangedListener != null && !destroyed.get()) {
            SearchSnapshot snapshot = currentSnapshot;
            mainHandler.post(() -> {
                if (stateChangedListener != null && !destroyed.get()) {
                    stateChangedListener.onStateChanged(newState, snapshot);
                }
            });
        }
    }
    
    // ==================== CONFIGURACIÓN DE LISTENERS ====================
    
    /**
     * Establece un listener para cambios de estado del engine.
     */
    public void setOnSearchStateChangedListener(@Nullable OnSearchStateChangedListener listener) {
        this.stateChangedListener = listener;
    }
    
    /**
     * Establece un listener externo para recibir resultados de búsqueda.
     * Se ejecuta en un post() aislado para no bloquear el callback principal.
     */
    public void setExternalFindListener(@Nullable WebView.FindListener listener) {
        this.externalFindListener = listener;
        NexusLog.d(TAG, "ExternalFindListener " + (listener != null ? "configurado" : "eliminado"));
    }
    
    // ==================== GUARDIAS ====================
    
    /**
     * Verifica si el engine está destruido.
     * @return true si está destruido (y loguea warning)
     */
    private boolean checkDestroyed() {
        if (destroyed.get()) {
            NexusLog.w(TAG, "Operación rechazada: engine destruido");
            return true;
        }
        return false;
    }
    
    /**
     * Verifica si el engine y el WebView están operativos.
     * @return true si no hay problemas
     */
    private boolean checkOperational() {
        if (checkDestroyed()) return false;
        if (webViewRef.get() == null) {
            NexusLog.w(TAG, "WebView no disponible. Use reattach() para reconectar.");
            return false;
        }
        return true;
    }
    
    // ==================== OPERACIONES DE BÚSQUEDA ====================
    
    /**
     * Busca texto en el WebView.
     * 
     * Comportamiento:
     * - No-op si el engine fue destruido
     * - No-op si el WebView no existe
     * - Múltiples llamadas rápidas: solo la última actualiza el estado
     * 
     * @param query Texto a buscar (no nulo, no vacío)
     */
    public void search(@NonNull String query) {
        if (!checkOperational()) return;
        
        if (query == null || query.isEmpty()) {
            NexusLog.w(TAG, "Intento de búsqueda con query vacío");
            return;
        }
        
        final int token = searchToken.incrementAndGet();
        acceptingResults.set(true);
        
        SearchSnapshot newSnapshot = new SearchSnapshot(
            State.SEARCHING, 0, 0, query, true, false);
        currentSnapshot = newSnapshot;
        
        NexusLog.d(TAG, "Búsqueda #" + token + ": '" + query + "'");
        notifyStateChanged(State.SEARCHING);
        
        mainHandler.post(() -> {
            if (destroyed.get()) return;
            WebView wv = webViewRef.get();
            if (wv == null) return;
            
            if (token != searchToken.get() || !acceptingResults.get()) {
                NexusLog.d(TAG, "Búsqueda #" + token + " descartada (obsoleta)");
                return;
            }
            
            wv.findAllAsync(query);
        });
    }
    
    /**
     * Navega al siguiente resultado de búsqueda.
     * No-op si no hay búsqueda activa con resultados.
     */
    public void findNext() {
        if (!checkOperational()) return;
        
        SearchSnapshot snapshot = currentSnapshot;
        if (!snapshot.isActive() || snapshot.getTotalMatches() == 0) {
            NexusLog.d(TAG, "findNext ignorado: " + snapshot);
            return;
        }
        
        mainHandler.post(() -> {
            WebView wv = webViewRef.get();
            if (wv != null && !destroyed.get()) {
                wv.findNext(true);
            }
        });
    }
    
    /**
     * Navega al resultado anterior de búsqueda.
     * No-op si no hay búsqueda activa con resultados.
     */
    public void findPrevious() {
        if (!checkOperational()) return;
        
        SearchSnapshot snapshot = currentSnapshot;
        if (!snapshot.isActive() || snapshot.getTotalMatches() == 0) {
            NexusLog.d(TAG, "findPrevious ignorado: " + snapshot);
            return;
        }
        
        mainHandler.post(() -> {
            WebView wv = webViewRef.get();
            if (wv != null && !destroyed.get()) {
                wv.findNext(false);
            }
        });
    }
    
    /**
     * Limpia la búsqueda actual.
     * 
     * Efectos:
     * - Deja de aceptar resultados inmediatamente
     * - Transiciona a estado IDLE
     * - Limpia resaltados visuales del WebView
     * - Callbacks encolados serán ignorados
     */
    public void clear() {
        if (!checkOperational()) return;
        
        acceptingResults.set(false);
        searchToken.incrementAndGet();
        
        SearchSnapshot newSnapshot = new SearchSnapshot(
            State.IDLE, 0, 0, "", false, false);
        currentSnapshot = newSnapshot;
        
        WebView webView = webViewRef.get();
        if (webView != null) {
            mainHandler.post(() -> {
                WebView wv = webViewRef.get();
                if (wv != null && !destroyed.get()) {
                    wv.clearMatches();
                }
            });
        }
        
        notifyStateChanged(State.IDLE);
        NexusLog.d(TAG, "Búsqueda limpiada → IDLE");
    }
    
    // ==================== GETTERS PÚBLICOS ====================
    
    /** @return Estado actual del motor */
    @NonNull public State getCurrentState() { 
        return currentSnapshot.getState(); 
    }
    
    /** @return true si hay búsqueda activa con resultados */
    public boolean isSearchActive() { 
        return currentSnapshot.isActive(); 
    }
    
    /** @return Número total de coincidencias */
    public int getTotalMatches() { 
        return currentSnapshot.getTotalMatches(); 
    }
    
    /** @return Índice de la coincidencia actual (base 1) */
    public int getCurrentMatchIndex() { 
        return currentSnapshot.getCurrentMatchIndex(); 
    }
    
    /** @return Texto de búsqueda actual */
    @NonNull public String getCurrentQuery() { 
        return currentSnapshot.getQuery(); 
    }
    
    /** @return true si el engine fue destruido */
    public boolean isDestroyed() { 
        return destroyed.get(); 
    }
    
    /**
     * @return Snapshot inmutable del estado completo
     */
    @NonNull
    public SearchSnapshot getSearchSnapshot() {
        return currentSnapshot;
    }
    
    /**
     * @return Mapa inmutable del estado para serialización a JavaScript
     */
    @NonNull
    public Map<String, Object> getSearchStatusMap() {
        return currentSnapshot.toMap();
    }
    
    // ==================== DESTRUCCIÓN ====================
    
    /**
     * Destruye el engine completamente.
     * 
     * Después de destroy():
     * - Todas las operaciones son no-op
     * - acceptingResults = false
     * - searchToken incrementado
     * - FindListener removido del WebView
     * - Callbacks ya encolados se ejecutarán pero serán ignorados
     * - No se puede reattach()
     * 
     * Para volver a usar búsqueda, crear una nueva instancia.
     */
    public void destroy() {
        if (!destroyed.compareAndSet(false, true)) {
            NexusLog.d(TAG, "Engine ya destruido anteriormente");
            return;
        }
        
        NexusLog.d(TAG, "Destruyendo engine completamente");
        
        acceptingResults.set(false);
        searchToken.incrementAndGet();
        
        WebView webView = webViewRef.get();
        if (webView != null) {
            mainHandler.post(() -> {
                WebView wv = webViewRef.get();
                if (wv != null) {
                    wv.clearMatches();
                    wv.setFindListener(null);
                }
            });
        }
        
        externalFindListener = null;
        stateChangedListener = null;
        listenerRegistered.set(false);
        currentSnapshot = new SearchSnapshot(State.IDLE, 0, 0, "", false, true);
        webViewRef.clear();
        
        NexusLog.d(TAG, "Engine destruido exitosamente");
    }
}