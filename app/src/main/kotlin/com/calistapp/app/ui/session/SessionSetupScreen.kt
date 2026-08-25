package com.calistapp.app.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.EditableStepper
import com.calistapp.app.ui.common.GlassCard
import com.calistapp.app.ui.common.NoHeartRateDialog
import com.calistapp.app.ui.common.SectionHeading
import com.calistapp.app.ui.common.WatchStatusStrip
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameSoft
import com.calistapp.app.ui.theme.OnyxBorder
import com.calistapp.app.ui.theme.Onyx
import com.calistapp.core.model.Routine

/**
 * The pre-flight screen: an optional warm-up, an optional stretch, the live-session toggles and a
 * session-wide rest default — then start. Sits between building a workout and running it, so the plan
 * you launch is the one you meant, warm-up and cooldown included.
 */
@Composable
fun SessionSetupScreen(
    onStarted: () -> Unit,
    onBack: () -> Unit,
    viewModel: SessionSetupViewModel = hiltViewModel(),
) {
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val warmUpId by viewModel.warmUpId.collectAsStateWithLifecycle()
    val stretchId by viewModel.stretchId.collectAsStateWithLifecycle()
    val defaultRest by viewModel.defaultRest.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val watchLink by viewModel.watchLink.collectAsStateWithLifecycle()
    var confirmNoWatch by rememberSaveable { mutableStateOf(false) }

    fun start() {
        viewModel.start()
        onStarted()
    }

    BackHandler(onBack = onBack)

    if (confirmNoWatch) {
        NoHeartRateDialog(
            onDismiss = { confirmNoWatch = false },
            onStartAnyway = ::start,
            onReconnect = viewModel::reconnectWatch,
        )
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Back",
                        tint = Ash,
                        modifier = Modifier.size(30.dp).clip(Capsule).clickable(onClick = onBack).padding(3.dp),
                    )
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(
                            plan.name.ifBlank { "Your workout" },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${plan.exercises.size} ${if (plan.exercises.size == 1) "exercise" else "exercises"} · get set, then go",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ash,
                        )
                    }
                }
            }

            if (plan.isCircuit) {
                item {
                    GlassCard {
                        Text("Circuit workout", style = MaterialTheme.typography.titleMedium, color = Chalk)
                        Text(
                            "Warm-up and stretch routines run once, so they're added to exercise-by-exercise " +
                                "workouts rather than circuits. Your settings and rest still apply.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ash,
                        )
                    }
                }
            } else {
                item { SectionHeading("Warm-up") }
                item {
                    RoutinePicker(
                        routines = viewModel.warmUps,
                        selectedId = warmUpId,
                        onSelect = viewModel::selectWarmUp,
                    )
                }
                item { SectionHeading("Stretching") }
                item {
                    RoutinePicker(
                        routines = viewModel.stretches,
                        selectedId = stretchId,
                        onSelect = viewModel::selectStretch,
                    )
                }
            }

            item { SectionHeading("Timers") }
            item {
                GlassCard {
                    EditableStepper(
                        label = "Rest between sets",
                        value = defaultRest,
                        onChange = viewModel::setDefaultRest,
                        step = 15,
                        format = { if (it <= 0) "per exercise" else "${it / 60}:${(it % 60).toString().padStart(2, '0')}" },
                    )
                    Text(
                        if (defaultRest <= 0) {
                            "Each exercise keeps the rest you gave it."
                        } else {
                            "Overrides every exercise's rest for this session."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Ash,
                    )
                }
            }

            item { SectionHeading("During the session") }
            item {
                GlassCard {
                    SettingToggle("Cue sounds", "Tick and go tones on countdowns", prefs.sound, viewModel::setSound)
                    SettingToggle("Vibration", "Buzz on rest-over and phase changes", prefs.vibration, viewModel::setVibration)
                    SettingToggle("Autoplay video", "Start the demo automatically", prefs.autoplayVideo, viewModel::setAutoplay)
                    SettingToggle("Hands-free cues", "Speak cues aloud, no need to touch the phone", prefs.handsFree, viewModel::setHandsFree)
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WatchStatusStrip(state = watchLink, onReconnect = viewModel::reconnectWatch)
            Button(
                onClick = { if (watchLink.isUsable) start() else confirmNoWatch = true },
                enabled = !plan.isEmpty,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = Capsule,
                colors = ButtonDefaults.buttonColors(containerColor = Flame),
            ) {
                Text("Start session", fontWeight = FontWeight.Bold, color = Onyx)
            }
        }
    }
}

/** The routine choices for one kind, with a leading "None" — tapping the chosen one clears it. */
@Composable
private fun RoutinePicker(
    routines: List<Routine>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RoutineOption(
            title = "None",
            subtitle = "Skip it",
            selected = selectedId == null,
            onClick = { selectedId?.let(onSelect) },
        )
        routines.forEach { r ->
            RoutineOption(
                title = r.name,
                subtitle = "${r.bodyFocus} · ${r.summary}",
                selected = selectedId == r.id,
                onClick = { onSelect(r.id) },
            )
        }
    }
}

@Composable
private fun RoutineOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) FlameSoft else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (selected) Flame.copy(alpha = 0.5f) else OnyxBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (selected) Flame else Chalk)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Ash)
        }
        Box(
            Modifier.size(22.dp).clip(Capsule)
                .background(if (selected) Flame else androidx.compose.ui.graphics.Color.Transparent)
                .border(1.dp, if (selected) Flame else OnyxBorder, Capsule),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, null, tint = Onyx, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Chalk)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Ash)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Onyx,
                checkedTrackColor = Flame,
                uncheckedThumbColor = Ash,
                uncheckedTrackColor = Onyx,
                uncheckedBorderColor = OnyxBorder,
            ),
        )
    }
}
