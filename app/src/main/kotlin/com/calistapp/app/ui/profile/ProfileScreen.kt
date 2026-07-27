package com.calistapp.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.SectionCard
import com.calistapp.core.model.Sex
import com.calistapp.core.model.UserProfile

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val onboarded by viewModel.isOnboarded.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf(Sex.MALE) }
    var age by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var restingHr by remember { mutableStateOf("") }
    var maxHr by remember { mutableStateOf("") }
    var vo2 by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    // Populate fields once the stored profile loads.
    LaunchedEffect(profile) {
        name = profile.name
        sex = profile.sex
        age = profile.ageYears.toString()
        weight = trimNum(profile.weightKg)
        height = trimNum(profile.heightCm)
        restingHr = profile.restingHr.toString()
        maxHr = profile.maxHr?.toString() ?: ""
        vo2 = profile.vo2Max?.let { trimNum(it) } ?: ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Your profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "These numbers personalize every calorie estimate. VO₂max is optional but noticeably improves accuracy.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!onboarded) {
            SectionCard {
                Text(
                    "Welcome! Fill this in once to unlock accurate tracking.",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        SectionCard(title = "About you") {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name (optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Sex", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sex.entries.forEach { option ->
                    FilterChip(
                        selected = sex == option,
                        onClick = { sex = option },
                        label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            NumberField("Age (years)", age) { age = it }
        }

        SectionCard(title = "Body") {
            NumberField("Weight (kg)", weight, decimal = true) { weight = it }
            NumberField("Height (cm)", height, decimal = true) { height = it }
        }

        SectionCard(title = "Heart & fitness") {
            NumberField("Resting HR (bpm)", restingHr) { restingHr = it }
            NumberField("Max HR (bpm, optional)", maxHr) { maxHr = it }
            NumberField("VO₂max (ml/kg/min, optional)", vo2, decimal = true) { vo2 = it }
        }

        Button(
            onClick = {
                viewModel.save(
                    UserProfile(
                        name = name.trim(),
                        sex = sex,
                        ageYears = age.toIntOrNull() ?: 30,
                        weightKg = weight.toDoubleOrNull() ?: 75.0,
                        heightCm = height.toDoubleOrNull() ?: 178.0,
                        restingHr = restingHr.toIntOrNull() ?: 60,
                        maxHr = maxHr.toIntOrNull(),
                        vo2Max = vo2.toDoubleOrNull(),
                    ),
                )
                saved = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (saved) "Saved ✓" else "Save profile")
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    decimal: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() || (decimal && it == '.') }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun trimNum(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
