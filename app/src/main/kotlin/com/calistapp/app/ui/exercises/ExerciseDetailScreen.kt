package com.calistapp.app.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.calistapp.app.ui.common.SectionCard
import com.calistapp.app.ui.common.StatTile
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Emerald
import com.calistapp.core.model.Exercise

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onStartWorkout: (String) -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val exercise by viewModel.exercise.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(exercise?.name ?: "Exercise") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    exercise?.let { ex ->
                        IconButton(onClick = { onEdit(ex.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val e = exercise
        if (e == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ExerciseDemo(
                urls = e.imageUrls,
                contentDescription = e.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
                    .clip(RoundedCornerShape(20.dp)),
            )

            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(e.bodyPart.displayName)
                Chip(e.difficulty.displayName)
                e.force?.let { Chip(it.replaceFirstChar(Char::uppercase)) }
                e.mechanic?.let { Chip(it.replaceFirstChar(Char::uppercase)) }
            }

            Button(onClick = { onStartWorkout(e.id) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("  Start workout with this", fontWeight = FontWeight.Bold)
            }

            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (e.efficiency > 0) StatTile("Efficiency", "★ ${e.efficiency}/5", accent = Emerald)
                    StatTile("Equipment", e.equipment.firstOrNull() ?: "Body only", accent = Amber)
                }
            }

            if (e.overview.isNotBlank()) {
                SectionCard(title = "Overview") { Text(e.overview, style = MaterialTheme.typography.bodyMedium) }
            }

            SectionCard(title = "Target muscles") {
                if (e.primaryMuscles.isNotEmpty()) {
                    LabeledText("Primary", e.primaryMuscles.joinToString(", "), MaterialTheme.colorScheme.primary)
                }
                if (e.secondaryMuscles.isNotEmpty()) {
                    LabeledText("Secondary", e.secondaryMuscles.joinToString(", "), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (e.problematicAreas.isNotEmpty()) {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Coral)
                        Text("Goes easy on", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Can stress: ${e.problematicAreas.joinToString(", ")}. Stop if you feel joint pain.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (e.instructions.isNotEmpty()) {
                SectionCard(title = "How to perform") {
                    e.instructions.forEachIndexed { i, step ->
                        NumberedLine(i + 1, step)
                    }
                }
            }

            if (e.commonMistakes.isNotEmpty()) {
                SectionCard(title = "Common mistakes") {
                    e.commonMistakes.forEach { BulletLine(it, Coral) }
                }
            }

            if (e.tips.isNotEmpty()) {
                SectionCard(title = "Tips") {
                    e.tips.forEach { BulletLine(it, Emerald) }
                }
            }

            // Un-authored dataset exercises can be enriched on demand via AI.
            if (e.commonMistakes.isEmpty() && e.tips.isEmpty()) {
                AiCoachingCard(aiState = aiState, onGenerate = viewModel::enrich)
            }
        }
    }
}

@Composable
private fun AiCoachingCard(aiState: ExerciseAiState, onGenerate: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("AI coaching notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        when (aiState) {
            is ExerciseAiState.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp))
                Text("Generating coaching notes…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            is ExerciseAiState.Error -> Text(
                aiState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )

            ExerciseAiState.Idle -> Text(
                "Generate an overview, common mistakes, and tips for this exercise with AI.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (aiState !is ExerciseAiState.Loading) {
            Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                Text("Generate with AI")
            }
        }
    }
}

@Composable
private fun Chip(label: String) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(),
    )
}

@Composable
private fun LabeledText(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NumberedLine(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$n.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BulletLine(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("•", color = color, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
