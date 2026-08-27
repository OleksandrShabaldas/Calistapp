package com.calistapp.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.ai.AiModelTier
import com.calistapp.app.data.ai.AiSettings
import com.calistapp.app.data.ai.AiSettingsRepository
import com.calistapp.app.data.exercise.ExerciseEnrichmentManager
import com.calistapp.app.data.exercise.ExercisePrefsRepository
import com.calistapp.app.data.exercise.ExerciseRepository
import com.calistapp.app.data.exercise.MediaDownloadManager
import com.calistapp.app.data.profile.ProfileRepository
import com.calistapp.app.data.profile.WeightRepository
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.TrainingGoals
import com.calistapp.core.model.UserProfile
import com.calistapp.core.progress.PerformedSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val weightRepository: WeightRepository,
    private val exercisePrefs: ExercisePrefsRepository,
    private val aiSettingsRepository: AiSettingsRepository,
    private val mediaDownloads: MediaDownloadManager,
    private val enrichmentManager: ExerciseEnrichmentManager,
    private val sessionRepository: SessionRepository,
    private val json: Json,
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

    // ---- AI settings (key + the two model tiers) --------------------------------------------------

    /** Null until read, so the form seeds from the stored values rather than the momentary defaults. */
    val aiSettings: StateFlow<AiSettings?> = aiSettingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Persist the whole AI form in one go — the key and both tiers' three model slots. */
    fun saveAiSettings(apiKey: String, thinking: List<String>, fast: List<String>) {
        viewModelScope.launch {
            aiSettingsRepository.setApiKey(apiKey)
            thinking.forEachIndexed { i, id -> aiSettingsRepository.setModel(AiModelTier.THINKING, i, id) }
            fast.forEachIndexed { i, id -> aiSettingsRepository.setModel(AiModelTier.FAST, i, id) }
        }
    }

    fun resetAiModels() = viewModelScope.launch { aiSettingsRepository.resetModels() }

    /** Bulk AI enrichment of the whole exercise library — a background, resumable, cached job. */
    val enrichmentProgress = enrichmentManager.progress
    fun startEnrichAll() = enrichmentManager.start()
    fun stopEnrichAll() = enrichmentManager.stop()

    // ---- Offline media downloads ------------------------------------------------------------------

    val mediaDownload: StateFlow<MediaDownloadManager.Progress> = mediaDownloads.progress

    fun startMediaDownload() = mediaDownloads.start()
    fun stopMediaDownload() = mediaDownloads.stop()
    fun clearMediaDownload() = mediaDownloads.clear()
    fun refreshMediaSize() = mediaDownloads.refreshSize()

    // ---- Data export ------------------------------------------------------------------------------

    /**
     * The whole training record as JSON — every finished session's sets, plan, calories and heart
     * summary (the bulky raw HR streams are left out). Your data, portable and yours to keep.
     */
    suspend fun buildExportJson(): String {
        val sessions = sessionRepository.observePerformed().first()
        return json.encodeToString(ListSerializer(PerformedSession.serializer()), sessions)
    }
}
