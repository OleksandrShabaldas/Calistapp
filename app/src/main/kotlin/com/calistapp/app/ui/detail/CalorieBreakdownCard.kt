package com.calistapp.app.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calistapp.app.ui.common.formatClock
import com.calistapp.app.ui.common.formatCompact
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Display
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.app.ui.theme.Sky
import com.calistapp.core.calorie.CalorieAudit
import com.calistapp.core.calorie.ExerciseIntensity
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.Sex
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The calorie figure, shown working — as a popup opened by tapping the total-energy hero.
 *
 * A number you can't check is a number you have to trust, and there's no reason to trust a fitness
 * app's calorie count. So this answers three things at a glance — what the number is, what it's made
 * of, and which published methods produced it — and then keeps every input, formula and intermediate
 * value one tap away, block by block, so the total can still be reproduced with a calculator. The old
 * version showed all of that at once, in the engine's computation order, which was complete but a wall.
 */
@Composable
fun CalorieBreakdownOverlay(audit: CalorieAudit, storedKcal: Double?, onDismiss: () -> Unit) {
    // One layer open at a time — an accordion, so the screen never becomes the wall it used to be.
    var open by remember { mutableStateOf<Layer?>(null) }
    fun toggle(layer: Layer) { open = if (open == layer) null else layer }

    SummaryOverlay(onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("How this was calculated", style = MaterialTheme.typography.headlineSmall, color = Chalk)
            Text("Every number, shown working.", style = MaterialTheme.typography.bodySmall, color = Ash)
        }

        StaleProfileNotice(audit, storedKcal)
        GlanceCard(audit, onOpenMethod = { open = it })

        Text(
            "THE WORKING — TAP TO EXPAND",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
            color = Ash,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )

        LayerRow("Your body", bodySummary(audit), open == Layer.BODY, { toggle(Layer.BODY) }) { InputsBody(audit) }
        LayerRow("Resting metabolism", restingSummary(audit), open == Layer.RESTING, { toggle(Layer.RESTING) }) { RestingBody(audit) }
        LayerRow("Heart rate → energy", hrSummary(audit), open == Layer.HEART_RATE, { toggle(Layer.HEART_RATE) }) { HeartRateBody(audit) }
        LayerRow("How the time was counted", timeSummary(audit), open == Layer.TIME, { toggle(Layer.TIME) }) { TimeBody(audit) }
        LayerRow("Block by block", "${audit.blocks.size} blocks · tap any for its arithmetic", open == Layer.BLOCKS, { toggle(Layer.BLOCKS) }) { BlocksBody(audit) }
        LayerRow("Total", totalSummary(audit), open == Layer.TOTAL, { toggle(Layer.TOTAL) }) { TotalBody(audit) }

        SourcesNote(audit)
    }
}

private enum class Layer { BODY, RESTING, HEART_RATE, TIME, BLOCKS, TOTAL }

// ---- At a glance -------------------------------------------------------------------------------

/**
 * The answer without reading anything: the total, one plain sentence on how it was reached, a bar
 * showing what the energy is *made of* (heart rate vs the rep-work floor vs rest), and the two
 * published methods as chips that jump to their working.
 */
