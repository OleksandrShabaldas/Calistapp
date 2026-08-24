package com.calistapp.app.ui.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.ExerciseMedia
import com.calistapp.core.model.MediaType

/** One page of the demonstration carousel. */
private sealed interface MediaPage {
    val label: String?

    /** A still, a start/finish slideshow, or an animated GIF — all handled by [ExerciseImage]. */
    data class Images(val urls: List<String>, override val label: String?) : MediaPage

    /** A real-person video clip. */
    data class Video(val url: String, override val label: String?) : MediaPage
}

/**
 * The full-size demonstration on the exercise detail screen. Swipes between every view an exercise
 * has — typically an animated GIF plus real-person video from a front and side angle — with a dot
 * per page and the angle labelled. Only the visible video plays.
 *
 * Falls back gracefully: an exercise with just [Exercise.imageUrls] shows the animated frames as a
 * single page (identical to the old [ExerciseDemo]); one with nothing shows the placeholder.
 */
@Composable
fun ExerciseMediaCarousel(
    exercise: Exercise,
    modifier: Modifier = Modifier,
) {
    val pages = remember(exercise.id, exercise.imageUrls, exercise.media) { buildPages(exercise) }

    if (pages.size <= 1) {
        // A single page needs no pager chrome — render it directly.
        val only = pages.firstOrNull()
        Box(modifier) {
            when (only) {
                is MediaPage.Video -> ExerciseVideoPlayer(only.url, active = true, modifier = Modifier.fillMaxSize())
                is MediaPage.Images -> ExerciseImage(only.urls, exercise.name, modifier = Modifier.fillMaxSize())
                null -> ExerciseImage(exercise.imageUrls, exercise.name, modifier = Modifier.fillMaxSize())
            }
            only?.label?.let { AngleBadge(it, Modifier.align(Alignment.BottomStart)) }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })

    Box(modifier) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            when (val page = pages[index]) {
                is MediaPage.Video -> ExerciseVideoPlayer(
                    url = page.url,
                    active = pagerState.currentPage == index && !pagerState.isScrollInProgress,
                    modifier = Modifier.fillMaxSize(),
                )

                is MediaPage.Images -> ExerciseImage(
                    urls = page.urls,
                    contentDescription = exercise.name,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        pages[pagerState.currentPage].label?.let { AngleBadge(it, Modifier.align(Alignment.BottomStart)) }

        PageDots(
            count = pages.size,
            current = pagerState.currentPage,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
        )
    }
}

private fun buildPages(exercise: Exercise): List<MediaPage> = buildList {
    if (exercise.media.isNotEmpty()) {
        // The video-catalog pipeline: `media` is the complete, authoritative rotation (GitHub
        // animation first when there is one, then each video angle). `imageUrls[0]` in this case is
        // only ever a synthetic gallery-grid thumbnail — the GIF's still frame, or a frame grabbed
        // from the first video when there's no GIF — so showing it as a page too would just be a
        // near-duplicate of the page right after it.
        exercise.media.forEach { m: ExerciseMedia ->
            when (m.type) {
                MediaType.VIDEO -> add(MediaPage.Video(m.url, m.angle))
                MediaType.IMAGE -> add(MediaPage.Images(listOf(m.url), m.angle))
            }
        }
    } else if (exercise.imageUrls.isNotEmpty()) {
        // Legacy path: free-exercise-db start/finish frames (or a single custom image), no video.
        add(MediaPage.Images(exercise.imageUrls, label = null))
    }
}

@Composable
private fun AngleBadge(label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
        modifier = modifier.padding(10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val on = i == current
            Box(
                Modifier
                    .size(if (on) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (on) Color.White else Color.White.copy(alpha = 0.45f)),
            )
        }
    }
}
