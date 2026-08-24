package com.calistapp.app.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.common.PillChip
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Cream
import com.calistapp.app.ui.theme.CreamMuted
import com.calistapp.app.ui.theme.Emerald
import com.calistapp.app.ui.theme.InkElevated
import com.calistapp.app.ui.theme.Sky
import com.calistapp.core.model.BodyPart
import com.calistapp.core.model.Difficulty

/**
 * The callbacks a filter surface needs. An interface rather than a pile of lambdas because both the
 * gallery and the planner implement the identical set, and their ViewModels can satisfy it directly.
 */
interface ExerciseFilterActions {
    fun setSort(sort: ExerciseSort)
    fun toggleBodyPart(bodyPart: BodyPart)
    fun toggleDifficulty(difficulty: Difficulty)
    fun togglePrimaryMuscle(muscle: String)
    fun toggleSecondaryMuscle(muscle: String)
    fun toggleEquipment(item: String)
    fun toggleAvoidArea(area: String)
    fun toggleCalisthenics()
    fun clearFilters()
}

/** Sort picker. Anchored to a chip so it reads as part of the filter row rather than a toolbar. */
@Composable
fun SortMenu(current: ExerciseSort, onSelect: (ExerciseSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PillChip(
            label = current.label,
            selected = true,
            leading = {
                Icon(Icons.Filled.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ExerciseSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label) },
                    onClick = { onSelect(sort); open = false },
                    trailingIcon = {
                        if (sort == current) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Emerald)
                        }
                    },
                )
            }
        }
    }
}

/**
 * The full filter surface. Everything here is multi-select, and every option is drawn from what's
 * actually in the loaded gallery, so no chip can lead to an empty result set on its own.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseFilterSheet(
    filters: ExerciseFilters,
    facets: FilterFacets,
    actions: ExerciseFilterActions,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = InkElevated,
        dragHandle = { BottomSheetDefaults.DragHandle(color = CreamMuted) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Filters",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Cream,
                    modifier = Modifier.weight(1f),
                )
                if (filters.activeCount > 0) {
                    TextButton(onClick = actions::clearFilters) { Text("Clear all", color = Coral) }
                }
            }

            FilterGroup("Body part") {
                BodyPart.entries.forEach { bp ->
                    PillChip(
                        label = bp.displayName,
                        selected = bp in filters.bodyParts,
                        onClick = { actions.toggleBodyPart(bp) },
                    )
                }
            }

            FilterGroup("Difficulty") {
                Difficulty.entries.forEach { d ->
                    PillChip(
                        label = d.displayName,
                        selected = d in filters.difficulties,
                        onClick = { actions.toggleDifficulty(d) },
                    )
                }
            }

            if (facets.primaryMuscles.isNotEmpty()) {
                FilterGroup("Target muscle", subtitle = "Exercises that train this as a primary mover") {
                    facets.primaryMuscles.forEach { m ->
                        PillChip(
                            label = m,
                            selected = m in filters.primaryMuscles,
                            accent = Emerald,
                            onClick = { actions.togglePrimaryMuscle(m) },
                        )
                    }
                }
            }

            if (facets.secondaryMuscles.isNotEmpty()) {
                FilterGroup("Secondary muscle", subtitle = "Worked, but not the main target") {
                    facets.secondaryMuscles.forEach { m ->
                        PillChip(
                            label = m,
                            selected = m in filters.secondaryMuscles,
                            accent = Sky,
                            onClick = { actions.toggleSecondaryMuscle(m) },
                        )
                    }
                }
            }

            if (facets.equipment.isNotEmpty()) {
                FilterGroup("Equipment") {
                    facets.equipment.forEach { item ->
                        PillChip(
                            label = item,
                            selected = item in filters.equipment,
                            accent = Amber,
                            onClick = { actions.toggleEquipment(item) },
                        )
                    }
                }
            }

            if (facets.problemAreas.isNotEmpty()) {
                // Deliberately an exclusion: you filter on a bad shoulder to make those exercises
                // go away, not to seek them out.
                FilterGroup("Avoid stressing", subtitle = "Hides exercises that load these areas") {
                    facets.problemAreas.forEach { area ->
                        PillChip(
                            label = area,
                            selected = area in filters.avoidAreas,
                            accent = Coral,
                            onClick = { actions.toggleAvoidArea(area) },
                        )
                    }
                }
            }

            FilterGroup("Equipment-free") {
                PillChip(
                    label = "Bodyweight only",
                    selected = filters.calisthenicsOnly,
                    onClick = actions::toggleCalisthenics,
                )
            }

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = Capsule) {
                Text("Show results", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(
    title: String,
    subtitle: String? = null,
    content: @Composable FlowRowScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Cream)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}
