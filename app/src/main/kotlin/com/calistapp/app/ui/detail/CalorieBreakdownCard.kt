package com.calistapp.app.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.common.SectionCard
import com.calistapp.app.ui.common.formatClock
import com.calistapp.app.ui.common.formatCompact
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.Sky
import com.calistapp.app.ui.theme.Violet
import com.calistapp.core.calorie.CalorieAudit
import com.calistapp.core.calorie.ExerciseIntensity
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.Sex
import java.util.Locale
import kotlin.math.abs

/**
 * The calorie figure, shown working.
 *
 * A number you can't check is a number you have to trust, and there's no reason to trust a fitness
 * app's calorie count — most of them are a heart-rate average times a constant, dressed up. This
 * card lays out every input, every published formula, and every intermediate value, block by block,
 * so the total can be reproduced with a calculator. Where the engine chose between competing
 * estimates it says which one won and by how much.
 *
 * Collapsed by default: it's an audit trail, not the headline.
 */
@Composable
fun CalorieBreakdownCard(audit: CalorieAudit, storedKcal: Double?) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    SectionCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "Collapse" else "Expand") {
                    expanded = !expanded
                },
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "How this was calculated",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Chalk,
                )
                Text(
                    "${audit.summary.totalKcal.kcal()} kcal from ${audit.sampling.sampleCount} heart-rate " +
                        "readings over ${audit.blocks.size} blocks — every step shown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ash,
                )
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = Ash,
                modifier = Modifier.size(22.dp),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                StaleProfileNotice(audit, storedKcal)
                InputsStep(audit)
                RestingStep(audit)
                HeartRateStep(audit)
                TimeStep(audit)
                BlocksStep(audit)
                TotalStep(audit)
                SourcesNote(audit)
            }
        }
    }
}

/**
 * The audit is recomputed from the stored samples using the *current* profile, so editing your
 * weight or age after the fact makes it disagree with the figure recorded at the time. Saying so is
 * the difference between a discrepancy that's explained and one that looks like a bug.
 */
@Composable
private fun StaleProfileNotice(audit: CalorieAudit, storedKcal: Double?) {
    if (storedKcal == null) return
    val drift = abs(storedKcal - audit.summary.totalKcal)
    if (drift < 0.5) return

    Column(
        Modifier
            .fillMaxWidth()
            .background(Amber.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Recomputed with your current profile",
            style = MaterialTheme.typography.labelLarge,
            color = Amber,
        )
        Text(
            "This session was recorded at ${storedKcal.kcal()} kcal. The working below redoes the " +
                "same calculation with the body data in your profile today, which comes to " +
                "${audit.summary.totalKcal.kcal()} kcal. The stored figure is the one shown above.",
            style = MaterialTheme.typography.bodySmall,
            color = Ash,
        )
    }
}

@Composable
private fun InputsStep(audit: CalorieAudit) {
    val p = audit.profile
    Step(1, "What it used about you") {
        Note(
            "Both formulas below are functions of your body. Nothing else about you enters the " +
                "calculation.",
        )
        AuditRow("Weight", "${p.weightKg.n(1)} kg")
        AuditRow("Height", "${p.heightCm.n(0)} cm")
        AuditRow("Age", "${p.ageYears}")
        AuditRow("Sex", if (p.sex == Sex.MALE) "Male" else "Female")
        AuditRow("VO₂max", p.vo2Max?.let { "${it.n(1)} ml/kg/min" } ?: "not set")
        AuditRow("Max HR", "${p.effectiveMaxHr} bpm" + if (p.maxHr == null) " (estimated from age)" else "")
        if (p.vo2Max == null) {
            Note(
                "Without VO₂max the estimate falls back to the age/weight/sex regression. Adding it " +
                    "in your profile switches to the fitness-adjusted one, which is measurably closer.",
            )
        }
    }
}

@Composable
private fun RestingStep(audit: CalorieAudit) {
    val r = audit.resting
    Step(2, "Resting metabolism (Mifflin–St Jeor)") {
        Note(
            "What your body burns doing nothing. It gets subtracted from every slice, so the total " +
                "is energy the workout actually cost — not energy you'd have spent on the sofa.",
        )
        Formula("kcal/day = 10×weight + 6.25×height − 5×age " + if (r.sexOffset > 0) "+ 5" else "− 161")
        AuditRow("10 × ${audit.profile.weightKg.n(1)} kg", r.weightTerm.n(1))
        AuditRow("6.25 × ${audit.profile.heightCm.n(0)} cm", r.heightTerm.n(1))
        AuditRow("− 5 × ${audit.profile.ageYears}", r.ageTerm.n(1))
        AuditRow(if (r.sexOffset > 0) "male +5" else "female −161", r.sexOffset.n(1))
        AuditRow("Resting", "${r.kcalPerDay.n(0)} kcal/day  ·  ${r.kcalPerMin.n(3)} kcal/min", strong = true)
    }
}

