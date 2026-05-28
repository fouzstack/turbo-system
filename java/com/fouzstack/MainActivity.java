package com.fouzstack;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.nexus.handlers.ExportHandler;
import com.nexus.handlers.PdfExportHandler;
import com.nexus.Nexus;
import com.nexus.adapters.ExportHandlerAdapter;
import com.nexus.adapters.ImportHandlerAdapterV2;
import com.nexus.adapters.PdfExportHandlerAdapter;
import com.nexus.adapters.SearchHandlerAdapter;
import com.nexus.handlers.WebViewSearchEngine;
import com.webvirt.WebVirt;
import com.webvirt.WebVirtMetrics;
import com.webvirt.WebVirtMetricsCollector;
import com.webvirt.loader.WebVirtFileLoader;

import com.webvirt.utils.LoggingUtil;
import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity {
	
	private static final String TAG = "MainActivity";
	private static final String VIRTUAL_HOST = "app.local";
	
	private WebView webView;
	private LinearLayout welcomeView;
	private Button startButton;
	private WebVirt webVirt;
	private Nexus nexus;
	private ImportHandlerAdapterV2 importAdapter;
	private ExportHandlerAdapter exportAdapter;
	private PdfExportHandlerAdapter pdfExportAdapter;
	private WebViewSearchEngine searchEngine;
	private SearchHandlerAdapter searchAdapter;
	private boolean isWebViewVisible = false;
	
	
	
	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// ✅ Inicializar LoggingUtil con el contexto
		LoggingUtil.init(getApplicationContext());
		
		
		setContentView(R.layout.activity_main);
		
		webView = findViewById(R.id.webView);
		welcomeView = findViewById(R.id.welcomeView);
		startButton = findViewById(R.id.startButton);
		
		if (getSupportActionBar() != null) getSupportActionBar().hide();
		
		webVirt = WebVirt.with(this)
		.host(VIRTUAL_HOST)
		//.withMetrics()
		.onPageReady(view -> {
			//webVirt.runBenchmark();
		})
		.bind(webView);
		
		// ============ PRECARGA DE ASSETS ============
		preloadCriticalAssets();
		// =========================================
		
		setupNexus();
		
		if (nexus != null) {
			nexus.attachToWebViewLifecycle();
		}
		
		startButton.setOnClickListener(v -> transitionToWebView());
		showWelcomeScreen();
	}
	
	/**
	* Precarga assets críticos en background.
	* Se ejecuta al inicio para tenerlos listos cuando se cargue la app.
	*/
	private void preloadCriticalAssets() {
		if (webVirt == null) return;
		
		WebVirtFileLoader fileLoader = webVirt.getFileLoader();
		if (fileLoader == null) return;
		
		// Precarga asíncrona - no bloquea la UI
		fileLoader.preloadAssets(
		"/index.html",
		"/assets/index-DGe01YXs.css",
		"/assets/index-CYWj2K2T.js",
		"/assets/root-DlM8lW8L.js",
		"/images/www.png"
		);
		
		Log.d(TAG, "🚀 Precarga de assets iniciada en background");
	}
	
	private void setupNexus() {
		Handler mainHandler = new Handler(Looper.getMainLooper());
		
		Consumer<String> toastCallback = message ->
		runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
		
		ExportHandler exportHandler = new ExportHandler(this, mainHandler);
		exportHandler.setToastCallback(toastCallback);
		exportAdapter = new ExportHandlerAdapter(exportHandler);
		
		importAdapter = new ImportHandlerAdapterV2(this);
		
		PdfExportHandler pdfExportHandler = new PdfExportHandler(this, mainHandler);
		pdfExportHandler.setToastCallback(toastCallback);
		pdfExportAdapter = new PdfExportHandlerAdapter(pdfExportHandler);
		
		searchEngine = new WebViewSearchEngine(webView);
		searchAdapter = new SearchHandlerAdapter(searchEngine);
		
		nexus = Nexus.installOn(webView)
		.withDebugMode(false)
		.withGlobalTimeout(30_000)
		.registerHandler("export", exportAdapter)
		.registerHandler("import", importAdapter)
		.registerHandler("pdf", pdfExportAdapter)
		.registerHandler("search", searchAdapter)
		.initialize()
		.withFilePicker(this);
		
		importAdapter.setNexus(nexus);
	}
	
	private void showWelcomeScreen() {
		webView.setVisibility(View.GONE);
		welcomeView.setVisibility(View.VISIBLE);
		isWebViewVisible = false;
	}
	
	private void showWebViewScreen() {
		welcomeView.setVisibility(View.GONE);
		webView.setVisibility(View.VISIBLE);
		isWebViewVisible = true;
	}
	
	private void transitionToWebView() {
		Animation fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out);
		Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
		
		fadeOut.setAnimationListener(new Animation.AnimationListener() {
			@Override
			public void onAnimationStart(Animation a) {}
			
			@Override
			public void onAnimationEnd(Animation a) {
				showWebViewScreen();
				loadReactApp();
			}
			
			@Override
			public void onAnimationRepeat(Animation a) {}
		});
		
		welcomeView.startAnimation(fadeOut);
		webView.startAnimation(fadeIn);
	}
	
	private void loadReactApp() {
		String url = webVirt.getBaseUrl();
		webView.loadUrl(url);
	}
	
	@Override
	public void onBackPressed() {
		if (isWebViewVisible && webView.canGoBack()) {
			webView.goBack();
			} else if (isWebViewVisible) {
			showWelcomeScreen();
			} else {
			super.onBackPressed();
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		if (webView != null) webView.onResume();
	}
	
	@Override
	protected void onDestroy() {
		// 1. Guardar métricas PRIMERO (antes de destruir nada)
		saveMetricsReport();
		
		// 2. Destruir Nexus
		if (nexus != null) {
			nexus.destroy();
			nexus = null;
		}
		
		// 3. Destruir WebView
		if (webView != null) {
			webView.loadUrl("about:blank");
			webView.clearHistory();
			webView.clearCache(true);
			webView.destroy();
			webView = null;
		}
		
		// 4. Destruir WebVirt (ya llama a fileLoader.destroy() internamente)
		if (webVirt != null) {
			webVirt.destroy();
			webVirt = null;
		}
		
		super.onDestroy();
	}
	
	/**
	* Guarda el reporte de métricas si hay datos registrados.
	* Debe ejecutarse antes de destruir WebVirt o FileLoader.
	*/
	private void saveMetricsReport() {
		if (webVirt == null) return;
		
		WebVirtMetricsCollector collector = webVirt.getMetricsCollector();
		if (!(collector instanceof WebVirtMetrics)) return;
		
		WebVirtMetrics metrics = (WebVirtMetrics) collector;
		if (metrics.getTotalAssetsLoaded() == 0) return;
		
		metrics.endSession();
		String report = metrics.generateReport(this);
		Log.d(TAG, "📊 Reporte de métricas guardado en Documents/webvirt_metrics.txt");
		Log.d(TAG, report);
	}
	
	@Override
	public void onTrimMemory(int level) {
		super.onTrimMemory(level);
		if (webVirt != null) {
			webVirt.onTrimMemory(level);
		}
	}
	
	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		if (webView != null) webView.saveState(outState);
	}
	
	@Override
	protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		if (webView != null) webView.restoreState(savedInstanceState);
	}
}