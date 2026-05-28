package com.webvirt.loader;

import static com.webvirt.WebVirtResponses.escapeHtml;
import static com.webvirt.WebVirtVersion.FULL;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import com.webvirt.WebVirtMetricsCollector;
import com.webvirt.extensions.cache.CachePolicy;
import com.webvirt.extensions.compression.CompressionStrategy;
import com.webvirt.extensions.manifest.AssetManifest;
import com.webvirt.extensions.manifest.AssetManifestEntry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebVirtFileLoader v3.6.0 — Coordinador Modular del Runtime HTTP Embebido.
 *
 * Actúa como façade y orchestrator del runtime HTTP para WebView en Android.
 * Delega en módulos especializados con responsabilidades claras:
 *
 * <pre>
 * WebVirtFileLoader
 * ├── RequestRouter       → Resolución de handlers y rutas
 * ├── AssetLoader         → Carga de assets con caché y coalescencia
 * ├── ResponseEnricher    → Construcción y enriquecimiento HTTP
 * ├── ResponseCache       → Abstracción de caché sobre CacheManager
 * ├── PrecacheManager     → Precarga asíncrona con backpressure
 * ├── RangeRequestHandler → Peticiones HTTP Range (Partial Content)
 * ├── InFlightRequestRegistry → Coalescencia de peticiones concurrentes
 * ├── PathUtils           → Sanitización y validación de paths
 * ├── StreamUtils         → Lectura segura de streams
 * ├── HttpUtils           → Helpers HTTP mínimos
 * └── ErrorResponseFactory → Generación de páginas de error HTML
 * </pre>
 *
 * COMPATIBILIDAD HACIA ATRÁS:
 * <ul>
 *   <li>Toda la API pública se mantiene sin cambios desde v3.5.2</li>
 *   <li>Builder con mismos métodos y defaults</li>
 *   <li>Mismo comportamiento observable (304, ETag, Range, CSP, cache-control)</li>
 *   <li>Mismo rendimiento o superior (snapshot inmutable, lock-free reads)</li>
 * </ul>
 *
 * MEJORAS v3.6.0:
 * <ul>
 *   <li>Arquitectura modular con 13 clases especializadas</li>
 *   <li>WebVirtFileLoader reducido de ~1500 a ~350 líneas</li>
 *   <li>Alta cohesión, bajo acoplamiento</li>
 *   <li>Testeabilidad mejorada (unit testing por dominio)</li>
 *   <li>Extensibilidad para futuras features (gzip, brotli, HTTP/2, streaming)</li>
 * </ul>
 *
 * @since 3.6.0
 */
public class WebVirtFileLoader {

    private static final String TAG = "WebVirtFileLoader";

    // ==================== MÓDULOS ====================

    private final RequestRouter requestRouter;
    private final SecurityManager securityManager;
    private final ResponseEnricher enricher;
    private final ResponseCache responseCache;
    private final AssetLoader assetLoader;
    private final PrecacheManager precacheManager;
    private final RangeRequestHandler rangeHandler;
    private final InFlightRequestRegistry inFlightRegistry;

    private final WebVirtMetricsCollector metricsCollector;
    private final AssetManifest assetManifest;
    private final String allowedDomain;
    private final long maxCacheSizeBytes;

    // ==================== BUILDER ====================

    /**
     * Builder para WebVirtFileLoader.
     *
     * Configura todos los aspectos del runtime antes de construir la instancia.
     * Todos los métodos son opcionales y tienen defaults razonables para SPA.
     */
    public static class Builder {
        private final Context context;
        private String allowedDomain = "app.local";
        private Set<String> allowedExtensions;
        private int cacheEntries = 200;
        private long maxFileSize = 10 * 1024 * 1024;        // 10 MB
        private long maxCacheSizeBytes = 50 * 1024 * 1024;  // 50 MB
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

        /**
         * Crea un nuevo Builder.
         *
         * @param context Contexto de aplicación (se usa applicationContext internamente)
         */
        public Builder(Context context) {
            this.context = context.getApplicationContext();
            this.allowedExtensions = SecurityManager.getDefaultAllowedExtensions();
        }

        /**
         * Configura el dominio virtual que WebVirt intercepta.
         *
         * @param domain Dominio (default: "app.local")
         */
        public Builder setDomain(String domain) {
            if (domain != null && !domain.trim().isEmpty()) {
                this.allowedDomain = domain.trim();
            }
            return this;
        }

        /**
         * Activa/desactiva logs de debug.
         *
         * @param debug true para activar
         */
        public Builder setDebugMode(boolean debug) {
            this.debugMode = debug;
            return this;
        }

        /**
         * Número máximo de entradas en caché.
         *
         * @param entries > 0
         */
        public Builder setCacheEntries(int entries) {
            if (entries > 0) {
                this.cacheEntries = entries;
            }
            return this;
        }

        /**
         * Tamaño máximo de archivo individual permitido.
         *
         * @param bytes > 0
         */
        public Builder setMaxFileSize(long bytes) {
            if (bytes > 0) {
                this.maxFileSize = bytes;
            }
            return this;
        }

