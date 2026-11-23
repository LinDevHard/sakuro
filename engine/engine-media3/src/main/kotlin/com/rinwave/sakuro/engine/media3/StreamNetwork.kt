package com.rinwave.sakuro.engine.media3

import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.NetworkTypeObserver
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.rinwave.sakuro.core.model.StreamHeaders
import com.rinwave.sakuro.core.player.MediaCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.Locale
import java.util.TreeSet

/**
 * Process-wide network plumbing for streams, shared by every [Media3PlayerEngine]:
 *
 * - browser-like request headers (a reduced Chrome-on-Android user agent tagged `Sakuro/x.y`,
 *   `Accept`, `Accept-Language`), so CDNs that reject unknown clients still answer;
 * - a session cookie jar, so cookies a server sets (signed-CDN and anti-bot flows) are sent
 *   back on later playlist/segment requests the way a browser would;
 * - one [SimpleCache] with a resizable LRU limit. Manifests and playlists always go to the
 *   network: a cached live playlist would freeze the stream.
 *
 * Cache rules follow Reddit's production notes on ExoPlayer (Alexey Bykov, "Improving video
 * playback with ExoPlayer"):
 * - internal `cacheDir`, not external storage, which some devices (e.g. OPPO) do not have;
 * - a dedicated directory: SimpleCache deletes files it does not recognise in its folder;
 * - exactly one instance per process (a folder can only be owned once);
 * - never constructed on the main thread: the constructor reads the index from disk and was a
 *   source of ANRs. [warmUp] opens it on IO at app start, and data sources resolve it lazily on
 *   loader threads;
 * - a cache-key factory ([StreamCacheKey]) so re-signed URLs still hit;
 * - explicit eviction ([ResizableLruEvictor]).
 */
@OptIn(UnstableApi::class)
internal class StreamNetwork private constructor(context: Context) : MediaCache {

    private val appContext = context.applicationContext
    private val evictor = ResizableLruEvictor(MediaCache.DEFAULT_LIMIT_MB * MediaCache.MB)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Opened off the main thread ([warmUp] or the first loader thread). Null when it cannot be
     * opened (disk error, folder already owned): playback then simply bypasses the cache.
     */
    private val cacheLazy = lazy {
        runCatching {
            SimpleCache(File(appContext.cacheDir, CACHE_DIR), evictor, StandaloneDatabaseProvider(appContext))
        }.getOrNull()
    }

    private fun cacheOrNull(): SimpleCache? {
        // Never pay the index read on the UI thread; that request just goes uncached.
        if (!cacheLazy.isInitialized() && Looper.myLooper() == Looper.getMainLooper()) return null
        return cacheLazy.value
    }

    private val prefs by lazy { appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    /** Last measured bitrate per Media3 network type, loaded on IO by [warmUp]. */
    @Volatile
    private var savedBitrates: Map<Int, Long> = emptyMap()

    /**
     * One meter for every player, seeded with the last session's measurement per network type.
     * Media3's built-in starting values come from a per-country, per-network-type table that can
     * be badly off (Reddit's example: it rates UK 3G above 4G), so the first quality choice of
     * an adaptive stream used to be a guess. Built on first use, after [warmUp] had a chance
     * to load the saved values.
     */
    val bandwidthMeter: DefaultBandwidthMeter by lazy {
        DefaultBandwidthMeter.Builder(appContext)
            .apply { savedBitrates.forEach { (type, bitrate) -> setInitialBitrateEstimate(type, bitrate) } }
            .build()
    }

    /** Remembers what the meter measured on the current network type; call when a player is released. */
    fun saveBandwidthEstimate() {
        val estimate = bandwidthMeter.bitrateEstimate
        if (estimate !in PLAUSIBLE_BITRATE) return
        val type = NetworkTypeObserver.getInstance(appContext).networkType
        if (type == C.NETWORK_TYPE_UNKNOWN || type == C.NETWORK_TYPE_OFFLINE) return
        io.launch { prefs.edit().putLong(KEY_BITRATE_PREFIX + type, estimate).apply() }
    }

    private val _usedBytes = MutableStateFlow(0L)
    override val usedBytes: StateFlow<Long> = _usedBytes.asStateFlow()

    val browserHeaders: Map<String, String> = mapOf(
        StreamHeaders.USER_AGENT to browserUserAgent(appContext),
        "Accept" to "*/*",
        "Accept-Language" to acceptLanguage(Locale.getDefault()),
    )

    init {
        // HttpURLConnection consults the process-wide handler; keep one if the host app set it.
        if (CookieHandler.getDefault() == null) {
            CookieHandler.setDefault(CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER))
        }
    }

