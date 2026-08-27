package com.calistapp.app.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.data.ai.AiSettings
import com.calistapp.app.share.shareTextFile
import com.calistapp.app.ui.common.SectionCard
import com.calistapp.app.ui.update.UpdateCard
import kotlinx.coroutines.launch
import com.calistapp.core.calorie.Vo2MaxEstimate
import com.calistapp.core.model.ProfileField
import com.calistapp.core.model.Sex
import com.calistapp.core.model.TrainingGoals
import com.calistapp.core.model.UserProfile

/**
 * Settings, organised like a phone's: a hub of category rows, each opening its own page (Back returns
 * to the hub). The old screen was one long scroll of everything; splitting it makes room for the AI
 * category — API key and the thinking/fast model tiers — without burying the profile form under it.
 */
private enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    PROFILE("Profile & body", "The metrics that personalise every calorie estimate", Icons.Filled.Person),
    GOALS("Weekly goals", "Calories and sessions you're aiming for each week", Icons.Filled.Flag),
    AI("AI", "API key, and the models for analysis and coaching", Icons.Filled.AutoAwesome),
    OFFLINE("Offline media", "Download exercise videos to use the app with no connection", Icons.Filled.CloudDownload),
    HIDDEN("Hidden exercises", "Bring back exercises you removed from the library", Icons.Filled.VisibilityOff),
    ABOUT("About & updates", "App version and updates", Icons.Filled.Info),
}

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val onboarded by viewModel.isOnboarded.collectAsStateWithLifecycle()
    val hidden by viewModel.hiddenExercises.collectAsStateWithLifecycle()

    var category by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    val cat = category

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (cat == null) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (!onboarded) {
                SectionCard {
                    Text(
                        "Welcome! Open Profile & body and fill it in once to unlock accurate tracking.",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            SettingsCategory.entries.forEach { c ->
                // The restore list is only worth a row when something's actually hidden.
                if (c == SettingsCategory.HIDDEN && hidden.isEmpty()) return@forEach
                SettingsRow(c, onClick = { category = c })
            }
        } else {
            BackHandler { category = null }
            DetailHeader(cat.title, onBack = { category = null })
            when (cat) {
                SettingsCategory.PROFILE -> ProfileDetail(viewModel)
                SettingsCategory.GOALS -> GoalsDetail(viewModel)
                SettingsCategory.AI -> AiSettingsDetail(viewModel)
                SettingsCategory.OFFLINE -> OfflineDetail(viewModel)
                SettingsCategory.HIDDEN -> HiddenExercisesSection(hidden = hidden, onRestore = viewModel::restore)
                SettingsCategory.ABOUT -> AboutDetail(viewModel)
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun SettingsRow(category: SettingsCategory, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(category.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    category.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        com.calistapp.app.ui.common.BackButton(onBack)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

/** Name, sex, age, body metrics and heart/fitness — everything that feeds the calorie formulas. */
@Composable
private fun ProfileDetail(viewModel: ProfileViewModel) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()

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

    // Seeded once from the stored profile, and only once it's been read — keyed on every emission it
    // would overwrite what's being typed each time the flow ticked (e.g. the watch syncing it back).
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

    val edited = UserProfile(
        name = name.trim(),
        sex = sex,
        ageYears = age.toIntOrNull() ?: -1,
        weightKg = weight.toDoubleOrNull() ?: -1.0,
        heightCm = height.toDoubleOrNull() ?: -1.0,
        restingHr = restingHr.toIntOrNull() ?: -1,
        maxHr = if (maxHr.isBlank()) null else maxHr.toIntOrNull() ?: -1,
        vo2Max = if (vo2.isBlank()) null else vo2.toDoubleOrNull() ?: -1.0,
    )
    val invalid = edited.invalidFields()
    fun touched() { saved = false }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "These numbers personalize every calorie estimate. VO₂max is optional but noticeably improves accuracy.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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

        Button(
            onClick = { viewModel.save(edited); saved = true },
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
    }
}

@Composable
private fun GoalsDetail(viewModel: ProfileViewModel) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    GoalsSection(goals = goals, onSave = viewModel::saveGoals)
}

/**
 * The AI settings: the Gemini API key, then two model tiers each with a primary and two fallbacks.
 * Thinking models do the reasoning-heavy work (session analysis, coaching); fast/lite models do the
 * high-volume helper work, which keeps the scarce good-model quota for the analysis that needs it.
 */
@Composable
private fun AiSettingsDetail(viewModel: ProfileViewModel) {
    val settings by viewModel.aiSettings.collectAsStateWithLifecycle()
    val s = settings
    if (s == null) {
        Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    var key by rememberSaveable { mutableStateOf(s.apiKey) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var t0 by rememberSaveable { mutableStateOf(s.thinkingModels.getOrElse(0) { "" }) }
    var t1 by rememberSaveable { mutableStateOf(s.thinkingModels.getOrElse(1) { "" }) }
    var t2 by rememberSaveable { mutableStateOf(s.thinkingModels.getOrElse(2) { "" }) }
    var f0 by rememberSaveable { mutableStateOf(s.fastModels.getOrElse(0) { "" }) }
    var f1 by rememberSaveable { mutableStateOf(s.fastModels.getOrElse(1) { "" }) }
    var f2 by rememberSaveable { mutableStateOf(s.fastModels.getOrElse(2) { "" }) }
    var saved by remember { mutableStateOf(false) }
    fun touched() { saved = false }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "Gemini API key") {
            Text(
                "Your key stays on this device. Without it the analysis and coaching features stay off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it; touched() },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showKey) "Hide key" else "Show key",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard(title = "Thinking models") {
            Text(
                "Used for the reasoning-heavy work — session analysis and coaching. Tried top to bottom, " +
                    "so a rate-limited primary falls through to a backup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ModelField("Primary", t0) { t0 = it; touched() }
            ModelField("Fallback 1", t1) { t1 = it; touched() }
            ModelField("Fallback 2", t2) { t2 = it; touched() }
        }

        SectionCard(title = "Fast models") {
            Text(
                "Used for the high-volume helper work — bulk exercise enrichment and quick suggestions. " +
                    "Lite models here have far more daily quota, so the good models are saved for analysis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ModelField("Primary", f0) { f0 = it; touched() }
            ModelField("Fallback 1", f1) { f1 = it; touched() }
            ModelField("Fallback 2", f2) { f2 = it; touched() }
        }

        Button(
            onClick = {
                viewModel.saveAiSettings(key, listOf(t0, t1, t2), listOf(f0, f1, f2))
                saved = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (saved) "Saved ✓" else "Save AI settings")
        }
        OutlinedButton(
            onClick = {
                t0 = AiSettings.DEFAULT_THINKING[0]; t1 = AiSettings.DEFAULT_THINKING[1]; t2 = AiSettings.DEFAULT_THINKING[2]
                f0 = AiSettings.DEFAULT_FAST[0]; f1 = AiSettings.DEFAULT_FAST[1]; f2 = AiSettings.DEFAULT_FAST[2]
                viewModel.resetAiModels()
                touched()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Reset models to defaults")
        }

        EnrichLibraryCard(viewModel)
    }
}

/**
 * Bulk-generate coaching content for the whole exercise library. Lives in Settings → AI rather than
 * in the browse flow: it's a one-time background chore, not something to meet every time you search.
 */
@Composable
private fun EnrichLibraryCard(viewModel: ProfileViewModel) {
    val p by viewModel.enrichmentProgress.collectAsStateWithLifecycle()
    SectionCard(title = "Enrich the whole library") {
        if (p.running) {
            Text(
                "Generating coaching content ${p.done} / ${p.total}…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { if (p.total > 0) p.done.toFloat() / p.total else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = viewModel::stopEnrichAll, modifier = Modifier.fillMaxWidth()) { Text("Stop") }
        } else {
            Text(
                "Generate an overview, common mistakes and tips for every exercise, using the Fast models. " +
                    "Runs in the background and is cached, so each exercise is only ever generated once. " +
                    "On the free tier the daily quota can pause it — just tap again tomorrow to continue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = viewModel::startEnrichAll, modifier = Modifier.fillMaxWidth()) {
                Text(if (p.done > 0) "Continue enriching" else "Enrich all with AI")
            }
        }
        p.lastError?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ModelField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        placeholder = { Text("model id, e.g. gemini-3.5-flash") },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Download every exercise video for offline use, with progress and a way to reclaim the space. */
@Composable
private fun OfflineDetail(viewModel: ProfileViewModel) {
    val p by viewModel.mediaDownload.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshMediaSize() }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "Offline media") {
            Text(
                "Exercise videos stream from the cloud and can be slow (or fail) on a poor connection. " +
                    "Download them once and the workout screen works with no network at all. Clips you've " +
                    "already watched are kept too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "On this device: ${formatBytes(p.bytes)}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )

            if (p.running) {
                Text(
                    "Downloading ${p.done} / ${p.total}" + if (p.failed > 0) " · ${p.failed} skipped" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = viewModel::stopMediaDownload, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop")
                }
            } else {
                if (p.total > 0 && p.done >= p.total) {
                    Text(
                        if (p.failed == 0) "All ${p.total} videos downloaded ✓" else "${p.total - p.failed} of ${p.total} downloaded · ${p.failed} unavailable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Button(onClick = viewModel::startMediaDownload, modifier = Modifier.fillMaxWidth()) {
                    Text("Download all for offline")
                }
            }

            p.lastError?.let {
                Text("Last error: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }

        if (p.bytes > 0 && !p.running) {
            OutlinedButton(onClick = viewModel::clearMediaDownload, modifier = Modifier.fillMaxWidth()) {
                Text("Clear downloaded media")
            }
        }
    }
}

private fun formatBytes(b: Long): String = when {
    b <= 0L -> "nothing yet"
    b < 1024L * 1024 -> "${b / 1024} KB"
    b < 1024L * 1024 * 1024 -> "%.0f MB".format(b / 1024.0 / 1024)
    else -> "%.1f GB".format(b / 1024.0 / 1024 / 1024)
}

@Composable
private fun AboutDetail(viewModel: ProfileViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "Your data") {
            Text(
                "Export every finished session — its sets, plan, calories and heart-rate summary — as a " +
                    "JSON file you can keep or move elsewhere. Your data stays yours.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    scope.launch {
                        exporting = true
                        runCatching {
                            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                            shareTextFile(
                                context = context,
                                content = viewModel.buildExportJson(),
                                fileName = "calistapp-export-$date.json",
                                mime = "application/json",
                                subject = "Calistapp training data",
                            )
                        }
                        exporting = false
                    }
                },
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (exporting) "Preparing…" else "Export training data")
            }
        }

        UpdateCard()
        Text(
            "Calistapp ${com.calistapp.app.BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    ex.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                TextButton(onClick = { onRestore(ex.id) }) {
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