        /**
         * Tamaño máximo total de caché en bytes.
         *
         * @param bytes > 0
         */
        public Builder setMaxCacheSize(long bytes) {
            if (bytes > 0) {
                this.maxCacheSizeBytes = bytes;
            }
            return this;
        }

        /**
         * Política CSP personalizada.
         *
         * @param csp String no vacío con la política CSP
         */
        public Builder setCspPolicy(String csp) {
            if (csp != null && !csp.trim().isEmpty()) {
                this.cspPolicy = csp.trim();
            }
            return this;
        }

        /**
         * Activa/desactiva el merge de headers del handler con los del runtime.
         *
         * @param merge true para mergear (default)
         */
        public Builder setMergeHeaders(boolean merge) {
            this.mergeHeaders = merge;
            return this;
        }

        /**
         * Extensiones de archivo permitidas.
         *
         * @param extensions Set de extensiones (copia defensiva interna)
         */
        public Builder setAllowedExtensions(Set<String> extensions) {
            if (extensions != null && !extensions.isEmpty()) {
                this.allowedExtensions = new HashSet<>(extensions);
            }
            return this;
        }

        /**
         * Colector de métricas personalizado.
         *
         * @param collector No nulo (usa WebVirtMetricsCollector.NOOP para desactivar)
         */
        public Builder setMetricsCollector(WebVirtMetricsCollector collector) {
            if (collector != null) {
                this.metricsCollector = collector;
            }
            return this;
        }

        /**
         * Estrategia de compresión. NOOP por defecto (correcto para WebView).
         *
         * @param strategy No nulo
         */
        public Builder setCompressionStrategy(CompressionStrategy strategy) {
            if (strategy != null) {
                this.compressionStrategy = strategy;
            }
            return this;
        }

        /**
         * Política de caché HTTP para headers Cache-Control.
         *
         * @param policy No nulo
         */
        public Builder setCachePolicy(CachePolicy policy) {
            if (policy != null) {
                this.cachePolicy = policy;
            }
            return this;
        }

        /**
         * Manifest de assets con hashing e integridad.
         *
         * @param manifest No nulo (usa AssetManifest.NOOP para desactivar)
         */
        public Builder setAssetManifest(AssetManifest manifest) {
            if (manifest != null) {
                this.assetManifest = manifest;
            }
            return this;
        }

        /**
         * Activa/desactiva la precarga asíncrona de assets críticos.
         *
         * @param enabled true para activar (default)
         */
        public Builder setPrecacheEnabled(boolean enabled) {
            this.enablePrecache = enabled;
            return this;
        }

        /**
         * Construye el WebVirtFileLoader completamente configurado.
         *
         * @return Instancia lista para usar
         */
        public WebVirtFileLoader build() {
            return new WebVirtFileLoader(this);
        }
    }

    // ==================== CONSTRUCTOR PRIVADO ====================

    private WebVirtFileLoader(Builder builder) {
        this.allowedDomain = builder.allowedDomain;
        this.maxCacheSizeBytes = builder.maxCacheSizeBytes;
        this.metricsCollector = builder.metricsCollector;
        this.assetManifest = builder.assetManifest;

        // Construir módulos en orden de dependencia
        this.requestRouter = new RequestRouter();
        this.securityManager = new SecurityManager(builder.allowedExtensions, builder.maxFileSize);
        this.enricher = new ResponseEnricher(builder.cspPolicy, builder.cachePolicy, builder.mergeHeaders);

        CacheManager cacheManager = new CacheManager(builder.maxCacheSizeBytes, builder.cacheEntries);
        this.responseCache = new ResponseCache(cacheManager, enricher);

        this.inFlightRegistry = new InFlightRequestRegistry();

        this.assetLoader = new AssetLoader(
            requestRouter, responseCache, enricher,
            inFlightRegistry, metricsCollector);

        this.precacheManager = new PrecacheManager(
            requestRouter, securityManager, responseCache,
            builder.assetManifest, builder.enablePrecache);

        this.rangeHandler = new RangeRequestHandler(requestRouter);
    }

    // ==================== API PÚBLICA (COMPATIBLE CON v3.5.2) ====================

    /**
     * Registra un handler para un prefijo de path.
     *
     * Los handlers se evalúan por prefijo más largo primero.
     * Múltiples llamadas con el mismo prefijo sobrescriben el handler anterior.
     *
     * @param pathPrefix Prefijo de ruta (ej: "/assets/", "/api/")
     * @param handler    Handler que sirve los recursos bajo ese prefijo
     * @return this para encadenamiento
     */
    public WebVirtFileLoader addPathHandler(String pathPrefix, PathHandler handler) {
        requestRouter.register(pathPrefix, handler);
        return this;
    }

    /**
     * Punto de entrada principal del runtime HTTP.
     *
     * Debe llamarse desde {@code WebViewClient.shouldInterceptRequest()}.
     * Procesa la petición completa: validación, routing, caché, coalescencia,
     * enriquecimiento HTTP, range requests y precarga.
     *
     * @param request Petición del WebView
     * @return WebResourceResponse o null si no debe interceptarse
     */
    public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
        Uri uri = request.getUrl();

        // Validación de esquema
        String scheme = uri.getScheme();
        if (!"https".equals(scheme) && !"http".equals(scheme)) return null;

