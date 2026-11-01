package com.duc1607.bb10dialer.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.SimLine
import duc1607.bb10.designsystem.components.Bb10Text
import duc1607.bb10.designsystem.theme.Bb10
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A list with [header] waiting above it. Dragging down at the top of the list pulls the header out
 * and pushes the list down with it. Pulled all the way it stays, until the list is scrolled again;
 * let go before that and it springs away. A fling arriving at the top does not open it: only a
 * finger does.
 */
@Composable
fun PullDownHeader(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var reveal by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    val connection = remember {
        object : NestedScrollConnection {
            private var settling: Job? = null

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0 || reveal <= 0f) return Offset.Zero
                settling?.cancel()
                val taken = available.y.coerceAtLeast(-reveal)
                reveal += taken
                return Offset(0f, taken)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0) return Offset.Zero
                settling?.cancel()
                val taken = min(available.y, height - reveal)
                reveal += taken
                return Offset(0f, taken)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (reveal <= 0f || reveal >= height) return Velocity.Zero
                settling = scope.launch {
                    animate(reveal, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                        reveal = value
                    }
                }
                return available
            }
        }
    }
    Box(modifier.fillMaxSize().clipToBounds().nestedScroll(connection)) {
        // Moved, not resized: the list inside stays as it is, and only the drawing changes.
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, reveal.roundToInt() - height) }
                .onSizeChanged { height = it.height },
        ) { header() }
        Box(Modifier.fillMaxSize().offset { IntOffset(0, reveal.roundToInt()) }) { content() }
    }
}

/** What the SIMs are: the number over the carrier, side by side with two. */
@Composable
fun SimHeader(lines: List<SimLine>, modifier: Modifier = Modifier) {
    val d = Bb10.dimens
    Row(
        modifier
            .fillMaxWidth()
            .background(Bb10.colors.background)
            .padding(horizontal = d.spaceM, vertical = d.spaceL * 2),
        horizontalArrangement = Arrangement.spacedBy(d.spaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (lines.isEmpty()) {
            SimColumn(stringResource(R.string.no_sim), null, big = true, Modifier.weight(1f))
        }
        for (line in lines) {
            val carrier = line.carrier ?: stringResource(R.string.unknown_carrier)
            SimColumn(
                title = line.number ?: stringResource(R.string.own_number_unknown),
                subtitle = if (lines.size > 1) stringResource(R.string.sim_carrier, line.slot, carrier) else carrier,
                big = lines.size == 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SimColumn(title: String, subtitle: String?, big: Boolean, modifier: Modifier) {
    val size = if (big) 32.sp else 22.sp
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Bb10Text(
            title,
            style = Bb10.typography.title.copy(fontSize = size, lineHeight = size * 1.25f, fontWeight = FontWeight.Light),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Bb10Text(
                subtitle,
                Modifier.padding(top = 2.dp),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
