package com.calistapp.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Flame

/**
 * A labelled −/＋ stepper whose value you can also just *type*. Tapping the number turns it into a
 * numeric field; every stepper in the app pairs the two, because "27 reps" is faster typed than
 * reached one tap at a time, but "one more" is faster tapped than typed. The arrows repeat when held
 * (see [RepeatingIconButton]).
 *
 * [onChange] is always handed the intended absolute value; callers clamp it to whatever range makes
 * sense for the field.
 */
@Composable
fun EditableStepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1,
    format: (Int) -> String = { it.toString() },
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ash)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RepeatingIconButton(onClick = { onChange(value - step) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Remove, "Decrease $label", tint = Chalk, modifier = Modifier.size(18.dp))
            }
            EditableNumber(
                value = value,
                onChange = onChange,
                display = format(value),
                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.widthIn(min = 48.dp).padding(horizontal = 2.dp),
            )
            RepeatingIconButton(onClick = { onChange(value + step) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Add, "Increase $label", tint = Chalk, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * A number that reads as text until you tap it, then becomes an inline field you type into. Commits
 * on Done or when focus leaves; an empty or unparseable entry is discarded, leaving the old value.
 *
 * [display] lets the resting state show something richer than the raw integer ("1:30", "off",
 * "+20 kg") while the field itself edits the plain number.
 */
@Composable
fun EditableNumber(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    display: String = value.toString(),
    color: androidx.compose.ui.graphics.Color = Chalk,
    textStyle: TextStyle = LocalTextStyle.current,
    onEditStart: () -> Unit = {},
) {
    var editing by remember { mutableStateOf(false) }

    if (!editing) {
        Text(
            display,
            style = textStyle,
            color = color,
            textAlign = TextAlign.Center,
            modifier = modifier.clickable { onEditStart(); editing = true },
        )
        return
    }

    var text by remember { mutableStateOf(value.toString()) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    fun commit() {
        text.trim().toIntOrNull()?.let(onChange)
        editing = false
    }

    // Autofocus and open the keyboard the moment the field appears — a tap that then needs a second
    // tap to actually type into is a worse control than the one it replaced.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        runCatching { focus.requestFocus() }
    }

    BasicTextField(
        value = text,
        onValueChange = { new -> text = new.filter { it.isDigit() }.take(4) },
        singleLine = true,
        textStyle = textStyle.copy(color = color, textAlign = TextAlign.Center),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Flame),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            commit()
            focusManager.clearFocus()
        }),
        modifier = modifier
            .focusRequester(focus)
            .onFocusChanged { if (!it.isFocused && editing) commit() },
    )
}

/** A large, tappable numeric display for the live rep counter — same edit-on-tap behaviour. */
@Composable
fun EditableNumberLarge(
    value: Int,
    onChange: (Int) -> Unit,
    color: androidx.compose.ui.graphics.Color,
    fontSize: Int = 56,
    modifier: Modifier = Modifier,
    onEditStart: () -> Unit = {},
) {
    EditableNumber(
        value = value,
        onChange = onChange,
        color = color,
        textStyle = LocalTextStyle.current.copy(fontSize = fontSize.sp, fontWeight = FontWeight.Bold),
        modifier = modifier.widthIn(min = 64.dp),
        onEditStart = onEditStart,
    )
}
