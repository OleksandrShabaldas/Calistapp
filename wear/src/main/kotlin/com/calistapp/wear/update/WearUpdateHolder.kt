package com.calistapp.wear.update

import android.content.Context
import com.calistapp.core.update.UpdateStatus
import com.calistapp.core.update.UpdateTarget
import com.calistapp.updater.AppUpdater
import com.calistapp.updater.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The watch's self-updater.
 *
 * Application-scoped, like [com.calistapp.wear.session.WearSessionManager], and for the same
 * reason: the phone can trigger a check while the watch UI isn't open, so this can't belong to a
 * ViewModel that dies with the Activity.
 *
 * The watch updates *itself* because it has to — a phone app cannot install a package onto a
 * paired watch. The phone's "update watch app" button just sends
 * [com.calistapp.core.sync.ControlCommand.CHECK_UPDATE], which lands here.
 */
object WearUpdateHolder {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var updater: AppUpdater? = null
    private var appContext: Context? = null
    private var job: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    fun attach(context: Context) {
        if (updater != null) return
        appContext = context.applicationContext
        val instance = AppUpdater(context.applicationContext, UpdateTarget.WATCH)
        updater = instance
        scope.launch { instance.state.collect { _state.value = it } }
    }

    val currentVersion: String get() = updater?.currentVersion?.name ?: "—"

    fun canInstall(): Boolean = updater?.canInstall() ?: false

    fun unknownSourcesIntent() = updater?.unknownSourcesSettingsIntent()

    /**
     * Check, and download automatically if something's available.
     *
     * Downloading without a second tap is deliberate here: this is often triggered from the phone
     * while the watch is on your wrist and unlooked-at, and a watch screen is a poor place to make
     * someone confirm twice. The *install* still prompts — that one can't be skipped and shouldn't be.
     */
    fun checkAndDownload() {
        val instance = updater ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            val result = instance.check()
            if (result is UpdateState.Available) downloadKeepingAwake(instance, result.update)
        }
    }

    fun download(update: UpdateStatus.Available) {
        val instance = updater ?: return
        if (job?.isActive == true) return
        job = scope.launch { downloadKeepingAwake(instance, update) }
    }

    /**
     * Run the download behind [WearUpdateService], so the screen is free to turn off, and say so
     * when it lands.
     *
     * The service is best-effort — see its `start` — so this never depends on it having started.
     */
    private suspend fun downloadKeepingAwake(instance: AppUpdater, update: UpdateStatus.Available) {
        val context = appContext
        context?.let { WearUpdateService.start(it) }
        try {
            val result = instance.download(update)
            if (result is UpdateState.ReadyToInstall && context != null) {
                WearUpdateService.notifyReadyToInstall(context, result.version.name)
            }
        } finally {
            context?.let { WearUpdateService.stop(it) }
        }
    }

    fun install() {
        val instance = updater ?: return
        (state.value as? UpdateState.ReadyToInstall)?.let {
            appContext?.let { context -> WearUpdateService.clearReadyNotice(context) }
            instance.install(it.file)
        }
    }

    fun reset() {
        appContext?.let { WearUpdateService.clearReadyNotice(it) }
        updater?.reset()
    }
}
