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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.AshFaint
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameDeep
import com.calistapp.app.ui.theme.OnyxBorder
import com.calistapp.app.ui.theme.Onyx

data class NavItem(val route: String, val label: String, val icon: ImageVector)

/**
 * Detached capsule navigation with the primary action raised out of its centre.
 *
 * Floating it clear of the screen edge — rather than using a stock edge-to-edge `NavigationBar` —
 * lets the ambient wash continue underneath, and gives the one action that matters most (start a
 * workout) a permanent, unmissable home.
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
    // Split evenly around the raised action button.
    val half = (items.size + 1) / 2
    val left = items.take(half)
    val right = items.drop(half)

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(Capsule)
                .background(Color.White.copy(alpha = 0.06f))
                .border(BorderStroke(1.dp, OnyxBorder), Capsule),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavCluster(left, currentRoute, onSelect, Modifier.weight(1f))
            // Reserved gap the action button sits in.
            Box(Modifier.size(78.dp))
            NavCluster(right, currentRoute, onSelect, Modifier.weight(1f))
        }

        Box(
            Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Flame, FlameDeep)))
                .clickable(onClick = onAction),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                actionIcon,
                contentDescription = actionDescription,
                tint = Onyx,
                modifier = Modifier.size(26.dp),
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
            val tint by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else AshFaint,
                label = "navTint",
            )
            val interaction = remember { MutableInteractionSource() }
            androidx.compose.foundation.layout.Column(
                Modifier
                    .size(width = 56.dp, height = 52.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onSelect(item.route) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}
