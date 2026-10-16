package com.calistapp.app.ui.exercises

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.media3.ui.PlayerView
import com.calistapp.app.R
import com.calistapp.app.data.exercise.ExerciseMediaStore

/** Muted demonstration; only the visible page plays. */
@Composable
fun ExerciseVideoPlayer(url: String, active: Boolean, modifier: Modifier = Modifier) {
    ExerciseVideoPlaylist(listOf(url), active, playing = true, modifier = modifier)
}

/** A shared player keeps URL changes, screen lifecycle, and tap-to-pause in agreement. */
@Composable
private fun rememberVideoPlayer(
    urls: List<String>,
    shouldPlay: Boolean,
    onFirstFrame: () -> Unit = {},
    onUnavailable: () -> Unit = {},
): ExoPlayer {
    val context = LocalContext.current
    val frameCallback by rememberUpdatedState(onFirstFrame)
    val failureCallback by rememberUpdatedState(onUnavailable)
    val wantsPlayback by rememberUpdatedState(shouldPlay)
    val player = remember(urls) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ExerciseMediaStore.dataSourceFactory(context)))
            .build().apply {
                var retries = 0
                addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() { frameCallback() }
                    override fun onPlayerError(error: PlaybackException) {
                        if (retries++ < MAX_RETRIES) prepare() else failureCallback()
                    }
                })
                setMediaItems(urls.map { MediaItem.fromUri(it) })
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                prepare()
            }
    }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(player, shouldPlay, owner) {
        player.playWhenReady = shouldPlay && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }
    DisposableEffect(owner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> player.playWhenReady = wantsPlayback
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

/** Loop all angles. A retained AndroidView must bind each new player on update. */
@Composable
fun ExerciseVideoPlaylist(
    urls: List<String>,
    active: Boolean,
    playing: Boolean,
    modifier: Modifier = Modifier,
    workoutScrim: Boolean = false,
    dimAmount: Float = 0f,
    onUnavailable: () -> Unit = {},
) {
    var hasFrame by remember(urls) { mutableStateOf(false) }
    val player = rememberVideoPlayer(urls, active && playing,
        onFirstFrame = { hasFrame = true }, onUnavailable = onUnavailable)
    AndroidView(
        factory = { ctx ->
            LayoutInflater.from(ctx).inflate(
                if (workoutScrim) R.layout.workout_video else R.layout.exercise_player_view, null,
            )
        },
        update = { root ->
            val view = if (root is PlayerView) root else root.findViewById<PlayerView>(R.id.workout_player)
            if (view.player !== player) view.player = player
            // Keep the texture attached and decoding, with the poster visible until the first frame.
            view.alpha = if (hasFrame) 1f else 0f
            if (workoutScrim) root.findViewById<android.view.View>(R.id.workout_dim).alpha = dimAmount
        },
        onRelease = { root ->
            val view = if (root is PlayerView) root else root.findViewById<PlayerView>(R.id.workout_player)
            view.player = null
        },
        modifier = modifier,
    )
}

/** Scrims live alongside the video in XML so Android composites them reliably. */
@Composable
fun NextUpVideoPlayer(urls: List<String>, modifier: Modifier = Modifier) {
    val player = rememberVideoPlayer(urls, true)
    AndroidView(
        factory = { LayoutInflater.from(it).inflate(R.layout.next_up_video, null) },
        update = { it.findViewById<PlayerView>(R.id.nextup_player).player = player },
        onRelease = { it.findViewById<PlayerView>(R.id.nextup_player).player = null },
        modifier = modifier,
    )
}

@Composable
fun AmbientVideoBackground(urls: List<String>, modifier: Modifier = Modifier) {
    val player = rememberVideoPlayer(urls, true)
    AndroidView(
        factory = { ctx ->
            LayoutInflater.from(ctx).inflate(R.layout.ambient_video, null).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    findViewById<PlayerView>(R.id.ambient_player).setRenderEffect(
                        RenderEffect.createBlurEffect(48f, 48f, Shader.TileMode.CLAMP),
                    )
                }
            }
        },
        update = { it.findViewById<PlayerView>(R.id.ambient_player).player = player },
        onRelease = { it.findViewById<PlayerView>(R.id.ambient_player).player = null },
        modifier = modifier,
    )
}

private const val MAX_RETRIES = 3