@Composable
private fun HeartRateStep(audit: CalorieAudit) {
    Step(3, "Heart rate → energy (Keytel et al., 2005)") {
        Note(
            "Evaluated at each heart-rate reading and integrated over the real curve — not applied " +
                "once to a session average, which is what makes most trackers' numbers soft.",
        )
        AuditRow("Regression", audit.keytel.label)
        Formula(audit.keytel.equation)
        AuditRow("Field calibration", "× ${audit.settings.hrCalibration.n(2)}")
        Note(
            "Keytel was fitted on steady cycling and treadmill work. Resistance training breaks its " +
                "assumptions in ways that all push the same way — heart rate stays high between " +
                "sets, gripping and bracing raise it without matching oxygen uptake, and wrist " +
                "sensors read high under motion — so the heart-rate term is scaled down " +
                "${((1 - audit.settings.hrCalibration) * 100).n(0)}%.",
        )
        if (audit.settings.netOfResting) {
            AuditRow("Reported as", "net of resting metabolism")
        }
        AuditRow(
            "Rest blocks bounded to",
            "${audit.resting.kcalPerMin.n(2)}–" +
                "${(audit.resting.kcalPerMin * audit.settings.restCeilingMultiplier).n(2)} kcal/min gross",
        )
        Note(
            "Heart rate stays high for minutes after a hard set without the oxygen cost to match, so " +
                "rest is capped at ${audit.settings.restCeilingMultiplier.n(0)}× resting before the " +
                "subtraction — at most ${
                    (audit.resting.kcalPerMin * (audit.settings.restCeilingMultiplier - 1) *
                        audit.settings.hrCalibration).n(2)
                } kcal/min ends up counted.",
        )
    }
}

@Composable
private fun TimeStep(audit: CalorieAudit) {
    val s = audit.sampling
    Step(4, "How the time was counted") {
        AuditRow("Heart-rate readings", "${s.sampleCount}")
        AuditRow("Spanning", formatClock(s.lastSampleMs - s.firstSampleMs))
        AuditRow("Integration slices", "${s.sliceCount}")
        Note(
            "One slice per reading, plus one at every work/rest boundary — so no slice is ever " +
                "split across working and resting.",
        )
        if (s.gapSliceCount > 0) {
            AuditRow(
                "Sensor gaps",
                "${s.gapSliceCount}, ${formatCompact(s.uncreditedGapMs)} not credited",
                accent = Coral,
            )
            Note(
                "A gap longer than ${formatCompact(audit.settings.maxIntervalMs)} is only credited " +
                    "up to that cap. Guessing across a dropout is how estimates run away.",
            )
        } else {
            AuditRow("Sensor gaps", "none")
        }
        AuditRow("Working", formatCompact(audit.summary.activeDurationMs))
        AuditRow("Resting", formatCompact(audit.summary.restDurationMs))
    }
}

@Composable
private fun BlocksStep(audit: CalorieAudit) {
    Step(5, "Block by block") {
        Note(
            "Each stretch of work is scored three ways — heart rate (corrected), the physical work " +
                "of the reps, and bare resting metabolism — and counted at whichever is largest. " +
                "Tap a block for its arithmetic.",
        )
        // Keyed so each block's open/closed state belongs to that block and survives a rebuild —
        // without it every row in the loop shares one saveable slot.
        audit.blocks.forEach { block ->
            key(block.ordinal) { BlockRow(block, audit.settings) }
        }
        if (audit.unsegmentedMs > 0) {
            AuditRow(
                "Time outside any block",
                "${formatCompact(audit.unsegmentedMs)} · ${audit.unsegmentedKcal.kcal()} kcal",
            )
            Note("Heart rate recorded before the first work/rest toggle, or after the last one.")
        }
    }
}

