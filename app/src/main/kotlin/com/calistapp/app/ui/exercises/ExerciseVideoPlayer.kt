package com.calistapp.app.ui.exercises

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.calistapp.app.data.exercise.ExerciseMediaStore

/**
 * Plays one looping, muted exercise-demonstration video (an MP4 streamed from the CDN).
 *
 * Sized for a carousel: only the visible page should play, so pass [active] = whether this is the
 * current page. Off-screen pages are paused, which keeps just one video decoding at a time and
 * avoids burning bandwidth pre-buffering pages the user may never swipe to. The player is released
 * when it leaves composition, and paused when the app is backgrounded.
 */
@Composable
fun ExerciseVideoPlayer(
    url: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val exoPlayer = remember(url) {
        val retries = intArrayOf(0)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ExerciseMediaStore.dataSourceFactory(context)))
            .build()
            .apply {
                // A transient network/CDN error shouldn't leave a blank frame — re-prepare a few
                // times before giving up. Bounded so a genuinely missing clip doesn't spin forever.
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        if (retries[0] < MAX_RETRIES) { retries[0]++; prepare() }
                    }
                })
                setMediaItem(MediaItem.fromUri(url))
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                prepare()
            }
    }

    // Only the on-screen page plays.
    LaunchedEffect(active, exoPlayer) { exoPlayer.playWhenReady = active }

    // Pause with the app; resume only if this is still the active page.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> if (active) exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        modifier = modifier,
    )
}

/**
 * Plays a list of clips as one looping playlist — the real-person angles of a single exercise, shown
 * one after another (front, then side, then front again…). The live workout hero uses this so the
 * several angles read as "the same movement from around the room" rather than separate pages.
 *
 * [active] gates whether this is the on-screen page (off-screen pages don't decode); [playing] is the
 * user's tap-to-pause. Video runs only when both are true. ExoPlayer auto-advances the list and
 * `REPEAT_MODE_ALL` loops it, so no per-clip end handling is needed.
 */
@Composable
fun ExerciseVideoPlaylist(
    urls: List<String>,
    active: Boolean,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val exoPlayer = remember(urls) {
        val retries = intArrayOf(0)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ExerciseMediaStore.dataSourceFactory(context)))
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        if (retries[0] < MAX_RETRIES) { retries[0]++; prepare() }
                    }
                })
                setMediaItems(urls.map { MediaItem.fromUri(it) })
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                prepare()
            }
    }

    LaunchedEffect(active, playing, exoPlayer) { exoPlayer.playWhenReady = active && playing }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> if (active && playing) exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        modifier = modifier,
    )
}

/** How many times a player re-prepares after a transient playback error before giving up. */
private const val MAX_RETRIES = 3
