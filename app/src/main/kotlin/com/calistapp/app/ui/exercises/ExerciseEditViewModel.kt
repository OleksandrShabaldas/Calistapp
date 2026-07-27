package com.calistapp.app.ui.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.ai.CoachSuggestResult
import com.calistapp.app.data.ai.ExerciseAiSuggestion
import com.calistapp.app.data.ai.ExerciseCoachRepository
import com.calistapp.app.data.exercise.ExerciseRepository
import com.calistapp.app.data.exercise.ExerciseSyncManager
import com.calistapp.app.ui.navigation.Routes
import com.calistapp.core.model.BodyPart
import com.calistapp.core.model.Difficulty
import com.calistapp.core.model.Exercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface EditAiState {
    data object Idle : EditAiState
    data object Loading : EditAiState
    data class Error(val message: String) : EditAiState
}

@HiltViewModel
class ExerciseEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExerciseRepository,
    private val coach: ExerciseCoachRepository,
) : ViewModel() {

    private val editingId: String? = savedStateHandle[Routes.EXERCISE_EDIT_ARG]
    val isNew: Boolean = editingId == null

    /** The exercise to edit, or a blank template for a new one. Drives the form's initial values. */
    val initial: StateFlow<Exercise?> =
        (editingId?.let(repository::observe) ?: flowOf(BLANK))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), if (isNew) BLANK else null)

    private val _aiState = MutableStateFlow<EditAiState>(EditAiState.Idle)
    val aiState: StateFlow<EditAiState> = _aiState.asStateFlow()

    fun generate(draft: Exercise, onResult: (ExerciseAiSuggestion) -> Unit) {
        if (_aiState.value is EditAiState.Loading) return
        if (draft.name.isBlank()) {
            _aiState.value = EditAiState.Error("Add a name first so the AI knows the exercise.")
            return
        }
        _aiState.value = EditAiState.Loading
        viewModelScope.launch {
            when (val result = coach.suggest(draft)) {
                is CoachSuggestResult.Success -> {
                    _aiState.value = EditAiState.Idle
                    onResult(result.suggestion)
                }
                is CoachSuggestResult.Failure -> _aiState.value = EditAiState.Error(result.message)
            }
        }
    }

    fun save(draft: Exercise, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val id = editingId ?: "custom_${UUID.randomUUID()}"
            val toSave = draft.copy(
                id = id,
                source = draft.source.ifBlank { "Custom" },
                // Tag so the sync overlay never overwrites the user's edits.
                tags = (draft.tags + ExerciseSyncManager.EDITED_TAG).distinct(),
            )
            repository.upsert(toSave)
            onSaved(id)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = editingId ?: return
        viewModelScope.launch {
            repository.delete(id)
            onDeleted()
        }
    }

    companion object {
        val BLANK = Exercise(
            id = "",
            name = "",
            bodyPart = BodyPart.OTHER,
            category = "custom",
            difficulty = Difficulty.BEGINNER,
        )
    }
}
