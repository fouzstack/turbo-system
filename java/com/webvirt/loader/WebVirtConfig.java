package com.webvirt;

import android.content.Context;

import com.webvirt.extensions.cache.CachePolicy;
import com.webvirt.extensions.compression.CompressionStrategy;
import com.webvirt.extensions.manifest.AssetManifest;
import com.webvirt.loader.WebVirtFileLoader;

import java.util.Set;

/**
* Configuración centralizada para WebVirt.
*
* Proporciona un Builder que internamente usa WebVirtFileLoader.Builder.
* Fachada simplificada para configuración común.
*
* Uso:
* <pre>
* WebVirtFileLoader loader = new WebVirtConfig.Builder(context)
*     .domain("myapp.local")
*     .cacheSize(100 * 1024 * 1024)
*     .buildLoader();  // ← Devuelve WebVirtFileLoader directamente
* </pre>
*
* @since 3.6.0
*/
public class WebVirtConfig {
	
	private final Context context;
	private final String domain;
	private final int cacheEntries;
	private final long maxFileSize;
	private final long maxCacheSizeBytes;
	private final boolean mergeHeaders;
	private final boolean debugMode;
	private final boolean enablePrecache;
	private final String cspPolicy;
	private final WebVirtMetricsCollector metricsCollector;
	private final CompressionStrategy compressionStrategy;
	private final CachePolicy cachePolicy;
	private final AssetManifest assetManifest;
	private final Set<String> allowedExtensions;
	
	private WebVirtConfig(Builder builder) {
		this.context = builder.context;
		this.domain = builder.domain;
		this.cacheEntries = builder.cacheEntries;
		this.maxFileSize = builder.maxFileSize;
		this.maxCacheSizeBytes = builder.maxCacheSizeBytes;
		this.mergeHeaders = builder.mergeHeaders;
		this.debugMode = builder.debugMode;
		this.enablePrecache = builder.enablePrecache;
		this.cspPolicy = builder.cspPolicy;
		this.metricsCollector = builder.metricsCollector;
		this.compressionStrategy = builder.compressionStrategy;
		this.cachePolicy = builder.cachePolicy;
		this.assetManifest = builder.assetManifest;
		this.allowedExtensions = builder.allowedExtensions;
	}
	
	/**
	* Construye un WebVirtFileLoader a partir de esta configuración.
	*
	* @return WebVirtFileLoader configurado y listo para usar
	*/
	public WebVirtFileLoader buildLoader() {
		WebVirtFileLoader.Builder loaderBuilder = new WebVirtFileLoader.Builder(context);
		
		// Transferir toda la configuración al Builder interno
		if (domain != null) loaderBuilder.setDomain(domain);
		if (allowedExtensions != null) loaderBuilder.setAllowedExtensions(allowedExtensions);
		if (cspPolicy != null) loaderBuilder.setCspPolicy(cspPolicy);
		if (metricsCollector != null) loaderBuilder.setMetricsCollector(metricsCollector);
		if (compressionStrategy != null) loaderBuilder.setCompressionStrategy(compressionStrategy);
		if (cachePolicy != null) loaderBuilder.setCachePolicy(cachePolicy);
		if (assetManifest != null) loaderBuilder.setAssetManifest(assetManifest);
		
		loaderBuilder.setCacheEntries(cacheEntries);
		loaderBuilder.setMaxFileSize(maxFileSize);
		loaderBuilder.setMaxCacheSize(maxCacheSizeBytes);
		loaderBuilder.setMergeHeaders(mergeHeaders);
		loaderBuilder.setDebugMode(debugMode);
		loaderBuilder.setPrecacheEnabled(enablePrecache);
		
		return loaderBuilder.build();
	}
	
	// ==================== BUILDER ====================
	
	/**
	* Builder para WebVirtConfig.
	*/
	public static class Builder {
		private final Context context;
		private String domain = "app.local";
		private int cacheEntries = 200;
		private long maxFileSize = 10 * 1024 * 1024;
		private long maxCacheSizeBytes = 50 * 1024 * 1024;
		private boolean mergeHeaders = true;
		private boolean debugMode = false;
		private boolean enablePrecache = true;
		
		private String cspPolicy =
		"default-src 'self'; " +
		"script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
		"style-src 'self' 'unsafe-inline'; " +
		"img-src 'self' data: blob:; " +
		"font-src 'self' data:; " +
		"media-src 'self' data: blob:; " +
		"connect-src 'self' data: blob:;";
		
		private WebVirtMetricsCollector metricsCollector = WebVirtMetricsCollector.NOOP;
		private CompressionStrategy compressionStrategy = CompressionStrategy.NOOP;
		private CachePolicy cachePolicy = CachePolicy.SPA_IMMUTABLE;
		private AssetManifest assetManifest = AssetManifest.NOOP;
		private Set<String> allowedExtensions;
		
		public Builder(Context context) {
			this.context = context.getApplicationContext();
		}
		
		public Builder domain(String domain) {
			this.domain = domain;
			return this;
		}
		
		public Builder cacheEntries(int entries) {
			this.cacheEntries = entries;
			return this;
		}
		
		public Builder maxFileSize(long bytes) {
			this.maxFileSize = bytes;
			return this;
		}
		
		public Builder cacheSize(long bytes) {
			this.maxCacheSizeBytes = bytes;
			return this;
		}
		
		public Builder csp(String csp) {
			this.cspPolicy = csp;
			return this;
		}
		
		public Builder mergeHeaders(boolean merge) {
			this.mergeHeaders = merge;
			return this;
		}
		
		public Builder debug(boolean debug) {
			this.debugMode = debug;
			return this;
		}
		
		public Builder precache(boolean enabled) {
			this.enablePrecache = enabled;
			return this;
		}
		
		public Builder metrics(WebVirtMetricsCollector collector) {
			this.metricsCollector = collector;
			return this;
		}
		
		public Builder compression(CompressionStrategy strategy) {
			this.compressionStrategy = strategy;
			return this;
		}
		
		public Builder cachePolicy(CachePolicy policy) {
			this.cachePolicy = policy;
			return this;
		}
		
		public Builder manifest(AssetManifest manifest) {
			this.assetManifest = manifest;
			return this;
		}
		
		public Builder allowedExtensions(Set<String> extensions) {
			this.allowedExtensions = extensions;
			return this;
		}
		
		/**
		* Construye la configuración.
		*/
		public WebVirtConfig build() {
			return new WebVirtConfig(this);
		}
		
		/**
		* Construye directamente el WebVirtFileLoader.
		* Atajo para build().buildLoader().
		*/
		public WebVirtFileLoader buildLoader() {
			return build().buildLoader();
		}
	}
}