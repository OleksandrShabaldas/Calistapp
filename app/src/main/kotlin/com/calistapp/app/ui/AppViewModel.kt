package com.calistapp.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.session.SessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The little the navigation shell needs to know: whether a workout is running.
 *
 * That single fact decides where the primary action goes — build a new workout, or return to the one
 * already in progress — and it has to be known outside any individual screen's ViewModel.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    sessionController: SessionController,
) : ViewModel() {

    val sessionRunning: StateFlow<Boolean> = sessionController.live
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
