package com.calistapp.app.data.exercise

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.calistapp.app.di.ApplicationScope
import com.calistapp.app.di.IoDispatcher
import com.calistapp.core.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Downloads every exercise video into [ExerciseMediaStore] for offline use.
 *
 * Videos are the media that streams from the private repo and occasionally won't load on a flaky
 * connection; pulling them onto the device once makes the live workout screen work with no network at
 * all. The actual loop runs inside [MediaDownloadService] — a foreground service — so it keeps going
 * when the app is backgrounded or the screen is off (a plain app-scoped coroutine gets frozen and its
 * network deferred under Doze, which is why the download used to stall). Resumable:
 * [ExerciseMediaStore] skips already-cached clips, so stopping and restarting picks up where it left
 * off. (Images stream from a public CDN and Coil caches them as you browse, so they aren't in this pass.)
 */
@Singleton
class MediaDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val exerciseRepository: ExerciseRepository,
) {
    data class Progress(
        val running: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        val failed: Int = 0,
        val bytes: Long = 0L,
        val lastError: String? = null,
    ) {
        val fraction: Float get() = if (total > 0) done.toFloat() / total else 0f
    }

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    init {
        scope.launch(io) { refreshSizeInternal() }
    }

    /** Kick off the download by starting the foreground service. No-op if one's already running. */
    fun start() {
        if (_progress.value.running) return
        ContextCompat.startForegroundService(context, Intent(context, MediaDownloadService::class.java))
    }

    fun stop() {
        context.stopService(Intent(context, MediaDownloadService::class.java))
        _progress.update { it.copy(running = false) }
    }

    /**
     * The download loop itself — every exercise video, one after another. Called by
     * [MediaDownloadService] from its foreground scope; suspends until finished or cancelled.
     */
    suspend fun download() = withContext(io) {
        val urls = exerciseRepository.observeAll().first()
            .flatMap { it.media }
            .filter { it.type == MediaType.VIDEO }
            .map { it.url }
            .distinct()
        _progress.value = Progress(running = true, total = urls.size, bytes = ExerciseMediaStore.sizeBytes(context))
        var done = 0
        var failed = 0
        var lastError: String? = null
        for (url in urls) {
            if (!isActive) break
            try {
                ExerciseMediaStore.cacheFully(context, url) { _, _ -> }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                lastError = e.message
            }
            done++
            _progress.update {
                it.copy(done = done, failed = failed, lastError = lastError, bytes = ExerciseMediaStore.sizeBytes(context))
            }
        }
        _progress.update { it.copy(running = false, bytes = ExerciseMediaStore.sizeBytes(context)) }
    }

    /** Delete all downloaded media and reset the counters. */
    fun clear() {
        stop()
        scope.launch(io) {
            ExerciseMediaStore.clear(context)
            _progress.value = Progress(bytes = 0L)
        }
    }

    fun refreshSize() {
        scope.launch(io) { refreshSizeInternal() }
    }

    private fun refreshSizeInternal() {
        _progress.update { it.copy(bytes = ExerciseMediaStore.sizeBytes(context)) }
    }
}
