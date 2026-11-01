package com.duc1607.bb10dialer.ui.theme

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalContext
import duc1607.bb10.designsystem.theme.rememberBb10UiScale
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.duc1607.bb10dialer.data.PhoneSettingsStore
import com.duc1607.bb10dialer.data.ThemeMode
import duc1607.bb10.designsystem.theme.Bb10
import duc1607.bb10.designsystem.theme.Bb10ScaleState
import duc1607.bb10.designsystem.theme.Bb10Theme
import duc1607.bb10.designsystem.theme.LocalBb10Colors
import duc1607.bb10.designsystem.tokens.Bb10Accents
import duc1607.bb10.designsystem.tokens.Bb10Colors
import duc1607.bb10.designsystem.tokens.bb10DarkColors
import duc1607.bb10.designsystem.tokens.bb10LightColors

/** Colours of the BB10 phone app that are not kit tokens, sampled from the real app. */
object DialerColors {
    @Composable
    @ReadOnlyComposable
    private fun forTheme(light: Color, dark: Color): Color = if (Bb10.colors.isDark) dark else light

    /** The app's own colour for the default accent, and one worked out from any other. */
    @Composable
    @ReadOnlyComposable
    private fun forAccent(default: Color, mixLight: Color, mixDark: Color, amount: Float): Color {
        val colors = Bb10.colors
        return if (colors.accent == Bb10Accents.Blue) default else lerp(colors.accent, if (colors.isDark) mixDark else mixLight, amount)
    }

    /** Call button, Callback and outgoing calls. */
    val CallGreen = Color(0xFF779933)
    /** The call button of the second SIM, next to a green one. */
    val CallSecondSim = Color(0xFF2F7389)
    val OutgoingGreen = Color(0xFF6E9A2C)
    val MissedRed = Color(0xFFE5202E)
    val IncomingBlue = Color(0xFF0092CC)

    /** An expanded row (call, contact). */
    val RowExpanded: Color
        @Composable @ReadOnlyComposable
        get() = forAccent(forTheme(Color(0xFFD5EAF0), Color(0xFF0E3644)), Color.White, Color.Black, 0.8f)
    /** The rows that a contact folds out. */
    val RowChild: Color @Composable @ReadOnlyComposable get() = forTheme(Color(0xFFDADADA), Color(0xFF262626))
    val RowChildDivider: Color @Composable @ReadOnlyComposable get() = forTheme(Color(0xFFC8C8C8), Color(0xFF3A3A3A))
    /** The action strip under a call, and call history rows. */
    val RowStrip: Color @Composable @ReadOnlyComposable get() = forTheme(Color(0xFFF0F0F0), Color(0xFF1C1C1C))

    val SectionLine: Color @Composable @ReadOnlyComposable get() = forTheme(Color(0xFFCCCCCC), Color(0xFF444444))
    val SectionText: Color @Composable @ReadOnlyComposable get() = forTheme(Color(0xFF6D6D6D), Color(0xFFA0A0A0))

    val AvatarFill: Color @Composable @ReadOnlyComposable get() = forTheme(Color(0xFFD5D5D5), Color(0xFF2E2E2E))
    val AvatarFigure: Color @Composable @ReadOnlyComposable get() = forTheme(Color(0xFF8A8A8A), Color(0xFF6E6E6E))

    /** Behind white text on a photo. */
    val PhotoScrim = Color(0x8C000000)
    /** The top and bottom of the contact card's header. */
    val CardHeaderTop = Color(0xFFADADAD)
    val CardHeaderBottom = Color(0xFF2B2B2B)
    /** The gap between groups of rows on the contact card. */
    val CardGroupGap: Color @Composable @ReadOnlyComposable get() = forTheme(Color(0xFFD5D5D5), Color(0xFF1C1C1C))
    /** The message icon on the contact card. */
    val MessageYellow = Color(0xFFEDBB16)

    /** Top of the Contacts tab, and the field in it. */
    val SearchBand: Color @Composable @ReadOnlyComposable get() = forTheme(Color(0xFFE6E6E6), Color(0xFF1C1C1C))
    val SearchLineStart: Color
        @Composable @ReadOnlyComposable
        get() = forAccent(Color(0xFF2F7389), Color.Black, Color.Black, 0.45f)