    /**
     * A data source chain for one engine: [headers] are read per request so a newly loaded
     * item's headers apply without rebuilding the player.
     */
    fun dataSourceFactory(headers: () -> Map<String, String>): DataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            // No setUserAgent(): Media3 would set it after the request headers and win over a
            // per-link User-Agent. The browser default rides in the default properties instead.
            .setDefaultRequestProperties(browserHeaders)
            .setConnectTimeoutMs(HTTP_CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(HTTP_READ_TIMEOUT_MS)
            // Plenty of CDNs redirect https → http and back; cleartext is allowed app-wide.
            .setAllowCrossProtocolRedirects(true)
        val withHeaders = ResolvingDataSource.Factory(http) { spec ->
            val extra = headers()
            if (extra.isEmpty()) spec else spec.withAdditionalHeaders(extra)
        }
        // Data sources are created on Media3's loader/playback threads, so resolving the cache
        // here keeps SimpleCache's disk access off the main thread.
        val selective = DataSource.Factory {
            val direct = withHeaders.createDataSource()
            val cache = if (evictor.maxBytes > 0) cacheOrNull() else null
            if (cache == null) {
                direct
            } else {
                SelectiveCacheDataSource(
                    cached = CacheDataSource.Factory()
                        .setCache(cache)
                        .setCacheKeyFactory { spec -> spec.key ?: StreamCacheKey.of(spec.uri.toString()) }
                        .setUpstreamDataSourceFactory(withHeaders)
                        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                        .createDataSource(),
                    direct = direct,
                    useCache = { spec -> evictor.maxBytes > 0 && isCacheable(spec) },
                )
            }
        }
        // DefaultDataSource keeps content://, file:// and asset URIs working; http(s) goes to [selective].
        return DefaultDataSource.Factory(appContext, selective)
    }

    /**
     * App-start work kept off the main thread: the saved bandwidth estimates and, when caching
     * is on, the cache index (so the first playback does not wait for it).
     */
    fun warmUp(openCache: Boolean) {
        io.launch {
            savedBitrates = prefs.all.mapNotNull { (key, value) ->
                val type = key.removePrefix(KEY_BITRATE_PREFIX).takeIf { key.startsWith(KEY_BITRATE_PREFIX) }
                    ?.toIntOrNull()
                val bitrate = (value as? Long)?.takeIf { it in PLAUSIBLE_BITRATE }
                if (type != null && bitrate != null) type to bitrate else null
            }.toMap()
            if (openCache) publishUsage()
        }
    }

    override fun setLimitBytes(bytes: Long) {
        evictor.maxBytes = bytes.coerceAtLeast(0)
        // Not opened yet (app start): the index load replays every span through the evictor,
        // which applies the new limit then, so there is no reason to touch disk now.
        if (!cacheLazy.isInitialized()) return
        if (bytes <= 0) {
            clear()
        } else {
            io.launch {
                cacheOrNull()?.let(evictor::trim)
                publishUsage()
            }
        }
    }

    override fun refreshUsage() {
        io.launch { publishUsage() }
    }

    override fun clear() {
        io.launch {
            val cache = cacheOrNull() ?: return@launch
            // Spans a playing engine holds locked are skipped by SimpleCache and go on the next clear.
            cache.keys.toList().forEach { key -> runCatching { cache.removeResource(key) } }
            publishUsage()
        }
    }

    /** Runs on IO only. */
    private fun publishUsage() {
        _usedBytes.value = runCatching { cacheOrNull()?.cacheSpace ?: 0L }.getOrDefault(0L)
    }

