package com.calistapp.app.ui.common

import androidx.compose.ui.graphics.Color
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.Sky
import com.calistapp.core.model.HrZone

/**
 * The one place heart-rate zones are given a colour.
 *
 * A cool-to-hot ramp — blue for the easy end, red for maximal — so intensity reads pre-attentively.
 * Shared by the "Time in zones" bars and the HR-over-time graph, which is the whole point: when the
 * graph is coloured by zone, a stretch of line and its zone bar are the *same* colour, and the two
 * charts reinforce each other instead of using two unrelated palettes.
 */
fun hrZoneColor(zone: HrZone): Color = when (zone) {
    HrZone.ZONE1 -> Sky
    HrZone.ZONE2 -> Amber
    HrZone.ZONE3 -> FlameGlow
    HrZone.ZONE4 -> Flame
    HrZone.ZONE5 -> Coral
}