@Composable
private fun GlanceCard(audit: CalorieAudit, onOpenMethod: (Layer) -> Unit) {
    val hr = audit.blocks.filter { it.basis == CalorieAudit.Basis.HEART_RATE }.sumOf { it.kcal }
    val rep = audit.blocks.filter { it.basis == CalorieAudit.Basis.REP_WORK }.sumOf { it.kcal }
    val rest = audit.blocks.filter {
        it.basis == CalorieAudit.Basis.REST || it.basis == CalorieAudit.Basis.RESTING_FLOOR
    }.sumOf { it.kcal } + audit.unsegmentedKcal

    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF241811), Color(0xFF151316))))
            .border(1.dp, FlameHot.copy(alpha = 0.18f), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${audit.summary.totalKcal.toInt()}",
                style = TextStyle(
                    fontFamily = Display,
                    fontWeight = FontWeight.Bold,
                    fontSize = 46.sp,
                    lineHeight = 44.sp,
                    letterSpacing = (-1.4).sp,
                    brush = Brush.linearGradient(listOf(FlameGlow, FlameHot)),
                ),
            )
            Text("kcal", style = MaterialTheme.typography.titleMedium, color = Ash, modifier = Modifier.padding(bottom = 5.dp))
        }

        Text(glanceSentence(audit), style = MaterialTheme.typography.bodySmall, color = Chalk.copy(alpha = 0.75f))

        CompositionBar(hr, rep, rest, headline = audit.summary.totalKcal.toInt())

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MethodChip("Keytel · 2005", "heart rate → energy", Modifier.weight(1f)) { onOpenMethod(Layer.HEART_RATE) }
            MethodChip("Mifflin–St Jeor", "resting metabolism", Modifier.weight(1f)) { onOpenMethod(Layer.RESTING) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompositionBar(hr: Double, rep: Double, rest: Double, headline: Int) {
    val total = (hr + rep + rest).coerceAtLeast(0.0001)
    val segments = listOf(
        Triple(hr, "heart rate", Brush.horizontalGradient(listOf(FlameHot, FlameGlow))),
        Triple(rep, "rep work", Brush.linearGradient(listOf(Amber, Amber))),
        Triple(rest, "rest", Brush.linearGradient(listOf(Sky.copy(alpha = 0.75f), Sky.copy(alpha = 0.75f)))),
    ).filter { it.first > 0.05 }

    // Round each component, then park the rounding residual on the largest so the legend adds up to the
    // headline exactly — a "reproducible with a calculator" screen shouldn't show 48 + 19 = 68.
    val ints = segments.map { it.first.roundToInt() }.toMutableList()
    if (ints.isNotEmpty()) {
        val biggest = segments.indices.maxByOrNull { segments[it].first } ?: 0
        ints[biggest] = (ints[biggest] + (headline - ints.sum())).coerceAtLeast(0)
    }

    val legendColors = mapOf("heart rate" to FlameHot, "rep work" to Amber, "rest" to Sky.copy(alpha = 0.75f))

    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(
            Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.06f)),
        ) {
            segments.forEachIndexed { i, (value, _, brush) ->
                Box(Modifier.fillMaxHeight().weight((value / total).toFloat()).background(brush))
                if (i < segments.lastIndex) Box(Modifier.width(2.dp))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            segments.forEachIndexed { i, (_, label, _) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(legendColors[label] ?: FlameHot))
                    Text("${ints[i]} ", style = MaterialTheme.typography.labelLarge, color = Chalk, fontWeight = FontWeight.Bold)
                    Text(label, style = MaterialTheme.typography.labelLarge, color = Ash)
                }
            }
        }
    }
}

@Composable
private fun MethodChip(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = FlameGlow, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Ash)
    }
}

// ---- A collapsible layer -----------------------------------------------------------------------

@Composable
private fun LayerRow(
    title: String,
    summary: String,
    expanded: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "chev")
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (expanded) FlameHot else Chalk, fontWeight = FontWeight.SemiBold)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = Ash)
            }
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = if (expanded) FlameHot else Ash,
                modifier = Modifier.size(22.dp).rotate(rotation),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 12.dp)) { content() }
        }
    }
}

// ---- Layer summaries (the one-line headline for each collapsed row) -----------------------------

private fun bodySummary(audit: CalorieAudit): String {
    val p = audit.profile
    val sex = if (p.sex == Sex.MALE) "male" else "female"
    val est = if (p.maxHr == null) " (est.)" else ""
    return "${p.weightKg.n(0)} kg · ${p.heightCm.n(0)} cm · ${p.ageYears} y · $sex · max HR ${p.effectiveMaxHr}$est"
}

private fun restingSummary(audit: CalorieAudit): String {
    val perMin = audit.resting.kcalPerMin.n(2)
    return if (audit.settings.netOfResting) "$perMin kcal/min, removed from every slice" else "$perMin kcal/min"
}

private fun hrSummary(audit: CalorieAudit): String =
    "Keytel · scaled ×${audit.settings.hrCalibration.n(2)} for resistance work"

private fun timeSummary(audit: CalorieAudit): String {
    val s = audit.sampling
    val gaps = if (s.gapSliceCount > 0) "${s.gapSliceCount} sensor ${if (s.gapSliceCount == 1) "gap" else "gaps"}" else "no sensor gaps"
    return "${formatClock(s.lastSampleMs - s.firstSampleMs)} · ${s.sampleCount} readings · $gaps"
}

private fun totalSummary(audit: CalorieAudit): String =
    "working ${audit.workBlocks.sumOf { it.kcal }.kcal()} + rest ${audit.restBlocks.sumOf { it.kcal }.kcal()} = ${audit.blockKcalTotal.kcal()} kcal"

