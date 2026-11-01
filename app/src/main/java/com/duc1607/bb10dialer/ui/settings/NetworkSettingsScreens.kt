package com.duc1607.bb10dialer.ui.settings

import android.content.Intent
import android.telecom.TelecomManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.SimLine
import com.duc1607.bb10dialer.telecom.SimCodes
import com.duc1607.bb10dialer.telecom.forwardedTo
import com.duc1607.bb10dialer.ui.common.PageBarAction
import com.duc1607.bb10dialer.ui.common.SectionHeader
import com.duc1607.bb10dialer.ui.theme.DialerIcons
import duc1607.bb10.designsystem.components.Bb10Dialog
import duc1607.bb10.designsystem.components.Bb10Divider
import duc1607.bb10.designsystem.components.Bb10Dropdown
import duc1607.bb10.designsystem.components.Bb10InputDialog
import duc1607.bb10.designsystem.components.Bb10Text
import duc1607.bb10.designsystem.components.Bb10TextField
import duc1607.bb10.designsystem.components.Bb10Toggle
import duc1607.bb10.designsystem.theme.Bb10
import kotlinx.coroutines.launch

/**
 * What a page of network settings shares: which SIM, and "Loading..." while the network answers.
 * Nothing can be switched until it has: a change made without knowing the state would be blind.
 * When it does not answer, the phone's own settings are one tap away.
 */
@Composable
private fun NetworkPage(
    title: String,
    simLines: List<SimLine>,
    slot: Int?,
    onSlot: (Int) -> Unit,
    busy: Boolean,
    failed: Boolean,
    onBack: () -> Unit,
    action: PageBarAction? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val d = Bb10.dimens
    SettingsPage(
        title,
        onBack,
        action,
        footer = {
            if (busy) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = d.spaceM, vertical = d.spaceS),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Bb10Text(stringResource(R.string.settings_loading), Modifier.weight(1f))
                    Spinner()
                }
            }
        },
    ) {
        if (simLines.size > 1) {
            Box(Modifier.fillMaxWidth().padding(d.spaceM)) {
                Bb10Dropdown(
                    stringResource(R.string.settings_sim),
                    simLines.map { line ->
                        val carrier = line.carrier ?: stringResource(R.string.unknown_carrier)
                        stringResource(R.string.sim_carrier, line.slot, carrier)
                    },
                    simLines.indexOfFirst { it.slot == slot }.coerceAtLeast(0),
                    { onSlot(simLines[it].slot) },
                )
            }
        }
        if (failed) {
            SystemOnly(stringResource(R.string.settings_network_failed), stringResource(R.string.settings_open_system)) {
                openFirst(context, Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS))
            }
        }
        content()
    }
}

