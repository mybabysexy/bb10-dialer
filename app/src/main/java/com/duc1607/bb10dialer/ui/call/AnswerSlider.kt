package com.duc1607.bb10dialer.ui.call

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.ui.theme.DialerColors
import com.duc1607.bb10dialer.ui.theme.DialerIcons
import duc1607.bb10.designsystem.components.Bb10Icon
import duc1607.bb10.designsystem.theme.Bb10
import kotlinx.coroutines.launch
import kotlin.math.abs

private val SliderHeight = 92.dp
private val ThumbRadius = 37.dp
private val RingWidth = 3.dp
private val RingGap = 5.dp
private val EndIconSize = 34.dp
/** Puts an end's icon in the middle of the thumb that arrives there. */
private val EndIconInset = (SliderHeight - EndIconSize) / 2
private val ArcWidth = 2.5.dp
private val ChevronWidth = 2.5.dp
private val ChevronSize = 8.dp
private val ChevronStep = 15.dp
/** From the thumb to the tip of the nearest chevron, whose arms reach back [ChevronSize]. */
private val ChevronGap = 28.dp

private const val CHEVRONS = 3
private const val WAVE_MILLIS = 1100
private const val GRIP_MILLIS = 120
/** How far along the thumb has to be let go for the call to be answered or declined. */
private const val COMMIT_AT = 0.8f
/** The half of the arc that shows at each end, in degrees. */
private const val ARC_HALF_SWEEP = 68f

/**
 * Answer to the left, decline to the right, or the other way round with [answerOnRight], by dragging
 * the round thumb; only a drag that starts on
 * the thumb counts, so that a phone taken out of a pocket does not answer. Holding the thumb fills
 * its gap like a camera's shutter button, and it turns green or red on the way to either end.
 *
 * It stays at the end once used: give it a new key for the next call.
 */
@Composable
internal fun AnswerSlider(onAnswer: () -> Unit, onDecline: () -> Unit, modifier: Modifier = Modifier, answerOnRight: Boolean = false) {
    val answer by rememberUpdatedState(onAnswer)
    val decline by rememberUpdatedState(onDecline)
    val scope = rememberCoroutineScope()
    // -1 is the answer end, 1 the decline end, wherever they are: [side] puts them on the screen.
    val position = remember { Animatable(0f) }
    val side = if (answerOnRight) -1f else 1f
    var held by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val grip = animateFloatAsState(if (held) 1f else 0f, tween(GRIP_MILLIS), label = "grip")
    val wave = rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(WAVE_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val answerLabel = stringResource(R.string.answer_call)
    val declineLabel = stringResource(R.string.decline_call)
    val answerProgress = (-position.value).coerceAtLeast(0f)
    val declineProgress = position.value.coerceAtLeast(0f)

    Box(
        modifier
            .fillMaxWidth()
            .height(SliderHeight)
            // A drag is no use to a screen reader.
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(answerLabel) { answer(); true },
                    CustomAccessibilityAction(declineLabel) { decline(); true },
                )
            }
            .pointerInput(side) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val travel = (size.width - size.height) / 2f
                    val thumbX = size.width / 2f + position.value * side * travel
                    if (done || abs(down.position.x - thumbX) > size.height / 2f) return@awaitEachGesture
                    down.consume()
                    held = true
                    var moved = position.value * travel
                    try {
                        drag(down.id) { change ->
                            moved += change.positionChange().x * side
                            change.consume()
                            val target = (moved / travel).coerceIn(-1f, 1f)
                            scope.launch { position.snapTo(target) }
                        }
                    } finally {
                        held = false
                    }
                    val landed = (moved / travel).coerceIn(-1f, 1f)
                    if (abs(landed) >= COMMIT_AT) {
                        done = true
                        scope.launch {
                            position.animateTo(if (landed < 0) -1f else 1f)
                            if (landed < 0) answer() else decline()
                        }
                    } else {
                        scope.launch { position.animateTo(0f, spring()) }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawTrack(position.value, side)
            drawChevrons(wave.value, position.value, side)
            drawThumb(position.value, grip.value, side)
        }
        // Over the thumb, which turns the end's colour when it arrives there.
        Bb10Icon(
            DialerIcons.Phone,
            Modifier
                .align(if (answerOnRight) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = EndIconInset)
                .alpha(answerSideAlpha(position.value)),
            tint = lerp(DialerColors.AnswerGreen, Color.White, answerProgress),
            size = EndIconSize,
        )
        Bb10Icon(
            DialerIcons.HangUp,
            Modifier
                .align(if (answerOnRight) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = EndIconInset)
                .alpha(declineSideAlpha(position.value)),
            tint = lerp(DialerColors.DeclineRed, Color.White, declineProgress),
            size = EndIconSize,
        )
    }
}

