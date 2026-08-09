package com.calistapp.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.profile.ProfileRepository
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.app.data.sync.WatchConnectionMonitor
import com.calistapp.app.data.sync.WatchLinkState
import com.calistapp.app.session.LiveSession
import com.calistapp.app.session.SessionController
import com.calistapp.core.model.SessionOverview
import com.calistapp.core.model.TrainingGoals
import com.calistapp.core.model.UserProfile
import com.calistapp.core.time.startOfWeekMs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class WeekStats(val totalKcal: Int, val sessions: Int, val activeMinutes: Int)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    sessionRepository: SessionRepository,
    sessionController: SessionController,
    private val watchConnection: WatchConnectionMonitor,
) : ViewModel() {

    val watchLink: StateFlow<WatchLinkState> = watchConnection.state

    fun reconnectWatch() { watchConnection.reconnect() }

    val profile: StateFlow<UserProfile> = profileRepository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile())

    val isOnboarded: StateFlow<Boolean> = profileRepository.isOnboarded
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val goals: StateFlow<TrainingGoals> = profileRepository.goals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainingGoals())

    val live: StateFlow<LiveSession?> = sessionController.live

    private val allSessions: StateFlow<List<SessionOverview>> = sessionRepository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent: StateFlow<List<SessionOverview>> = allSessions
        .map { it.take(6) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The week is combined in as a flow rather than read inside the map, so it keeps up with the
     * clock. Derived from the session list alone, the window was only recalculated when a workout
     * was saved — leave the app open past midnight on a Sunday and it would still be totalling last
     * week. Only collected while a screen is watching, and only re-emits when the week turns over.
     */
    private fun currentWeekStarts(): Flow<Long> = flow {
        while (true) {
            emit(startOfWeekMs(System.currentTimeMillis()))
            delay(WEEK_CHECK_INTERVAL_MS)
        }
    }.distinctUntilChanged()

    val week: StateFlow<WeekStats> = combine(allSessions, currentWeekStarts()) { sessions, weekStart ->
        val thisWeek = sessions.filter { it.startMs >= weekStart }
        WeekStats(
            totalKcal = thisWeek.sumOf { it.totalKcal },
            sessions = thisWeek.size,
            activeMinutes = (thisWeek.sumOf { it.activeDurationMs } / 60_000L).toInt(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekStats(0, 0, 0))

    private companion object {
        /** Fine enough to catch the week turning over without being a wakeup source. */
        const val WEEK_CHECK_INTERVAL_MS = 60_000L
    }
}
