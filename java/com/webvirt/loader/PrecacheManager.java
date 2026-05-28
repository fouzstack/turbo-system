package com.webvirt.loader;

import android.util.Log;
import android.webkit.WebResourceResponse;


import com.webvirt.extensions.manifest.AssetManifest;
import com.webvirt.extensions.manifest.AssetManifestEntry;
import com.webvirt.loader.PathUtils;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
* Gestor de precarga asíncrona de assets con backpressure.
*
* Responsabilidades:
* - Precarga automática de assets críticos al detectar HTML principal
* - Precarga manual bajo demanda (preloadAsset / preloadAssets)
* - Control de concurrencia con executor single-thread
* - Backpressure: límites de cantidad (MAX_PRECACHED_ASSETS) y memoria (MAX_PRECACHED_BYTES)
* - Delegación de criticidad en ManifestBasedPrecacheStrategy
*
* Thread-safe. Diseñado para ejecutarse en background con mínima prioridad.
*
* @since 3.6.0
*/
public class PrecacheManager {
	
	private static final String TAG = "WebVirtPrecache";
	
	// Límites de backpressure
	private static final int MAX_PRECACHED_ASSETS = 20;
	private static final long MAX_PRECACHED_BYTES = 5 * 1024 * 1024; // 5 MB
	
	// Dependencias
	private final RequestRouter requestRouter;
	private final SecurityManager securityManager;
	private final ResponseCache responseCache;
	private final AssetManifest assetManifest;
	private final ManifestBasedPrecacheStrategy precacheStrategy;
	
	// Concurrencia
	private final ExecutorService executor;
	private final Set<String> precachedAssets = ConcurrentHashMap.newKeySet();
	private final AtomicBoolean started = new AtomicBoolean(false);
	private final AtomicLong precachedBytes = new AtomicLong(0);
	
	// Estado
	private volatile boolean enabled;
	
