package com.calistapp.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.exercise.ExercisePrefsRepository
import com.calistapp.app.data.exercise.ExerciseRepository
import com.calistapp.app.data.profile.ProfileRepository
import com.calistapp.app.data.profile.WeightRepository
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.TrainingGoals
import com.calistapp.core.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val weightRepository: WeightRepository,
    private val exercisePrefs: ExercisePrefsRepository,
    exerciseRepository: ExerciseRepository,
) : ViewModel() {

    /**
     * Null until the stored profile has actually been read.
     *
     * The form seeds itself from the first value it sees, so handing it a placeholder default would
     * have it latch onto 30 years old and 75 kg and then ignore the real profile when DataStore
     * finally delivered it.
     */
    val profile: StateFlow<UserProfile?> = profileRepository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isOnboarded: StateFlow<Boolean> = profileRepository.isOnboarded
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val goals: StateFlow<TrainingGoals?> = profileRepository.goals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(profile: UserProfile) {
        viewModelScope.launch {
            profileRepository.save(profile)
            // Every profile save with a weight in it is a dated reading worth keeping — that's the
            // whole bodyweight history, with nothing extra for the user to remember to do.
            weightRepository.record(profile.weightKg)
        }
    }

    fun saveGoals(goals: TrainingGoals) {
        viewModelScope.launch { profileRepository.saveGoals(goals) }
    }

    /** The dataset exercises the user has hidden from the library — the restore list. */
    val hiddenExercises: StateFlow<List<Exercise>> =
        combine(exerciseRepository.observeAll(), exercisePrefs.hiddenIds) { all, hidden ->
            if (hidden.isEmpty()) emptyList() else all.filter { it.id in hidden }.sortedBy { it.name }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(exerciseId: String) {
        viewModelScope.launch { exercisePrefs.unhide(exerciseId) }
    }
}
