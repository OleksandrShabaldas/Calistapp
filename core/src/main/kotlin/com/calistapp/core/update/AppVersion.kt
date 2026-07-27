package com.calistapp.core.update

/**
 * A release version under Calistapp's scheme: `major.minor.patch`, every segment a single digit.
 *
 * `0.0.x` is a small fix, `0.x.0` a bigger change, and before any segment would need a second digit
 * it carries into the one on its left — the release after `0.9.x` is `1.0.0`, never `0.10.0`.
 *
 * [parse] is deliberately more permissive than that scheme: it accepts a leading `v`, extra
 * whitespace, a missing patch segment, and multi-digit numbers. An updater reads tags written by
 * whoever cut the release, and refusing to parse a slightly-off tag would mean silently never
 * offering an update. [compareTo] is numeric, so even an off-scheme `0.10.0` orders correctly.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<AppVersion> {

    /** The canonical string, without a `v` prefix. */
    val name: String get() = "$major.$minor.$patch"

    /**
     * The Android `versionCode` this version implies. Derived rather than tracked separately so the
     * two can't drift; only valid while segments stay single-digit, which the scheme guarantees.
     */
    val code: Int get() = major * 100 + minor * 10 + patch

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString(): String = name

    companion object {
        /** Parse a tag or version name (`v0.0.1`, `0.0.1`, `0.1`). Returns null if it isn't one. */
        fun parse(raw: String?): AppVersion? {
            val trimmed = raw?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
            // Stop at the first character that can't be part of a dotted number, so a tag like
            // "1.2.0-beta" still yields 1.2.0 rather than nothing.
            val numeric = trimmed.takeWhile { it.isDigit() || it == '.' }
            val parts = numeric.split('.').filter { it.isNotBlank() }
            if (parts.isEmpty()) return null
            val nums = parts.map { it.toIntOrNull() ?: return null }
            return AppVersion(
                major = nums[0],
                minor = nums.getOrElse(1) { 0 },
                patch = nums.getOrElse(2) { 0 },
            )
        }
    }
}
