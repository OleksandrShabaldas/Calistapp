package com.calistapp.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.core.model.SessionOverview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Sessions tab: every workout, newest first. The Insights tab has its own model
 * ([com.calistapp.app.ui.insights.InsightsViewModel]).
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val sessions: StateFlow<List<SessionOverview>> = sessionRepository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Called once a swiped-away session's undo window has closed. */
    fun deleteSession(id: String) {
        viewModelScope.launch { runCatching { sessionRepository.deleteSession(id) } }
    }
}
