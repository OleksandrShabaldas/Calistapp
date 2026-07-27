package com.calistapp.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.profile.ProfileRepository
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.app.data.sync.WatchConnectionMonitor
import com.calistapp.app.data.sync.WatchLinkState
import com.calistapp.app.session.LiveSession
import com.calistapp.app.session.SessionController
import com.calistapp.core.model.UserProfile
import com.calistapp.core.model.WorkoutSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    val live: StateFlow<LiveSession?> = sessionController.live

    private val allSessions: StateFlow<List<WorkoutSession>> = sessionRepository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent: StateFlow<List<WorkoutSession>> = allSessions
        .map { it.take(6) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val week: StateFlow<WeekStats> = allSessions
        .map { sessions ->
            val cutoff = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
            val recentWeek = sessions.filter { it.startMs >= cutoff }
            WeekStats(
                totalKcal = recentWeek.sumOf { it.summary?.totalKcal ?: 0.0 }.toInt(),
                sessions = recentWeek.size,
                activeMinutes = recentWeek.sumOf { (it.summary?.activeDurationMs ?: 0L) }.toInt() / 60_000,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekStats(0, 0, 0))
}
