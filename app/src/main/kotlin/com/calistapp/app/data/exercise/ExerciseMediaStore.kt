package com.calistapp.app.data.exercise

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * The on-device store for exercise videos — both the streaming cache and the offline download target,
 * so a clip is fetched from the network at most once and then plays instantly, online or off.
 *
 * Two deliberate choices make it an offline store rather than a scratch cache:
 * - It lives in `filesDir`, not `cacheDir`, so Android can't quietly evict it under storage pressure —
 *   media the user chose to download for offline stays downloaded.
 * - It uses a [NoOpCacheEvictor] (never evicts), so "Download all" isn't undone the next time a few
 *   new clips stream in. Storage is reclaimed only when the user asks, via [clear].
 *
 * [SimpleCache] permits one instance per directory, hence the process-wide lazy singleton.
 */
@UnstableApi
object ExerciseMediaStore {

    @Volatile
    private var cache: SimpleCache? = null

    private fun cache(context: Context): SimpleCache {
        val app = context.applicationContext
        return cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(app.filesDir, "exercise_media"),
                NoOpCacheEvictor(),
                StandaloneDatabaseProvider(app),
            ).also { cache = it }
        }
    }

    private fun httpFactory() = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        // Authenticates against the private video repo; empty when no token is configured.
        .setDefaultRequestProperties(ExerciseVideoAuth.headers)

    /** The player's data source: serve from the store, fall back to the network (and cache the result). */
    fun dataSourceFactory(context: Context): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(httpFactory())
            // A network hiccup on a partially-cached clip falls back to the network instead of failing.
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /**
     * Fully download [url] into the store, blocking until done. Safe to re-run: [CacheWriter] skips
     * spans already cached, so an interrupted "Download all" resumes rather than starting over. Must
     * be called off the main thread.
     */
    fun cacheFully(context: Context, url: String, onProgress: (bytesCached: Long, total: Long) -> Unit) {
        val source = CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(httpFactory())
            .createDataSource()
        val writer = CacheWriter(
            source,
            DataSpec(Uri.parse(url)),
            null,
            CacheWriter.ProgressListener { requestLength, bytesCached, _ -> onProgress(bytesCached, requestLength) },
        )
        writer.cache()
    }

    /**
     * A [MediaDataSource] that reads [url] through the very same store the player uses — offline copy
     * first, network (authed) on a miss. Handed to a `MediaMetadataRetriever` so a thumbnail frame can
     * be grabbed from a downloaded clip with no network, and from any clip (including non-faststart
     * ones a remote `setDataSource(url)` chokes on) with full random access. Caller closes it.
     */
    fun frameDataSource(context: Context, url: String): MediaDataSource {
        val factory = dataSourceFactory(context)
        val uri = Uri.parse(url)
        return object : MediaDataSource() {
            private var source: DataSource? = null
            private var nextPos = -1L
            private var total = C.LENGTH_UNSET.toLong()

            @Synchronized
            private fun openAt(position: Long) {
                closeSource()
                val ds = factory.createDataSource()
                val opened = ds.open(DataSpec.Builder().setUri(uri).setPosition(position).build())
                if (opened != C.LENGTH_UNSET.toLong()) total = position + opened
                source = ds
                nextPos = position
            }

            @Synchronized
            override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                if (size == 0) return 0
                if (source == null || position != nextPos) openAt(position)
                val read = source!!.read(buffer, offset, size)
                if (read == C.RESULT_END_OF_INPUT) return -1
                nextPos += read
                return read
            }

            @Synchronized
            override fun getSize(): Long {
                if (total < 0) runCatching { openAt(0) }
                return total
            }

            @Synchronized
            override fun close() = closeSource()

            private fun closeSource() {
                source?.let { runCatching { it.close() } }
                source = null
                nextPos = -1L
            }
        }
    }

    /** Bytes currently held on disk. */
    fun sizeBytes(context: Context): Long = cache(context).cacheSpace

    /** Drop everything — the "clear downloaded media" action. */
    fun clear(context: Context) {
        val c = cache(context)
        c.keys.toList().forEach { key -> c.removeResource(key) }
    }
}
