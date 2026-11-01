package com.duc1607.bb10dialer.ui.calls

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.CallEntry
import com.duc1607.bb10dialer.data.CallKind
import com.duc1607.bb10dialer.ui.theme.DialerColors
import com.duc1607.bb10dialer.ui.theme.DialerIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

fun CallKind.icon(): ImageVector = when (this) {
    CallKind.Outgoing -> DialerIcons.Outgoing
    CallKind.Incoming, CallKind.Voicemail -> DialerIcons.Incoming
    CallKind.Missed, CallKind.Rejected, CallKind.Blocked -> DialerIcons.Missed
}

fun CallKind.tint(): Color = when (this) {
    CallKind.Outgoing -> DialerColors.OutgoingGreen
    CallKind.Incoming, CallKind.Voicemail -> DialerColors.IncomingBlue
    CallKind.Missed, CallKind.Rejected, CallKind.Blocked -> DialerColors.MissedRed
}

/** "Saturday, October 31, 2026", in the device's language and order. */
fun dayFormatter(): DateTimeFormatter {
    val locale = Locale.getDefault()
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEMMMMdyyyy"), locale)
}

fun DateTimeFormatter.title(date: LocalDate): String = format(date)

/** "00:05", or "1:02:05" past an hour. */
fun playbackTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    val minutes = (seconds % 3600 / 60).toString().padStart(2, '0')
    val rest = (seconds % 60).toString().padStart(2, '0')
    return if (seconds >= 3600) "${seconds / 3600}:$minutes:$rest" else "$minutes:$rest"
}

/** "9:48 PM" or "21:48", following the device's 12/24 hour setting. */
fun Context.callTime(millis: Long): String = DateFormat.getTimeFormat(this).format(Date(millis))

/** The history line of a call: how long it took, or how it ended without being answered. */
fun Context.callOutcome(call: CallEntry): String = when (call.kind) {
    CallKind.Missed -> getString(R.string.call_missed)
    CallKind.Rejected -> getString(R.string.call_rejected)
    CallKind.Blocked -> getString(R.string.call_blocked)
    CallKind.Voicemail -> getString(R.string.call_voicemail)
    CallKind.Outgoing, CallKind.Incoming ->
        if (call.durationSeconds <= 0) getString(R.string.call_canceled) else duration(call.durationSeconds)
}

fun Context.callDuration(seconds: Long): String = duration(seconds)

private fun Context.duration(seconds: Long): String = when {
    seconds < 60 -> getString(R.string.call_duration_seconds, seconds)
    seconds < 3600 -> getString(R.string.call_duration_minutes, seconds / 60, seconds % 60)
    else -> getString(R.string.call_duration_hours, seconds / 3600, seconds % 3600 / 60)
}
