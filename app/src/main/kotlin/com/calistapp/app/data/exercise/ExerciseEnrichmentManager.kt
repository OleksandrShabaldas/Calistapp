package com.calistapp.app.data.exercise

import com.calistapp.app.data.ai.AiResult
import com.calistapp.app.data.ai.ExerciseCoachRepository
import com.calistapp.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optionally enriches the *entire* library with AI coaching content, one exercise at a time with a
 * delay to stay under Gemini's free-tier rate limit. Fully resumable — each enriched exercise is
 * cached, so stopping and restarting picks up where it left off (and already-authored/enriched
 * exercises are skipped).
 */
@Singleton
class ExerciseEnrichmentManager @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val coach: ExerciseCoachRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    data class Progress(
        val running: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        val lastError: String? = null,
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private var job: Job? = null

    /** ~4.5s between calls keeps us comfortably under the ~15 requests/min free-tier limit. */
    private val delayBetweenMs = 4_500L

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            val todo = exerciseRepository.observeAll().first().filterNot { coach.isEnriched(it) }
            _progress.value = Progress(running = true, done = 0, total = todo.size)
            var done = 0
            for (exercise in todo) {
                if (!isActive) break
                when (val result = coach.enrich(exercise)) {
                    is AiResult.Failure -> {
                        _progress.update { it.copy(lastError = result.message) }
                        // A failure usually means quota/key issues — back off longer.
                        delay(delayBetweenMs * 2)
                    }
                    is AiResult.Success -> delay(delayBetweenMs)
                }
                done++
                _progress.update { it.copy(done = done) }
            }
            _progress.update { it.copy(running = false) }
        }
    }

    fun stop() {
        job?.cancel()
        _progress.update { it.copy(running = false) }
    }
}
