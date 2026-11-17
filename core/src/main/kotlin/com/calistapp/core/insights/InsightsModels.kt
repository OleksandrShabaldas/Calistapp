package com.calistapp.core.insights

import com.calistapp.core.model.Exercise
import com.calistapp.core.model.UserProfile
import com.calistapp.core.progress.PerformedSession
import com.calistapp.core.progress.WeightEntry
import java.time.LocalDate
import java.time.ZoneId

/** The length of an Insights issue. Each period is compared with the one just before it. */
enum class InsightsPeriod(val days: Int, val label: String, val noun: String, val previous: String, val within: String) {
    WEEK(7, "Week", "week", "the week before", "this week"),
    FOUR_WEEKS(28, "4 weeks", "month", "the four weeks before", "in four weeks"),
    QUARTER(91, "Quarter", "quarter", "the quarter before", "this quarter"),
    YEAR(364, "Year", "year", "the year before", "this year"),
}

/** One calendar span, inclusive of both dates, with its epoch bounds (end exclusive). */
data class InsightsWindow(val start: LocalDate, val endInclusive: LocalDate, val startMs: Long, val endMs: Long) {
    fun contains(atMs: Long): Boolean = atMs in startMs until endMs
}

/** Everything the engine reads. Pure data, so a report can be built in a unit test. */
data class InsightsInput(
    val sessions: List<PerformedSession>,
    /** Exercise catalog by id, for muscles and push/pull. Missing ids fall back to name heuristics. */
    val exercises: Map<String, Exercise> = emptyMap(),
    /** Days the user planned to train (from the schedule). Empty = no schedule, judged by frequency. */
    val plannedDays: Set<LocalDate> = emptySet(),
    /** Minutes slept the night before each date, when sleep is connected. */
    val sleepBefore: Map<LocalDate, Int> = emptyMap(),
    val weights: List<WeightEntry> = emptyList(),
    /** Needed for training load and for reading effort against heart rate. */
    val profile: UserProfile? = null,
    val period: InsightsPeriod = InsightsPeriod.FOUR_WEEKS,
    val nowMs: Long,
    val zone: ZoneId = ZoneId.systemDefault(),
    /** Phrase ids the user saw recently; the phrasebook avoids repeating them. */
    val recentPhraseIds: Set<String> = emptySet(),
)

enum class Question(val number: String, val title: String, val short: String) {
    STRONGER("01", "Am I getting stronger?", "Stronger"),
    FITTER("02", "Am I getting fitter?", "Fitter"),
    BALANCED("03", "Am I balanced?", "Balanced"),
    CONSISTENT("04", "Am I consistent?", "Consistent"),
    RECOVERING("05", "Am I recovering?", "Recovered"),
}

/** How a question came out. Drives the verdict bar segment and the pill. */
enum class Verdict { GOOD, MIXED, WATCH, NOT_ENOUGH_DATA }

/** The rung of a key movement this period. */
enum class MovementStatus { IMPROVED, STUCK, SLIPPED, NEW, STEADY }

data class KeyMovement(
    val exerciseKey: String,
    val name: String,
    val from: String,
    val to: String,
    /** "▲ 2", "▲ 5 kg", "▲ 7 s"; blank when there's nothing to compare. */
    val delta: String,
    val status: MovementStatus,
    /** "5 reps, four sessions running" for a stuck movement. */
    val note: String = "",
)

/** A supporting row on a question's page ("Same Push workout, lower heart rate · 151 → 141 bpm"). */
data class Evidence(val title: String, val detail: String)

/** A dated value for a trend line. */
data class ChartPoint(val atMs: Long, val value: Double)

/** A vertical marker on a trend ("Circuit days added"). */
data class ChartMarker(val atMs: Long, val label: String)

/** The picture on each question card, chosen by the question. */
sealed interface InsightChart {
    data class Trend(
        val points: List<ChartPoint>,
        val unit: String,
        val lowLabel: String,
        val highLabel: String,
        val markers: List<ChartMarker> = emptyList(),
    ) : InsightChart

