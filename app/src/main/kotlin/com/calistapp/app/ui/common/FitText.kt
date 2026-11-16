package com.calistapp.app.ui.common

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Text that always stays on **one line**: it steps its font size down until it fits the space it's
 * given (never below [minFontSize]; past that it ellipsises). For short labels in tight, fixed shapes —
 * a weather line like "8° · partly cloudy" inside a gauge, which used to wrap onto a second line.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = 9.sp,
    textAlign: TextAlign = TextAlign.Center,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val maxWidth = constraints.maxWidth
        val fitted = remember(text, style, maxWidth) {
            var size = style.fontSize.takeIf { it.isSp } ?: 14.sp
            if (constraints.hasBoundedWidth) {
                while (size.value > minFontSize.value) {
                    val w = measurer.measure(text, style.copy(fontSize = size), maxLines = 1, softWrap = false).size.width
                    if (w <= maxWidth) break
                    size = (size.value * 0.93f).sp
                }
            }
            val final = if (size.value < minFontSize.value) minFontSize else size
            // Letter spacing in sp scales with the shrink, so a tracked-out caps label keeps its look.
            val spacing = style.letterSpacing.takeIf { it.isSp && style.fontSize.isSp }
                ?.let { (it.value * final.value / style.fontSize.value).sp } ?: style.letterSpacing
            style.copy(fontSize = final, letterSpacing = spacing)
        }
        Text(text, style = fitted, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = textAlign)
    }
}
