package com.calistapp.core.medals

import com.calistapp.core.medals.MedalMetal.COPPER
import com.calistapp.core.medals.MedalMetal.GOLD
import com.calistapp.core.medals.MedalMetal.ONYX
import com.calistapp.core.medals.MedalMetal.SILVER

/**
 * The 40 medals: 26 lines in six kinds. A line with several tiers is one medal re-struck in a rising
 * metal (copper → silver → gold, onyx only for the 52-week streak and the 500th session); every
 * one-off is copper. Order here is display order, and the ids are persisted, so never rename one.
 *
 * Names, "how" lines and emblems match the medal set drawn on the Insights design canvas.
 */
object MedalCatalog {

    val all: List<MedalDefinition> = listOf(
        // ---- Skills: first times, one-offs ----------------------------------------------------
        oneOff(
            "first-pull-up", MedalKind.SKILLS, "First pull-up", "One clean rep", "e-pullup",
            threshold = 1.0, unit = MedalUnit.REPS, rule = MedalRule.RepsInOneSet(Movement.PULL_UP),
        ),
        oneOff(
            "first-muscle-up", MedalKind.SKILLS, "First muscle-up", "Bar or rings", "e-muscleup",
            threshold = 1.0, unit = MedalUnit.REPS, rule = MedalRule.RepsInOneSet(Movement.MUSCLE_UP),
        ),
        // Single-side movements are logged as the total of both sides: one rep on each leg is 2.
        oneOff(
            "first-pistol-squat", MedalKind.SKILLS, "First pistol squat", "Each leg", "e-pistol",
            threshold = 2.0, unit = MedalUnit.LEGS, rule = MedalRule.RepsInOneSet(Movement.PISTOL_SQUAT),
        ),
        oneOff(
            "l-sit-30", MedalKind.SKILLS, "30-second L-sit", "One 30 s hold", "e-lsit",
            threshold = 30.0, unit = MedalUnit.SECONDS, rule = MedalRule.HoldSeconds(Movement.L_SIT),
        ),
        oneOff(
            "tuck-front-lever", MedalKind.SKILLS, "Tuck front lever", "One 10 s hold", "e-lever",
            threshold = 10.0, unit = MedalUnit.SECONDS, rule = MedalRule.HoldSeconds(Movement.FRONT_LEVER),
        ),

        // ---- Strength: ladders, all in one set ------------------------------------------------
        ladder(
            "pull-ups", MedalKind.STRENGTH, "Pull-ups", "In one set", "e-pullup",
            tiers = threeTiers(10, 15, 20), unit = MedalUnit.REPS, rule = MedalRule.RepsInOneSet(Movement.PULL_UP),
        ),
        ladder(
            "push-ups", MedalKind.STRENGTH, "Push-ups", "In one set", "e-pushup",
            tiers = threeTiers(25, 50, 75), unit = MedalUnit.REPS, rule = MedalRule.RepsInOneSet(Movement.PUSH_UP),
        ),
        ladder(
            "dips", MedalKind.STRENGTH, "Dips", "In one set", "e-dip",
            tiers = threeTiers(15, 25, 35), unit = MedalUnit.REPS, rule = MedalRule.RepsInOneSet(Movement.DIP),
        ),
        ladder(
            "weighted-pull-up", MedalKind.STRENGTH, "Weighted pull-up", "Kg × 5 reps", "e-kettle",
            tiers = threeTiers(10, 20, 30, prefix = "+"), unit = MedalUnit.KG,
            rule = MedalRule.WeightedReps(Movement.PULL_UP, minReps = 5),
        ),

        // ---- Showing up ----------------------------------------------------------------------
        ladder(
            "week-streak", MedalKind.SHOWING_UP, "Week streak", "Weeks in a row", "e-flame",
            tiers = fourTiers(4, 10, 26, 52), unit = MedalUnit.STREAK_WEEKS, rule = MedalRule.WeekStreak,
        ),
        ladder(
            "sessions", MedalKind.SHOWING_UP, "Sessions", "Logged", "e-check",
            tiers = fourTiers(50, 100, 250, 500), unit = MedalUnit.COUNT, rule = MedalRule.SessionCount,
        ),
        oneOff(
            "perfect-month", MedalKind.SHOWING_UP, "Perfect month", "Every planned day", "e-calcheck",
            threshold = 1.0, unit = MedalUnit.DAYS, rule = MedalRule.PerfectMonth(minPlannedDays = 4),
        ),
        oneOff(
            "comeback", MedalKind.SHOWING_UP, "Comeback", "Back after 14 days off", "e-comeback",
            threshold = 1.0, unit = MedalUnit.EVENT, rule = MedalRule.Comeback(daysOff = 14),
        ),

        // ---- Heart: only a heart-rate tracker can award these ----------------------------------
        oneOff(
            "recovery-30", MedalKind.HEART, "Recovery 30", "−30 bpm in a minute", "e-heartpulse",
            threshold = 30.0, unit = MedalUnit.BPM, rule = MedalRule.HrRecovery,
        ),
        oneOff(
            "ten-bpm-lower", MedalKind.HEART, "10 bpm lower", "Same workout", "e-trenddown",
            threshold = 10.0, unit = MedalUnit.BPM,
            rule = MedalRule.SameWorkoutHrDrop(baselineRuns = 3, minCoverage = FULL_HR_COVERAGE),
        ),
        oneOff(
            "every-beat", MedalKind.HEART, "Every beat", "25 full-HR sessions", "e-ecg",
            threshold = 25.0, unit = MedalUnit.SESSIONS, rule = MedalRule.FullHrSessions(FULL_HR_COVERAGE),
        ),
        oneOff(
            "steady-engine", MedalKind.HEART, "Steady engine", "10 h in zone 2", "e-gauge",
            threshold = 10.0, unit = MedalUnit.HOURS, rule = MedalRule.ZoneHours(zone = 2),
        ),

        // ---- Balance -------------------------------------------------------------------------
        oneOff(
            "full-body-week", MedalKind.BALANCE, "Full-body week", "Every muscle group", "e-body",
            threshold = MuscleGroup.entries.size.toDouble(), unit = MedalUnit.GROUPS, rule = MedalRule.FullBodyWeek,
        ),
        oneOff(
            "even-split", MedalKind.BALANCE, "Even split", "Push : pull ≤ 1.2", "e-scales",
            threshold = 1.0, unit = MedalUnit.COUNT,
            rule = MedalRule.EvenSplit(maxRatio = 1.2, weeks = 4, minSetsEachSide = 20),
        ),
        oneOff(
            "legs-every-week", MedalKind.BALANCE, "Legs every week", "8 weeks running", "e-legs",
            threshold = 8.0, unit = MedalUnit.WEEKS, rule = MedalRule.LegsEveryWeek,
        ),
        oneOff(
            "explorer", MedalKind.BALANCE, "Explorer", "50 exercises", "e-compass",
            threshold = 50.0, unit = MedalUnit.COUNT, rule = MedalRule.DistinctExercises,
        ),

        // ---- Life ----------------------------------------------------------------------------
        oneOff(
            "cold-day", MedalKind.LIFE, "Cold-day session", "Outside under 8°", "e-snow",
            threshold = 1.0, unit = MedalUnit.EVENT, rule = MedalRule.ColdOutdoor(belowFeelsLikeC = 8.0),
        ),
        // Early bird is 4–7 am: a 2 am start is a late night, not an early morning.
        oneOff(
            "early-bird", MedalKind.LIFE, "Early bird", "10 before 7 am", "e-sunrise",
            threshold = 10.0, unit = MedalUnit.COUNT, rule = MedalRule.StartsBetween(fromHour = 4, untilHour = 7),
        ),
        oneOff(
            "night-owl", MedalKind.LIFE, "Night owl", "10 after 9 pm", "e-moon",
            threshold = 10.0, unit = MedalUnit.COUNT, rule = MedalRule.StartsBetween(fromHour = 21, untilHour = 4),
        ),
        oneOff(
            "twice-in-a-day", MedalKind.LIFE, "Twice in a day", "Two sessions in one day", "e-twice",
            threshold = 1.0, unit = MedalUnit.EVENT, rule = MedalRule.TwoInOneDay(minGapMinutes = 60),
            hidden = true,
        ),
        oneOff(
            "century", MedalKind.LIFE, "Century", "100 reps of one move in a session", "e-hundred",
            threshold = 100.0, unit = MedalUnit.REPS, rule = MedalRule.RepsInOneSession,
            hidden = true,
        ),
    )

