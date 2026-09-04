package com.calistapp.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.theme.AshFaint
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameDeep
import com.calistapp.app.ui.theme.Onyx
import com.calistapp.app.ui.theme.OnyxBorder
import com.calistapp.app.ui.theme.OnyxRaised

data class NavItem(val route: String, val label: String, val icon: ImageVector)

/**
 * Detached capsule navigation with the primary action raised out of its centre.
 *
 * Icon-only (the labels are gone): the selected tab lights up and wears an orange glow, and the
 * centre action is a larger-than-the-bar button that glows too — the one thing that matters most,
 * unmissable.
 */
@Composable
fun FloatingNavBar(
    items: List<NavItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionDescription: String = "Start workout",
    actionIcon: ImageVector = Icons.Filled.FitnessCenter,
) {
    val half = (items.size + 1) / 2
    val left = items.take(half)
    val right = items.drop(half)

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        // The floating capsule panel (kept). A frosted, mostly-opaque fill so content scrolling under
        // it reads as *behind* the bar rather than colliding with the icons — and no dark scrim band
        // behind it, so the bar floats over the content instead of a black rectangle.
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(Capsule)
                .background(OnyxRaised.copy(alpha = 0.82f))
                .border(BorderStroke(1.dp, OnyxBorder), Capsule),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavCluster(left, currentRoute, onSelect, Modifier.weight(1f))
            Box(Modifier.size(86.dp))
            NavCluster(right, currentRoute, onSelect, Modifier.weight(1f))
        }

        Box(
            Modifier
                .glow(Flame, spread = 18.dp, alpha = 0.4f)
                .size(72.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Flame, FlameDeep)))
                .clickable(onClick = onAction),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                actionIcon,
                contentDescription = actionDescription,
                tint = Onyx,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun NavCluster(
    items: List<NavItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            val primary = MaterialTheme.colorScheme.primary
            val tint by animateColorAsState(if (selected) primary else AshFaint, label = "navTint")
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    // No .clip() here — it would cut the selected icon's glow; the null-indication
                    // clickable doesn't need it.
                    .size(width = 52.dp, height = 52.dp)
                    .clickable(interactionSource = interaction, indication = null, onClick = { onSelect(item.route) }),
                contentAlignment = Alignment.Center,
            ) {
                GlowIcon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = tint,
                    size = 25.dp,
                    glowColor = primary,
                    glowRadius = 8.dp,
                    glowAlpha = if (selected) 0.5f else 0f,
                )
            }
        }
    }
}
