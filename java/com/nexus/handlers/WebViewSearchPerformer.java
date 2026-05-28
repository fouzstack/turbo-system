package com.nexus.handlers;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nexus.NexusLog;

import java.lang.ref.WeakReference;

/**
 * Implementación real de {@link SearchPerformer} usando un WebView.
 * 
 * Características:
 * - WeakReference<WebView> para evitar memory leaks
 * - Todas las operaciones garantizadas en UI thread
 * - Logging consistente con NexusLog
 * - Verificación de validez del WebView en cada operación
 */
public class WebViewSearchPerformer implements SearchPerformer {
    
    private static final String TAG = "WebViewPerformer";
    
    private final WeakReference<WebView> webViewRef;
    private final Handler mainHandler;
    
    /**
     * @param webView WebView sobre el que se realizarán las búsquedas.
     *                Se almacena como WeakReference.
     */
    public WebViewSearchPerformer(@NonNull WebView webView) {
        this.webViewRef = new WeakReference<>(webView);
        this.mainHandler = new Handler(Looper.getMainLooper());
        NexusLog.d(TAG, "Performer creado para WebView: " + webView.hashCode());
    }
    
    /**
     * Inicia búsqueda asíncrona en el UI thread.
     * No-op si el WebView ya no existe.
     */
    @Override
    public void findAllAsync(@NonNull String query) {
        if (!isValid()) {
            NexusLog.w(TAG, "findAllAsync ignorado: WebView no disponible");
            return;
        }
        
        mainHandler.post(() -> {
            WebView wv = webViewRef.get();
            if (wv != null) {
                wv.findAllAsync(query);
            }
        });
    }
    
    /**
     * Navega entre resultados en el UI thread.
     * No-op si el WebView ya no existe.
     */
    @Override
    public void findNext(boolean forward) {
        if (!isValid()) {
            NexusLog.w(TAG, "findNext ignorado: WebView no disponible");
            return;
        }
        
        mainHandler.post(() -> {
            WebView wv = webViewRef.get();
            if (wv != null) {
                wv.findNext(forward);
            }
        });
    }
    
    /**
     * Limpia resaltados visuales en el UI thread.
     * No-op si el WebView ya no existe.
     */
    @Override
    public void clearMatches() {
        if (!isValid()) {
            NexusLog.w(TAG, "clearMatches ignorado: WebView no disponible");
            return;
        }
        
        mainHandler.post(() -> {
            WebView wv = webViewRef.get();
            if (wv != null) {
                wv.clearMatches();
            }
        });
    }
    
    /**
     * Registra el listener en el UI thread.
     * No-op si el WebView ya no existe.
     */
    @Override
    public void setFindListener(@Nullable WebView.FindListener listener) {
        if (!isValid() && listener != null) {
            NexusLog.w(TAG, "setFindListener ignorado: WebView no disponible");
            return;
        }
        
        mainHandler.post(() -> {
            WebView wv = webViewRef.get();
            if (wv != null) {
                wv.setFindListener(listener);
            }
        });
    }
    
    /**
     * @return true si el WebView sigue vivo
     */
    @Override
    public boolean isValid() {
        return webViewRef.get() != null;
    }
}