package com.calistapp.app.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.sync.WatchCommandSender
import com.calistapp.app.data.sync.WatchConnectionMonitor
import com.calistapp.app.data.sync.WatchLinkState
import com.calistapp.core.sync.ControlCommand
import com.calistapp.core.sync.ControlPayload
import com.calistapp.core.sync.DeviceOrigin
import com.calistapp.core.update.UpdateStatus
import com.calistapp.updater.AppUpdater
import com.calistapp.updater.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the watch half of the update card is showing. */
enum class WatchUpdateState { IDLE, SENT, UNREACHABLE }

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updater: AppUpdater,
    private val watch: WatchCommandSender,
    watchConnection: WatchConnectionMonitor,
) : ViewModel() {

    val state: StateFlow<UpdateState> = updater.state
    val watchLink: StateFlow<WatchLinkState> = watchConnection.state

    private val _watchUpdate = MutableStateFlow(WatchUpdateState.IDLE)
    val watchUpdate: StateFlow<WatchUpdateState> = _watchUpdate.asStateFlow()

    val currentVersion: String get() = updater.currentVersion.name

    fun canInstall(): Boolean = updater.canInstall()
    fun unknownSourcesIntent() = updater.unknownSourcesSettingsIntent()

    fun check() {
        viewModelScope.launch { updater.check() }
    }

    fun download(update: UpdateStatus.Available) {
        viewModelScope.launch { updater.download(update) }
    }

    fun install() {
        (state.value as? UpdateState.ReadyToInstall)?.let { updater.install(it.file) }
    }

    fun dismiss() = updater.reset()

    /**
     * Ask the watch to update itself.
     *
     * The phone can't push a package to the watch, so this only starts the check over there — the
     * watch downloads its own APK and shows its own install prompt, which has to be confirmed on
     * the wrist.
     */
    fun updateWatch() {
        viewModelScope.launch {
            if (!watchLink.value.isUsable) {
                _watchUpdate.value = WatchUpdateState.UNREACHABLE
                return@launch
            }
            watch.send(
                ControlPayload(
                    command = ControlCommand.CHECK_UPDATE,
                    timestampMs = System.currentTimeMillis(),
                    origin = DeviceOrigin.PHONE,
                ),
            )
            _watchUpdate.value = WatchUpdateState.SENT
        }
    }

    fun clearWatchNotice() {
        _watchUpdate.value = WatchUpdateState.IDLE
    }
}