    fun byId(id: String): MedalDefinition? = all.firstOrNull { it.id == id }

    /** What a hidden medal shows until it is earned. */
    const val HIDDEN_NAME = "Hidden"
    const val HIDDEN_HOW = "Found by doing"
    const val HIDDEN_EMBLEM = "e-question"
}

/** Heart rate must cover this much of a session for it to count as fully measured. */
internal const val FULL_HR_COVERAGE = 0.95

private fun oneOff(
    id: String,
    kind: MedalKind,
    name: String,
    how: String,
    emblem: String,
    threshold: Double,
    unit: MedalUnit,
    rule: MedalRule,
    hidden: Boolean = false,
) = MedalDefinition(
    id = id, kind = kind, name = name, how = how, emblem = emblem,
    tiers = listOf(MedalTier(threshold, COPPER, label(threshold))),
    unit = unit, rule = rule, hidden = hidden,
)

private fun ladder(
    id: String,
    kind: MedalKind,
    name: String,
    how: String,
    emblem: String,
    tiers: List<MedalTier>,
    unit: MedalUnit,
    rule: MedalRule,
) = MedalDefinition(id = id, kind = kind, name = name, how = how, emblem = emblem, tiers = tiers, unit = unit, rule = rule)

private fun threeTiers(a: Int, b: Int, c: Int, prefix: String = "") = listOf(
    MedalTier(a.toDouble(), COPPER, "$prefix$a"),
    MedalTier(b.toDouble(), SILVER, "$prefix$b"),
    MedalTier(c.toDouble(), GOLD, "$prefix$c"),
)

private fun fourTiers(a: Int, b: Int, c: Int, d: Int) = threeTiers(a, b, c) + MedalTier(d.toDouble(), ONYX, "$d")

private fun label(threshold: Double): String =
    if (threshold % 1.0 == 0.0) threshold.toInt().toString() else threshold.toString()
