package com.calistapp.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.SectionCard
import com.calistapp.app.ui.update.UpdateCard
import com.calistapp.core.calorie.Vo2MaxEstimate
import com.calistapp.core.model.ProfileField
import com.calistapp.core.model.Sex
import com.calistapp.core.model.TrainingGoals
import com.calistapp.core.model.UserProfile

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val onboarded by viewModel.isOnboarded.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val hidden by viewModel.hiddenExercises.collectAsStateWithLifecycle()

    var name by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf(Sex.MALE) }
    var age by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var restingHr by rememberSaveable { mutableStateOf("") }
    var maxHr by rememberSaveable { mutableStateOf("") }
    var vo2 by rememberSaveable { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var hydrated by rememberSaveable { mutableStateOf(false) }

    // Seeded from the stored profile exactly once, and only once it has actually been read. Keyed on
    // every emission it would re-run whenever the profile flow ticked — including the watch syncing
    // it back — and overwrite whatever was being typed at that moment with the value already on disk.
    LaunchedEffect(profile) {
        val stored = profile ?: return@LaunchedEffect
        if (hydrated) return@LaunchedEffect
        hydrated = true
        name = stored.name
        sex = stored.sex
        age = stored.ageYears.toString()
        weight = trimNum(stored.weightKg)
        height = trimNum(stored.heightCm)
        restingHr = stored.restingHr.toString()
        maxHr = stored.maxHr?.toString() ?: ""
        vo2 = stored.vo2Max?.let { trimNum(it) } ?: ""
    }

    // What the form currently describes. Parsed once here so validation and saving can never
    // disagree about what the fields mean — the old code parsed at save time and silently
    // substituted a default for anything it couldn't read.
    val edited = UserProfile(
        name = name.trim(),
        sex = sex,
        ageYears = age.toIntOrNull() ?: -1,
        weightKg = weight.toDoubleOrNull() ?: -1.0,
        heightCm = height.toDoubleOrNull() ?: -1.0,
        restingHr = restingHr.toIntOrNull() ?: -1,
        // Blank optional fields mean "not set"; unparseable ones become a sentinel so validation
        // rejects them rather than treating them as absent.
        maxHr = if (maxHr.isBlank()) null else maxHr.toIntOrNull() ?: -1,
        vo2Max = if (vo2.isBlank()) null else vo2.toDoubleOrNull() ?: -1.0,
    )
    val invalid = edited.invalidFields()
    fun touched() { saved = false }

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
                value = name, onValueChange = { name = it; touched() },
                label = { Text("Name (optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Sex", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sex.entries.forEach { option ->
                    FilterChip(
                        selected = sex == option,
                        onClick = { sex = option; touched() },
                        label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            NumberField(
                label = "Age (years)",
                value = age,
                error = errorFor(ProfileField.AGE, invalid, age, "5–120"),
            ) { age = it; touched() }
        }

        SectionCard(title = "Body") {
            NumberField(
                label = "Weight (kg)",
                value = weight,
                decimal = true,
                error = errorFor(ProfileField.WEIGHT, invalid, weight, "20–400 kg"),
            ) { weight = it; touched() }
            NumberField(
                label = "Height (cm)",
                value = height,
                decimal = true,
                error = errorFor(ProfileField.HEIGHT, invalid, height, "80–260 cm"),
            ) { height = it; touched() }
        }

        SectionCard(title = "Heart & fitness") {
            NumberField(
                label = "Resting HR (bpm)",
                value = restingHr,
                error = errorFor(ProfileField.RESTING_HR, invalid, restingHr, "25–120 bpm"),
            ) { restingHr = it; touched() }
            NumberField(
                label = "Max HR (bpm, optional)",
                value = maxHr,
                error = errorFor(ProfileField.MAX_HR, invalid, maxHr, "100–230 bpm, above resting"),
            ) { maxHr = it; touched() }
            NumberField(
                label = "VO₂max (ml/kg/min, optional)",
                value = vo2,
                decimal = true,
                error = errorFor(ProfileField.VO2_MAX, invalid, vo2, "10–95"),
            ) { vo2 = it; touched() }

            Vo2MaxSuggestion(
                from = edited,
                alreadySet = vo2.isNotBlank(),
                onUse = { vo2 = trimNum(it); touched() },
            )
        }

        GoalsSection(
            goals = goals,
            onSave = viewModel::saveGoals,
        )

        Button(
            onClick = { viewModel.save(edited); saved = true },
            // Nothing is guessed on your behalf: an unreadable or out-of-range value used to become
            // a silent default, and every calorie estimate afterwards was quietly wrong.
            enabled = invalid.isEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (saved) "Saved ✓" else "Save profile")
        }
        if (invalid.isNotEmpty()) {
            Text(
                "Fix the highlighted fields to save. These feed the calorie formulas directly, so a " +
                    "wrong one is worse than a blank one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (hidden.isNotEmpty()) {
            HiddenExercisesSection(hidden = hidden, onRestore = viewModel::restore)
        }

        UpdateCard()

        // Clear of the floating nav bar.
        Spacer(Modifier.height(96.dp))
    }
}

/** The restore list for exercises hidden from the library — each row brings one back. */
@Composable
private fun HiddenExercisesSection(
    hidden: List<com.calistapp.core.model.Exercise>,
    onRestore: (String) -> Unit,
) {
    SectionCard(title = "Hidden exercises") {
        Text(
            "Hidden from the library. Restore one to see it again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        hidden.forEach { ex ->
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    ex.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                androidx.compose.material3.TextButton(onClick = { onRestore(ex.id) }) {
                    Text("Restore")
                }
            }
        }
    }
}

/**
 * Offers a VO₂max derived from the heart rates already on this screen.
 *
 * Worth surfacing because it isn't cosmetic: the calorie engine switches to Keytel's
 * fitness-adjusted regression the moment a VO₂max exists, so filling this in changes every estimate
 * the app produces afterwards. Offered rather than applied silently — it's an estimate with a stated
 * error, and a measured value should beat it.
 */
@Composable
private fun Vo2MaxSuggestion(from: UserProfile, alreadySet: Boolean, onUse: (Double) -> Unit) {
    val estimate = Vo2MaxEstimate.forProfile(from)
    val blocked = Vo2MaxEstimate.blockedReason(from)

    when {
        estimate != null && !alreadySet -> Column {
            Text(
                "Estimated ${trimNum(estimate)} ml/kg/min from your max and resting heart rates. " +
                    "Roughly ±10–15% against a lab measurement, and it improves every calorie " +
                    "figure the app produces.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { onUse(estimate) },
                contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
                Text("Use ${trimNum(estimate)}")
            }
        }

        estimate != null -> Text(
            "For reference, your heart rates imply about ${trimNum(estimate)} ml/kg/min.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        blocked != null && !alreadySet -> Text(
            blocked,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The weekly targets the dashboard ring is measured against.
 *
 * Saved on its own rather than with the body data: changing what you're aiming for shouldn't require
 * re-validating your height, and the two are edited on completely different cadences.
 */
@Composable
private fun GoalsSection(goals: TrainingGoals?, onSave: (TrainingGoals) -> Unit) {
    if (goals == null) return

    var kcal by rememberSaveable(goals) { mutableStateOf(goals.weeklyKcal.toString()) }
    var sessions by rememberSaveable(goals) { mutableStateOf(goals.weeklySessions.toString()) }

    val edited = TrainingGoals(
        weeklyKcal = kcal.toIntOrNull() ?: -1,
        weeklySessions = sessions.toIntOrNull() ?: -1,
    )
    val changed = edited != goals

    SectionCard(title = "Weekly goals") {
        Text(
            "What the ring on the home screen fills against. A calendar week, Monday to Sunday.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NumberField(
            label = "Calories per week",
            value = kcal,
            error = "Enter 100–20,000".takeIf { edited.weeklyKcal !in TrainingGoals.WEEKLY_KCAL_RANGE },
        ) { kcal = it }
        NumberField(
            label = "Sessions per week",
            value = sessions,
            error = "Enter 1–14".takeIf { edited.weeklySessions !in TrainingGoals.WEEKLY_SESSIONS_RANGE },
        ) { sessions = it }
        Button(
            onClick = { onSave(edited) },
            enabled = changed && edited.isValid,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (changed) "Save goals" else "Goals saved ✓")
        }
    }
}

/**
 * The message for a field, or null when it's fine. Blank optional fields aren't errors; blank
 * required ones say so plainly rather than showing a range the user hasn't typed anything into yet.
 */
private fun errorFor(
    field: ProfileField,
    invalid: Set<ProfileField>,
    raw: String,
    range: String,
): String? = when {
    field !in invalid -> null
    raw.isBlank() -> "Required — the calorie estimate can't be personalized without it"
    else -> "Enter a value in $range"
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    decimal: Boolean = false,
    error: String? = null,
    onChange: (String) -> Unit,
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { input -> onChange(input.filter { it.isDigit() || (decimal && it == '.') }) },
            label = { Text(label) },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun trimNum(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
