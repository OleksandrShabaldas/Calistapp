package com.calistapp.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.calistapp.core.update.AppVersion
import com.calistapp.core.update.GitHubRelease
import com.calistapp.core.update.ReleaseCheck
import com.calistapp.core.update.UpdateSource
import com.calistapp.core.update.UpdateStatus
import com.calistapp.core.update.UpdateTarget
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/** What the update UI renders. One flow drives check → download → install. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val current: AppVersion) : UpdateState
    data class Available(val update: UpdateStatus.Available) : UpdateState

    /**
     * [progress] is 0..1, or null when the size isn't known yet.
     *
     * [reconnecting] is set while a dropped transfer is being picked back up. It stays a
     * *downloading* state rather than becoming a failure because the bytes already fetched are
     * kept — the retry continues from them, so the progress shown next to it is still true.
     */
    data class Downloading(
        val version: AppVersion,
        val progress: Float?,
        val reconnecting: Boolean = false,
    ) : UpdateState

    /** Downloaded and verified; waiting for the user to confirm the system install prompt. */
    data class ReadyToInstall(val version: AppVersion, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Checks GitHub Releases for a newer build, downloads it, and hands it to the system installer.
 *
 * Calistapp isn't on the Play Store, so nothing updates it automatically — without this, a new
 * version means finding the release page and sideloading by hand.
 *
 * **What this deliberately cannot do:** install silently. The APK is passed to Android's package
 * installer, which shows its own confirmation every time, and the user must have granted Calistapp
 * "install unknown apps" first. That prompt is the security boundary and this class does not try to
 * work around it.
 *
 * **What it checks before prompting**, because "download a file and execute it" is exactly the
 * shape of the thing you want to be careful about:
 *  - the download URL is HTTPS ([ReleaseCheck] rejects anything else);
 *  - the downloaded file is really an APK Android can parse;
 *  - it declares the same package name as the running app;
 *  - it is signed by the **same certificate** as the running app;
 *  - on a watch, it actually declares itself a watch build.
 *
 * The signature check is the important one. Android would reject a mismatched signer anyway, but
 * only after the user had accepted an install prompt for a package that isn't ours — better to
 * never show that prompt.
 */
class AppUpdater(
    context: Context,
    private val target: UpdateTarget,
    private val okHttp: OkHttpClient = OkHttpClient(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /**
     * A second client, used only for the APK itself.
     *
     * [okHttp]'s timeouts are sized for the release-metadata call: a few kilobytes of JSON that
     * should either arrive promptly or be reported as broken. An APK is several megabytes, and on a
     * watch it may be relayed through the phone over Bluetooth, where a ten-second gap between
     * chunks is unremarkable. Sharing one client meant those ordinary stalls were read as a dead
     * connection and surfaced mid-download as a bare "timeout".
     */
    private val downloadHttp: OkHttpClient by lazy {
        okHttp.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // No ceiling on the call as a whole. A slow link is not a failure, and the read timeout
            // above already catches a connection that has genuinely stopped delivering.
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** The version currently running, read from the installed package rather than BuildConfig. */
    val currentVersion: AppVersion
        get() = AppVersion.parse(installedPackage()?.versionName) ?: AppVersion(0, 0, 0)

    /** Whether the user has allowed this app to install packages. Always true below Android 8. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            appContext.packageManager.canRequestPackageInstalls()

    /** The system screen where "install unknown apps" is granted for this app. */
    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // ---- Check ------------------------------------------------------------------------------------

    /** Ask GitHub what the newest release is and compare it with what's installed. */
    suspend fun check(): UpdateState {
        _state.value = UpdateState.Checking
        val result = runCatching { fetchLatestRelease() }
        val next = result.fold(
            onSuccess = { release ->
                when (val status = ReleaseCheck.evaluate(currentVersion, release, target)) {
                    is UpdateStatus.Available -> UpdateState.Available(status)
                    is UpdateStatus.UpToDate -> UpdateState.UpToDate(currentVersion)
                    is UpdateStatus.Unavailable -> UpdateState.Failed(status.reason)
                }
            },
            onFailure = { UpdateState.Failed(it.friendlyMessage()) },
        )
        _state.value = next
        return next
    }

    private suspend fun fetchLatestRelease(): GitHubRelease? = withContext(io) {
        val request = Request.Builder()
            .url(UpdateSource.LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        okHttp.newCall(request).execute().use { response ->
            // A repo with no published releases answers 404 — that's "nothing to update to", not a
            // failure worth showing as an error.
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) {
                error(
                    if (response.code == 403) {
                        "GitHub rate limit reached. Try again in a few minutes."
                    } else {
                        "Couldn't reach GitHub (HTTP ${response.code})."
                    },
                )
            }
            val body = response.body?.string().orEmpty()
            json.decodeFromString(GitHubRelease.serializer(), body)
        }
    }

    // ---- Download ---------------------------------------------------------------------------------

    /**
     * Download [update]'s APK and verify it. On success the state becomes
     * [UpdateState.ReadyToInstall] and [install] can be called.
     */
    suspend fun download(update: UpdateStatus.Available): UpdateState {
        _state.value = UpdateState.Downloading(update.version, null)
        val next = try {
            UpdateState.ReadyToInstall(update.version, downloadAndVerify(update))
        } catch (cancellation: CancellationException) {
            // Called off rather than broken. Offer the same update again instead of leaving a
            // progress bar that will never move — the bytes fetched so far are kept on disk, so
            // starting it again picks up where this left off.
            _state.value = UpdateState.Available(update)
            throw cancellation
        } catch (t: Throwable) {
            UpdateState.Failed(t.friendlyMessage())
        }
        _state.value = next
        return next
    }

    private suspend fun downloadAndVerify(update: UpdateStatus.Available): File = withContext(io) {
        val dir = File(appContext.cacheDir, DOWNLOAD_DIR).apply { mkdirs() }
        // One file per version+target, so a download in progress can't be mistaken for a complete
        // one — only the verified rename below produces the final name.
        val destination = File(dir, "calistapp-${target.name.lowercase()}-${update.version.name}.apk")
        val partial = File(dir, destination.name + ".part")

        // A watch download runs over a link that drops routinely: the screen blanks, the radio
        // powers down, the phone relaying the bytes wanders off. Each of those arrives here as an
        // IOException, and each used to end the update. Retrying is only reasonable because the
        // transfer resumes — the retries are cheap and never re-fetch what already landed.
        var attempt = 1
        while (true) {
            try {
                fetchInto(partial, update)
                break
            } catch (dropped: IOException) {
                currentCoroutineContext().ensureActive()
                if (attempt >= MAX_ATTEMPTS) throw dropped
                _state.value = UpdateState.Downloading(
                    version = update.version,
                    progress = partial.progressOf(update),
                    reconnecting = true,
                )
                delay(RETRY_DELAY_MS * attempt)
                attempt++
            }
        }

        try {
            verify(partial, update.version)
        } catch (t: Throwable) {
            // Bytes that don't verify are poison: left in place, every later resume would build on
            // the same bad file and fail the same way forever.
            partial.delete()
            throw t
        }

        destination.delete()
        if (!partial.renameTo(destination)) error("Couldn't finalise the download.")
        destination
    }

    /**
     * Stream the APK into [partial], continuing from whatever is already there.
     *
     * Resuming is what makes this survivable on a watch. A several-megabyte download that restarts
     * from zero every time the connection hiccups doesn't finish at all — it gets most of the way
     * and then reports whatever the socket threw, which is how "70%, then an error" happened.
     */
    private suspend fun fetchInto(partial: File, update: UpdateStatus.Available) {
        val already = partial.length()
        val request = Request.Builder()
            .url(update.asset.downloadUrl)
            .apply { if (already > 0) header("Range", "bytes=$already-") }
            .build()

        downloadHttp.newCall(request).execute().use { response ->
            // 416 says what's on disk already reaches or passes the asset's length — the release
            // was re-uploaded underneath us, so the leftovers are worthless. Drop them and let the
            // retry above start clean.
            if (response.code == HTTP_RANGE_NOT_SATISFIABLE) {
                partial.delete()
                throw IOException("The release file changed; starting the download again.")
            }
            if (!response.isSuccessful) {
                val message = "Download failed (HTTP ${response.code})."
                // A server-side error is worth another go; a 404 or a 403 is not.
                if (response.code >= 500) throw IOException(message) else error(message)
            }

            // Only append when the server actually honoured the range. One that ignores it answers
            // 200 with the whole file, and appending that to what we have would corrupt it.
            val resumed = response.code == HTTP_PARTIAL && already > 0
            val body = response.body ?: error("Download returned no content.")
            val startAt = if (resumed) already else 0L
            val total = body.contentLength().takeIf { it > 0 }?.plus(startAt) ?: update.asset.size

            body.byteStream().use { input ->
                FileOutputStream(partial, resumed).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = startAt
                    var lastReported = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        // A blocking read doesn't notice cancellation by itself, so without this a
                        // download that was called off would keep running to completion.
                        currentCoroutineContext().ensureActive()
                        if (total > 0) {
                            // Only publish on whole-percent changes; a StateFlow emission per
                            // 8 KB chunk would recompose the UI thousands of times.
                            val percent = ((copied * 100) / total).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                _state.value =
                                    UpdateState.Downloading(update.version, percent / 100f)
                            }
                        }
                    }
                }
            }
        }
    }

    /** How far along [this] partial file is, against the size GitHub reported for the asset. */
    private fun File.progressOf(update: UpdateStatus.Available): Float? =
        update.asset.size.takeIf { it > 0 }?.let { (length().toFloat() / it).coerceIn(0f, 1f) }

    // ---- Verification ------------------------------------------------------------------------------

    /** Throws with a human-readable reason if [file] isn't a genuine update for this app. */
    private fun verify(file: File, version: AppVersion) {
        val pm = appContext.packageManager
        val archive = pm.getPackageArchiveInfo(file.absolutePath, signatureFlags or PackageManager.GET_CONFIGURATIONS)
            ?: error("The downloaded file isn't a valid APK.")

        if (archive.packageName != appContext.packageName) {
            error("That APK is for ${archive.packageName}, not ${appContext.packageName}.")
        }

        // Phone and watch builds share an applicationId, so the package name above can't tell them
        // apart. The watch build is the one declaring the watch hardware feature; installing the
        // phone build over it would leave the watch with an app that has no watch UI.
        if (target == UpdateTarget.WATCH) {
            val isWatchBuild = archive.reqFeatures.orEmpty().any { it.name == FEATURE_WATCH }
            if (!isWatchBuild) error("That APK isn't the watch build.")
        }

        val installedCerts = certificateHashes(installedPackage())
        val downloadCerts = certificateHashes(archive)
        if (installedCerts.isEmpty() || downloadCerts.isEmpty()) {
            error("Couldn't read the APK's signature.")
        }
        if (installedCerts.intersect(downloadCerts).isEmpty()) {
            error("Version ${version.name} is signed by a different key and was not installed.")
        }
    }

    private val signatureFlags: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun installedPackage(): PackageInfo? = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, signatureFlags)
    }.getOrNull()

    /** SHA-256 of each signing certificate, so two packages can be compared for a common signer. */
    private fun certificateHashes(info: PackageInfo?): Set<String> {
        if (info == null) return emptySet()
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.orEmpty().filterNotNull()
            .map { digest.digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }
            .toSet()
    }

    // ---- Install -----------------------------------------------------------------------------------

    /**
     * Hand the verified APK to the system installer. The user still confirms in the system's own
     * dialog — this only opens it.
     */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    /** Drop any downloaded APKs — they're just cache once installed. */
    fun clearDownloads() {
        runCatching { File(appContext.cacheDir, DOWNLOAD_DIR).deleteRecursively() }
    }

    fun reset() {
        _state.value = UpdateState.Idle
    }

    /**
     * Turn a failure into something a person can act on.
     *
     * Anything this class raises itself is already written for a reader and passes through
     * untouched. What doesn't is the socket layer's vocabulary: a stalled read surfaces as the
     * single word "timeout", which on a watch screen reads as the app being broken rather than the
     * connection having wandered off.
     */
    private fun Throwable.friendlyMessage(): String = when (this) {
        is UnknownHostException -> "No internet connection."
        is IOException -> "Connection lost. Try again."
        else -> message?.takeIf { it.isNotBlank() } ?: "Update failed (${this::class.simpleName})."
    }

    private companion object {
        const val DOWNLOAD_DIR = "updates"
        const val FEATURE_WATCH = "android.hardware.type.watch"

        /** Attempts at the download before it's reported as failed. Each one resumes, not restarts. */
        const val MAX_ATTEMPTS = 5

        /** Multiplied by the attempt number, so the gaps widen if the link is properly down. */
        const val RETRY_DELAY_MS = 2_000L

        const val CONNECT_TIMEOUT_SECONDS = 30L
        const val READ_TIMEOUT_SECONDS = 120L

        const val HTTP_PARTIAL = 206
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}
