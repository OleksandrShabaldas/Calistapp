package com.calistapp.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.profile.ProfileRepository
import com.calistapp.core.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    val profile: StateFlow<UserProfile> = profileRepository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile())

    val isOnboarded: StateFlow<Boolean> = profileRepository.isOnboarded
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun save(profile: UserProfile) {
        viewModelScope.launch { profileRepository.save(profile) }
    }
}
