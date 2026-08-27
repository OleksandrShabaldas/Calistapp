package com.calistapp.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.OnyxFillStrong

/**
 * The one back affordance for the app's custom-chrome screens (the immersive ones that can't use a
 * Material `TopAppBar`). A circular onyx button, so "go back" looks the same on the exercise detail,
 * the session setup and the settings sub-pages instead of three different hand-rolled icons.
 */
@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(38.dp)
            .clip(Capsule)
            .background(OnyxFillStrong)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Chalk, modifier = Modifier.size(20.dp))
    }
}