/** The end that is not being dragged to fades out, with all that belongs to it. */
private fun answerSideAlpha(position: Float) = (1f - position * 2f).coerceIn(0f, 1f)
private fun declineSideAlpha(position: Float) = (1f + position * 2f).coerceIn(0f, 1f)

/** [side] is 1 with the answer end on the left, -1 with it on the right. */
private fun DrawScope.drawTrack(position: Float, side: Float) {
    val radius = size.height / 2
    drawRoundRect(DialerColors.CallTrack, cornerRadius = CornerRadius(radius))
    val inset = ArcWidth.toPx() / 2
    val arcSize = Size(size.height - inset * 2, size.height - inset * 2)
    val stroke = Stroke(ArcWidth.toPx(), cap = StrokeCap.Round)
    val left = Offset(inset, inset)
    val right = Offset(size.width - size.height + inset, inset)
    drawArc(
        DialerColors.AnswerGreen.copy(alpha = answerSideAlpha(position)),
        startAngle = if (side > 0) 180f - ARC_HALF_SWEEP else -ARC_HALF_SWEEP,
        sweepAngle = ARC_HALF_SWEEP * 2,
        useCenter = false,
        topLeft = if (side > 0) left else right,
        size = arcSize,
        style = stroke,
    )
    drawArc(
        DialerColors.DeclineRed.copy(alpha = declineSideAlpha(position)),
        startAngle = if (side > 0) -ARC_HALF_SWEEP else 180f - ARC_HALF_SWEEP,
        sweepAngle = ARC_HALF_SWEEP * 2,
        useCenter = false,
        topLeft = if (side > 0) right else left,
        size = arcSize,
        style = stroke,
    )
}

/** Three chevrons pointing away from the thumb, lit one after the other from the thumb outwards. */
private fun DrawScope.drawChevrons(phase: Float, position: Float, side: Float) {
    val middle = size.width / 2
    val first = ThumbRadius.toPx() + ChevronGap.toPx()
    for (i in 0 until CHEVRONS) {
        val age = (phase - i.toFloat() / CHEVRONS + 1f) % 1f
        val alpha = MIN_CHEVRON_ALPHA + (1f - MIN_CHEVRON_ALPHA) * (1f - age)
        val distance = first + i * ChevronStep.toPx()
        drawChevron(middle - distance * side, -side, DialerColors.AnswerGreen.copy(alpha = alpha * answerSideAlpha(position)))
        drawChevron(middle + distance * side, side, DialerColors.DeclineRed.copy(alpha = alpha * declineSideAlpha(position)))
    }
}

private const val MIN_CHEVRON_ALPHA = 0.2f

/** A chevron with its tip at [x], pointing in [direction]. */
private fun DrawScope.drawChevron(x: Float, direction: Float, color: Color) {
    val y = size.height / 2
    val arm = ChevronSize.toPx()
    val tail = x - direction * arm
    drawLine(color, Offset(tail, y - arm), Offset(x, y), ChevronWidth.toPx(), StrokeCap.Round)
    drawLine(color, Offset(tail, y + arm), Offset(x, y), ChevronWidth.toPx(), StrokeCap.Round)
}

private fun DrawScope.drawThumb(position: Float, grip: Float, side: Float) {
    val travel = (size.width - size.height) / 2
    val center = Offset(size.width / 2 + position * side * travel, size.height / 2)
    val end = if (position < 0) DialerColors.AnswerGreen else DialerColors.DeclineRed
    val color = lerp(Color.White, end, abs(position))
    val outer = ThumbRadius.toPx()
    val inner = outer - RingWidth.toPx() - RingGap.toPx()
    // Hides what the thumb passes over: the gap shows the track, not the chevrons.
    drawCircle(DialerColors.CallTrack, outer, center)
    drawCircle(color, outer - RingWidth.toPx() / 2, center, style = Stroke(RingWidth.toPx()))
    drawCircle(color, inner + (outer - inner) * grip, center)
}
