package com.nexus;

import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Message;
import android.view.KeyEvent;
import android.webkit.ClientCertRequest;
import android.webkit.HttpAuthHandler;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SafeBrowsingResponse;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
* Observador del ciclo de vida del WebView.
*
* Permite que múltiples bibliotecas (Nexus, WebVirt, etc.)
* escuchen eventos del WebView SIN sobrescribir el WebViewClient.
*
* Patrón Decorator completo: delega TODOS los métodos al cliente original.
*/
public final class WebViewLifecycleObserver {
	
	/**
	* Listener para eventos del ciclo de vida del WebView.
	*/
	public interface Listener {
		default void onPageFinished(@NonNull WebView webView, @NonNull String url) {}
		default void onPageStarted(@NonNull WebView webView, @NonNull String url) {}
		default void onReceivedError(@NonNull WebView webView, int errorCode,
		@NonNull String description, @NonNull String failingUrl) {}
		default void onWebViewDestroy(@NonNull WebView webView) {}
	}
	
	private final WebView webView;
	private final List<Listener> listeners = new CopyOnWriteArrayList<>();
	private WebViewClientWrapper wrapper;
	private boolean installed = false;
	
	public WebViewLifecycleObserver(@NonNull WebView webView) {
		this.webView = webView;
	}
	
	/**
	* Instala el observador. Guarda el cliente actual (WebVirt) y lo envuelve.
	*/
	public void install() {
		if (installed) return;
		
		WebViewClient currentClient = webView.getWebViewClient();
		
		if (currentClient == null) {
			currentClient = new WebViewClient();
		}
		
		if (currentClient instanceof WebViewClientWrapper) {
			return;
		}
		
		wrapper = new WebViewClientWrapper(currentClient, listeners);
		webView.setWebViewClient(wrapper);
		installed = true;
	}
	
	public void addListener(@NonNull Listener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}
	
	public void removeListener(@NonNull Listener listener) {
		listeners.remove(listener);
	}
	
	public void uninstall() {
		if (!installed || wrapper == null) return;
		
		listeners.clear();
		webView.setWebViewClient(wrapper.getDelegate());
		wrapper = null;
		installed = false;
	}
	
	/**
	* Decorator completo que delega TODOS los métodos al WebViewClient original.
	*/
	private static class WebViewClientWrapper extends WebViewClient {
		private final WebViewClient delegate;
		private final List<Listener> listeners;
		
		WebViewClientWrapper(@NonNull WebViewClient delegate, @NonNull List<Listener> listeners) {
			this.delegate = delegate;
			this.listeners = listeners;
		}
		
		WebViewClient getDelegate() {
			return delegate;
		}
		
		// ==================== MÉTODOS QUE NEXUS NECESITA OBSERVAR ====================
		
		@Override
		public void onPageFinished(WebView view, String url) {
			delegate.onPageFinished(view, url);
			notifyPageFinished(view, url);
		}
		
		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			delegate.onPageStarted(view, url, favicon);
			notifyPageStarted(view, url);
		}
		
		@Override
		public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
			delegate.onReceivedError(view, request, error);
			String failingUrl = request != null ? request.getUrl().toString() : "unknown";
			notifyReceivedError(view, error.getErrorCode(), error.getDescription().toString(), failingUrl);
		}
		
		// ==================== MÉTODOS CRÍTICOS PARA WEBVIRT ====================
		
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
			return delegate.shouldOverrideUrlLoading(view, request);
		}
		
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			return delegate.shouldOverrideUrlLoading(view, url);
		}
		
		@Nullable
		@Override
		public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
			return delegate.shouldInterceptRequest(view, request);
		}
		
		@Nullable
		@Override
		public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
			return delegate.shouldInterceptRequest(view, url);
		}
		
		// ==================== RESTO DE MÉTODOS - DELEGACIÓN COMPLETA ====================
		
		@Override
		public void onLoadResource(WebView view, String url) {
			delegate.onLoadResource(view, url);
		}
		
		@Override
		public void onPageCommitVisible(WebView view, String url) {
			delegate.onPageCommitVisible(view, url);
		}
		
		@Override
		public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
			delegate.onReceivedError(view, errorCode, description, failingUrl);
		}
		
		@Override
		public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
			delegate.onReceivedHttpError(view, request, errorResponse);
		}
		
		@Override
		public void onFormResubmission(WebView view, Message dontResend, Message resend) {
			delegate.onFormResubmission(view, dontResend, resend);
		}
		
		@Override
		public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
			delegate.doUpdateVisitedHistory(view, url, isReload);
		}
		
		@Override
		public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
			delegate.onReceivedSslError(view, handler, error);
		}
		
		@Override
		public void onReceivedClientCertRequest(WebView view, ClientCertRequest request) {
			delegate.onReceivedClientCertRequest(view, request);
		}
		
		@Override
		public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
			delegate.onReceivedHttpAuthRequest(view, handler, host, realm);
		}
		
		@Override
		public boolean shouldOverrideKeyEvent(WebView view, KeyEvent event) {
			return delegate.shouldOverrideKeyEvent(view, event);
		}
		
		@Override
		public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
			delegate.onUnhandledKeyEvent(view, event);
		}
		
		@Override
		public void onScaleChanged(WebView view, float oldScale, float newScale) {
			delegate.onScaleChanged(view, oldScale, newScale);
		}
		
		@Override
		public void onReceivedLoginRequest(WebView view, String realm, @Nullable String account, String args) {
			delegate.onReceivedLoginRequest(view, realm, account, args);
		}
		
		@Override
		public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
			return delegate.onRenderProcessGone(view, detail);
		}
		
		@Override
		public void onSafeBrowsingHit(WebView view, WebResourceRequest request, int threatType, SafeBrowsingResponse callback) {
			delegate.onSafeBrowsingHit(view, request, threatType, callback);
		}
		
		// ==================== MÉTODOS DE NOTIFICACIÓN A LISTENERS ====================
		
		private void notifyPageFinished(WebView view, String url) {
			for (Listener listener : listeners) {
				try {
					listener.onPageFinished(view, url);
					} catch (Exception e) {
					android.util.Log.e("WebViewLifecycle", "Error onPageFinished: " + e.getMessage());
				}
			}
		}
		
		private void notifyPageStarted(WebView view, String url) {
			for (Listener listener : listeners) {
				try {
					listener.onPageStarted(view, url);
					} catch (Exception e) {
					android.util.Log.e("WebViewLifecycle", "Error onPageStarted: " + e.getMessage());
				}
			}
		}
		
		private void notifyReceivedError(WebView view, int errorCode, String description, String failingUrl) {
			for (Listener listener : listeners) {
				try {
					listener.onReceivedError(view, errorCode, description, failingUrl);
					} catch (Exception e) {
					android.util.Log.e("WebViewLifecycle", "Error onReceivedError: " + e.getMessage());
				}
			}
		}
	}
}