private fun glanceSentence(audit: CalorieAudit): String = buildString {
    append("Integrated from ${audit.sampling.sampleCount} real heart-rate readings across ${audit.blocks.size} work/rest blocks")
    if (audit.settings.netOfResting) append(", with resting metabolism removed")
    append(". Every figure below is reproducible with a calculator.")
}

// ---- Stale-profile banner ----------------------------------------------------------------------

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
        Text("Recomputed with your current profile", style = MaterialTheme.typography.labelLarge, color = Amber)
        Text(
            "This session was recorded at ${storedKcal.kcal()} kcal. The working below redoes the " +
                "same calculation with the body data in your profile today, which comes to " +
                "${audit.summary.totalKcal.kcal()} kcal. The stored figure is the one shown above.",
            style = MaterialTheme.typography.bodySmall,
            color = Ash,
        )
    }
}

// ---- Layer bodies (the full detail, unchanged — now behind a tap) ------------------------------

@Composable
private fun InputsBody(audit: CalorieAudit) {
    val p = audit.profile
    Note("Both formulas below are functions of your body. Nothing else about you enters the calculation.")
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

@Composable
private fun RestingBody(audit: CalorieAudit) {
    val r = audit.resting
    Note(
        "What your body burns doing nothing. It gets subtracted from every slice, so the total is " +
            "energy the workout actually cost — not energy you'd have spent on the sofa.",
    )
    Formula("kcal/day = 10×weight + 6.25×height − 5×age " + if (r.sexOffset > 0) "+ 5" else "− 161")
    AuditRow("10 × ${audit.profile.weightKg.n(1)} kg", r.weightTerm.n(1))
    AuditRow("6.25 × ${audit.profile.heightCm.n(0)} cm", r.heightTerm.n(1))
    AuditRow("− 5 × ${audit.profile.ageYears}", r.ageTerm.n(1))
    AuditRow(if (r.sexOffset > 0) "male +5" else "female −161", r.sexOffset.n(1))
    AuditRow("Resting", "${r.kcalPerDay.n(0)} kcal/day  ·  ${r.kcalPerMin.n(3)} kcal/min", strong = true)
}

@Composable
private fun HeartRateBody(audit: CalorieAudit) {
    Note(
        "Evaluated at each heart-rate reading and integrated over the real curve — not applied once " +
            "to a session average, which is what makes most trackers' numbers soft.",
    )
    AuditRow("Regression", audit.keytel.label)
    Formula(audit.keytel.equation)
    AuditRow("Field calibration", "× ${audit.settings.hrCalibration.n(2)}")
    Note(
        "Keytel was fitted on steady cycling and treadmill work. Resistance training breaks its " +
            "assumptions in ways that all push the same way — heart rate stays high between sets, " +
            "gripping and bracing raise it without matching oxygen uptake, and wrist sensors read " +
            "high under motion — so the heart-rate term is scaled down " +
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

@Composable
private fun TimeBody(audit: CalorieAudit) {
    val s = audit.sampling
    AuditRow("Heart-rate readings", "${s.sampleCount}")
    AuditRow("Spanning", formatClock(s.lastSampleMs - s.firstSampleMs))
    AuditRow("Integration slices", "${s.sliceCount}")
    Note(
        "One slice per reading, plus one at every work/rest boundary — so no slice is ever split " +
            "across working and resting.",
    )
    if (s.gapSliceCount > 0) {
        AuditRow("Sensor gaps", "${s.gapSliceCount}, ${formatCompact(s.uncreditedGapMs)} not credited", accent = Coral)
        Note(
            "A gap longer than ${formatCompact(audit.settings.maxIntervalMs)} is only credited up to " +
                "that cap. Guessing across a dropout is how estimates run away.",
        )
    } else {
        AuditRow("Sensor gaps", "none")
    }
    AuditRow("Working", formatCompact(audit.summary.activeDurationMs))
    AuditRow("Resting", formatCompact(audit.summary.restDurationMs))
}

@Composable
private fun BlocksBody(audit: CalorieAudit) {
    Note(
        "Each stretch of work is scored three ways — heart rate (corrected), the physical work of " +
            "the reps, and bare resting metabolism — and counted at whichever is largest. Tap a " +
            "block for its arithmetic.",
    )
    // Keyed so each block's open/closed state belongs to that block and survives a rebuild — without
    // it every row in the loop shares one saveable slot.
    audit.blocks.forEach { block ->
        key(block.ordinal) { BlockRow(block, audit.settings) }
    }
    if (audit.unsegmentedMs > 0) {
        AuditRow("Time outside any block", "${formatCompact(audit.unsegmentedMs)} · ${audit.unsegmentedKcal.kcal()} kcal")
        Note("Heart rate recorded before the first work/rest toggle, or after the last one.")
    }
}

@Composable
private fun TotalBody(audit: CalorieAudit) {
    AuditRow("Working blocks", "${audit.workBlocks.sumOf { it.kcal }.kcal()} kcal")
    AuditRow("Rest blocks", "${audit.restBlocks.sumOf { it.kcal }.kcal()} kcal")
    if (audit.unsegmentedKcal > 0) {
        AuditRow("Untagged time", "${audit.unsegmentedKcal.kcal()} kcal")
    }
    AuditRow("Session total", "${audit.blockKcalTotal.kcal()} kcal", strong = true, accent = Flame)
    AuditRow("Reps logged", "${audit.summary.totalReps}")
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
                Text(block.title(), style = MaterialTheme.typography.labelLarge, color = Chalk)
                Text(block.subtitle(), style = MaterialTheme.typography.labelSmall, color = Ash)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${block.kcal.kcal()} kcal", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = accent)
                Text(block.basis.label(), style = MaterialTheme.typography.labelSmall, color = Ash)
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
                    AuditRow("Counted at the largest", "${block.kcal.kcal()} kcal · ${block.basis.label()}", strong = true, accent = accent)
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
            "Capped at ${settings.restCeilingMultiplier.n(0)}× resting (${hr.ceilingSlices}/${hr.sliceCount} slices)",
            "${ceiling.n(2)} kcal/min gross",
            accent = Amber,
        )
    }
    if (hr.floorSlices > 0) {
        AuditRow(
            "Raised to resting (${hr.floorSlices}/${hr.sliceCount} slices)",
            "${hr.restingKcalPerMin.n(2)} kcal/min gross",
            accent = Amber,
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
            "${hr.sliceCount} slices, each at its own instantaneous heart rate. The rate above is the " +
                "integral divided by the time, so it differs a little from the value at the average " +
                "bpm whenever heart rate moved during the block.",
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
        "Muscle recruited  ${(c.muscleMassFraction * 100).n(0)}% vs ${(c.referenceMuscleMassFraction * 100).n(0)}% reference",
        "× ${c.recruitment.n(3)}",
    )
    AuditRow("Cardiac lag over ${c.blockSeconds.n(0)} s", "× ${c.hrLag.n(3)}")
    AuditRow(if (c.isometric) "Static hold reads low on HR" else "Not a static hold", "× ${c.isometricUplift.n(3)}")
    if (c.clamped) {
        AuditRow("Product ${c.product.n(3)} clamped to", c.factor.n(3), accent = Amber)
    }
    AuditRow("${block.heartRate.kcal.kcal()} × ${c.factor.n(3)}", "${block.correctedKcal.kcal()} kcal", strong = true)
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
private fun SourcesNote(audit: CalorieAudit) {
    // Read off a real block rather than restated here, so the note can't outlive the constant.
    val efficiency = audit.blocks.firstNotNullOfOrNull { it.mechanical }?.efficiency

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Text(
            "Keytel et al. (2005), heart rate → energy expenditure. Mifflin–St Jeor (1990), resting " +
                "metabolic rate." + (
                efficiency?.let {
                    " Rep work is force × distance ÷ muscular efficiency at ${(it * 100).n(0)}%, the " +
                        "established gross figure for concentric work."
                } ?: ""
                ),
            style = MaterialTheme.typography.labelSmall,
            color = Ash,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "No estimate here is generated by AI. The AI coach reads these numbers; it never produces them.",
            style = MaterialTheme.typography.labelSmall,
            color = Ash,
        )
    }
}

// ---- Small building blocks ---------------------------------------------------------------------

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
private fun AuditRow(label: String, value: String, strong: Boolean = false, accent: Color? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Ash, modifier = Modifier.weight(1f))
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
        color = FlameGlow,
        modifier = Modifier
            .fillMaxWidth()
            .background(FlameGlow.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
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
    // A muted, receding orange rather than a hue swap — "this block was rest" reads as a quieter
    // version of the same warm palette, not a foreign colour dropped into an otherwise orange list.
    CalorieAudit.Basis.REST -> Flame.copy(alpha = 0.5f)
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
