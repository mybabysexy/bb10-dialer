package com.duc1607.bb10dialer.ui.call

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.ContactPhoto
import com.duc1607.bb10dialer.ui.common.Avatar
import com.duc1607.bb10dialer.ui.theme.DialerColors
import com.duc1607.bb10dialer.ui.theme.DialerIcons
import duc1607.bb10.designsystem.components.Bb10Text
import duc1607.bb10.designsystem.theme.Bb10

/** Who is on the other end. [title] is the name, or the number when it is not saved. */
@Immutable
data class CallParty(val title: String, val subtitle: String? = null, val photo: ContactPhoto? = null)

/** The photo darkens downwards, until it is solid at the name. */
private val CallShade = Brush.verticalGradient(
    0f to Color.Transparent,
    0.55f to DialerColors.CallPanel.copy(alpha = 0.45f),
    1f to DialerColors.CallPanel,
)

/** Keeps the status bar and the user's number readable over a bright photo. */
private val TopShade = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.6f),
    1f to Color.Transparent,
)

/** The short band that holds the name when it moved to the top. */
private val BandShade = Brush.verticalGradient(
    0f to DialerColors.CallPanel.copy(alpha = 0.6f),
    1f to DialerColors.CallPanel,
)

/**
 * What the call screens share: the photo over the whole screen, [name] at the end of its fade, and
 * [panel] covering the rest. Without a [panelWeight] the panel is as tall as its content.
 * [nameOnTop] leaves the panel all the room there is: only a band with the name stays above it.
 */
@Composable
internal fun CallLayout(
    photo: ContactPhoto?,
    myNumber: String?,
    modifier: Modifier = Modifier,
    panelWeight: Float? = null,
    nameOnTop: Boolean = false,
    name: @Composable () -> Unit,
    panel: @Composable () -> Unit,
) {
    val d = Bb10.dimens
    Box(modifier.fillMaxSize().background(DialerColors.CallPanel)) {
        CallPhoto(photo, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            if (nameOnTop) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(BandShade)
                        .statusBarsPadding()
                        .padding(horizontal = d.spaceM, vertical = d.spaceS),
                ) { name() }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth().background(CallShade)) {
                    Box(Modifier.fillMaxWidth().background(TopShade).statusBarsPadding()) {
                        if (myNumber != null) {
                            Bb10Text(
                                stringResource(R.string.my_number, myNumber),
                                Modifier.padding(horizontal = d.spaceM, vertical = d.spaceS),
                                style = Bb10.typography.secondary,
                                color = Bb10.colors.onAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = d.spaceM, vertical = d.spaceS),
                    ) { name() }
                }
            }
            Box(
                (if (panelWeight != null) Modifier.weight(panelWeight) else Modifier)
                    .fillMaxWidth()
                    .background(DialerColors.CallPanel),
            ) { panel() }
        }
    }
}

@Composable
private fun CallPhoto(photo: ContactPhoto?, modifier: Modifier) {
    if (photo != null) {
        Avatar(modifier, photo)
    } else {
        Box(modifier.background(DialerColors.CallBackdrop)) {
            Image(
                imageVector = DialerIcons.CallFigure,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Right of the middle, leaving room for the user's number and the name.
                        translationX = size.width * FIGURE_SHIFT
                    },
                colorFilter = ColorFilter.tint(DialerColors.CallFigure),
            )
        }
    }
}

private const val FIGURE_SHIFT = 0.14f
