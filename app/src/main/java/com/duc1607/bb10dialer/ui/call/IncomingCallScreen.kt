package com.duc1607.bb10dialer.ui.call

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.ui.theme.DialerIcons
import duc1607.bb10.designsystem.components.Bb10Icon
import duc1607.bb10.designsystem.components.Bb10Text
import duc1607.bb10.designsystem.components.bb10Clickable
import duc1607.bb10.designsystem.theme.Bb10

private val PanelContentWidth = 400.dp
private val SideActionSize = 52.dp
private val SideActionRing = 1.dp
private val SideActionGap = 24.dp
private const val SPENT_ACTION_ALPHA = 0.4f

/** Someone is calling: the photo, who it is, and the slider with its two side actions. */
@Composable
fun IncomingCallScreen(
    party: CallParty,
    myNumber: String?,
    ringerSilenced: Boolean,
    onSilence: () -> Unit,
    /** Null when Reply Now is turned off in Phone Settings. */
    onSms: (() -> Unit)?,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
    /** Who the user is talking to, when this is a second call. */
    waitingOn: String? = null,
    /** Set on a second call: ends the one under way instead of holding it. */
    onEndAndAnswer: (() -> Unit)? = null,
    /** Off in Phone Settings: answering is left to the Call key and the notification. */
    showSlider: Boolean = true,
    /** The slider the other way round, and the actions over its ends with it. */
    answerOnRight: Boolean = false,
) {
    val c = Bb10.colors
    val d = Bb10.dimens
    CallLayout(
        photo = party.photo,
        myNumber = myNumber,
        modifier = modifier,
        name = {
            Column {
                Bb10Text(
                    party.title,
                    Modifier.semantics { heading() },
                    style = Bb10.typography.title.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
                    color = c.onAccent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (party.subtitle != null) {
                    Bb10Text(party.subtitle, style = Bb10.typography.body, color = c.onAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (waitingOn != null) {
                    Bb10Text(
                        stringResource(R.string.call_in_progress_with, waitingOn),
                        style = Bb10.typography.secondary,
                        color = c.onAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        panel = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = d.spaceM)
                    .padding(top = d.spaceXs, bottom = d.spaceL),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The silence action over the thumb and the message over the decline end, as designed.
                Box(Modifier.widthIn(max = PanelContentWidth).fillMaxWidth()) {
                    if (onEndAndAnswer != null) {
                        SideAction(
                            DialerIcons.HangUp,
                            stringResource(R.string.end_and_answer),
                            modifier = Modifier.align(if (answerOnRight) Alignment.CenterEnd else Alignment.CenterStart),
                            onClick = onEndAndAnswer,
                        )
                    }
                    SideAction(
                        DialerIcons.RingerOff,
                        stringResource(R.string.silence_ringer),
                        enabled = !ringerSilenced,
                        modifier = Modifier.align(Alignment.Center),
                        onClick = onSilence,
                    )
                    if (onSms != null) {
                        SideAction(
                            DialerIcons.Message,
                            stringResource(R.string.action_sms),
                            modifier = Modifier.align(if (answerOnRight) Alignment.CenterStart else Alignment.CenterEnd),
                            onClick = onSms,
                        )
                    }
                }
                if (showSlider) {
                    Spacer(Modifier.height(SideActionGap))
                    AnswerSlider(onAnswer, onDecline, Modifier.widthIn(max = PanelContentWidth), answerOnRight)
                }
            }
        },
    )
}

/** A round icon button. Silencing can't be undone, so it stays dimmed once used. */
@Composable
private fun SideAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(SideActionSize)
            .alpha(if (enabled) 1f else SPENT_ACTION_ALPHA)
            .clip(CircleShape)
            .border(BorderStroke(SideActionRing, Color.White.copy(alpha = 0.35f)), CircleShape)
            .semantics { contentDescription = label }
            .bb10Clickable(enabled = enabled, role = Role.Button, circle = true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Bb10Icon(icon, tint = Bb10.colors.onAccent, size = 26.dp)
    }
}
