package com.calistapp.app.ui.exercises

import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import com.calistapp.app.R
import com.calistapp.app.ui.session.LiveExerciseHero
import com.calistapp.core.model.BodyPart
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.ExerciseMedia
import com.calistapp.core.model.MediaType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Local Media3 fixture; exercises rendering without contacting the private video repository. */
class WorkoutPlaybackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var firstUrl: String
    private lateinit var nextUrl: String

    @Before fun copyClip() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = instrumentation.targetContext.cacheDir
        fun copy(name: String): String {
            val file = File(folder, name)
            instrumentation.context.assets.open("playback.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
            return file.toURI().toString()
        }
        firstUrl = copy("playback-first.mp4")
        nextUrl = copy("playback-next.mp4")
    }

    @Test fun rendersFramesRebindsPlayerAndHonorsPause() {
        val url = mutableStateOf(firstUrl)
        val playing = mutableStateOf(true)
        compose.setContent {
            ExerciseVideoPlaylist(listOf(url.value), active = true, playing = playing.value,
                workoutScrim = true, modifier = Modifier.fillMaxSize())
        }
        waitForFrame()
        lateinit var originalView: PlayerView
        lateinit var originalPlayer: Player
        compose.runOnIdle {
            originalView = playerView()!!
            originalPlayer = originalView.player!!
            val bitmap = (originalView.videoSurfaceView as TextureView).bitmap!!
            var litPixels = 0
            for (y in 0 until bitmap.height step 16) for (x in 0 until bitmap.width step 16) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel) > 90) litPixels++
            }
            assertTrue("Decoded footage must contain visible pixels", litPixels > 20)
            bitmap.recycle()
            url.value = nextUrl
        }
        waitForFrame()
        compose.runOnIdle {
            assertSame("The existing AndroidView is retained", originalView, playerView())
            assertNotSame("The retained view must bind the replacement player", originalPlayer, playerView()!!.player)
            assertEquals(nextUrl, playerView()!!.player!!.currentMediaItem!!.localConfiguration!!.uri.toString())
            playing.value = false
        }
        compose.waitUntil(5_000) { onMain { playerView()?.player?.playWhenReady == false } }
    }

    @Test fun demonstrationOpensInfoAndSeparateButtonPauses() {
        var opened = 0
        val dim = mutableStateOf(0f)
        val exercise = Exercise("qa", "QA exercise", BodyPart.CHEST,
            media = listOf(ExerciseMedia(firstUrl, MediaType.VIDEO)))
        compose.setContent {
            LiveExerciseHero(exercise, autoplay = true, onOpenExercise = { opened++ },
                dimAmount = dim.value, modifier = Modifier.fillMaxSize())
        }
        waitForFrame()
        compose.onRoot().performTouchInput { click(Offset(width * 0.25f, height * 0.35f)) }
        compose.runOnIdle { assertEquals(1, opened); dim.value = 0.6f }
        compose.runOnIdle {
            assertEquals(0.6f, compose.activity.findViewById<View>(R.id.workout_dim).alpha, 0.001f)
        }
        compose.onNodeWithContentDescription("Pause demonstration").performClick()
        compose.waitUntil(5_000) { onMain { playerView()?.player?.playWhenReady == false } }
        compose.onNodeWithContentDescription("Play demonstration").performClick()
        compose.waitUntil(5_000) { onMain { playerView()?.player?.isPlaying == true } }
    }

    private fun waitForFrame() = compose.waitUntil(15_000) {
        onMain { playerView()?.let { it.alpha == 1f && it.player?.isPlaying == true && (it.player?.currentPosition ?: 0) > 400 } == true }
    }

    private fun onMain(block: () -> Boolean): Boolean {
        var result = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        return result
    }

    private fun playerView(): PlayerView? {
        fun find(view: View): PlayerView? {
            if (view is PlayerView) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
            return null
        }
        return find(compose.activity.window.decorView)
    }
}
