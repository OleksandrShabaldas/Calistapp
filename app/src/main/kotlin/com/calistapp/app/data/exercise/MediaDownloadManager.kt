package com.calistapp.app.data.exercise

import android.content.Context
import com.calistapp.app.di.ApplicationScope
import com.calistapp.app.di.IoDispatcher
import com.calistapp.core.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Downloads every exercise video into [ExerciseMediaStore] for offline use.
 *
 * Videos are the media that streams from the private repo and occasionally won't load on a flaky
 * connection; pulling them onto the device once makes the live workout screen work with no network at
 * all. App-scoped so a download survives leaving the settings screen, and resumable — [ExerciseMediaStore]
 * skips already-cached clips, so stopping and restarting picks up where it left off. (Images stream
 * from a public CDN and Coil caches them as you browse, so they aren't part of this pass.)
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

    private var job: Job? = null

    init {
        scope.launch(io) { refreshSizeInternal() }
    }

    /** Download every exercise video. No-op if one's already running. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(io) {
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
    }

    fun stop() {
        job?.cancel()
        _progress.update { it.copy(running = false) }
    }

    /** Delete all downloaded media and reset the counters. */
    fun clear() {
        scope.launch(io) {
            job?.cancel()
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