@Composable
private fun BlockRow(block: CalorieAudit.Block, settings: CalorieAudit.Settings) {
    var open by rememberSaveable { mutableStateOf(false) }
    val accent = block.basis.accent()

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
            .clickable(onClickLabel = if (open) "Collapse" else "Expand") { open = !open }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(accent, RoundedCornerShape(4.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    block.title(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Chalk,
                )
                Text(
                    block.subtitle(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Ash,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${block.kcal.kcal()} kcal",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    block.basis.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Ash,
                )
            }
        }

        AnimatedVisibility(visible = open) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                HeartRateTermRows(block, settings)
                block.correction?.let { CorrectionRows(it, block) }
                block.mechanical?.let { RepWorkRows(it, block) }
                if (block.type == SegmentType.ACTIVE) {
                    AuditRow("Resting floor", "${block.restingFloorKcal.kcal()} kcal")
                    AuditRow(
                        "Counted at the largest",
                        "${block.kcal.kcal()} kcal · ${block.basis.label()}",
                        strong = true,
                        accent = accent,
                    )
                }
            }
        }
    }
}

/**
 * The heart-rate chain, written so it reconciles line by line. Where a bound actually bit, the
 * bounded value is shown in the chain rather than left implicit — otherwise the arithmetic on screen
 * visibly fails to reach the result, which is exactly the impression this card exists to avoid.
 */
@Composable
private fun HeartRateTermRows(block: CalorieAudit.Block, settings: CalorieAudit.Settings) {
    val hr = block.heartRate
    val ceiling = hr.restingKcalPerMin * settings.restCeilingMultiplier

    SubHeading("Heart rate")
    AuditRow("Average over the block", "${hr.avgBpm} bpm")
    AuditRow("Keytel at ${hr.avgBpm} bpm", "${hr.keytelAtAvgKcalPerMin.n(2)} kcal/min gross")
    if (hr.ceilingSlices > 0) {
        AuditRow(
            "Capped at ${settings.restCeilingMultiplier.n(0)}× resting " +
                "(${hr.ceilingSlices}/${hr.sliceCount} slices)",
            "${ceiling.n(2)} kcal/min gross",
            accent = Sky,
        )
    }
    if (hr.floorSlices > 0) {
        AuditRow(
            "Raised to resting (${hr.floorSlices}/${hr.sliceCount} slices)",
            "${hr.restingKcalPerMin.n(2)} kcal/min gross",
            accent = Sky,
        )
    }
    AuditRow("− resting", "${hr.restingKcalPerMin.n(2)} kcal/min")
    AuditRow("× calibration", hr.calibration.n(2))
    AuditRow(
        "Integrated over ${formatClock(hr.coveredDurationMs)}",
        "${hr.kcal.kcal()} kcal  (${hr.effectiveKcalPerMin.n(2)} kcal/min)",
        strong = true,
    )
    if (hr.sliceCount > 1) {
        Note(
            "${hr.sliceCount} slices, each at its own instantaneous heart rate. The rate above is " +
                "the integral divided by the time, so it differs a little from the value at the " +
                "average bpm whenever heart rate moved during the block.",
        )
    }
}

@Composable
private fun CorrectionRows(c: ExerciseIntensity.Correction, block: CalorieAudit.Block) {
    SubHeading("Exercise correction  × ${c.factor.n(3)}")
    Note(
        "Applied only where heart rate is blind. Bounded to " +
            "×${ExerciseIntensity.MIN_CORRECTION.n(2)}–×${ExerciseIntensity.MAX_CORRECTION.n(2)}, " +
            "so knowing the movement can nudge the estimate but never drive it.",
    )
    AuditRow(
        "Muscle recruited  ${(c.muscleMassFraction * 100).n(0)}% vs " +
            "${(c.referenceMuscleMassFraction * 100).n(0)}% reference",
        "× ${c.recruitment.n(3)}",
    )
    AuditRow("Cardiac lag over ${c.blockSeconds.n(0)} s", "× ${c.hrLag.n(3)}")
    AuditRow(
        if (c.isometric) "Static hold reads low on HR" else "Not a static hold",
        "× ${c.isometricUplift.n(3)}",
    )
    if (c.clamped) {
        AuditRow("Product ${c.product.n(3)} clamped to", c.factor.n(3), accent = Amber)
    }
    AuditRow(
        "${block.heartRate.kcal.kcal()} × ${c.factor.n(3)}",
        "${block.correctedKcal.kcal()} kcal",
        strong = true,
    )
}

