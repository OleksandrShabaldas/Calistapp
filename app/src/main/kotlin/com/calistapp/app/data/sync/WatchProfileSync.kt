package com.calistapp.app.data.sync

import android.content.Context
import com.calistapp.app.data.profile.ProfileRepository
import com.calistapp.app.di.ApplicationScope
import com.calistapp.core.model.UserProfile
import com.calistapp.core.sync.WearSync
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the watch's copy of the user's profile in sync. Uses the DataClient (not a message) so the
 * value persists and re-syncs whenever the watch reconnects — the watch needs weight/age/sex/HR to
 * compute calories accurately on-device.
 */
@Singleton
class WatchProfileSync @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val profileRepository: ProfileRepository,
    private val json: Json,
) {
    private val dataClient = Wearable.getDataClient(context)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            profileRepository.profile.distinctUntilChanged().collect { profile ->
                runCatching {
                    val bytes = json.encodeToString(UserProfile.serializer(), profile).toByteArray()
                    val request = PutDataRequest.create(WearSync.PATH_PROFILE).apply {
                        data = bytes
                        setUrgent()
                    }
                    dataClient.putDataItem(request).await()
                }
            }
        }
    }
}
