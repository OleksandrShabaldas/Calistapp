package com.calistapp.app.data.exercise

import android.content.Context
import com.calistapp.app.ui.exercises.VideoThumbUrlMapper
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.ExerciseMedia
import com.calistapp.core.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches real-person video demonstrations (and animated GIFs) to exercises, from a bundled
 * manifest keyed to exercise ids — the same overlay pattern as [ExerciseEnrichments], but for media
 * instead of coaching text.
 *
 * The manifest (`assets/exercise_videos.json`) is a list of [VideoManifestEntry]; each names, per
 * exercise, an optional GIF/thumbnail from the public exercises-dataset and any number of video
 * angles from the CDN-hosted video repository. URLs are built from [ExerciseVideoSource] bases so
 * only relative paths live in the manifest. An empty manifest is a valid no-op.
 */
@Singleton
class ExerciseVideoCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    val byId: Map<String, VideoManifestEntry> by lazy { load() }

    private fun load(): Map<String, VideoManifestEntry> = runCatching {
        context.assets.open(ASSET_NAME).use { it.readBytes().decodeToString() }
            .let { json.decodeFromString(ListSerializer(VideoManifestEntry.serializer()), it) }
            .associateBy { it.id }
    }.getOrDefault(emptyMap())

    /**
     * Merge this exercise's demonstrations onto it: sets [Exercise.media], and backfills
     * [Exercise.imageUrls] with a thumbnail when the exercise has none yet (so the gallery tile isn't
     * a bare placeholder) — the GitHub animation's still frame when there is one, otherwise a frame
     * grabbed from the first video via the `videoframe://` marker [VideoThumbUrlMapper] recognizes.
     * Returns the exercise unchanged if the manifest has no entry for it, or it contributes no media.
     */
    fun applyTo(base: Exercise): Exercise {
        val entry = byId[base.id] ?: return base
        val media = buildList {
            entry.ghGif?.let {
                add(ExerciseMedia(ExerciseVideoSource.GH_BASE + it, MediaType.IMAGE, "Animation", "github"))
            }
            entry.videos.forEach {
                add(ExerciseMedia(ExerciseVideoSource.VIDEO_BASE + it.path, MediaType.VIDEO, it.angle, "musclewiki"))
            }
        }
        if (media.isEmpty()) return base
        val thumb = entry.ghImage?.let { ExerciseVideoSource.GH_BASE + it }
            ?: entry.videos.firstOrNull()?.let { VideoThumbUrlMapper.SCHEME + ExerciseVideoSource.VIDEO_BASE + it.path }
        return base.copy(
            media = media,
            imageUrls = if (base.imageUrls.isEmpty() && thumb != null) listOf(thumb) else base.imageUrls,
        )
    }

    companion object {
        const val ASSET_NAME = "exercise_videos.json"
    }
}

/** CDN roots the manifest's relative paths hang off of. */
object ExerciseVideoSource {
    /**
     * The MuscleWiki-derived video library. Lives in a **private** GitHub repo (not jsDelivr, which
     * only serves public repos) — the videos are a third party's commercial content, so this app
     * keeps them off the public internet and reads them with [ExerciseVideoAuth]'s token instead.
     * `raw.githubusercontent.com` honors a PAT's `Authorization` header for private repos the token
     * can read.
     */
    const val VIDEO_BASE = "https://raw.githubusercontent.com/OleksandrShabaldas/calistapp-exercise-videos/main/"

    /** hasaneyldrm/exercises-dataset — animated GIFs (`videos/…`) and thumbnails (`images/…`). Public, no auth needed. */
    const val GH_BASE = "https://cdn.jsdelivr.net/gh/hasaneyldrm/exercises-dataset@main/"
}

/**
 * The read-only fine-grained PAT that authenticates [ExerciseVideoSource.VIDEO_BASE] requests. Set
 * via `GITHUB_VIDEO_TOKEN` in `local.properties` (see the comment there); blank disables video
 * playback gracefully (the carousel just falls back to the GIF page).
 */
object ExerciseVideoAuth {
    val headers: Map<String, String> by lazy {
        com.calistapp.app.BuildConfig.GITHUB_VIDEO_TOKEN
            .takeIf { it.isNotBlank() }
            ?.let { mapOf("Authorization" to "Bearer $it") }
            ?: emptyMap()
    }
}

/** One exercise's media, as it appears in `assets/exercise_videos.json`. */
@Serializable
data class VideoManifestEntry(
    val id: String,
    /** Relative path of an animated GIF in the exercises-dataset repo, e.g. `videos/0001-x.gif`. */
    val ghGif: String? = null,
    /** Relative path of a still thumbnail in the exercises-dataset repo, e.g. `images/0001-x.jpg`. */
    val ghImage: String? = null,
    /** Real-person video angles, relative to [ExerciseVideoSource.VIDEO_BASE]. */
    val videos: List<VideoAngle> = emptyList(),
)

@Serializable
data class VideoAngle(
    val path: String,
    val angle: String,
)
