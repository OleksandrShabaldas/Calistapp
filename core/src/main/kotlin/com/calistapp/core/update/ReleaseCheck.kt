package com.calistapp.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Where releases are published. Both modules read this so they can't drift to different repos. */
object UpdateSource {
    const val OWNER = "OleksandrShabaldas"
    const val REPO = "Calistapp"

    /** GitHub's "newest published, non-prerelease" endpoint. Public repo, so no auth needed. */
    const val LATEST_RELEASE_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    const val RELEASES_PAGE = "https://github.com/$OWNER/$REPO/releases"
}

/** Which device an APK is for. The two ship in the same release as separate assets. */
enum class UpdateTarget { PHONE, WATCH }

@Serializable
data class ReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
)

/** The subset of GitHub's release JSON this app needs. Unknown keys are ignored by [WearJson]-style configs. */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<ReleaseAsset> = emptyList(),
)

/** The outcome of comparing the installed version against the newest release. */
sealed interface UpdateStatus {
    /** Nothing newer is published. */
    data object UpToDate : UpdateStatus

    /** A newer release exists and carries an APK for this device. */
    data class Available(
        val version: AppVersion,
        val asset: ReleaseAsset,
        val notes: String,
        val releaseUrl: String,
    ) : UpdateStatus

    /** Something is wrong with the release itself — explained rather than silently ignored. */
    data class Unavailable(val reason: String) : UpdateStatus
}

object ReleaseCheck {

    /**
     * Decide whether [release] is something [current] should update to.
     *
     * Strictly greater-than: equal versions are up to date, and a *lower* version is too. Offering
     * a downgrade would be worse than useless, because Android rejects installing a lower
     * versionCode over a higher one — the user would get an opaque installer failure instead.
     */
    fun evaluate(
        current: AppVersion,
        release: GitHubRelease?,
        target: UpdateTarget,
    ): UpdateStatus {
        if (release == null) return UpdateStatus.Unavailable("No releases published yet.")
        if (release.draft || release.prerelease) return UpdateStatus.UpToDate

        val latest = AppVersion.parse(release.tagName) ?: AppVersion.parse(release.name)
            ?: return UpdateStatus.Unavailable("Release \"${release.tagName}\" isn't a version number.")

        if (latest <= current) return UpdateStatus.UpToDate

        val asset = assetFor(release.assets, target)
            ?: return UpdateStatus.Unavailable(
                "Version ${latest.name} is out, but it has no ${target.label} APK attached.",
            )

        // A plain-HTTP download could be swapped in transit for something else entirely, and this
        // one ends in an install prompt. GitHub always serves HTTPS; anything else is a red flag.
        if (!asset.downloadUrl.startsWith("https://")) {
            return UpdateStatus.Unavailable("Download link for ${latest.name} isn't secure (HTTPS).")
        }

        return UpdateStatus.Available(
            version = latest,
            asset = asset,
            notes = release.body.trim(),
            releaseUrl = release.htmlUrl.ifBlank { UpdateSource.RELEASES_PAGE },
        )
    }

    /**
     * Pick the APK for [target].
     *
     * Matching is by "does the filename mention the watch", not by an exact filename, so renaming
     * the assets doesn't quietly stop updates working. The watch is identified positively and the
     * phone by exclusion — that way an unlabelled `Calistapp.apk` is treated as the phone build
     * (the common case) instead of matching nothing.
     */
    fun assetFor(assets: List<ReleaseAsset>, target: UpdateTarget): ReleaseAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        fun isWatch(a: ReleaseAsset) =
            a.name.contains("wear", true) || a.name.contains("watch", true)
        return when (target) {
            UpdateTarget.WATCH -> apks.firstOrNull(::isWatch)
            UpdateTarget.PHONE -> apks.firstOrNull { !isWatch(it) }
        }
    }

    private val UpdateTarget.label: String
        get() = when (this) {
            UpdateTarget.PHONE -> "phone"
            UpdateTarget.WATCH -> "watch"
        }
}
