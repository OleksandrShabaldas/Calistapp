package com.calistapp.core.medals

import com.calistapp.core.model.formatKg
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** What a medal is for — the colour of its ribbon. */
enum class MedalKind(val label: String) {
    SKILLS("Skills"),
    STRENGTH("Strength"),
    SHOWING_UP("Showing up"),
    HEART("Heart"),
    BALANCE("Balance"),
    LIFE("Life"),
}

/** The metal a tier is struck in. Onyx is kept for the two longest ladders' last rung. */
enum class MedalMetal { COPPER, SILVER, GOLD, ONYX }

/**
 * One rung of a medal line. [threshold] is in the line's own [MedalUnit] (reps, seconds, kg, weeks…);
 * [label] is how the rung reads on the face ("15", "+20", "52").
 */
data class MedalTier(val threshold: Double, val metal: MedalMetal, val label: String)

/**
 * One line of the catalog: a single medal, or a ladder of tiers struck in rising metals.
 *
 * @property id stable forever — the app persists which awards were seen by `id#tier`.
 * @property emblem the drawing on the face (`e-pullup`, `e-flame`…), resolved by the UI.
 * @property how the short line under the name that says what earns it.
 * @property hidden shown as an unnamed "Found by doing" medal, with no progress, until earned.
 */
data class MedalDefinition(
    val id: String,
    val kind: MedalKind,
    val name: String,
    val how: String,
    val emblem: String,
    val tiers: List<MedalTier>,
    val unit: MedalUnit,
    val rule: MedalRule,
    val hidden: Boolean = false,
)

/**
 * What a line measures. Thresholds live on the tiers; the numbers here are the rule's fixed conditions
 * (the 5 reps of a weighted pull-up, the 8 °C of a cold day) that no tier changes.
 */
sealed interface MedalRule {
    /** Most reps in one working set of [movement]. Single-side movements count the logged total. */
    data class RepsInOneSet(val movement: Movement) : MedalRule

    /** Longest single hold of [movement], in seconds. */
    data class HoldSeconds(val movement: Movement) : MedalRule

    /** Most added load on a set of [movement] of at least [minReps] reps. */
    data class WeightedReps(val movement: Movement, val minReps: Int) : MedalRule

    /** Calendar weeks in a row (Monday-first) with at least one session. */
    data object WeekStreak : MedalRule

    /** Sessions logged, all time. */
    data object SessionCount : MedalRule

    /** Every planned day of a calendar month trained, in a month with at least [minPlannedDays]. */
    data class PerfectMonth(val minPlannedDays: Int) : MedalRule

    /** A session after at least [daysOff] full days without one. */
    data class Comeback(val daysOff: Int) : MedalRule

    /** A session whose mean heart-rate recovery reached the threshold, in bpm. */
    data object HrRecovery : MedalRule

    /**
     * A run of a saved workout at an average heart rate at least the threshold below the mean of its
     * first [baselineRuns] runs. Only runs with trustworthy heart rate (see [FullHrSessions]) count.
     */
    data class SameWorkoutHrDrop(val baselineRuns: Int, val minCoverage: Double) : MedalRule

    /** Sessions measured, not estimated, with heart rate covering at least [minCoverage] of them. */
    data class FullHrSessions(val minCoverage: Double) : MedalRule

    /** Hours spent in heart-rate [zone] (1–5), all time. */
    data class ZoneHours(val zone: Int) : MedalRule

    /** Every one of the six [MuscleGroup]s trained within one calendar week. */
    data object FullBodyWeek : MedalRule

    /**
     * Push and pull working sets within [maxRatio] of each other (either way round) across [weeks]
     * calendar weeks, with at least [minSetsEachSide] of each, judged by `Exercise.force`.
     */
    data class EvenSplit(val maxRatio: Double, val weeks: Int, val minSetsEachSide: Int) : MedalRule

    /** Calendar weeks in a row with at least one leg working set. */
    data object LegsEveryWeek : MedalRule

    /** Distinct exercises ever worked (working sets, not warm-up routines). */
    data object DistinctExercises : MedalRule

    /** An outdoor session that felt colder than [belowFeelsLikeC]. */
    data class ColdOutdoor(val belowFeelsLikeC: Double) : MedalRule

    /** Sessions started at a local time from [fromHour] (inclusive) to [untilHour] (exclusive), wrapping midnight. */
    data class StartsBetween(val fromHour: Int, val untilHour: Int) : MedalRule

    /** Two substantial sessions on one calendar day, at least [minGapMinutes] apart. */
    data class TwoInOneDay(val minGapMinutes: Int) : MedalRule

    /** Most reps of a single exercise in one session. */
    data object RepsInOneSession : MedalRule
}