        // Validación de dominio
        String host = uri.getHost();
        if (!allowedDomain.equals(host)) return null;

        // Extracción segura de path
        String path = PathUtils.extractSafePath(uri.getPath());
        if (path == null) return null;

        // Disparar precache si es HTML principal
        precacheManager.triggerIfMainHtml(path);

        // Resolución con AssetManifest
        AssetManifestEntry manifestEntry = assetManifest.resolve(path);
        String resolvedPath = manifestEntry != null ? manifestEntry.hashedPath : path;
        String integrityHash = manifestEntry != null ? manifestEntry.integrity : null;

        // Validación de seguridad
        if (!securityManager.isPathAllowed(resolvedPath)) return null;

        // Range request (Partial Content)
        String rangeHeader = HttpUtils.getRequestHeader(request, "Range");
        if (rangeHeader != null) {
            metricsCollector.recordRangeRequest();
            return rangeHandler.handleRangeRequest(resolvedPath, rangeHeader, request);
        }

        // Carga normal con caché
        boolean cacheable = PathUtils.isCacheableStatic(resolvedPath);
        return assetLoader.load(resolvedPath, request, cacheable, integrityHash);
    }

    // ==================== API DE PRECARGA ====================

    /**
     * Precarga un asset específico de forma asíncrona.
     *
     * Útil para assets que se sabe que se necesitarán pronto
     * pero no son detectados automáticamente como críticos.
     *
     * @param path Ruta del asset a precargar
     */
    public void preloadAsset(String path) {
        precacheManager.preloadAsset(path);
    }

    /**
     * Precarga múltiples assets de forma asíncrona.
     *
     * @param paths Rutas de los assets a precargar
     */
    public void preloadAssets(String... paths) {
        precacheManager.preloadAssets(paths);
    }

    /**
     * Activa o desactiva la precarga automática.
     *
     * Cuando se desactiva, se limpia la caché de precarga.
     *
     * @param enabled true para activar, false para desactivar
     */
    public void setPrecacheEnabled(boolean enabled) {
        precacheManager.setEnabled(enabled);
    }

    /**
     * Verifica si la precarga automática está activada.
     *
     * @return true si está activada
     */
    public boolean isPrecacheEnabled() {
        return precacheManager.isEnabled();
    }

    // ==================== API DE CACHÉ ====================

    /**
     * Invalida una entrada específica de la caché.
     *
     * @param path Ruta del recurso a invalidar
     */
    public void invalidateCache(String path) {
        responseCache.invalidate(path);
    }

    /**
     * Limpia completamente la caché y la precarga.
     */
    public void clearCache() {
        responseCache.clear();
        precacheManager.clear();
    }

    /**
     * Libera memoria según el nivel de presión del sistema.
     *
     * Debe llamarse desde {@code Application.onTrimMemory()} o
     * {@code ComponentCallbacks2.onTrimMemory()}.
     *
     * @param level Nivel de trim memory (TRIM_MEMORY_*)
     */
    public void trimMemory(int level) {
        responseCache.trimMemory(level, maxCacheSizeBytes);
    }

    // ==================== MÉTRICAS DE CACHÉ ====================

    /** @return Número actual de entradas en caché */
    public int getCacheEntryCount() {
        return responseCache.getEntryCount();
    }

    /** @return Tamaño actual de la caché en bytes */
    public long getCacheSizeBytes() {
        return responseCache.getCurrentSizeBytes();
    }

    /** @return Número de assets precargados */
    public int getPrecachedAssetCount() {
        return precacheManager.getCachedCount();
    }

    /** @return Bytes totales de assets precargados */
    public long getPrecachedBytes() {
        return precacheManager.getCachedBytes();
    }

    /** @return Tasa de aciertos de caché (0.0 a 1.0) */
    public double getCacheHitRate() {
        return responseCache.getHitRate();
    }

    /** @return Número total de aciertos de caché */
    public long getCacheHitCount() {
        return responseCache.getHitCount();
    }

    /** @return Número total de fallos de caché */
    public long getCacheMissCount() {
        return responseCache.getMissCount();
    }

    /** @return Número total de desalojos de caché */
    public long getCacheEvictionCount() {
        return responseCache.getEvictionCount();
    }

    // ==================== LIFECYCLE ====================

    /**
     * Destruye el runtime liberando todos los recursos.
     *
     * Detiene la precarga, limpia la caché y cierra el executor.
     * Debe llamarse cuando el WebView ya no se use.
     */
    public void destroy() {
        precacheManager.shutdown();
        clearCache();
        inFlightRegistry.clear();
    }

    // ==================== CLASES INTERNAS PRIVADAS ====================

    // ---------- PathUtils ----------

    /**
     * Operaciones seguras de path.
     * Clase utilitaria sin estado, sin dependencias.
     */
    private static final class PathUtils {

        private static final Set<String> CACHEABLE_EXTENSIONS;

        static {
            Set<String> set = new HashSet<>();
            set.add("css");
            set.add("js");
            set.add("mjs");
            set.add("json");
            set.add("map");
            set.add("woff");
            set.add("woff2");
            set.add("ttf");
            set.add("otf");
            set.add("png");
            set.add("svg");
            set.add("ico");
            set.add("webmanifest");
            set.add("html");
            set.add("htm");
            CACHEABLE_EXTENSIONS = Collections.unmodifiableSet(set);
        }

        private PathUtils() {
            throw new AssertionError("No instances");
        }

        static String extractSafePath(String path) {
            if (path == null || path.isEmpty()) return "/";
            try {
                path = URLDecoder.decode(path, "UTF-8");
                path = sanitizePath(path);
                return path;
            } catch (Exception e) {
                return null;
            }
        }

        static String sanitizePath(String path) {
            if (path == null) return null;
            path = path.replace('\\', '/');
            while (path.contains("//")) {
                path = path.replace("//", "/");
            }
            if (path.contains("..")) return null;
            if (!path.startsWith("/")) path = "/" + path;
            return path;
        }

        static String getExtension(String path) {
            if (path == null) return null;
            int lastDot = path.lastIndexOf('.');
            return lastDot > 0 ? path.substring(lastDot + 1).toLowerCase() : null;
        }

        static boolean isMainHtml(String path) {
            return path.equals("/") ||
                   path.equals("/index.html") ||
                   (path.endsWith(".html") && !path.contains("/assets/"));
        }

        static boolean isCacheableStatic(String path) {
            String ext = getExtension(path);
            return ext != null && CACHEABLE_EXTENSIONS.contains(ext);
        }
    }

    // ---------- StreamUtils ----------

    /**
     * Utilidades de lectura segura de streams.
     */
    private static final class StreamUtils {

        static final int MAX_CACHEABLE_READ_BYTES = 5 * 1024 * 1024; // 5 MB

        private StreamUtils() {
            throw new AssertionError("No instances");
        }

        static byte[] readFully(InputStream is) throws IOException {
            return readFullyLimited(is, MAX_CACHEABLE_READ_BYTES);
        }

        static byte[] readFullyLimited(InputStream is, int maxBytes) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;

            while ((read = is.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException(
                        "Stream exceeds limit: " + maxBytes + " bytes (read: " + total + ")");
                }
                baos.write(buffer, 0, read);
            }

            return baos.toByteArray();
        }
    }

    // ---------- HttpUtils ----------

    /**
     * Utilidades HTTP mínimas.
     */
    private static final class HttpUtils {

        private static final ThreadLocal<SimpleDateFormat> HTTP_DATE_FORMAT =
            new ThreadLocal<SimpleDateFormat>() {
                @Override
                protected SimpleDateFormat initialValue() {
                    SimpleDateFormat sdf = new SimpleDateFormat(
                        "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
                    sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
                    return sdf;
                }
            };

        private HttpUtils() {
            throw new AssertionError("No instances");
        }

        static String getRequestHeader(WebResourceRequest request, String headerName) {
            Map<String, String> headers = request.getRequestHeaders();
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(headerName)) return entry.getValue();
                }
            }
            return null;
        }

        static SimpleDateFormat getHttpDateFormat() {
            return HTTP_DATE_FORMAT.get();
        }
    }

    // ---------- RequestRouter ----------

    /**
     * Router de peticiones hacia PathHandlers.
     * Soporta prefijos con lectura lock-free.
     */
    private static final class RequestRouter {

        private final Map<String, PathHandler> handlers = new ConcurrentHashMap<>(4);
        private volatile List<String> sortedPrefixes = Collections.emptyList();
        private final Object prefixWriteLock = new Object();

        void register(String pathPrefix, PathHandler handler) {
            if (pathPrefix == null || handler == null) return;

            handlers.put(pathPrefix, handler);

            synchronized (prefixWriteLock) {
                List<String> newList = new ArrayList<>(sortedPrefixes);
                if (!newList.contains(pathPrefix)) {
                    newList.add(pathPrefix);
                    newList.sort((a, b) -> Integer.compare(b.length(), a.length()));
                    sortedPrefixes = Collections.unmodifiableList(newList);
                }
            }
        }

        PathHandler resolve(String path) {
            PathHandler exact = handlers.get(path);
            if (exact != null) return exact;

            List<String> prefixes = sortedPrefixes;
            for (String prefix : prefixes) {
                if (path.startsWith(prefix)) {
                    return handlers.get(prefix);
                }
            }
            return null;
        }

        Map<String, PathHandler> getHandlers() {
            return handlers;
        }
    }

    // ---------- ResponseEnricher ----------

    /**
     * Construcción y enriquecimiento de respuestas HTTP.
     */
    private static final class ResponseEnricher {

        private final String cspPolicy;
        private final CachePolicy cachePolicy;
        private final boolean mergeHeaders;

        ResponseEnricher(String cspPolicy, CachePolicy cachePolicy, boolean mergeHeaders) {
            this.cspPolicy = cspPolicy;
            this.cachePolicy = cachePolicy;
            this.mergeHeaders = mergeHeaders;
        }

        Map<String, String> buildResponseHeaders(
                String path, String mimeType, String etag, String integrityHash) {

            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", mimeType != null ? mimeType : "application/octet-stream");
            headers.put("Content-Security-Policy", cspPolicy);
            headers.put("X-Content-Type-Options", "nosniff");
            headers.put("X-Frame-Options", "DENY");
            headers.put("X-XSS-Protection", "1; mode=block");
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("ETag", etag);
            headers.put("Vary", "Accept-Encoding");

            if (integrityHash != null) {
                headers.put("Content-Integrity", integrityHash);
            }

            if (mimeType != null) {
                headers.put("Cache-Control", cachePolicy.getCacheControlHeader(mimeType));
            }

            return headers;
        }

        WebResourceResponse enrichResponse(
                WebResourceResponse original, String path, String integrityHash) {

            if (original == null) return null;

            Map<String, String> enrichedHeaders = new LinkedHashMap<>();
            Map<String, String> originalHeaders = original.getResponseHeaders();
            if (mergeHeaders && originalHeaders != null) {
                enrichedHeaders.putAll(originalHeaders);
            }

            if (!enrichedHeaders.containsKey("Content-Security-Policy"))
                enrichedHeaders.put("Content-Security-Policy", cspPolicy);
            if (!enrichedHeaders.containsKey("X-Content-Type-Options"))
                enrichedHeaders.put("X-Content-Type-Options", "nosniff");
            if (!enrichedHeaders.containsKey("X-Frame-Options"))
                enrichedHeaders.put("X-Frame-Options", "DENY");
            if (!enrichedHeaders.containsKey("X-XSS-Protection"))
                enrichedHeaders.put("X-XSS-Protection", "1; mode=block");
            if (!enrichedHeaders.containsKey("Access-Control-Allow-Origin"))
                enrichedHeaders.put("Access-Control-Allow-Origin", "*");

            if (integrityHash != null && !enrichedHeaders.containsKey("Content-Integrity"))
                enrichedHeaders.put("Content-Integrity", integrityHash);

            String mimeType = original.getMimeType();
            if (mimeType != null && !enrichedHeaders.containsKey("Cache-Control")) {
                enrichedHeaders.put("Cache-Control", cachePolicy.getCacheControlHeader(mimeType));
            }

            if (!enrichedHeaders.containsKey("Vary"))
                enrichedHeaders.put("Vary", "Accept-Encoding");

            int statusCode = original.getStatusCode();
            if (statusCode < 100) statusCode = 200;
            String reasonPhrase = original.getReasonPhrase();
            if (reasonPhrase == null || reasonPhrase.isEmpty())
                reasonPhrase = getDefaultReasonPhrase(statusCode);
            String finalMimeType = original.getMimeType();
            if (finalMimeType == null || finalMimeType.isEmpty()) finalMimeType = "text/plain";
            String encoding = original.getEncoding();
            if (encoding == null || encoding.isEmpty()) encoding = "UTF-8";

            return new WebResourceResponse(
                finalMimeType, encoding, statusCode, reasonPhrase,
                enrichedHeaders, original.getData());
        }

        static String getDefaultReasonPhrase(int statusCode) {
            switch (statusCode) {
                case 200: return "OK";
                case 206: return "Partial Content";
                case 304: return "Not Modified";
                case 400: return "Bad Request";
                case 403: return "Forbidden";
                case 404: return "Not Found";
                case 416: return "Range Not Satisfiable";
                case 500: return "Internal Server Error";
                default:  return "OK";
            }
        }
    }

    // ---------- ResponseCache ----------

    /**
     * Abstracción de caché HTTP sobre CacheManager.
     */
    private static final class ResponseCache {

        private final CacheManager cacheManager;
        private final ResponseEnricher enricher;

        ResponseCache(CacheManager cacheManager, ResponseEnricher enricher) {
            this.cacheManager = cacheManager;
            this.enricher = enricher;
        }

        CacheResult lookup(String path, String ifNoneMatch, String ifModifiedSince) {
            CacheManager.CacheEntry entry = cacheManager.getEntry(path);
            if (entry == null) return CacheResult.miss();

            if (ifModifiedSince != null) {
                try {
                    long ifModifiedTime = HttpUtils.getHttpDateFormat()
                        .parse(ifModifiedSince).getTime();
                    if (entry.lastModified <= ifModifiedTime) {
                        return CacheResult.notModified(entry);
                    }
                } catch (ParseException ignored) {}
            }

            if (ifNoneMatch != null && ifNoneMatch.equals(entry.etag)) {
                return CacheResult.notModified(entry);
            }

            return CacheResult.hit(entry);
        }

        void cacheResponseWithHeaders(
                String path, WebResourceResponse response, String integrityHash) {

            try {
                InputStream is = response.getData();
                if (!(is instanceof ByteArrayInputStream)) return;

                ByteArrayInputStream bais = (ByteArrayInputStream) is;
                int available = bais.available();

                if (available == 0 || available > StreamUtils.MAX_CACHEABLE_READ_BYTES) {
                    if (bais.markSupported()) bais.reset();
                    return;
                }

                byte[] data = StreamUtils.readFullyLimited(bais, StreamUtils.MAX_CACHEABLE_READ_BYTES);
                if (data.length == 0) {
                    if (bais.markSupported()) bais.reset();
                    return;
                }

                String etag = CacheManager.generateETag(data);
                String mimeType = response.getMimeType();
                String encoding = response.getEncoding();
                long lastModified = System.currentTimeMillis();

                Map<String, String> headers = enricher.buildResponseHeaders(
                    path, mimeType, etag, integrityHash);

                cacheManager.put(path, data, mimeType, encoding, headers, etag, lastModified);

                if (bais.markSupported()) {
                    bais.reset();
                }

            } catch (IOException e) {
                Log.e(TAG, "Cache limit: " + path + " - " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Cache error: " + path + " - " + e.getMessage());
            }
        }

        void invalidate(String path) { cacheManager.remove(path); }
        void clear() { cacheManager.clear(); }

        void trimMemory(int level, long maxCacheSizeBytes) {
            if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
                cacheManager.clear();
            } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                cacheManager.trimToSize(maxCacheSizeBytes / 4);
            } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE) {
                cacheManager.trimToSize(maxCacheSizeBytes / 2);
            }
        }

        int getEntryCount() { return cacheManager.getEntryCount(); }
        long getCurrentSizeBytes() { return cacheManager.getCurrentSizeBytes(); }
        double getHitRate() { return cacheManager.getHitRate(); }
        long getHitCount() { return cacheManager.getHitCount(); }
        long getMissCount() { return cacheManager.getMissCount(); }
        long getEvictionCount() { return cacheManager.getEvictionCount(); }

        // --- CacheResult ---

        static class CacheResult {
            final CacheManager.CacheEntry entry;
            final boolean hit;
            final boolean notModified;

            private CacheResult(CacheManager.CacheEntry entry, boolean hit, boolean notModified) {
                this.entry = entry;
                this.hit = hit;
                this.notModified = notModified;
            }

            static CacheResult hit(CacheManager.CacheEntry entry) {
                return new CacheResult(entry, true, false);
            }

            static CacheResult miss() {
                return new CacheResult(null, false, false);
            }

            static CacheResult notModified(CacheManager.CacheEntry entry) {
                return new CacheResult(entry, true, true);
            }
        }
    }

    // ---------- InFlightRequestRegistry ----------

    /**
     * Registro de peticiones en vuelo para coalescencia.
     */
    private static final class InFlightRequestRegistry {

        private final ConcurrentHashMap<String, InFlightRequest> inFlightRequests = new ConcurrentHashMap<>();

        InFlightRequest register(String path) {
            InFlightRequest inFlight = inFlightRequests.get(path);

            if (inFlight != null && inFlight.isCompleted()) {
                inFlightRequests.remove(path);
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

        void complete(String path, InFlightRequest request) {
            request.markCompleted();
            inFlightRequests.remove(path, request);
        }

        boolean isCompletedAndCached(InFlightRequest request) {
            return request.isCompleted() && request.isCached();
        }

        void clear() {
            inFlightRequests.clear();
        }

        // --- InFlightRequest ---

        static class InFlightRequest {
            private final Object lock = new Object();
            private volatile boolean completed = false;
            private volatile boolean cached = false;

            Object getLock() { return lock; }
            boolean isCompleted() { return completed; }
            void markCompleted() { this.completed = true; }
            boolean isCached() { return cached; }
            void markCached() { this.cached = true; }
        }
    }

    // ---------- AssetLoader ----------

    /**
     * Orquestador de carga de assets.
     */
    private static final class AssetLoader {

        private final RequestRouter requestRouter;
        private final ResponseCache responseCache;
        private final ResponseEnricher enricher;
        private final InFlightRequestRegistry inFlightRegistry;
        private final WebVirtMetricsCollector metricsCollector;

        AssetLoader(
                RequestRouter requestRouter,
                ResponseCache responseCache,
                ResponseEnricher enricher,
                InFlightRequestRegistry inFlightRegistry,
                WebVirtMetricsCollector metricsCollector) {
            this.requestRouter = requestRouter;
            this.responseCache = responseCache;
            this.enricher = enricher;
            this.inFlightRegistry = inFlightRegistry;
            this.metricsCollector = metricsCollector;
        }

        WebResourceResponse load(
                String path, WebResourceRequest request,
                boolean cacheable, String integrityHash) {

            long startTime = SystemClock.elapsedRealtime();
            long fileSize = 0;
            boolean fromCache = false;

            PathHandler handler = requestRouter.resolve(path);
            if (handler == null) return null;

            try {
                if (cacheable) {
                    String ifNoneMatch = request != null
                        ? HttpUtils.getRequestHeader(request, "If-None-Match") : null;
                    String ifModifiedSince = request != null
                        ? HttpUtils.getRequestHeader(request, "If-Modified-Since") : null;

                    ResponseCache.CacheResult cacheResult = responseCache.lookup(
                        path, ifNoneMatch, ifModifiedSince);

                    if (cacheResult.hit) {
                        if (cacheResult.notModified) {
                            metricsCollector.recordAssetLoad(
                                path, SystemClock.elapsedRealtime() - startTime, true, 0);
                            return cacheResult.entry.to304Response();
                        }

                        fromCache = true;
                        WebResourceResponse response = cacheResult.entry.toResponse();
                        if (response != null) {
                            metricsCollector.recordAssetLoad(
                                path, SystemClock.elapsedRealtime() - startTime, true,
                                cacheResult.entry.size);
                        }
                        return response;
                    }

                    InFlightRequestRegistry.InFlightRequest inFlight =
                        inFlightRegistry.register(path);

                    synchronized (inFlight.getLock()) {
                        try {
                            if (inFlightRegistry.isCompletedAndCached(inFlight)) {
                                ResponseCache.CacheResult freshResult =
                                    responseCache.lookup(path, null, null);
                                if (freshResult.hit) {
                                    fromCache = true;
                                    WebResourceResponse response = freshResult.entry.toResponse();
                                    if (response != null) {
                                        metricsCollector.recordAssetLoad(
                                            path, SystemClock.elapsedRealtime() - startTime,
                                            true, freshResult.entry.size);
                                    }
                                    return response;
                                }
                            }

                            WebResourceResponse response = loadFromHandler(
                                path, handler, request, startTime, cacheable, integrityHash);

                            inFlight.markCompleted();
                            inFlight.markCached();

                            return response;

                        } finally {
                            inFlightRegistry.complete(path, inFlight);
                        }
                    }
                }

                return loadFromHandler(path, handler, request, startTime, false, integrityHash);

            } catch (Exception e) {
                metricsCollector.recordHttpError();
                Log.e(TAG, "Error loading: " + path, e);
                return ErrorResponseFactory.createErrorResponse(500, "Internal Server Error");
            }
        }

        private WebResourceResponse loadFromHandler(
                String path, PathHandler handler, WebResourceRequest request,
                long startTime, boolean cacheable, String integrityHash) {

            long fileSize = 0;

            WebResourceResponse response = handler.handle(path, request);
            if (response == null) return null;

            boolean isBAIS = response.getData() instanceof ByteArrayInputStream;
            if (isBAIS) {
                try {
                    fileSize = response.getData().available();
                } catch (IOException ignored) {}
            }

            if (cacheable && isBAIS) {
                responseCache.cacheResponseWithHeaders(path, response, integrityHash);
            }

            response = enricher.enrichResponse(response, path, integrityHash);

            metricsCollector.recordAssetLoad(
                path, SystemClock.elapsedRealtime() - startTime, false, fileSize);

            return response;
        }
    }

    // ---------- PrecacheManager ----------

    /**
     * Gestor de precarga asíncrona de assets con backpressure.
     */
    private static final class PrecacheManager {

        private static final String TAG = "WebVirtPrecache";
        private static final int MAX_PRECACHED_ASSETS = 20;
        private static final long MAX_PRECACHED_BYTES = 5 * 1024 * 1024;

        private final RequestRouter requestRouter;
        private final SecurityManager securityManager;
        private final ResponseCache responseCache;
        private final AssetManifest assetManifest;

        private final ExecutorService executor;
        private final Set<String> precachedAssets = ConcurrentHashMap.newKeySet();
        private volatile boolean enabled;
        private final AtomicBoolean started = new AtomicBoolean(false);
        private final AtomicLong precachedBytes = new AtomicLong(0);

        PrecacheManager(
                RequestRouter requestRouter,
                SecurityManager securityManager,
                ResponseCache responseCache,
                AssetManifest assetManifest,
                boolean enabled) {
            this.requestRouter = requestRouter;
            this.securityManager = securityManager;
            this.responseCache = responseCache;
            this.assetManifest = assetManifest;
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

        void triggerIfMainHtml(String path) {
            if (enabled && PathUtils.isMainHtml(path)) {
                trigger();
            }
        }

        private void trigger() {
            if (!enabled || executor.isShutdown()) return;
            if (!started.compareAndSet(false, true)) return;

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        int count = 0;
                        for (Map.Entry<String, PathHandler> entry :
                                requestRouter.getHandlers().entrySet()) {
                            if (count >= MAX_PRECACHED_ASSETS) break;
                            if (precachedBytes.get() >= MAX_PRECACHED_BYTES) break;

                            String path = entry.getKey();
                            if (isCriticalAsset(path)) {
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

        boolean precacheAsset(String path) {
            if (path == null || precachedAssets.contains(path)) return false;
            if (precachedAssets.size() >= MAX_PRECACHED_ASSETS) return false;
            if (precachedBytes.get() >= MAX_PRECACHED_BYTES) return false;

            try {
                if (!securityManager.isPathAllowed(path)) return false;

                PathHandler handler = requestRouter.resolve(path);
                if (handler == null) return false;

                WebResourceResponse response = handler.handle(path, null);

                if (response != null && response.getData() instanceof ByteArrayInputStream) {
                    int available = response.getData().available();
                    responseCache.cacheResponseWithHeaders(path, response, null);
                    precachedAssets.add(path);
                    precachedBytes.addAndGet(available);
                    return true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Precache fallo: " + path + " - " + e.getMessage());
            }
            return false;
        }

        void preloadAsset(String path) {
            if (path == null) return;
            if (enabled && !executor.isShutdown()) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        String resolvedPath = resolvePath(path);
                        precacheAsset(resolvedPath);
                    }
                });
            }
        }

        void preloadAssets(String... paths) {
            if (paths == null) return;
            for (String path : paths) {
                preloadAsset(path);
            }
        }

        private String resolvePath(String path) {
            AssetManifestEntry entry = assetManifest.resolve(path);
            return entry != null ? entry.hashedPath : path;
        }

        private boolean isCriticalAsset(String path) {
            if (path == null) return false;
            if (path.equals("/") || path.equals("/index.html")) return true;
            if (path.contains("/assets/index-") ||
                path.contains("/assets/vendor-") ||
                path.contains("/assets/main-") ||
                path.contains("/assets/runtime-")) return true;
            if (path.endsWith(".css") && !path.contains("/chunks/") && !path.contains("/pages/")) {
                String filename = path.substring(path.lastIndexOf('/') + 1);
                if (filename.startsWith("index") || filename.startsWith("main") ||
                    filename.startsWith("app") || filename.startsWith("global")) return true;
            }
            if (path.endsWith(".js") && !path.contains("/chunks/") && !path.contains("/pages/")) {
                String filename = path.substring(path.lastIndexOf('/') + 1);
                if (filename.startsWith("index") || filename.startsWith("main") ||
                    filename.startsWith("app")) return true;
            }
            return false;
        }

        void setEnabled(boolean enabled) {
            this.enabled = enabled;
            if (!enabled) {
                precachedAssets.clear();
                precachedBytes.set(0);
            }
        }

        boolean isEnabled() { return enabled; }
        int getCachedCount() { return precachedAssets.size(); }
        long getCachedBytes() { return precachedBytes.get(); }

        void clear() {
            precachedAssets.clear();
            precachedBytes.set(0);
        }

        void shutdown() {
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
    }

    // ---------- RangeRequestHandler ----------

    /**
     * Maneja peticiones HTTP Range (Partial Content).
     */
    private static final class RangeRequestHandler {

        private final RequestRouter requestRouter;

        RangeRequestHandler(RequestRouter requestRouter) {
            this.requestRouter = requestRouter;
        }

        WebResourceResponse handleRangeRequest(
                String path, String rangeHeader, WebResourceRequest request) {

            PathHandler handler = requestRouter.resolve(path);
            if (handler == null) return null;

            try {
                if (handler instanceof FilePathHandler) {
                    return ((FilePathHandler) handler).handleRange(path, rangeHeader, request);
                }
                if (handler instanceof AssetPathHandler) {
                    return ((AssetPathHandler) handler).handleRange(path, rangeHeader, request);
                }

                WebResourceResponse fullResponse = handler.handle(path, request);
                if (fullResponse == null || fullResponse.getData() == null) return null;

                byte[] data;
                try {
                    data = StreamUtils.readFully(fullResponse.getData());
                } catch (OutOfMemoryError oom) {
                    return fullResponse;
                }

                long fileSize = data.length;
                long[] range = RangeParser.parse(rangeHeader, fileSize);
                if (range == null) return create416Response(fileSize);

                long start = range[0], end = range[1];
                int contentLength = (int) (end - start + 1);

                Map<String, String> headers = new LinkedHashMap<>();
                headers.put("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
                headers.put("Content-Length", String.valueOf(contentLength));
                headers.put("Accept-Ranges", "bytes");
                headers.put("Content-Type", fullResponse.getMimeType());

                byte[] partialData = java.util.Arrays.copyOfRange(data, (int) start, (int) end + 1);

                return new WebResourceResponse(
                    fullResponse.getMimeType(), fullResponse.getEncoding(),
                    206, "Partial Content", headers,
                    new ByteArrayInputStream(partialData));

            } catch (Exception e) {
                return null;
            }
        }

        static WebResourceResponse create416Response(long fileSize) {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Range", "bytes */" + fileSize);
            headers.put("Content-Length", "0");
            return new WebResourceResponse(
                "text/plain", "UTF-8", 416, "Range Not Satisfiable",
                headers, new ByteArrayInputStream(new byte[0]));
        }
    }

    // ---------- ErrorResponseFactory ----------

    /**
     * Generación de respuestas de error HTML.
     */
    private static final class ErrorResponseFactory {

        private ErrorResponseFactory() {
            throw new AssertionError("No instances");
        }

        static WebResourceResponse createErrorResponse(int code, String message) {
            String html = buildErrorHtml(code, message);
            byte[] data = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", "text/html; charset=utf-8");
            headers.put("Content-Length", String.valueOf(data.length));

            return new WebResourceResponse(
                "text/html", "UTF-8", code, "Error", headers,
                new ByteArrayInputStream(data));
        }

        private static String buildErrorHtml(int code, String message) {
            return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                + "<title>Error " + code + "</title>\n<style>\n"
                + "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;"
                + "display:flex;align-items:center;justify-content:center;height:100vh;margin:0;"
                + "background:#1a1a2e;color:#eee}\n"
                + ".error-container{text-align:center;padding:2rem}\n"
                + ".error-code{font-size:6rem;font-weight:700;color:#e74c3c;line-height:1;margin:0}\n"
                + ".error-message{font-size:1rem;color:#999;margin-top:1rem}\n"
                + ".error-footer{margin-top:2rem;padding-top:1rem;border-top:1px solid #333;"
                + "font-size:.8rem;color:#555}\n"
                + "</style>\n</head>\n<body>\n<div class=\"error-container\">\n"
                + "<h1 class=\"error-code\">" + code + "</h1>\n"
                + "<p class=\"error-message\">" + escapeHtml(message) + "</p>\n"
                + "<div class=\"error-footer\">" + FULL + "</div>\n</div>\n</body>\n</html>";
        }
    }
}