    companion object {
        private const val CACHE_DIR = "stream-cache"
        private const val PREFS = "stream_network"
        private const val KEY_BITRATE_PREFIX = "bitrate_"

        /** 100 kbit/s … 2 Gbit/s; anything outside is a measurement glitch. */
        private val PLAUSIBLE_BITRATE = 100_000L..2_000_000_000L
        private const val HTTP_CONNECT_TIMEOUT_MS = 15_000
        private const val HTTP_READ_TIMEOUT_MS = 20_000
        private const val CHROME_VERSION = "131.0.0.0"

        @Volatile
        private var instance: StreamNetwork? = null

        fun get(context: Context): StreamNetwork = instance ?: synchronized(this) {
            instance ?: StreamNetwork(context).also { instance = it }
        }

        /** Manifests and playlists change (live) and are tiny; media segments and files are cached. */
        internal fun isCacheable(spec: DataSpec): Boolean {
            val scheme = spec.uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return false
            val path = spec.uri.path?.lowercase().orEmpty()
            return manifestSuffixes.none { path.endsWith(it) }
        }

        private val manifestSuffixes = listOf(".m3u8", ".m3u", ".mpd")

        /**
         * Chrome's reduced user agent (fixed "Android 10; K" instead of the real version and
         * model, as Chrome itself sends) plus a `Sakuro/<version>` product token.
         */
        internal fun browserUserAgent(context: Context): String {
            val version = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "dev"
            return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/$CHROME_VERSION Mobile Safari/537.36 Sakuro/$version"
        }

        /** "ru-RU,ru;q=0.9,en;q=0.8" for a Russian device; English devices get "en-US,en;q=0.9". */
        internal fun acceptLanguage(locale: Locale): String {
            val tag = locale.toLanguageTag().takeIf { it != "und" } ?: return "en-US,en;q=0.9"
            val language = locale.language
            return buildList {
                add(tag)
                if (language.isNotEmpty() && language != tag) add("$language;q=0.9")
                if (language != "en") add("en;q=0.8")
            }.joinToString(",")
        }
    }
}

/** Routes each request either through the cache or straight to the network. */
@OptIn(UnstableApi::class)
internal class SelectiveCacheDataSource(
    private val cached: DataSource,
    private val direct: DataSource,
    private val useCache: (DataSpec) -> Boolean,
) : DataSource {

    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        cached.addTransferListener(transferListener)
        direct.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (useCache(dataSpec)) cached else direct
        active = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(active) { "read() before open()" }.read(buffer, offset, length)

    override fun getUri() = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }
}

/**
 * Least-recently-used eviction like Media3's own evictor, but with a limit that can change
 * at runtime (the settings slider), since a [SimpleCache]'s evictor is fixed at creation.
 */
@OptIn(UnstableApi::class)
internal class ResizableLruEvictor(@Volatile var maxBytes: Long) : CacheEvictor {

    private val spans = TreeSet<CacheSpan>(compareBy<CacheSpan> { it.lastTouchTimestamp }.thenBy { it })
    private var currentSize = 0L

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        synchronized(this) {
            spans.add(span)
            currentSize += span.length
        }
        evict(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        synchronized(this) {
            if (spans.remove(span)) currentSize -= span.length
        }
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    fun trim(cache: Cache) = evict(cache, 0)

    private fun evict(cache: Cache, requiredSpace: Long) {
        while (true) {
            val victim = synchronized(this) {
                if (currentSize + requiredSpace <= maxBytes || spans.isEmpty()) null else spans.first()
            } ?: return
            // removeSpan calls back into onSpanRemoved; if it is a no-op (already gone), drop it here.
            cache.removeSpan(victim)
            synchronized(this) { if (spans.remove(victim)) currentSize -= victim.length }
        }
    }
}

/** The stream cache as the settings screen and the app's DI see it. */
fun streamMediaCache(context: Context): MediaCache = StreamNetwork.get(context)

/** Loads saved bandwidth estimates and opens the stream cache on a background thread; call once at app start. */
fun warmUpStreamNetwork(context: Context, openCache: Boolean) = StreamNetwork.get(context).warmUp(openCache)