@Composable
private fun Spinner() {
    val color = Bb10.colors.accent
    val angle by rememberInfiniteTransition(label = "spinner").animateFloat(
        0f,
        360f,
        infiniteRepeatable(tween(SPIN_MILLIS, easing = LinearEasing)),
        label = "angle",
    )
    Canvas(Modifier.size(24.dp)) {
        drawArc(color, angle, SPIN_SWEEP, false, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

private const val SPIN_MILLIS = 1000
private const val SPIN_SWEEP = 270f

@Composable
private fun rememberSlot(simLines: List<SimLine>) =
    rememberSaveable(simLines.firstOrNull()?.slot) { mutableStateOf(simLines.firstOrNull()?.slot) }

private class ForwardKind(val label: Int, val code: String)

private val ForwardKinds = listOf(
    ForwardKind(R.string.forward_all, "21"),
    ForwardKind(R.string.forward_busy, "67"),
    ForwardKind(R.string.forward_unreachable, "62"),
    ForwardKind(R.string.forward_no_reply, "61"),
)

@Composable
internal fun CallForwardingPage(simLines: List<SimLine>, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = Bb10.dimens
    var slotState by rememberSlot(simLines)
    var busy by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    // A kind's number, empty when it is off; not there until known.
    val forwards = remember { mutableStateMapOf<String, String>() }
    val typed = remember { mutableStateMapOf<String, String>() }
    val forwardToHint = stringResource(R.string.forward_to)

    LaunchedEffect(slotState) {
        busy = true
        failed = false
        forwards.clear()
        for (kind in ForwardKinds) {
            val number = forwardedTo(SimCodes.run(context, slotState, "*#${kind.code}#"))
            if (number == null) {
                failed = true
                break
            }
            forwards[kind.code] = number
        }
        busy = false
    }

    NetworkPage(stringResource(R.string.setting_forwarding), simLines, slotState, { slotState = it }, busy, failed, onBack) {
        for (kind in ForwardKinds) {
            val current = forwards[kind.code]
            val number = typed[kind.code] ?: current.orEmpty()
            Bb10Toggle(
                !current.isNullOrEmpty(),
                { on ->
                    scope.launch {
                        busy = true
                        val code = if (on) "**${kind.code}*$number#" else "##${kind.code}#"
                        val reply = SimCodes.run(context, slotState, code)
                        val read = forwardedTo(SimCodes.run(context, slotState, "*#${kind.code}#"))
                        if (reply == null || read == null) failed = true else forwards[kind.code] = read
                        busy = false
                    }
                },
                label = stringResource(kind.label),
                enabled = !busy && current != null && (number.isNotBlank() || !current.isNullOrEmpty()),
            )
            SettingField(
                null,
                number,
                forwardToHint,
                { typed[kind.code] = it },
                enabled = !busy && current != null && current.isEmpty(),
            )
            Box(Modifier.padding(bottom = d.spaceS))
            Bb10Divider()
        }
    }
}

/** A page of switches, each one a service the network turns on and off by code. */
private class ServiceSwitch(val label: Int, val query: String, val on: String, val off: String)

@Composable
private fun ServiceSwitches(
    switch: ServiceSwitch,
    state: Boolean?,
    busy: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Bb10Toggle(state == true, onChange, label = stringResource(switch.label), enabled = !busy && state != null)
    Bb10Divider()
}

/** The states of [switches], read one after the other for the chosen SIM. */
@Composable
private fun rememberSwitchStates(
    switches: List<ServiceSwitch>,
    slot: Int?,
    read: (String?) -> Boolean?,
    onBusy: (Boolean) -> Unit,
    onFailed: (Boolean) -> Unit,
): Map<String, Boolean> {
    val context = LocalContext.current
    val states = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(slot) {
        onBusy(true)
        onFailed(false)
        states.clear()
        for (switch in switches) {
            val state = read(SimCodes.run(context, slot, switch.query))
            if (state == null) {
                onFailed(true)
                break
            }
            states[switch.query] = state
        }
        onBusy(false)
    }
    return states
}

@Composable
internal fun CallWaitingPage(simLines: List<SimLine>, onBack: () -> Unit) {
    val switch = ServiceSwitch(R.string.waiting_label, "*#43#", "*43#", "#43#")
    OneServicePage(R.string.setting_waiting, R.string.waiting_note, switch, SimCodes::isEnabled, simLines, onBack)
}

@Composable
internal fun ShowMyNumberPage(simLines: List<SimLine>, onBack: () -> Unit) {
    // The code's own sense is the opposite: *31# hides the number, #31# shows it.
    val switch = ServiceSwitch(R.string.show_number_label, "*#31#", "#31#", "*31#")
    OneServicePage(R.string.setting_show_number, null, switch, SimCodes::showsNumber, simLines, onBack)
}

@Composable
private fun OneServicePage(
    title: Int,
    note: Int?,
    switch: ServiceSwitch,
    read: (String?) -> Boolean?,
    simLines: List<SimLine>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var slot by rememberSlot(simLines)
    var busy by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var changed by remember { mutableStateOf<Boolean?>(null) }
    val states = rememberSwitchStates(listOf(switch), slot, read, { busy = it }, { failed = it })
    val state = changed ?: states[switch.query]
    NetworkPage(stringResource(title), simLines, slot, { slot = it; changed = null }, busy, failed, onBack) {
        ServiceSwitches(switch, state, busy) { on ->
            scope.launch {
                busy = true
                val reply = SimCodes.run(context, slot, if (on) switch.on else switch.off)
                val now = read(SimCodes.run(context, slot, switch.query))
                if (reply == null || now == null) failed = true else changed = now
                busy = false
            }
        }
        if (note != null) SettingNote(stringResource(note))
    }
}

private val IncomingBars = listOf(
    ServiceSwitch(R.string.block_all, "*#35#", "*35*%s#", "#35*%s#"),
    ServiceSwitch(R.string.block_roaming, "*#351#", "*351*%s#", "#351*%s#"),
)
private val OutgoingBars = listOf(
    ServiceSwitch(R.string.block_all, "*#33#", "*33*%s#", "#33*%s#"),
    ServiceSwitch(R.string.block_international, "*#331#", "*331*%s#", "#331*%s#"),
    ServiceSwitch(R.string.block_international_roaming, "*#332#", "*332*%s#", "#332*%s#"),
)

@Composable
internal fun CallBlockingPage(simLines: List<SimLine>, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var slot by rememberSlot(simLines)
    var busy by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val changed = remember { mutableStateMapOf<String, Boolean>() }
    val states = rememberSwitchStates(IncomingBars + OutgoingBars, slot, SimCodes::isEnabled, { busy = it }, { failed = it })
    // The network's password for this: asked each time, never kept.
    var pending by remember { mutableStateOf<Pair<ServiceSwitch, Boolean>?>(null) }
    var password by remember { mutableStateOf("") }
    var changingPassword by remember { mutableStateOf(false) }

    NetworkPage(
        stringResource(R.string.setting_blocking),
        simLines,
        slot,
        { slot = it; changed.clear() },
        busy,
        failed,
        onBack,
        action = PageBarAction(DialerIcons.Blocking, stringResource(R.string.block_change_password)) {
            changingPassword = true
        },
    ) {
        SectionHeader(stringResource(R.string.block_incoming))
        for (bar in IncomingBars) {
            ServiceSwitches(bar, changed[bar.query] ?: states[bar.query], busy) { pending = bar to it }
        }
        SectionHeader(stringResource(R.string.block_outgoing))
        for (bar in OutgoingBars) {
            ServiceSwitches(bar, changed[bar.query] ?: states[bar.query], busy) { pending = bar to it }
        }
    }

    pending?.let { (bar, on) ->
        Bb10InputDialog(
            title = stringResource(bar.label),
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.block_password),
            password = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            confirmEnabled = password.isNotEmpty(),
            onConfirm = {
                val code = (if (on) bar.on else bar.off).format(password)
                pending = null
                password = ""
                scope.launch {
                    busy = true
                    val reply = SimCodes.run(context, slot, code)
                    val now = SimCodes.isEnabled(SimCodes.run(context, slot, bar.query))
                    if (reply == null || now == null) failed = true else changed[bar.query] = now
                    busy = false
                }
            },
            onDismiss = {
                pending = null
                password = ""
            },
        )
    }
    if (changingPassword) {
        ChangePasswordDialog(
            onDone = { old, new ->
                changingPassword = false
                scope.launch {
                    busy = true
                    if (SimCodes.run(context, slot, "**03*330*$old*$new*$new#") == null) failed = true
                    busy = false
                }
            },
            onDismiss = { changingPassword = false },
        )
    }
}

@Composable
private fun ChangePasswordDialog(onDone: (String, String) -> Unit, onDismiss: () -> Unit) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    Bb10Dialog(
        title = stringResource(R.string.block_change_password),
        confirmText = stringResource(R.string.save),
        dismissText = stringResource(R.string.cancel),
        focusContent = true,
        confirmEnabled = old.isNotEmpty() && new.isNotEmpty(),
        onConfirm = { onDone(old, new) },
        onDismiss = onDismiss,
    ) {
        Column {
            SettingFieldPassword(stringResource(R.string.block_password_old), old) { old = it }
            SettingFieldPassword(stringResource(R.string.block_password_new), new) { new = it }
        }
    }
}

@Composable
private fun SettingFieldPassword(label: String, value: String, onChange: (String) -> Unit) {
    Bb10TextField(
        value = value,
        onValueChange = onChange,
        label = label,
        password = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
}
