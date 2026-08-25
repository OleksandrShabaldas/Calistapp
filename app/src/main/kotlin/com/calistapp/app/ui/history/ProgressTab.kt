package com.calistapp.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.common.GlassCard
import com.calistapp.app.ui.common.GradientStatCard
import com.calistapp.app.ui.common.MetricBlock
import com.calistapp.app.ui.common.SectionHeading
import com.calistapp.app.ui.common.SegmentedToggle
import com.calistapp.app.ui.common.formatDate
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.Sky
import com.calistapp.app.ui.theme.StatAmber
import com.calistapp.app.ui.theme.StatBlue
import com.calistapp.app.ui.theme.StatGreen
import com.calistapp.app.ui.theme.StatOlive
import com.calistapp.app.ui.theme.StatRed
import com.calistapp.app.ui.theme.StatTeal
import com.calistapp.app.ui.theme.Violet
import com.calistapp.core.progress.BodyMassTrend
import com.calistapp.core.progress.ExerciseProgress
import com.calistapp.core.progress.StatsPeriod
import com.calistapp.core.progress.StatsSummary
import com.calistapp.core.progress.TrainingLoad
import com.calistapp.core.progress.TrainingProgress
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * What the training adds up to.
 *
 * The session list answers "what did I do"; this answers "am I getting anywhere", which is the
 * question a training log exists for. A period selector scopes the headline grid to this week / month
 * / all time; the multi-week chart, rolling training load and personal bests below sit outside that
 * window on purpose — they are trends, not snapshots. Every figure is derived from stored sessions on
 * demand, so nothing can drift away from the history it describes.
 */
