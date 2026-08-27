package com.calistapp.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameSoft
import com.calistapp.app.ui.theme.Onyx

/**
 * The one AI card, shared by every "let AI do a thing" surface (session analysis, exercise coaching).
 *
 * Before this, three screens each drew their own — a Material button here, a custom Flame capsule
 * there — so the same idea looked like three different features. One component: a soft-Flame card, an
 * AutoAwesome header, uniform loading/error handling, and a single Flame action button. Callers supply
 * only what's theirs — the title, the [body] (idle blurb or result) and the action.
 */
@Composable
fun AiActionCard(
    title: String,
    loading: Boolean,
    error: String?,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    loadingLabel: String = "Generating…",
    body: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FlameSoft)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Flame, modifier = Modifier.size(18.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Chalk)
        }

        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Flame)
                Text(loadingLabel, style = MaterialTheme.typography.bodyMedium, color = Ash)
            }
        } else {
            error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Coral)
            }
            body()
        }

        if (!loading) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(Capsule)
                    .background(Flame)
                    .clickable(onClick = onAction)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(actionLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Onyx)
            }
        }
    }
}