	/**
	* Constructor completo.
	*
	* @param requestRouter    Router de handlers
	* @param securityManager  Validador de seguridad
	* @param responseCache    Caché de respuestas
	* @param assetManifest    Manifiesto de assets
	* @param precacheStrategy Estrategia de criticidad
	* @param enabled          Si la precarga está activa inicialmente
	*/
	public PrecacheManager(
	RequestRouter requestRouter,
	SecurityManager securityManager,
	ResponseCache responseCache,
	AssetManifest assetManifest,
	ManifestBasedPrecacheStrategy precacheStrategy,
	boolean enabled) {
		this.requestRouter = requestRouter;
		this.securityManager = securityManager;
		this.responseCache = responseCache;
		this.assetManifest = assetManifest != null ? assetManifest : AssetManifest.NOOP;
		this.precacheStrategy = precacheStrategy != null
		? precacheStrategy
		: ManifestBasedPrecacheStrategy.spaDefault(this.assetManifest);
		this.enabled = enabled;
		
		this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r, "WebVirt-Precache");
				t.setPriority(Thread.MIN_PRIORITY);
				t.setDaemon(true);
				return t;
			}
		});
	}
	
	/**
	* Constructor con estrategia por defecto (SPA).
	*/
	public PrecacheManager(
	RequestRouter requestRouter,
	SecurityManager securityManager,
	ResponseCache responseCache,
	AssetManifest assetManifest,
	boolean enabled) {
		this(requestRouter, securityManager, responseCache, assetManifest,
		ManifestBasedPrecacheStrategy.spaDefault(assetManifest), enabled);
	}
	
	// ==================== DISPARO AUTOMÁTICO ====================
	
	/**
	* Dispara la precarga si el path corresponde al HTML principal.
	* Llamado desde el runtime en cada interceptación.
	*
	* @param path Path de la petición actual
	*/
	public void triggerIfMainHtml(String path) {
		if (enabled && PathUtils.isMainHtml(path)) {
			trigger();
		}
	}
	
	/**
	* Inicia la precarga asíncrona de assets críticos.
	* Solo se ejecuta una vez (protegido por AtomicBoolean).
	*/
	private void trigger() {
		if (!enabled || executor.isShutdown()) return;
		if (!started.compareAndSet(false, true)) return;
		
		executor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					// Obtener handlers ordenados por prioridad de criticidad
					List<Map.Entry<String, PathHandler>> handlers =
					new ArrayList<>(requestRouter.getHandlers().entrySet());
					
					// Ordenar: críticos primero
					handlers.sort((a, b) -> {
						boolean aCritical = precacheStrategy.isCritical(a.getKey());
						boolean bCritical = precacheStrategy.isCritical(b.getKey());
						if (aCritical && !bCritical) return -1;
						if (!aCritical && bCritical) return 1;
						return 0;
					});
					
					int count = 0;
					for (Map.Entry<String, PathHandler> entry : handlers) {
						// Backpressure: límite de cantidad
						if (count >= MAX_PRECACHED_ASSETS) break;
						// Backpressure: límite de memoria
						if (precachedBytes.get() >= MAX_PRECACHED_BYTES) break;
						
						String path = entry.getKey();
						
						// Solo precargar assets críticos según la estrategia
						if (precacheStrategy.isCritical(path)) {
							String resolvedPath = resolvePath(path);
							if (precacheAsset(resolvedPath)) {
								count++;
							}
						}
					}
					
					Log.d(TAG, "Precache completada: " + count + " assets, " +
					(precachedBytes.get() / 1024) + " KB");
					
					} catch (Exception e) {
					Log.e(TAG, "Precache error: " + e.getMessage());
				}
			}
		});
	}
	
	// ==================== PRECARGA INDIVIDUAL ====================
	
	/**
	* Precarga un asset individual.
	* Validación completa de seguridad, disponibilidad y límites.
	*
	* @param path Path resuelto (hasheado si aplica) del asset
	* @return true si se precargó exitosamente
	*/
	public boolean precacheAsset(String path) {
		// Validaciones básicas
		if (path == null) return false;
		if (precachedAssets.contains(path)) return false;
		
		// Backpressure
		if (precachedAssets.size() >= MAX_PRECACHED_ASSETS) return false;
		if (precachedBytes.get() >= MAX_PRECACHED_BYTES) return false;
		
		try {
			// Validación de seguridad
			if (!securityManager.isPathAllowed(path)) return false;
			
			// Resolver handler
			PathHandler handler = requestRouter.resolve(path);
			if (handler == null) return false;
			
			// Cargar asset (request = null → carga completa sin headers condicionales)
			WebResourceResponse response = handler.handle(path, null);
			
			// Solo cachear ByteArrayInputStream (datos completos en memoria)
			if (response != null && response.getData() instanceof ByteArrayInputStream) {
				int available = response.getData().available();
				
				// Almacenar en caché
				responseCache.cacheResponseWithHeaders(path, response, null);
				
				// Registrar en tracking
				precachedAssets.add(path);
				precachedBytes.addAndGet(available);
				
				return true;
			}
			
			} catch (Exception e) {
			Log.e(TAG, "Precache fallo: " + path + " - " + e.getMessage());
		}
		
		return false;
	}
	
	// ==================== API PÚBLICA DE PRECARGA MANUAL ====================
	
	/**
	* Precarga un asset específico de forma asíncrona.
	*
	* @param path Ruta lógica del asset a precargar
	*/
	public void preloadAsset(String path) {
		if (path == null) return;
		if (!enabled || executor.isShutdown()) return;
		
		executor.execute(new Runnable() {
			@Override
			public void run() {
				String resolvedPath = resolvePath(path);
				precacheAsset(resolvedPath);
			}
		});
	}
	
	/**
	* Precarga múltiples assets de forma asíncrona.
	*
	* @param paths Rutas lógicas de los assets a precargar
	*/
	public void preloadAssets(String... paths) {
		if (paths == null) return;
		for (String path : paths) {
			preloadAsset(path);
		}
	}
	
	// ==================== RESOLUCIÓN DE PATHS ====================
	
	/**
	* Resuelve un path lógico a su path físico usando el AssetManifest.
	*
	* @param logicalPath Path lógico original
	* @return Path hasheado si existe en el manifiesto, o el path original
	*/
	private String resolvePath(String logicalPath) {
		AssetManifestEntry entry = assetManifest.resolve(logicalPath);
		return entry != null ? entry.hashedPath : logicalPath;
	}
	
	// ==================== CONTROL DE ESTADO ====================
	
	/**
	* Activa o desactiva la precarga automática.
	* Al desactivar, limpia los assets precargados.
	*
	* @param enabled true para activar, false para desactivar
	*/
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
		if (!enabled) {
			clear();
		}
	}
	
	/**
	* Verifica si la precarga automática está activada.
	*
	* @return true si está activada
	*/
	public boolean isEnabled() {
		return enabled;
	}
	
	/**
	* Reinicia el flag de precarga iniciada.
	* Útil si se necesita re-ejecutar la precarga después de añadir nuevos handlers.
	*/
	public void reset() {
		started.set(false);
	}
	
	// ==================== MÉTRICAS ====================
	
	/**
	* @return Número de assets actualmente precargados
	*/
	public int getCachedCount() {
		return precachedAssets.size();
	}
	
	/**
	* @return Bytes totales de assets precargados
	*/
	public long getCachedBytes() {
		return precachedBytes.get();
	}
	
	/**
	* @return Estrategia de criticidad en uso
	*/
	public ManifestBasedPrecacheStrategy getStrategy() {
		return precacheStrategy;
	}
	
	// ==================== LIMPIEZA ====================
	
	/**
	* Limpia los assets precargados (sin detener el executor).
	*/
	public void clear() {
		precachedAssets.clear();
		precachedBytes.set(0);
	}
	
	// ==================== LIFECYCLE ====================
	
	/**
	* Apaga el executor y limpia los recursos.
	* Debe llamarse al destruir el runtime.
	*/
	public void shutdown() {
		enabled = false;
		if (!executor.isShutdown()) {
			executor.shutdown();
			try {
				if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
					executor.shutdownNow();
				}
				} catch (InterruptedException e) {
				executor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
		clear();
	}
	
	/**
	* @return true si el executor ya fue apagado
	*/
	public boolean isShutdown() {
		return executor.isShutdown();
	}
}