package com.calistapp.app.ui.exercises

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.map.Mapper
import coil.request.Options
import com.calistapp.app.data.exercise.ExerciseVideoAuth
import okio.Buffer

/**
 * Thumbnails a video by grabbing one frame from it — used for exercises the GitHub animation pack
 * doesn't cover (most of them; it's a much smaller dataset than the video library). Model it as
 * [VideoThumb] rather than encoding a fake URL scheme, so it composes cleanly with the rest of Coil.
 *
 * A frame ~0.5s in (not frame zero, which is often a blank/fade-in beat) is decoded once via
 * [MediaMetadataRetriever] and handed back as a [SourceResult] — Coil's normal JPEG decoder takes it
 * from there and writes it through the usual disk cache, so this only touches the network once per
 * exercise, not on every app launch or gallery scroll.
 */
data class VideoThumb(val videoUrl: String)

/**
 * Recognizes the `videoframe://<url>` marker scheme used in [Exercise.imageUrls][com.calistapp.core.model.Exercise.imageUrls]
 * for exercises with no GitHub GIF, and turns it into a [VideoThumb] for [VideoFrameFetcher] to
 * handle. Keeps the plain-`List<String>` shape of `imageUrls` — no core model change needed for this
 * one fallback case.
 */
class VideoThumbUrlMapper : Mapper<String, VideoThumb> {
    override fun map(data: String, options: Options): VideoThumb? =
        data.takeIf { it.startsWith(SCHEME) }?.let { VideoThumb(it.removePrefix(SCHEME)) }

    companion object {
        const val SCHEME = "videoframe://"
    }
}

class VideoFrameFetcher(private val data: VideoThumb, private val options: Options) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(data.videoUrl, ExerciseVideoAuth.headers)
            val frame = retriever.getFrameAtTime(FRAME_TIME_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0)
                ?: error("No frame decodable from ${data.videoUrl}")

            val buffer = Buffer()
            frame.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
            frame.recycle()

            return SourceResult(
                source = ImageSource(source = buffer, context = options.context),
                mimeType = "image/jpeg",
                dataSource = DataSource.NETWORK,
            )
        } finally {
            retriever.release()
        }
    }

    class Factory : Fetcher.Factory<VideoThumb> {
        override fun create(data: VideoThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            VideoFrameFetcher(data, options)
    }

    companion object {
        private const val FRAME_TIME_US = 500_000L
    }
}