    /** Between the dial pad and the numbers shown over it. */
    val PadScrim: Color @Composable @ReadOnlyComposable get() = forTheme(Color.White.copy(alpha = 0.8f), Color.Black.copy(alpha = 0.8f))

    val DialHeader: Color @Composable @ReadOnlyComposable get() = forTheme(Color(0xFFF3F3F3), Color(0xFF141414))
    val DialHint: Color @Composable @ReadOnlyComposable get() = forTheme(Color(0xFFC4C4C4), Color(0xFF5F5F5F))

    /** Label of the selected tab of the bottom bar. */
    val SelectedTabLabel: Color
        @Composable @ReadOnlyComposable
        get() = forAccent(forTheme(Color(0xFF0A6484), Color(0xFF39B3E0)), Color.Black, Color.White, 0.25f)

    val MenuDivider = Color(0xFF191919)

    /** The call screens: what the photo fades into, and the controls on it. */
    val CallPanel = Color(0xFF17171C)
    val CallTrack = Color(0xFF2C2C33)
    val CallBackdrop = Color(0xFF23233F)
    val CallFigure = Color(0xFF6A66A3)
    val AnswerGreen = Color(0xFF8CC63F)
    val DeclineRed = Color(0xFFEE2A2F)
    /**
     * Call Again and End Call, side by side. Colour blind eyes cannot tell the app's green and red
     * apart, so this green leans to blue and is much lighter than this red.
     */
    val CallAgainBar = Color(0xFF0A8469)
    val EndCallBar = Color(0xFF9C1818)
}

/** The kit's light theme with the neutrals of the BB10 phone app. */
private fun dialerLightColors(accent: Color): Bb10Colors = bb10LightColors(accent).copy(
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F0F0),
    divider = Color(0xFFDADADA),
    field = Color(0xFFF5F5F5),
    textPrimary = Color(0xFF323232),
    textSecondary = Color(0xFF7A7A7A),
)

/** The kit's dark theme, on black, with the fields that the light one sets. */
private fun dialerDarkColors(accent: Color): Bb10Colors = bb10DarkColors(accent).copy(
    background = Color.Black,
    surface = Color(0xFF141414),
    surfaceVariant = Color(0xFF1F1F1F),
    divider = Color(0xFF333333),
    field = Color(0xFF1F1F1F),
    textPrimary = Color(0xFFE6E6E6),
    textSecondary = Color(0xFF9A9A9A),
)

/** The right-hand menu is black on the light app. */
private fun dialerMenuColors(accent: Color): Bb10Colors = bb10DarkColors(accent).copy(
    surface = Color.Black,
    divider = DialerColors.MenuDivider,
)

/**
 * The display size chosen in Phone Settings. It is kept in device protected storage, as the kit keeps
 * it in the application context's preferences: a call before the first unlock reads it too.
 */
@Composable
fun ProvideDialerScale(content: @Composable (Bb10ScaleState) -> Unit) {
    val base = LocalContext.current
    val storage = remember(base) { DeviceStorageContext(base) }
    CompositionLocalProvider(LocalContext provides storage) {
        val scale = rememberBb10UiScale()
        CompositionLocalProvider(LocalContext provides base) { content(scale) }
    }
}

private class DeviceStorageContext(base: Context) : ContextWrapper(base) {
    private val storage = base.createDeviceProtectedStorageContext()
    override fun getApplicationContext(): Context = storage
}

@Composable
fun DialerTheme(scale: Bb10ScaleState, content: @Composable () -> Unit) {
    val settings by PhoneSettingsStore.state(LocalContext.current).collectAsState()
    val dark = when (settings.themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val accent = Bb10Accents.all.getOrElse(settings.accentIndex) { Bb10Accents.Blue }
    val colors = remember(dark, accent) { if (dark) dialerDarkColors(accent) else dialerLightColors(accent) }
    Bb10Theme(scale = scale, dark = dark, accent = accent) {
        CompositionLocalProvider(LocalBb10Colors provides colors, content = content)
    }
}

@Composable
fun DialerMenuTheme(content: @Composable () -> Unit) {
    // On the dark app, black would not stand out from what is under it.
    val dark = Bb10.colors.isDark
    val accent = Bb10.colors.accent
    val colors = remember(dark, accent) {
        dialerMenuColors(accent).let { if (dark) it.copy(surface = Color(0xFF1F1F1F)) else it }
    }
    CompositionLocalProvider(LocalBb10Colors provides colors, content = content)
}
