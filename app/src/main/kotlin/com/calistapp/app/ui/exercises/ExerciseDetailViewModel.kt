package com.calistapp.app.ui.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.ai.AiResult
import com.calistapp.app.data.ai.ExerciseCoachRepository
import com.calistapp.app.data.exercise.ExerciseRepository
import com.calistapp.core.model.Exercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ExerciseAiState {
    data object Idle : ExerciseAiState
    data object Loading : ExerciseAiState
    data class Error(val message: String) : ExerciseAiState
}

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExerciseRepository,
    private val coach: ExerciseCoachRepository,
) : ViewModel() {

    private val exerciseId: String = checkNotNull(savedStateHandle["exerciseId"])

    val exercise: StateFlow<Exercise?> = repository.observe(exerciseId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _aiState = MutableStateFlow<ExerciseAiState>(ExerciseAiState.Idle)
    val aiState: StateFlow<ExerciseAiState> = _aiState.asStateFlow()

    fun enrich() {
        val current = exercise.value ?: return
        if (_aiState.value is ExerciseAiState.Loading) return
        _aiState.value = ExerciseAiState.Loading
        viewModelScope.launch {
            when (val result = coach.enrich(current)) {
                is AiResult.Success -> _aiState.value = ExerciseAiState.Idle
                is AiResult.Failure -> _aiState.value = ExerciseAiState.Error(result.message)
            }
        }
    }
}
