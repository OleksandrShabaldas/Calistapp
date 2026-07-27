package com.calistapp.app.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.SectionCard
import com.calistapp.core.model.BodyPart
import com.calistapp.core.model.Difficulty
import com.calistapp.core.model.Exercise

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: ExerciseEditViewModel = hiltViewModel(),
) {
    val initial by viewModel.initial.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()

    // The loaded exercise (or blank template) whose non-editable fields we preserve on save.
    var base by remember { mutableStateOf(ExerciseEditViewModel.BLANK) }

    var name by remember { mutableStateOf("") }
    var bodyPart by remember { mutableStateOf(BodyPart.OTHER) }
    var difficulty by remember { mutableStateOf(Difficulty.BEGINNER) }
    var equipment by remember { mutableStateOf("") }
    var primary by remember { mutableStateOf("") }
    var secondary by remember { mutableStateOf("") }
    var force by remember { mutableStateOf<String?>(null) }
    var mechanic by remember { mutableStateOf<String?>(null) }
    var efficiency by remember { mutableStateOf(0) }
    var problematic by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var overview by remember { mutableStateOf("") }
    var mistakes by remember { mutableStateOf("") }
    var tips by remember { mutableStateOf("") }
    var populated by remember { mutableStateOf(false) }

    // Populate the form once the exercise (or blank template) is available.
    LaunchedEffect(initial) {
        val e = initial ?: return@LaunchedEffect
        if (populated) return@LaunchedEffect
        populated = true
        base = e
        name = e.name
        bodyPart = e.bodyPart
        difficulty = e.difficulty
        equipment = e.equipment.joinToString(", ")
        primary = e.primaryMuscles.joinToString(", ")
        secondary = e.secondaryMuscles.joinToString(", ")
        force = e.force
        mechanic = e.mechanic
        efficiency = e.efficiency
        problematic = e.problematicAreas.joinToString(", ")
        imageUrl = e.imageUrls.joinToString("\n")
        instructions = e.instructions.joinToString("\n")
        overview = e.overview
        mistakes = e.commonMistakes.joinToString("\n")
        tips = e.tips.joinToString("\n")
    }

    fun draft(): Exercise = base.copy(
        name = name.trim(),
        bodyPart = bodyPart,
        difficulty = difficulty,
        equipment = commaList(equipment),
        primaryMuscles = commaList(primary),
        secondaryMuscles = commaList(secondary),
        force = force,
        mechanic = mechanic,
        efficiency = efficiency,
        problematicAreas = commaList(problematic),
        // One URL per line: a single GIF, or several frames that get animated in order.
        imageUrls = lineList(imageUrl),
        instructions = lineList(instructions),
        overview = overview.trim(),
        commonMistakes = lineList(mistakes),
        tips = lineList(tips),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "Add exercise" else "Edit exercise") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(draft(), onSaved) },
                        enabled = name.isNotBlank(),
                    ) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            LabeledChips("Body part") {
                BodyPart.entries.forEach { bp ->
                    FilterChip(selected = bodyPart == bp, onClick = { bodyPart = bp }, label = { Text(bp.displayName) })
                }
            }
            LabeledChips("Difficulty") {
                Difficulty.entries.forEach { d ->
                    FilterChip(selected = difficulty == d, onClick = { difficulty = d }, label = { Text(d.displayName) })
                }
            }

            OutlinedTextField(equipment, { equipment = it }, label = { Text("Equipment (comma-separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(primary, { primary = it }, label = { Text("Primary muscles (comma-separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(secondary, { secondary = it }, label = { Text("Secondary muscles (comma-separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            // AI generate — fills the coaching fields from the details above.
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Generate details with AI", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "Fill in the name and muscles above, then let AI draft the overview, instructions, mistakes, tips, problem areas and efficiency. You can edit everything after.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (val s = aiState) {
                    is EditAiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Text("Generating…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is EditAiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    EditAiState.Idle -> Unit
                }
                if (aiState !is EditAiState.Loading) {
                    Button(
                        onClick = {
                            viewModel.generate(draft()) { s ->
                                if (s.overview.isNotBlank()) overview = s.overview
                                if (s.instructions.isNotEmpty()) instructions = s.instructions.joinToString("\n")
                                if (s.commonMistakes.isNotEmpty()) mistakes = s.commonMistakes.joinToString("\n")
                                if (s.tips.isNotEmpty()) tips = s.tips.joinToString("\n")
                                if (s.problematicAreas.isNotEmpty()) problematic = s.problematicAreas.joinToString(", ")
                                if (s.efficiency in 1..5) efficiency = s.efficiency
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Generate with AI") }
                }
            }

            LabeledChips("Force") {
                ForceOptions.forEach { opt ->
                    FilterChip(selected = force == opt.second, onClick = { force = opt.second }, label = { Text(opt.first) })
                }
            }
            LabeledChips("Mechanic") {
                MechanicOptions.forEach { opt ->
                    FilterChip(selected = mechanic == opt.second, onClick = { mechanic = opt.second }, label = { Text(opt.first) })
                }
            }
            LabeledChips("Efficiency (strength vs energy)") {
                (0..5).forEach { n ->
                    FilterChip(selected = efficiency == n, onClick = { efficiency = n }, label = { Text(if (n == 0) "—" else "$n") })
                }
            }

            OutlinedTextField(problematic, { problematic = it }, label = { Text("Problem areas (comma-separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                imageUrl,
                { imageUrl = it },
                label = { Text("Image URLs — one per line (optional)") },
                supportingText = {
                    Text("A single animated GIF, or two or more frames (start → finish) that get played in a loop.")
                },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(overview, { overview = it }, label = { Text("Overview") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(instructions, { instructions = it }, label = { Text("How to perform (one step per line)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(mistakes, { mistakes = it }, label = { Text("Common mistakes (one per line)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tips, { tips = it }, label = { Text("Tips (one per line)") }, minLines = 2, modifier = Modifier.fillMaxWidth())

            Button(
                onClick = { viewModel.save(draft(), onSaved) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (viewModel.isNew) "Add exercise" else "Save changes", fontWeight = FontWeight.Bold) }

            if (!viewModel.isNew && base.source.equals("Custom", true)) {
                OutlinedButton(onClick = { viewModel.delete(onBack) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                    Text("  Delete exercise", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabeledChips(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

private val ForceOptions = listOf("—" to null, "Push" to "push", "Pull" to "pull", "Static" to "static")
private val MechanicOptions = listOf("—" to null, "Compound" to "compound", "Isolation" to "isolation")

private fun commaList(s: String): List<String> = s.split(",").map(String::trim).filter(String::isNotBlank)
private fun lineList(s: String): List<String> = s.split("\n").map(String::trim).filter(String::isNotBlank)
