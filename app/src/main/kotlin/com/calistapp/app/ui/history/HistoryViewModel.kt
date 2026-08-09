package com.calistapp.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.profile.ProfileRepository
import com.calistapp.app.data.profile.WeightRepository
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.core.model.SessionOverview
import com.calistapp.core.progress.BodyMass
import com.calistapp.core.progress.BodyMassTrend
import com.calistapp.core.progress.TrainingProgress
import com.calistapp.core.progress.summarizeProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    profileRepository: ProfileRepository,
    weightRepository: WeightRepository,
) : ViewModel() {

    val sessions: StateFlow<List<SessionOverview>> = sessionRepository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Trends and personal bests over the whole history.
     *
     * Recomputed from what's stored rather than maintained incrementally: it's a pass over set logs,
     * cheap next to what it replaced, and it can never drift out of step with the sessions it
     * describes. The week window is re-evaluated on collection so it doesn't go stale overnight.
     */
    val progress: StateFlow<TrainingProgress?> =
        combine(sessionRepository.observePerformed(), profileRepository.profile) { sessions, profile ->
            summarizeProgress(
                sessions = sessions,
                nowMs = System.currentTimeMillis(),
                weekCount = WEEKS_SHOWN,
                // Training load is a function of your heart-rate reserve, so it needs the profile.
                profile = profile,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Bodyweight against the training logged over the same window.
     *
     * Explicitly *not* a check on the calorie engine — see [BodyMass] for why that isn't possible
     * without knowing intake. It's arithmetic offered as context, and the card says so.
     */
    val bodyMass: StateFlow<BodyMassTrend?> =
        combine(weightRepository.entries, sessionRepository.observePerformed()) { entries, sessions ->
            BodyMass.trend(entries) { fromMs, toMs ->
                sessions.filter { it.startMs in fromMs..toMs }.sumOf { it.kcal }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private companion object {
        /** A quarter — long enough to show a trend, short enough to read on a phone. */
        const val WEEKS_SHOWN = 12
    }
}
