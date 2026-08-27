package com.calistapp.app.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.PillChip
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.AshFaint
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.OnyxRaised
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
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val total by viewModel.totalCount.collectAsStateWithLifecycle()
    val facets by viewModel.facets.collectAsStateWithLifecycle()
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // A new search should land on its best matches at the top; the shared scroll position used to
    // leave you halfway down results you never saw the start of.
    LaunchedEffect(filters.query) { listState.scrollToItem(0) }

    // Recent belongs to the default browse view — it shouldn't compete with a search or a filter.
    val showRecent = recent.isNotEmpty() && filters.query.isBlank() && filters.activeCount == 0

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
        state = listState,
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
                color = Ash,
            )
        }
        if (showRecent) {
            item {
                Text(
                    "Recent",
                    style = MaterialTheme.typography.titleSmall,
                    color = Chalk,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recent, key = { it.id }) { ex ->
                        RecentChip(exercise = ex, onClick = { onOpenExercise(ex.id) })
                    }
                }
            }
            item {
                Text(
                    "All exercises",
                    style = MaterialTheme.typography.titleSmall,
                    color = Chalk,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        items(exercises, key = { it.id }) { exercise ->
            ExerciseCard(
                exercise = exercise,
                isFavourite = exercise.id in favourites,
                onToggleFavourite = { viewModel.toggleFavourite(exercise.id) },
                onClick = { onOpenExercise(exercise.id) },
            )
        }
        if (exercises.isEmpty()) {
            item {
                Text(
                    if (filters.query.isNotBlank() || filters.activeCount > 0) {
                        "Nothing matches that. Try a shorter search, or loosen the filters."
                    } else {
                        "The gallery downloads on first launch — check your connection."
                    },
                    color = Ash,
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
                // Sit just above the floating nav bar, tracking the system inset so the gap stays the
                // same on gesture and three-button navigation rather than floating too high on either.
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 84.dp),
        )
    }
}

@Composable
private fun ExerciseCard(
    exercise: Exercise,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    onClick: () -> Unit,
) {
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
            // Eight hundred exercises, and you use fifteen. Starring pins those to the top of both
            // this list and the workout picker.
            IconButton(onClick = onToggleFavourite) {
                Icon(
                    if (isFavourite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isFavourite) {
                        "Remove ${exercise.name} from favourites"
                    } else {
                        "Add ${exercise.name} to favourites"
                    },
                    tint = if (isFavourite) Amber else AshFaint,
                )
            }
        }
    }
}

/** A compact recent-exercise tile for the horizontal Recent strip. */
@Composable
private fun RecentChip(exercise: Exercise, onClick: () -> Unit) {
    Column(
        Modifier.width(84.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ExerciseImage(
            urls = exercise.imageUrls,
            contentDescription = exercise.name,
            animate = false,
            phaseKey = exercise.id,
            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)),
        )
        Text(
            exercise.name,
            style = MaterialTheme.typography.labelSmall,
            color = Chalk,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
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
