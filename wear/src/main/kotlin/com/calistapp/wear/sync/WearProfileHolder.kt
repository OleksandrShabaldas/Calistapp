package com.calistapp.wear.sync

import com.calistapp.core.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the latest profile synced down from the phone. Populated by [ProfileReceiverService] and a
 * one-shot read in the ViewModel; read wherever the watch needs the user's physiology.
 */
object WearProfileHolder {
    private val _profile = MutableStateFlow(UserProfile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    fun update(profile: UserProfile) {
        _profile.value = profile
    }
}
