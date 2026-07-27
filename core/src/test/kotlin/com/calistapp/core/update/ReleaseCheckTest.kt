package com.calistapp.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `parses a release tag with and without the v prefix`() {
        assertEquals(AppVersion(0, 0, 1), AppVersion.parse("v0.0.1"))
        assertEquals(AppVersion(0, 0, 1), AppVersion.parse("0.0.1"))
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("  V1.2.3  "))
    }

    @Test
    fun `missing segments default to zero`() {
        assertEquals(AppVersion(1, 0, 0), AppVersion.parse("v1"))
        assertEquals(AppVersion(1, 2, 0), AppVersion.parse("1.2"))
    }

    @Test
    fun `a suffixed tag still yields its version`() {
        assertEquals(AppVersion(1, 2, 0), AppVersion.parse("1.2.0-beta1"))
    }

    @Test
    fun `garbage is rejected rather than guessed at`() {
        assertNull(AppVersion.parse(null))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("latest"))
    }

    @Test
    fun `ordering is numeric, not lexicographic`() {
        assertTrue(AppVersion(0, 0, 2) > AppVersion(0, 0, 1))
        assertTrue(AppVersion(0, 1, 0) > AppVersion(0, 0, 9))
        assertTrue(AppVersion(1, 0, 0) > AppVersion(0, 9, 9))
        // Off-scheme, but must still order sanely if someone ever tags it.
        assertTrue(AppVersion(0, 10, 0) > AppVersion(0, 9, 0))
    }

    @Test
    fun `version code matches the documented derivation`() {
        assertEquals(1, AppVersion(0, 0, 1).code)
        assertEquals(95, AppVersion(0, 9, 5).code)
        assertEquals(123, AppVersion(1, 2, 3).code)
    }
}

class ReleaseCheckTest {

    private val phoneApk = ReleaseAsset("Calistapp-phone.apk", "https://example.com/p.apk", 100)
    private val wearApk = ReleaseAsset("Calistapp-wear.apk", "https://example.com/w.apk", 200)

    private fun release(
        tag: String,
        assets: List<ReleaseAsset> = listOf(phoneApk, wearApk),
        draft: Boolean = false,
        prerelease: Boolean = false,
    ) = GitHubRelease(
        tagName = tag,
        name = tag,
        body = "notes",
        htmlUrl = "https://example.com/release",
        draft = draft,
        prerelease = prerelease,
        assets = assets,
    )

    private val installed = AppVersion(0, 0, 1)

    // ---- The core decision ----------------------------------------------------------------------

    @Test
    fun `a newer release is offered`() {
        val status = ReleaseCheck.evaluate(installed, release("v0.0.2"), UpdateTarget.PHONE)
        val available = status as UpdateStatus.Available
        assertEquals(AppVersion(0, 0, 2), available.version)
        assertEquals(phoneApk, available.asset)
    }

    @Test
    fun `the same version is up to date`() {
        assertEquals(
            UpdateStatus.UpToDate,
            ReleaseCheck.evaluate(installed, release("v0.0.1"), UpdateTarget.PHONE),
        )
    }

    @Test
    fun `an older release is never offered as a downgrade`() {
        // Android refuses to install a lower versionCode over a higher one, so offering this would
        // hand the user an opaque installer failure.
        assertEquals(
            UpdateStatus.UpToDate,
            ReleaseCheck.evaluate(AppVersion(0, 2, 0), release("v0.1.0"), UpdateTarget.PHONE),
        )
    }

    @Test
    fun `drafts and prereleases are ignored`() {
        assertEquals(
            UpdateStatus.UpToDate,
            ReleaseCheck.evaluate(installed, release("v0.9.0", draft = true), UpdateTarget.PHONE),
        )
        assertEquals(
            UpdateStatus.UpToDate,
            ReleaseCheck.evaluate(installed, release("v0.9.0", prerelease = true), UpdateTarget.PHONE),
        )
    }

    // ---- Asset selection: the two APKs must never be confused ------------------------------------

    @Test
    fun `each target gets its own APK`() {
        val forPhone = ReleaseCheck.evaluate(installed, release("v0.0.2"), UpdateTarget.PHONE)
        val forWatch = ReleaseCheck.evaluate(installed, release("v0.0.2"), UpdateTarget.WATCH)
        assertEquals(phoneApk, (forPhone as UpdateStatus.Available).asset)
        assertEquals(wearApk, (forWatch as UpdateStatus.Available).asset)
    }

    @Test
    fun `the watch never receives the phone build`() {
        // Installing the phone APK on a watch would replace the watch app with something that has
        // no watch UI — worse than not updating at all.
        val onlyPhone = release("v0.0.2", assets = listOf(phoneApk))
        val status = ReleaseCheck.evaluate(installed, onlyPhone, UpdateTarget.WATCH)
        assertTrue("Expected Unavailable, got $status", status is UpdateStatus.Unavailable)
    }

    @Test
    fun `an unlabelled apk is treated as the phone build`() {
        val plain = ReleaseAsset("Calistapp.apk", "https://example.com/a.apk", 1)
        assertEquals(plain, ReleaseCheck.assetFor(listOf(plain), UpdateTarget.PHONE))
        assertNull(ReleaseCheck.assetFor(listOf(plain), UpdateTarget.WATCH))
    }

    @Test
    fun `watch assets are matched however they are spelled`() {
        listOf("Calistapp-wear.apk", "calistapp-WEAR.apk", "Calistapp-watch-v2.apk").forEach { name ->
            val asset = ReleaseAsset(name, "https://example.com/w.apk", 1)
            assertEquals(
                "'$name' should be recognised as the watch build",
                asset,
                ReleaseCheck.assetFor(listOf(asset), UpdateTarget.WATCH),
            )
        }
    }

    @Test
    fun `non-apk attachments are ignored`() {
        val notes = ReleaseAsset("CHANGELOG.md", "https://example.com/c.md", 1)
        val status = ReleaseCheck.evaluate(installed, release("v0.0.2", listOf(notes)), UpdateTarget.PHONE)
        assertTrue("Expected Unavailable, got $status", status is UpdateStatus.Unavailable)
    }

    // ---- Refusals ---------------------------------------------------------------------------------

    @Test
    fun `a plain-HTTP download is refused`() {
        val insecure = ReleaseAsset("Calistapp-phone.apk", "http://example.com/p.apk", 1)
        val status = ReleaseCheck.evaluate(installed, release("v0.0.2", listOf(insecure)), UpdateTarget.PHONE)
        assertTrue("Expected Unavailable, got $status", status is UpdateStatus.Unavailable)
    }

    @Test
    fun `an unparseable tag is reported rather than ignored`() {
        val status = ReleaseCheck.evaluate(installed, release("nightly"), UpdateTarget.PHONE)
        assertTrue("Expected Unavailable, got $status", status is UpdateStatus.Unavailable)
    }

    @Test
    fun `no published release is not an error state`() {
        val status = ReleaseCheck.evaluate(installed, null, UpdateTarget.PHONE)
        assertTrue("Expected Unavailable, got $status", status is UpdateStatus.Unavailable)
    }
}
