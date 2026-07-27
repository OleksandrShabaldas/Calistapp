package com.calistapp.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.ai.AiResult
import com.calistapp.app.data.ai.InsightsRepository
import com.calistapp.app.data.profile.ProfileRepository
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.core.model.UserProfile
import com.calistapp.core.model.WorkoutSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Error(val message: String) : AiUiState
}

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val insightsRepository: InsightsRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    val session: StateFlow<WorkoutSession?> = sessionRepository.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val profile: StateFlow<UserProfile> = profileRepository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile())

    private val _aiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val aiState: StateFlow<AiUiState> = _aiState.asStateFlow()

    fun generateInsight() {
        val current = session.value ?: return
        val summary = current.summary ?: return
        if (_aiState.value is AiUiState.Loading) return
        _aiState.value = AiUiState.Loading
        viewModelScope.launch {
            when (val result = insightsRepository.analyzeSession(current, summary, profile.value)) {
                is AiResult.Success -> {
                    sessionRepository.updateInsight(sessionId, result.text)
                    _aiState.value = AiUiState.Idle
                }
                is AiResult.Failure -> _aiState.value = AiUiState.Error(result.message)
            }
        }
    }

    fun deleteSession(onDone: () -> Unit) {
        viewModelScope.launch {
            sessionRepository.deleteSession(sessionId)
            onDone()
        }
    }
}
