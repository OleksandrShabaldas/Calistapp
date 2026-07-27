package com.calistapp.app.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.PillChip
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Cream
import com.calistapp.app.ui.theme.CreamMuted
import com.calistapp.app.ui.theme.Emerald
import com.calistapp.app.ui.theme.InkElevated
import com.calistapp.app.ui.theme.Sky
import com.calistapp.app.ui.theme.Violet
import com.calistapp.core.model.BodyPart
import com.calistapp.core.model.Difficulty
import com.calistapp.core.model.Exercise

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ExerciseGalleryScreen(
    onOpenExercise: (String) -> Unit,
    onAddExercise: () -> Unit,
    viewModel: ExercisesViewModel = hiltViewModel(),
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val total by viewModel.totalCount.collectAsStateWithLifecycle()
    val enrichment by viewModel.enrichmentProgress.collectAsStateWithLifecycle()
    val facets by viewModel.facets.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }

    if (showFilters) {
        ExerciseFilterSheet(
            filters = filters,
            facets = facets,
            actions = viewModel,
            onDismiss = { showFilters = false },
        )
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Exercises", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            OutlinedTextField(
                value = filters.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search name, muscle, tag…") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        // Sort + filter controls. Every filter lives in the sheet — duplicating the body-part
        // options inline just put a wall of chips between the search box and the results.
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SortMenu(current = filters.sort, onSelect = viewModel::setSort)
                PillChip(
                    label = if (filters.activeCount > 0) "Filters · ${filters.activeCount}" else "Filters",
                    selected = filters.activeCount > 0,
                    accent = Violet,
                    onClick = { showFilters = true },
                )
                if (filters.activeCount > 0) {
                    PillChip(label = "Clear", accent = Coral, onClick = viewModel::clearFilters)
                }
            }
        }
        item {
            Text(
                "${exercises.size} of $total exercises",
                style = MaterialTheme.typography.labelMedium,
                color = CreamMuted,
            )
        }
        item {
            EnrichAllCard(
                progress = enrichment,
                onStart = viewModel::startEnrichAll,
                onStop = viewModel::stopEnrichAll,
            )
        }
        items(exercises, key = { it.id }) { exercise ->
            ExerciseCard(exercise = exercise, onClick = { onOpenExercise(exercise.id) })
        }
        if (exercises.isEmpty()) {
            item {
                Text(
                    if (filters.query.isNotBlank() || filters.activeCount > 0) {
                        "Nothing matches that. Try a shorter search, or loosen the filters."
                    } else {
                        "The gallery downloads on first launch — check your connection."
                    },
                    color = CreamMuted,
                )
            }
        }
    }
        ExtendedFloatingActionButton(
            onClick = onAddExercise,
            icon = { Icon(Icons.Filled.Add, contentDescription = "Add exercise") },
            text = { Text("Add") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                // Clear of the floating nav bar, which now occupies the bottom edge.
                .padding(end = 16.dp, bottom = 116.dp),
        )
    }
}

@Composable
private fun EnrichAllCard(
    progress: com.calistapp.app.data.exercise.ExerciseEnrichmentManager.Progress,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.layout.Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("AI coaching for the whole library", fontWeight = FontWeight.SemiBold)
            }
            if (progress.running) {
                Text(
                    "Enriching ${progress.done} / ${progress.total}…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { if (progress.total > 0) progress.done.toFloat() / progress.total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop") }
            } else {
                Text(
                    "Generate an overview, mistakes and tips for every exercise. Runs in the background and " +
                        "uses your Gemini quota; results are cached so it only runs once each.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text(if (progress.done > 0) "Continue enriching" else "Enrich all with AI")
                }
            }
            progress.lastError?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ExerciseCard(exercise: Exercise, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExerciseImage(
                urls = exercise.imageUrls,
                contentDescription = exercise.name,
                // Keyed by id so cards animate out of phase with each other.
                phaseKey = exercise.id,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${exercise.bodyPart.displayName} • ${exercise.difficulty.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (exercise.primaryMuscles.isNotEmpty()) {
                    Text(
                        exercise.primaryMuscles.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (exercise.efficiency > 0) {
                EfficiencyBadge(exercise.efficiency)
            }
        }
    }
}

@Composable
private fun EfficiencyBadge(efficiency: Int) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "★ $efficiency",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}
