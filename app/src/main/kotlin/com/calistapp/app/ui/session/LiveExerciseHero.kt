package com.calistapp.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.calistapp.app.data.exercise.ExerciseVideoAuth
import com.calistapp.app.data.exercise.ExerciseVideoSource
import com.calistapp.app.ui.exercises.ExerciseImage
import com.calistapp.app.ui.exercises.ExerciseVideoPlaylist
import com.calistapp.app.ui.common.GlowIcon
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Onyx
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.MediaType

/** One swipeable page of the live hero. */
private sealed interface HeroPage {
    /** The real-person angles, cycled one after another as a playlist. */
    data class Video(val urls: List<String>) : HeroPage

    /** The animation GIF, or the exercise's start/finish frames as a fallback. */
    data class Stills(val urls: List<String>) : HeroPage
}

/**
 * The full-bleed demonstration behind the live workout screen.
 *
 * Two pages at most: the real-person footage (its several angles cycled as one looping playlist), and
 * — swipe left — the animation. Tap the demonstration for exercise info; its separate control pauses
 * playback. With no video token configured, or an
 * exercise that has no clips, it falls back to the animation, then to the start/finish still frames,
 * then to a labelled placeholder. Only the on-screen page decodes.
 */
@Composable
fun LiveExerciseHero(
    exercise: Exercise?,
    autoplay: Boolean,
    onOpenExercise: () -> Unit,
    dimAmount: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val hasToken = remember { ExerciseVideoAuth.headers.isNotEmpty() }
    val pages = remember(exercise, hasToken) { buildHeroPages(exercise, hasToken) }

    var playing by remember(exercise?.id) { mutableStateOf(autoplay) }
    LaunchedEffect(autoplay) { playing = autoplay }

    Box(modifier) {
        when {
            pages.isEmpty() -> ExerciseImage(
                urls = exercise?.imageUrls.orEmpty(),
                contentDescription = exercise?.name,
                modifier = Modifier.fillMaxSize().workoutScrim(dimAmount).clickable(onClick = onOpenExercise),
            )

            pages.size == 1 -> {
                HeroPageContent(pages[0], active = true, playing = playing, posterUrls = exercise?.imageUrls.orEmpty(),
                    fallbackUrls = exercise?.media?.filter { it.type == MediaType.IMAGE }?.map { it.url }.orEmpty(),
                    dimAmount = dimAmount, onTap = onOpenExercise)
                PausedGlyph(visible = pages[0] is HeroPage.Video, playing = playing) { playing = !playing }
            }

            else -> {
                val pager = rememberPagerState(pageCount = { pages.size })
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { i ->
                    HeroPageContent(
                        page = pages[i],
                        active = pager.currentPage == i && !pager.isScrollInProgress,
                        playing = playing,
                        posterUrls = exercise?.imageUrls.orEmpty(),
                        fallbackUrls = exercise?.media?.filter { it.type == MediaType.IMAGE }?.map { it.url }.orEmpty(),
                        dimAmount = dimAmount,
                        onTap = onOpenExercise,
                    )
                }
                val current = pages[pager.currentPage]
                PausedGlyph(visible = current is HeroPage.Video, playing = playing) { playing = !playing }
                PageDots(
                    count = pages.size,
                    current = pager.currentPage,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroPageContent(
    page: HeroPage,
    active: Boolean,
    playing: Boolean,
    posterUrls: List<String>,
    fallbackUrls: List<String>,
    dimAmount: Float,
    onTap: () -> Unit,
) {
    var unavailable by remember(page) { mutableStateOf(false) }
    when (page) {
        is HeroPage.Video -> Box(Modifier.fillMaxSize().clickable(onClick = onTap)) {
            // A still frame of this same clip behind the player (the TextureView's shutter is
            // transparent), so a clip that's still buffering — or one that never loads — shows the
            // exercise rather than a black rectangle. Offline-first, so a downloaded clip needs no network.
            ExerciseImage(
                urls = if (unavailable) fallbackUrls.ifEmpty { posterUrls } else posterUrls,
                contentDescription = "Exercise demonstration",
                animate = unavailable && active,
                modifier = Modifier.fillMaxSize().workoutScrim(dimAmount),
            )
            if (!unavailable) {
                ExerciseVideoPlaylist(
                    urls = page.urls,
                    active = active,
                    playing = playing,
                    modifier = Modifier.fillMaxSize(),
                    workoutScrim = true,
                    dimAmount = dimAmount,
                    onUnavailable = { unavailable = true },
                )
            }
        }

        is HeroPage.Stills -> ExerciseImage(
            urls = page.urls,
            contentDescription = null,
            animate = active,
            modifier = Modifier.fillMaxSize().workoutScrim(dimAmount).clickable(onClick = onTap),
        )
    }
}

/** Separate playback control leaves taps on the demonstration available for exercise info. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.PausedGlyph(visible: Boolean, playing: Boolean, onClick: () -> Unit) {
    if (!visible) return
    Box(
        Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 16.dp)
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.animation.AnimatedContent(targetState = playing, label = "playbackControl") { isPlaying ->
            GlowIcon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (isPlaying) "Pause demonstration" else "Play demonstration", tint = Chalk,
                size = 26.dp, glowRadius = 4.dp, glowAlpha = 0.4f)
        }
    }
}

/** Matches workout_video_scrim.xml; applied only to images, never over a video view. */
private fun Modifier.workoutScrim(dimAmount: Float) = drawWithContent {
    drawContent()
    drawRect(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Onyx.copy(alpha = 0.92f)))
    drawRect(Onyx.copy(alpha = dimAmount))
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val on = i == current
            Box(
                Modifier
                    .size(width = if (on) 18.dp else 6.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(if (on) Chalk else Chalk.copy(alpha = 0.4f)),
            )
        }
    }
}

private fun buildHeroPages(exercise: Exercise?, hasToken: Boolean): List<HeroPage> {
    if (exercise == null) return emptyList()
    val videoUrls = exercise.media.filter {
        it.type == MediaType.VIDEO && (hasToken || !it.url.startsWith(ExerciseVideoSource.VIDEO_BASE))
    }.map { it.url }
    val animation = exercise.media.firstOrNull { it.type == MediaType.IMAGE }?.url
    return buildList {
        if (videoUrls.isNotEmpty()) add(HeroPage.Video(videoUrls))
        if (animation != null) add(HeroPage.Stills(listOf(animation)))
        if (isEmpty() && exercise.imageUrls.isNotEmpty()) add(HeroPage.Stills(exercise.imageUrls))
    }
}