    data class Split(val parts: List<SplitPart>, val neglected: List<Neglected>) : InsightChart

    data class Calendar(val days: List<DayMark>) : InsightChart

    /** Felt effort against the effort the heart rate implies, per session, oldest first. */
    data class EffortGap(val points: List<EffortPoint>) : InsightChart

    data object None : InsightChart
}

enum class MovementGroup(val label: String, val noun: String, val dayNoun: String) {
    PUSH("Push", "pushing", "push"),
    PULL("Pull", "pulling", "pull"),
    CORE("Core", "core", "core"),
    LEGS("Legs", "legs", "leg"),
}

data class SplitPart(val group: MovementGroup, val sets: Int, val share: Double)
data class Neglected(val muscle: String, val days: Int)
data class DayMark(val date: LocalDate, val planned: Boolean, val trained: Boolean)
data class EffortPoint(val atMs: Long, val felt: Double, val expected: Double)

/** What a footer strip or recap card suggests doing, and where the app should take the user. */
enum class MoveKind { ADD_LOAD, UNSTICK, PLAN_GROUP_DAY, KEEP_DOING, EASY_CIRCUIT, SHORTEN_DAY, PLAN_WEEK, EASY_DAY }

data class Move(
    val kind: MoveKind,
    val title: String,
    val detail: String,
    /** Button text for the footer strip, when the move can be acted on in one tap. */
    val action: String? = null,
    /** Exercises the move refers to (to load, to add to a planned day). */
    val exerciseIds: List<String> = emptyList(),
    val group: MovementGroup? = null,
)

data class QuestionVerdict(
    val question: Question,
    val verdict: Verdict,
    /** "YES", "MOSTLY", "NOT QUITE", "WATCH IT", "NOT YET". */
    val pill: String,
    val fact: String,
    val chart: InsightChart,
    val move: Move?,
    val keyMovements: List<KeyMovement> = emptyList(),
    val evidence: List<Evidence> = emptyList(),
    /** The sessions behind the verdict, newest first ("See the 9 sessions behind this"). */
    val sessionIds: List<String> = emptyList(),
)

data class RecordEntry(
    val exerciseKey: String,
    val name: String,
    /** "14", "+20 kg", "32 s", "6 a side". */
    val value: String,
    /** "reps", "kg", "s", "" when the value carries its own unit. */
    val unit: String,
    /** "▲ 2", "▲ 5 KG". */
    val delta: String,
    val atMs: Long,
    val note: String = "",
    /** The movement's per-session best over the last twelve weeks, for the lead record's sparkline. */
    val points: List<ChartPoint> = emptyList(),
)

data class RecordBoard(val lead: RecordEntry, val others: List<RecordEntry>)

enum class NumberKey { SESSIONS, ACTIVE_TIME, KCAL, REPS, TRAINING_LOAD, BODYWEIGHT }

/** Which way a change reads: [good] true = green-ish, false = warning, null = neutral. */
data class NumberStat(val key: NumberKey, val label: String, val value: String, val delta: String?, val good: Boolean?)

data class InsightsReport(
    val period: InsightsPeriod,
    val issue: Int,
    val window: InsightsWindow,
    val previous: InsightsWindow,
    /** One or two sentences; the cover sets the first in white and the second muted. */
    val headline: List<String>,
    val summary: String,
    val questions: List<QuestionVerdict>,
    val records: RecordBoard?,
    val numbers: List<NumberStat>,
    /** Up to three things to do next, for the recap's last card. */
    val moves: List<Move>,
    val sessionsInWindow: Int,
    /** Every phrase id used, so the app can remember them and the next issue avoids them. */
    val phraseIds: List<String>,
) {
    val isEmpty: Boolean get() = sessionsInWindow == 0 && questions.all { it.verdict == Verdict.NOT_ENOUGH_DATA }
    val goodCount: Int get() = questions.count { it.verdict == Verdict.GOOD }
    val workCount: Int get() = questions.count { it.verdict == Verdict.MIXED || it.verdict == Verdict.WATCH }
}
