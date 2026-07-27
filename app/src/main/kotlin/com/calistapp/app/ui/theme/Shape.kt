package com.calistapp.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Generous radii throughout. Material's defaults (4–16dp) read as small tight boxes; pushing cards
 * to ~26dp and making every interactive row a full capsule is a large part of why the reference
 * design feels soft and considered rather than boxy.
 */
val CalistShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/** Fully rounded — chips, rows, toggles, the floating nav bar. */
val Capsule = RoundedCornerShape(50)

/** The standard card. */
val CardShape = RoundedCornerShape(26.dp)
