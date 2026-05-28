package com.webvirt.benchmark;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.webvirt.WebVirt;
import com.webvirt.loader.WebVirtFileLoader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
* WebVirtBenchmark v3.6.0 — Usa el WebVirtFileLoader real.
*
* CORRECCIÓN: El benchmark ahora inyecta el mismo WebVirtFileLoader
* que usa la app principal, midiendo la caché real.
*
* CORRECCIÓN v3.6.1: Guarda archivo usando el mismo patrón que
* WebVirtMetrics.generateReport() — getExternalFilesDir() sin permisos.
*
* @since 3.6.1
*/
public class WebVirtBenchmark {
	
	private static final String TAG = "WebVirtBenchmark";
	private static final int MAX_DURATION_MS = 5000;
	private static final int SINGLE_TIMEOUT_MS = 3000;
	
	private final Context appContext;
	private final WebVirt webVirt;
	private final WebVirtFileLoader fileLoader;
	private final String baseUrl;
	
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final Object lock = new Object();
	
	private volatile long pageFinishedTime = 0;
	private volatile boolean waiting = false;
	
	private WebView benchmarkView;
	private Thread benchmarkThread;
	private BenchmarkListener listener;
	
	public interface BenchmarkListener {
		void onBenchmarkCompleted(long coldMs, long warmMs, long memoryKb);
		void onBenchmarkError(String error);
	}
	
	public WebVirtBenchmark(@NonNull Context context, @NonNull WebVirt webVirt, @NonNull String baseUrl) {
		this.appContext = context.getApplicationContext();
		this.webVirt = webVirt;
		this.fileLoader = webVirt.getFileLoader();
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
	}
	
	public void setBenchmarkListener(BenchmarkListener listener) {
		this.listener = listener;
	}
	