@Composable
private fun RepWorkRows(w: ExerciseIntensity.MechanicalWork, block: CalorieAudit.Block) {
    SubHeading("Rep work (physics, independent of heart rate)")
    if (w.isometric) {
        Note("A static hold displaces nothing, so there is no external work to compute — its cost is internal and sits in the heart-rate term above.")
        return
    }
    AuditRow(
        "Mass moved  ${(w.loadFraction * 100).n(0)}% of ${w.bodyweightKg.n(1)} kg" +
            if (w.externalLoadKg > 0) " + ${w.externalLoadKg.n(1)} kg added" else "",
        "${w.movedKg.n(1)} kg",
    )
    AuditRow("Lift height", "${w.romMetres.n(2)} m")
    AuditRow("Lowering counted at", "× ${(1 + w.eccentricFactor).n(2)}")
    AuditRow("Muscular efficiency", "÷ ${(w.efficiency * 100).n(0)}%")
    AuditRow("Per rep", "${w.kcalPerRep.n(3)} kcal")
    AuditRow("× ${w.reps} reps", "${w.kcal.kcal()} kcal", strong = true)
    if (w.kcal > block.correctedKcal) {
        Note(
            "This beat the heart-rate estimate, so it's what the block was counted at — the usual " +
                "cause is the wrist sensor losing the signal on grip-heavy work.",
        )
    }
}

@Composable
private fun TotalStep(audit: CalorieAudit) {
    Step(6, "Total") {
        AuditRow("Working blocks", "${audit.workBlocks.sumOf { it.kcal }.kcal()} kcal")
        AuditRow("Rest blocks", "${audit.restBlocks.sumOf { it.kcal }.kcal()} kcal")
        if (audit.unsegmentedKcal > 0) {
            AuditRow("Untagged time", "${audit.unsegmentedKcal.kcal()} kcal")
        }
        AuditRow(
            "Session total",
            "${audit.blockKcalTotal.kcal()} kcal",
            strong = true,
            accent = Flame,
        )
        AuditRow("Reps logged", "${audit.summary.totalReps}")
    }
}

@Composable
private fun SourcesNote(audit: CalorieAudit) {
    // Read off a real block rather than restated here, so the note can't outlive the constant.
    val efficiency = audit.blocks.firstNotNullOfOrNull { it.mechanical }?.efficiency

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Text(
            "Keytel et al. (2005), heart rate → energy expenditure. Mifflin–St Jeor (1990), resting " +
                "metabolic rate." + (
                efficiency?.let {
                    " Rep work is force × distance ÷ muscular efficiency at ${(it * 100).n(0)}%, " +
                        "the established gross figure for concentric work."
                } ?: ""
                ),
            style = MaterialTheme.typography.labelSmall,
            color = Ash,
        )
        Text(
            "No estimate here is generated by AI. The AI coach below reads these numbers; it never " +
                "produces them.",
            style = MaterialTheme.typography.labelSmall,
            color = Ash,
        )
    }
}

// ---- Small building blocks ---------------------------------------------------------------------

@Composable
private fun Step(number: Int, title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Violet,
            )
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Chalk,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
private fun SubHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = Chalk,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun AuditRow(
    label: String,
    value: String,
    strong: Boolean = false,
    accent: Color? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Ash,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
            color = accent ?: if (strong) Chalk else Ash,
        )
    }
}

/** The formula itself, set apart so it reads as the thing being evaluated rather than prose. */
@Composable
private fun Formula(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Sky,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
            .padding(8.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = Ash.copy(alpha = 0.85f))
}

// ---- Formatting --------------------------------------------------------------------------------

/** Fixed-point, always with a dot — this screen is arithmetic and has to be unambiguous. */
private fun Double.n(digits: Int): String = String.format(Locale.US, "%.${digits}f", this)

private fun Double.kcal(): String = if (this >= 100) n(0) else n(1)

private fun CalorieAudit.Basis.label(): String = when (this) {
    CalorieAudit.Basis.HEART_RATE -> "heart rate"
    CalorieAudit.Basis.REP_WORK -> "rep work"
    CalorieAudit.Basis.RESTING_FLOOR -> "resting floor"
    CalorieAudit.Basis.REST -> "rest"
}

private fun CalorieAudit.Basis.accent(): Color = when (this) {
    CalorieAudit.Basis.HEART_RATE -> Flame
    CalorieAudit.Basis.REP_WORK -> Amber
    CalorieAudit.Basis.RESTING_FLOOR -> Ash
    CalorieAudit.Basis.REST -> Sky
}

private fun CalorieAudit.Block.title(): String {
    val name = exerciseName
    return when {
        name != null && setIndex != null -> "Set $setIndex · $name"
        name != null -> name
        type == SegmentType.REST -> "Rest"
        else -> "Work block $ordinal"
    }
}

private fun CalorieAudit.Block.subtitle(): String {
    val parts = mutableListOf(formatClock(coveredDurationMs), "${heartRate.avgBpm} bpm")
    if (reps > 0) parts += "$reps reps"
    if (coveredDurationMs < wallDurationMs) {
        parts += "${formatCompact(wallDurationMs - coveredDurationMs)} unsensed"
    }
    return parts.joinToString(" · ")
}