/**
 * How a line's number reads in a progress label ("13 of 15", "+5 of +10 kg", "8 of 10 s") and in its
 * to-go phrase ("2 reps to go", "1 week to go", "38 to go"). [EVENT] lines are things that happen or
 * don't (a comeback, a cold day): they have no bar to fill.
 */
enum class MedalUnit(
    private val prefix: String,
    private val suffix: String,
    private val one: String,
    private val many: String,
) {
    REPS("", "", "rep", "reps"),
    /** Single-side skills: the logged total of 2 reads as one rep on each leg. */
    LEGS("", " legs", "leg", "legs"),
    SECONDS("", " s", "s", "s"),
    KG("+", " kg", "kg", "kg"),
    STREAK_WEEKS("", "", "week", "weeks"),
    WEEKS("", " weeks", "week", "weeks"),
    COUNT("", "", "", ""),
    SESSIONS("", "", "session", "sessions"),
    BPM("", " bpm", "bpm", "bpm"),
    HOURS("", " h", "h", "h"),
    GROUPS("", " groups", "group", "groups"),
    DAYS("", " days", "day", "days"),
    EVENT("", "", "", ""),
    ;

    val measurable: Boolean get() = this != EVENT

    /** "13 of 15", "+5 of +10 kg", "4 of 10 h". */
    fun ofLabel(current: Double, target: Double): String =
        "$prefix${number(current, down = true)} of $prefix${number(target, down = true)}$suffix"

    /** "2 reps to go", "1 s to go", "38 to go"; under an hour of hours reads in minutes. */
    fun toGo(remaining: Double): String {
        if (this == HOURS && remaining < 1.0) {
            val minutes = ceil(remaining * 60 - ROUNDING).toInt().coerceAtLeast(1)
            return "$minutes min to go"
        }
        val n = number(remaining, down = false)
        val word = if (n == "1") one else many
        return if (word.isEmpty()) "$n to go" else "$n $word to go"
    }

    /**
     * Hours are floored in the label and ceilinged in the to-go, so the two always add up to the target
     * ("4 of 10 h", "6 h to go" at 4.4 h). Kilograms keep their half-kilo; everything else is whole.
     */
    private fun number(value: Double, down: Boolean): String = when (this) {
        KG -> formatKg(value)
        else -> (if (down) floor(value + ROUNDING) else ceil(value - ROUNDING)).roundToInt().toString()
    }

    private companion object {
        /** Absorbs float noise (599 min / 60 = 9.98333…) so a floor or ceiling lands where arithmetic says. */
        const val ROUNDING = 1e-6
    }
}

/**
 * One tier earned. Never stored as a fact: it is re-derived from the history on every read, so an
 * edited, imported or deleted session moves the medal with it.
 *
 * @property value the athlete's own number at the moment it was earned (11 reps, 32 s, −31 bpm).
 * @property engraving the rim text without the date, already upper-cased: "11 REPS IN ONE SET".
 */
data class MedalAward(
    val medalId: String,
    val tierIndex: Int,
    val metal: MedalMetal,
    val earnedAtMs: Long,
    val sessionId: String,
    val value: Double,
    val engraving: String,
) {
    /** What the app persists to remember this award was seen: "pull-ups#1". */
    val key: String get() = "$medalId#$tierIndex"
}

/**
 * Where a line stands against its next tier.
 *
 * @property nextTierIndex null once every tier is earned ([complete]).
 * @property fraction 0..1 toward the next tier, measured from zero (13 of 15 is 0.87).
 * @property measurable false for event lines, whose bar would only ever read 0 or 1.
 */
data class MedalProgress(
    val nextTierIndex: Int?,
    val fraction: Double,
    val label: String,
    val toGo: String,
    val measurable: Boolean,
) {
    val complete: Boolean get() = nextTierIndex == null
}

/** One catalog line as it stands for this history. [progress] is null for a hidden, unearned medal. */
data class MedalLine(
    val definition: MedalDefinition,
    val awards: List<MedalAward>,
    val progress: MedalProgress?,
) {
    val earnedTiers: Int get() = awards.size
    val isEarned: Boolean get() = awards.isNotEmpty()

    /** The tier the face shows: the highest struck, or null when nothing is struck yet. */
    val topAward: MedalAward? get() = awards.maxByOrNull { it.tierIndex }
}

/** Everything the medal case needs, derived from history in one pass. */
data class MedalReport(
    /** In catalog order. */
    val lines: List<MedalLine>,
) {
    /** Every tier earned, oldest first (ties in catalog order) — the order to strike them in. */
    val awards: List<MedalAward> by lazy {
        val order = lines.withIndex().associate { (i, line) -> line.definition.id to i }
        lines.flatMap { it.awards }
            .sortedWith(compareBy({ it.earnedAtMs }, { order[it.medalId] ?: 0 }, { it.tierIndex }))
    }

    fun line(id: String): MedalLine? = lines.firstOrNull { it.definition.id == id }
}