	/**
	* Benchmark ligero usando el WebVirtFileLoader real.
	*/
	public void runQuickBenchmark() {
		if (!running.compareAndSet(false, true)) {
			return;
		}
		
		benchmarkThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					long benchmarkStart = SystemClock.elapsedRealtime();
					
					createBenchmarkView();
					if (benchmarkView == null) {
						notifyError("No se pudo crear WebView");
						return;
					}
					
					Thread.sleep(500);
					
					if (SystemClock.elapsedRealtime() - benchmarkStart > MAX_DURATION_MS) {
						notifyError("Timeout excedido");
						return;
					}
					
					// 1. Cold start (limpiar cache)
					if (fileLoader != null) {
						fileLoader.clearCache();
					}
					Thread.sleep(200);
					long coldStartTime = SystemClock.elapsedRealtime();
					long coldDuration = measureLoad();
					long coldMs = (coldDuration > 0) ? (coldDuration - coldStartTime) : -1;
					
					if (SystemClock.elapsedRealtime() - benchmarkStart > MAX_DURATION_MS) {
						notifyError("Timeout excedido");
						return;
					}
					
					// 2. Warm start (con cache)
					Thread.sleep(300);
					long warmStartTime = SystemClock.elapsedRealtime();
					long warmDuration = measureLoad();
					long warmMs = (warmDuration > 0) ? (warmDuration - warmStartTime) : -1;
					
					// 3. Memoria
					Runtime runtime = Runtime.getRuntime();
					long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024;
					
					if (coldMs <= 0 || warmMs <= 0) {
						notifyError("Mediciones inválidas: cold=" + coldMs + " warm=" + warmMs);
						return;
					}
					
					if (coldMs > 30000 || warmMs > 30000) {
						notifyError("Valores anómalos detectados");
						return;
					}
					
					// Notificar resultados
					if (listener != null) {
						final long finalColdMs = coldMs;
						final long finalWarmMs = warmMs;
						final long finalMemoryKb = usedMemory;
						
						new Handler(Looper.getMainLooper()).post(new Runnable() {
							@Override
							public void run() {
								listener.onBenchmarkCompleted(finalColdMs, finalWarmMs, finalMemoryKb);
							}
						});
					}
					
					// Guardar reporte
					saveBenchmarkReport(coldMs, warmMs, usedMemory);
					
					} catch (Exception e) {
					notifyError(e.getMessage());
					} finally {
					destroyBenchmarkView();
					running.set(false);
				}
			}
		}, "Benchmark-Quick");
		
		benchmarkThread.start();
	}
	
	/**
	* El WebView del benchmark usa el MISMO WebVirtFileLoader.
	*/
	private void createBenchmarkView() {
		final Object viewLock = new Object();
		final WebView[] viewHolder = new WebView[1];
		
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				try {
					WebView view = new WebView(appContext);
					WebSettings settings = view.getSettings();
					settings.setJavaScriptEnabled(true);
					settings.setDomStorageEnabled(true);
					
					if (fileLoader != null) {
						view.setWebViewClient(new WebViewClient() {
							@Nullable
							@Override
							public WebResourceResponse shouldInterceptRequest(
							WebView view, WebResourceRequest request) {
								WebResourceResponse response = fileLoader.shouldInterceptRequest(request);
								if (response != null) return response;
								return super.shouldInterceptRequest(view, request);
							}
							
							@Override
							public void onPageFinished(WebView view, String url) {
								synchronized (lock) {
									if (waiting) {
										pageFinishedTime = SystemClock.elapsedRealtime();
										lock.notifyAll();
									}
								}
							}
						});
						} else {
						view.setWebViewClient(new WebViewClient() {
							@Override
							public void onPageFinished(WebView view, String url) {
								synchronized (lock) {
									if (waiting) {
										pageFinishedTime = SystemClock.elapsedRealtime();
										lock.notifyAll();
									}
								}
							}
						});
					}
					
					viewHolder[0] = view;
					} catch (Exception e) {
					Log.e(TAG, "Error creating benchmark view", e);
				}
				
				synchronized (viewLock) {
					viewLock.notifyAll();
				}
			}
		});
		
		synchronized (viewLock) {
			try {
				viewLock.wait(3000);
				} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		
		benchmarkView = viewHolder[0];
	}
	
	private long measureLoad() throws InterruptedException {
		if (benchmarkView == null) return -1;
		
		synchronized (lock) {
			waiting = true;
			pageFinishedTime = 0;
			
			new Handler(Looper.getMainLooper()).post(new Runnable() {
				@Override
				public void run() {
					if (benchmarkView != null) {
						benchmarkView.loadUrl(baseUrl);
					}
				}
			});
			
			long timeoutEnd = SystemClock.elapsedRealtime() + SINGLE_TIMEOUT_MS;
			
			while (waiting && pageFinishedTime == 0) {
				long remaining = timeoutEnd - SystemClock.elapsedRealtime();
				if (remaining <= 0) break;
				lock.wait(Math.min(remaining, 1000));
			}
			
			waiting = false;
			return pageFinishedTime;
		}
	}
	
	private void destroyBenchmarkView() {
		if (benchmarkView != null) {
			final WebView view = benchmarkView;
			benchmarkView = null;
			
			new Handler(Looper.getMainLooper()).post(new Runnable() {
				@Override
				public void run() {
					try {
						view.stopLoading();
						view.destroy();
						} catch (Exception e) {
						// Ignorar
					}
				}
			});
		}
	}
	
	// ==================== GUARDAR REPORTE (CORREGIDO) ====================
	
	/**
	* Guarda reporte de benchmark usando el MISMO patrón que WebVirtMetrics.
	*
	* PATRÓN PROBADO (funciona sin permisos):
	* 1. getExternalFilesDir(null) → sin permisos en Android 4.4+
	* 2. Fallback a getFilesDir() si es null
	* 3. FileWriter + PrintWriter con try-with-resources
	* 4. Verificar que el archivo existe después de escribir
	*/
	private void saveBenchmarkReport(long coldMs, long warmMs, long memoryKb) {
		Log.d(TAG, "💾 saveBenchmarkReport() llamado");
		Log.d(TAG, "Cold: " + coldMs + "ms, Warm: " + warmMs + "ms, Memory: " + memoryKb + "KB");
		
		try {
			// ✅ MISMO PATRÓN QUE WebVirtMetrics.generateReport()
			File documentsDir = appContext.getExternalFilesDir(null);
			if (documentsDir == null) {
				documentsDir = appContext.getFilesDir();
				Log.d(TAG, "📁 Usando almacenamiento interno: " + documentsDir.getAbsolutePath());
				} else {
				Log.d(TAG, "📁 Usando almacenamiento externo: " + documentsDir.getAbsolutePath());
			}
			
			// Subdirectorio para benchmarks
			File benchmarkDir = new File(documentsDir, "WebVirtBenchmarks");
			Log.d(TAG, "📁 Directorio: " + benchmarkDir.getAbsolutePath());
			
			// Crear directorio si no existe
			File parentDir = benchmarkDir.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				boolean created = parentDir.mkdirs();
				Log.d(TAG, "📁 Padre creado: " + created);
			}
			if (!benchmarkDir.exists()) {
				boolean created = benchmarkDir.mkdirs();
				Log.d(TAG, "📁 BenchmarkDir creado: " + created);
				if (!created) {
					Log.e(TAG, "❌ No se pudo crear: " + benchmarkDir.getAbsolutePath());
					return;
				}
			}
			
			// Nombre de archivo con timestamp
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
			String fileName = "webvirt_benchmark_" + sdf.format(new Date()) + ".txt";
			File reportFile = new File(benchmarkDir, fileName);
			
			Log.d(TAG, "📄 Guardando en: " + reportFile.getAbsolutePath());
			
			// Construir contenido
			String report = buildReportContent(coldMs, warmMs, memoryKb);
			
			// ✅ MISMO PATRÓN: FileWriter + PrintWriter con try-with-resources
			try (FileWriter fw = new FileWriter(reportFile, false);
			PrintWriter pw = new PrintWriter(fw)) {
				pw.print(report);
				pw.flush();
				Log.d(TAG, "✅ Escritura completada | " + report.length() + " chars");
			}
			
			// ✅ MISMO PATRÓN: Verificar que el archivo existe
			if (reportFile.exists()) {
				Log.d(TAG, "✅ VERIFICADO: Archivo creado");
				Log.d(TAG, "   Ruta: " + reportFile.getAbsolutePath());
				Log.d(TAG, "   Tamaño: " + reportFile.length() + " bytes");
				} else {
				Log.e(TAG, "❌ ERROR: Archivo NO existe después de escribir");
			}
			
			} catch (IOException e) {
			Log.e(TAG, "❌ IOException: " + e.getMessage(), e);
			notifyError("Error guardando reporte: " + e.getMessage());
			} catch (Exception e) {
			Log.e(TAG, "❌ Error inesperado: " + e.getMessage(), e);
			notifyError("Error inesperado: " + e.getMessage());
		}
	}
	
	/**
	* Construye el contenido del reporte.
	*/
	private String buildReportContent(long coldMs, long warmMs, long memoryKb) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
		StringBuilder sb = new StringBuilder();
		
		sb.append("╔══════════════════════════════════════════════════════╗\n");
		sb.append("║         WEBVIRT BENCHMARK REPORT                    ║\n");
		sb.append("╠══════════════════════════════════════════════════════╣\n\n");
		
		sb.append("📅 Date:        ").append(sdf.format(new Date())).append("\n");
		sb.append("🌐 Host:        ").append(webVirt.getHost()).append("\n");
		sb.append("🔗 Base URL:    ").append(baseUrl).append("\n\n");
		
		sb.append("⚡ PERFORMANCE\n");
		sb.append("─────────────────────────────────────────────────\n");
		sb.append(String.format("  Cold start:   %d ms\n", coldMs));
		sb.append(String.format("  Warm start:   %d ms\n", warmMs));
		long diff = coldMs - warmMs;
		double ratio = warmMs > 0 ? (double) coldMs / warmMs : 0;
		sb.append(String.format("  Difference:   %d ms (%.1fx faster with cache)\n", diff, ratio));
		sb.append(String.format("  Memory:       %d KB (%.1f MB)\n\n", memoryKb, memoryKb / 1024.0));
		
		if (fileLoader != null) {
			sb.append("🗄️ CACHE METRICS\n");
			sb.append("─────────────────────────────────────────────────\n");
			sb.append(String.format("  Entries:      %d\n", fileLoader.getCacheEntryCount()));
			sb.append(String.format("  Size:         %s\n", formatBytes(fileLoader.getCacheSizeBytes())));
			sb.append(String.format("  Hit rate:     %.1f%%\n", fileLoader.getCacheHitRate() * 100));
			sb.append(String.format("  Hits:         %d\n", fileLoader.getCacheHitCount()));
			sb.append(String.format("  Misses:       %d\n", fileLoader.getCacheMissCount()));
			sb.append(String.format("  Evictions:    %d\n\n", fileLoader.getCacheEvictionCount()));
			
			sb.append("📦 PRECACHE\n");
			sb.append("─────────────────────────────────────────────────\n");
			sb.append(String.format("  Assets:       %d\n", fileLoader.getPrecachedAssetCount()));
			sb.append(String.format("  Size:         %s\n\n", formatBytes(fileLoader.getPrecachedBytes())));
		}
		
		sb.append("📊 RAW DATA\n");
		sb.append("─────────────────────────────────────────────────\n");
		sb.append("coldStart_ms,").append(coldMs).append("\n");
		sb.append("warmStart_ms,").append(warmMs).append("\n");
		sb.append("memory_kb,").append(memoryKb).append("\n");
		sb.append("timestamp,").append(System.currentTimeMillis()).append("\n\n");
		
		sb.append("╚══════════════════════════════════════════════════════╝\n");
		sb.append("WebVirt v3.6.0 — Hybrid Web Runtime Engine\n");
		
		return sb.toString();
	}
	
	private String formatBytes(long bytes) {
		if (bytes < 1024) return bytes + " B";
		if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
		return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
	}
	
	private void notifyError(String error) {
		Log.e(TAG, "Benchmark error: " + error);
		if (listener != null) {
			new Handler(Looper.getMainLooper()).post(new Runnable() {
				@Override
				public void run() {
					listener.onBenchmarkError(error);
				}
			});
		}
	}
	
	public void stop() {
		running.set(false);
		synchronized (lock) {
			lock.notifyAll();
		}
		if (benchmarkThread != null) {
			benchmarkThread.interrupt();
		}
		destroyBenchmarkView();
	}
	
	public boolean isRunning() {
		return running.get();
	}
}