package com.calistapp.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameSoft
import com.calistapp.app.ui.theme.NumericLarge
import com.calistapp.app.ui.theme.OnyxFillStrong
import com.calistapp.app.ui.theme.OnyxRaised

/**
 * The app's shared numeric keypad, styled like the reference training app: a dark sheet with a big
 * running value, optional unit tabs, an optional `MAX` key and a help blurb. Every number entry on
 * the live workout screen — reps, seconds, added weight, effort — goes through this one control, so
 * the interaction is identical wherever you type a figure.
 *
 * Presented as a modal bottom sheet. The caller owns the visibility flag and reads the committed
 * value from [onConfirm]; dismissing without confirming keeps the old value.
 *
 * @param tabs optional segmented labels shown above the pad (e.g. `REPS`/`TIME`, or `RIR`/`RPE`/`%RM`).
 * @param maxValue when non-null, a `MAX` key that commits this sentinel — used for an all-out set.
 * @param help optional explanation revealed by the `?` button (used for the effort scales).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberPadSheet(
    title: String,
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    tabs: List<String> = emptyList(),
    selectedTab: Int = 0,
    onSelectTab: (Int) -> Unit = {},
    maxValue: Int? = null,
    help: String? = null,
) {
    Sheet(onDismiss, modifier) {
        NumberPadBody(
            title = title,
            initialText = if (initial > 0) initial.toString() else "",
            unit = unit,
            tabs = tabs,
            selectedTab = selectedTab,
            onSelectTab = onSelectTab,
            maxValue = maxValue,
            decimal = false,
            help = help,
            onConfirm = { onConfirm(it.toInt()) },
        )
    }
}

/**
 * The same keypad, but for a fractional value — added weight, where 2.5 / 7.5 / 12.5 kg are the
 * everyday increments. It swaps the `MAX` corner for a decimal point and commits a [Double].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecimalPadSheet(
    title: String,
    initial: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = "kg",
) {
    Sheet(onDismiss, modifier) {
        NumberPadBody(
            title = title,
            initialText = if (initial > 0) trimNum(initial) else "",
            unit = unit,
            tabs = emptyList(),
            selectedTab = 0,
            onSelectTab = {},
            maxValue = null,
            decimal = true,
            help = null,
            onConfirm = onConfirm,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OnyxRaised,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
        dragHandle = null,
        modifier = modifier,
        content = { content() },
    )
}

@Composable
private fun NumberPadBody(
    title: String,
    initialText: String,
    unit: String?,
    tabs: List<String>,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    maxValue: Int?,
    decimal: Boolean,
    help: String?,
    onConfirm: (Double) -> Unit,
) {
    // The running entry, as a string so leading edits behave like a real keypad; "" reads as 0.
    var entry by remember(initialText) { mutableStateOf(initialText) }
    var showHelp by remember { mutableStateOf(false) }

    val maxLen = if (decimal) 6 else 4
    fun press(digit: Int) {
        if (entry.length >= maxLen) return
        entry = if (entry.contains('.')) {
            // Past the decimal point, keep the digit verbatim (no leading-zero trimming).
            entry + digit
        } else {
            (entry + digit).trimStart('0').ifEmpty { "0" }
        }
    }
    fun dot() {
        if (!decimal || entry.contains('.')) return
        entry = entry.ifEmpty { "0" } + "."
    }
    fun backspace() { entry = entry.dropLast(1) }
    val value = entry.toDoubleOrNull() ?: 0.0

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Ash,
                modifier = Modifier.weight(1f),
            )
            if (help != null) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (showHelp) FlameSoft else OnyxFillStrong)
                        .clickable { showHelp = !showHelp },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("?", style = MaterialTheme.typography.labelLarge, color = if (showHelp) Flame else Ash)
                }
            }
        }

        if (showHelp && help != null) {
            Text(
                help,
                style = MaterialTheme.typography.bodySmall,
                color = Ash,
                textAlign = TextAlign.Center,
            )
        }

        if (tabs.size > 1) {
            SegmentTabs(tabs = tabs, selected = selectedTab, onSelect = onSelectTab)
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entry.ifEmpty { "0" }, style = NumericLarge, color = Chalk)
            if (unit != null) {
                Text(
                    unit,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ash,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }

        // Rows 1-3.
        listOf(1, 2, 3, 4, 5, 6, 7, 8, 9).chunked(3).forEach { rowDigits ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowDigits.forEach { d ->
                    PadKey(Modifier.weight(1f), onClick = { press(d) }) {
                        Text("$d", style = MaterialTheme.typography.headlineMedium, color = Chalk)
                    }
                }
            }
        }
        // Last row: decimal point (or MAX, or blank) · 0 · backspace.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                decimal -> PadKey(Modifier.weight(1f), onClick = { dot() }) {
                    Text(".", style = MaterialTheme.typography.headlineMedium, color = Chalk)
                }
                maxValue != null -> PadKey(Modifier.weight(1f), onClick = { onConfirm(maxValue.toDouble()) }) {
                    Text("MAX", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ash)
                }
                else -> Box(Modifier.weight(1f))
            }
            PadKey(Modifier.weight(1f), onClick = { press(0) }) {
                Text("0", style = MaterialTheme.typography.headlineMedium, color = Chalk)
            }
            PadKey(Modifier.weight(1f), onClick = { backspace() }) {
                Icon(Icons.AutoMirrored.Filled.Backspace, "Delete", tint = Ash, modifier = Modifier.size(24.dp))
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Flame)
                .clickable { onConfirm(value) }
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Check, null, tint = com.calistapp.app.ui.theme.Onyx, modifier = Modifier.size(18.dp))
                Text("Done", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = com.calistapp.app.ui.theme.Onyx)
            }
        }
    }
}

/** Trim a trailing ".0" so 20.0 reads as "20" but 2.5 stays "2.5". */
private fun trimNum(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else ((v * 10).toLong() / 10.0).toString()

@Composable
private fun PadKey(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(OnyxFillStrong)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The dark segmented switch used inside the pad (REPS/TIME, RIR/RPE/%RM). */
@Composable
private fun SegmentTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(OnyxFillStrong)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) Flame else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (active) com.calistapp.app.ui.theme.Onyx else Ash,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