fun LazyListScope.progressTab(
    stats: StatsSummary?,
    progress: TrainingProgress?,
    bodyMass: BodyMassTrend?,
    period: StatsPeriod,
    onPeriodChange: (StatsPeriod) -> Unit,
) {
    if (progress == null) return

    if (progress.isEmpty) {
        item {
            GlassCard {
                Text("Nothing to chart yet", style = MaterialTheme.typography.titleMedium, color = Chalk)
                Text(
                    "Finish a workout and this fills in: weekly trend, personal bests, and which " +
                        "movements you're actually putting the volume into.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                )
            }
        }
        return
    }

    // Period selector + the coloured headline grid it scopes.
    item {
        SegmentedToggle(
            options = StatsPeriod.entries.map { it.label },
            selectedIndex = period.ordinal,
            onSelect = { onPeriodChange(StatsPeriod.entries[it]) },
        )
    }
    stats?.let { item { StatGrid(it) } }

    // The 30-day day-dot strip — always 30 days, independent of the period above.
    if (progress.recentDays.isNotEmpty()) item { StreakStrip(progress) }

    progress.ramp?.let { item { TrainingLoadCard(it) } }
    item { WeeklyChart(progress) }
    bodyMass?.let { item { BodyMassCard(it) } }

    if (progress.exercises.isNotEmpty()) {
        item {
            SectionHeading(
                "Most performed exercises",
                count = progress.exercises.size,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        items(progress.exercises, key = { it.key }) { ExerciseProgressRow(it) }
    }
}

/**
 * The six-tile headline grid. Colour carries meaning per tile, so these are solid gradient cards
 * rather than glass. Volume is honestly zero for pure bodyweight training, which is why reps and
 * exercise count sit alongside it.
 */
@Composable
private fun StatGrid(stats: StatsSummary) {
    val tiles = listOf(
        Tile("${stats.workouts}", "Workouts", StatRed, stats.period.label.lowercase()),
        Tile("${stats.exercises}", "Exercises", StatAmber, "movements"),
        Tile("${stats.totalReps}", "Reps", StatOlive, "working sets"),
        Tile(volumeLabel(stats.totalVolumeKg), "Volume", StatGreen, "added load"),
        Tile(durationLabel(stats.totalActiveMs), "Duration", StatTeal, "active time"),
        Tile(durationLabel(stats.avgActiveMs), "Avg session", StatBlue, "per workout"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    GradientStatCard(
                        value = t.value,
                        label = t.label,
                        gradient = t.gradient,
                        caption = t.caption,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (stats.isEmpty) {
            Text(
                "No sessions in this window yet — try All time.",
                style = MaterialTheme.typography.bodySmall,
                color = Ash,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private data class Tile(
    val value: String,
    val label: String,
    val gradient: com.calistapp.app.ui.theme.StatGradient,
    val caption: String,
)

/**
 * The last 30 days as a train/rest strip — one pip per day, lit when you trained. The most honest
 * thing a consistency view can show is the gaps, so rest days are drawn, not omitted.
 */
@Composable
private fun StreakStrip(progress: TrainingProgress) {
    GlassCard {
        SectionHeading("Last 30 days", count = progress.recentTrainedDays)
        Row(
            Modifier.fillMaxWidth().height(30.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            progress.recentDays.forEach { trained ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(if (trained) 30.dp else 16.dp)
                        .background(
                            if (trained) Flame else Chalk.copy(alpha = 0.07f),
                            RoundedCornerShape(3.dp),
                        ),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("30 days ago", style = MaterialTheme.typography.labelSmall, color = Ash)
            Text("today", style = MaterialTheme.typography.labelSmall, color = Ash)
        }
    }
}

/**
 * This week's training load against the four-week average.
 *
 * Load is Banister TRIMP — heart rate and duration through an exponential, so a short hard session
 * and a long easy one compare honestly in a way calories don't. Presented as a direction and a
 * ratio, never as a risk score: the acute:chronic literature is contested, and the app has no
 * business telling anyone they're about to get hurt.
 */
@Composable
private fun TrainingLoadCard(ramp: TrainingLoad.Ramp) {
    val accent = when (ramp.band) {
        TrainingLoad.Band.STEADY -> Flame
        TrainingLoad.Band.EASING_OFF -> Sky
        TrainingLoad.Band.RAMPING -> Amber
        TrainingLoad.Band.SHARP_JUMP -> Coral
    }

    GlassCard(accent = accent) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Training load", style = MaterialTheme.typography.titleMedium, color = Chalk)
                Text(ramp.band.label, style = MaterialTheme.typography.labelLarge, color = accent)
            }
            Text(
                "%.2f×".format(ramp.ratio),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
        Text(ramp.band.detail, style = MaterialTheme.typography.bodySmall, color = Ash)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MetricBlock("This week", "${ramp.acuteLoad.toInt()}", accent)
            MetricBlock("Average week", "${ramp.chronicLoad.toInt()}", Ash)
        }
        Text(
            "Banister TRIMP — heart rate and time, weighted so hard minutes count for more. " +
                "A description of your last week, not a prediction about your next one.",
            style = MaterialTheme.typography.labelSmall,
            color = Ash,
        )
    }
}

/**
 * Twelve weeks of calories as bars.
 *
 * Scaled to the busiest week rather than to a goal — this is about the shape of the trend, and a
 * chart pinned to a target you're nowhere near just shows twelve stubs. Weeks with nothing in them
 * are drawn as empty slots, because a gap is the most useful thing a training chart can show you.
 */
@Composable
private fun WeeklyChart(progress: TrainingProgress) {
    val peak = progress.peakWeekKcal.coerceAtLeast(1)

    GlassCard {
        SectionHeading("Last ${progress.weeks.size} weeks")
        Row(
            Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            progress.weeks.forEach { week ->
                val fraction = week.kcal.toFloat() / peak
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (week.trained) {
                        Text(
                            "${week.kcal}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Ash,
                            maxLines = 1,
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            // A trained week always shows something, even a light one.
                            .height((6f + fraction * 78f).dp)
                            .background(
                                if (week.trained) Flame.copy(alpha = 0.25f + fraction * 0.55f)
                                else Chalk.copy(alpha = 0.06f),
                                RoundedCornerShape(4.dp),
                            ),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                progress.weeks.firstOrNull()?.let { formatDate(it.weekStartMs).substringBefore(" •") }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = Ash,
            )
            Text("this week", style = MaterialTheme.typography.labelSmall, color = Ash)
        }
    }
}

/**
 * Bodyweight against training energy over the same window.
 *
 * The caveat is in the card, not just in a comment, because this is exactly the figure a fitness app
 * would be tempted to dress up as "your calorie estimate is 8% off" — and it can't be. Mass change
 * is intake minus *everything* you spend, and the app measures neither intake nor the resting
 * metabolism that dominates the rest. What's shown is the arithmetic, labelled as arithmetic.
 */
@Composable
private fun BodyMassCard(trend: BodyMassTrend) {
    val accent = when (trend.direction) {
        BodyMassTrend.Direction.DOWN -> Sky
        BodyMassTrend.Direction.UP -> Amber
        BodyMassTrend.Direction.STEADY -> Ash
    }
    val sign = if (trend.changeKg > 0) "+" else ""

    GlassCard {
        SectionHeading("Bodyweight")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MetricBlock("Now", "%.1f kg".format(trend.latest.weightKg), Chalk)
            MetricBlock("Change", "$sign%.1f kg".format(trend.changeKg), accent)
            MetricBlock("Over", "${trend.spanDays} days", Ash)
        }

        if (trend.isMeaningful) {
            Text(
                "That change corresponds to roughly ${kotlin.math.abs(trend.impliedEnergyKcal)} kcal " +
                    "${if (trend.changeKg < 0) "of net deficit" else "of net surplus"} over the period " +
                    "— about ${kotlin.math.abs(trend.impliedDailyKcal)} kcal a day. Training you " +
                    "logged in the same window came to ${trend.loggedTrainingKcal} kcal.",
                style = MaterialTheme.typography.bodySmall,
                color = Ash,
            )
        } else {
            Text(
                "Steady over ${trend.spanDays} days. Training logged in that window: " +
                    "${trend.loggedTrainingKcal} kcal.",
                style = MaterialTheme.typography.bodySmall,
                color = Ash,
            )
        }

        Text(
            "This is arithmetic, not a check on the calorie estimate. What you weigh responds to " +
                "what you eat minus everything you spend, and most of what you spend is resting " +
                "metabolism and daily movement — neither of which this app measures. Without food " +
                "logging, a difference here can't be pinned on any one of them.",
            style = MaterialTheme.typography.labelSmall,
            color = Ash,
        )
    }
}

@Composable
private fun ExerciseProgressRow(exercise: ExerciseProgress) {
    GlassCard(contentPadding = 12) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    exercise.exerciseName,
                    style = MaterialTheme.typography.titleSmall,
                    color = Chalk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${exercise.totalReps} reps · ${exercise.totalSets} sets · " +
                        "${exercise.sessionCount} ${if (exercise.sessionCount == 1) "session" else "sessions"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ash,
                )
            }
            Text(
                formatDate(exercise.lastPerformedMs).substringBefore(" •"),
                style = MaterialTheme.typography.labelSmall,
                color = Ash,
                textAlign = TextAlign.End,
            )
        }

        // The records. Shown only when there's something to show — a "best: —" row is noise.
        val most = exercise.mostReps
        val heaviest = exercise.heaviest
        if (most != null || heaviest != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                most?.let {
                    RecordChip(
                        label = "Most reps",
                        value = "${it.reps}" + if (it.addedWeightKg > 0) " · +${it.addedWeightKg.toInt()} kg" else "",
                        atMs = it.atMs,
                        accent = Flame,
                    )
                }
                heaviest?.let {
                    RecordChip(
                        label = "Heaviest",
                        value = "+${it.addedWeightKg.toInt()} kg × ${it.reps}",
                        atMs = it.atMs,
                        accent = Violet,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordChip(label: String, value: String, atMs: Long, accent: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ash)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
        Text(
            formatDate(atMs).substringBefore(" •"),
            style = MaterialTheme.typography.labelSmall,
            color = Ash,
        )
    }
}

/** "1.2k kg" / "480 kg" / "0 kg" — compact so a big lifter's total still fits a tile. */
private fun volumeLabel(kg: Double): String {
    val v = kg.roundToInt()
    return if (v >= 10_000) "${"%.1f".format(v / 1000.0)}k kg" else "$v kg"
}

/** "1h 12m" / "45m" — active time on a tile. */
private fun durationLabel(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    if (minutes < 60) return "${minutes}m"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0L) "${h}h" else "${h}h ${m}m"
}
