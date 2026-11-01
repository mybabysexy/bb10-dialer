package com.duc1607.bb10dialer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.ui.common.PageBar
import com.duc1607.bb10dialer.ui.common.PageBarAction
import duc1607.bb10.designsystem.components.Bb10Button
import duc1607.bb10.designsystem.components.Bb10Divider
import duc1607.bb10.designsystem.components.Bb10Text
import duc1607.bb10.designsystem.components.Bb10TextField
import duc1607.bb10.designsystem.components.Bb10TitleBar
import duc1607.bb10.designsystem.components.bb10VerticalScroll
import duc1607.bb10.designsystem.theme.Bb10

/**
 * A settings page: title, a form that scrolls, and the bar with Back and maybe an action. Not the
 * kit's scaffold, which only draws a corner of the bar when Back is all there is.
 */
@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    action: PageBarAction? = null,
    footer: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsFrame(title, onBack, action) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxWidth().bb10VerticalScroll(rememberScrollState()), content = content)
            footer()
        }
    }
}

/** [SettingsPage] for what scrolls by itself. */
@Composable
internal fun SettingsFrame(
    title: String,
    onBack: () -> Unit,
    action: PageBarAction? = null,
    content: @Composable () -> Unit,
) {
    val d = Bb10.dimens
    Column(Modifier.fillMaxSize().background(Bb10.colors.background)) {
        Bb10TitleBar(title)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.fillMaxSize().padding(bottom = d.actionBarHeight)) { content() }
            PageBar(onBack, action, Bb10.colors.accent, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** The grey sentence that explains what is above it. */
@Composable
internal fun SettingNote(text: String) {
    val d = Bb10.dimens
    Bb10Text(
        text,
        Modifier.padding(horizontal = d.spaceM, vertical = d.spaceS),
        style = Bb10.typography.secondary,
        color = Bb10.colors.textSecondary,
    )
}

/** A number or a text the page keeps, under its label. */
@Composable
internal fun SettingField(
    label: String?,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    numeric: Boolean = true,
    enabled: Boolean = true,
) {
    val d = Bb10.dimens
    Box(Modifier.fillMaxWidth().padding(horizontal = d.spaceM, vertical = d.spaceXs)) {
        Bb10TextField(
            value = value,
            onValueChange = onChange,
            label = label,
            placeholder = placeholder,
            enabled = enabled,
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Phone) else KeyboardOptions.Default,
        )
    }
}

/**
 * For what Android keeps to itself: the setting is shown as it is on a BlackBerry, greyed out, with
 * the reason and the way to the place where the system does offer it, when there is one.
 */
@Composable
internal fun SystemOnly(note: String, buttonText: String? = null, onButton: () -> Unit = {}) {
    val d = Bb10.dimens
    Column(
        Modifier.fillMaxWidth().padding(d.spaceM),
        verticalArrangement = Arrangement.spacedBy(d.spaceS),
    ) {
        Bb10Text(note, style = Bb10.typography.secondary, color = Bb10.colors.textSecondary)
        if (buttonText != null) Bb10Button(buttonText, onButton)
    }
    Bb10Divider()
}

/** A list of numbers to choose one of, with "Unknown" for 0 when [zeroIsUnknown]. */
@Composable
internal fun numberOptions(values: List<Int>, zeroIsUnknown: Boolean, unit: Int? = null): List<String> {
    val unknown = stringResource(R.string.settings_unknown)
    return values.map { value ->
        when {
            value == 0 && zeroIsUnknown -> unknown
            unit != null -> stringResource(unit, value)
            else -> value.toString()
        }
    }
}
