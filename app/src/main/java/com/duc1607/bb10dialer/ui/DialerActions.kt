package com.duc1607.bb10dialer.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.PhoneSettingsStore
import com.duc1607.bb10dialer.data.smartDialed
import com.duc1607.bb10dialer.telecom.dialerSpecialCode
import com.duc1607.bb10dialer.telecom.isMmi
import com.duc1607.bb10dialer.telecom.placeCall
import com.duc1607.bb10dialer.telecom.simAccount

/** What the screens ask of the outside world. */
@Stable
class DialerActions(private val context: Context) {

    /** [sim] is the slot to call from, when the user chose one. Codes go to the phone, not out. */
    fun call(number: String, sim: Int? = null) {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val telecom = context.getSystemService(TelecomManager::class.java)
        val account = sim?.let { simAccount(context, it) }
        val emergency = runCatching { telephony.isEmergencyNumber(number) }.getOrDefault(false)
        if (!emergency) {
            val secret = dialerSpecialCode(number)
            if (secret != null && runCatching { telephony.sendDialerSpecialCode(secret) }.isSuccess) return
            if (isMmi(number)) {
                val handled = runCatching {
                    if (account != null) telecom.handleMmi(number, account) else telecom.handleMmi(number)
                }.getOrDefault(false)
                if (handled) return
            }
        }
        val dialed = if (emergency) number else smartDialed(number, PhoneSettingsStore.get(context))
        if (!placeCall(context, dialed, account)) toast(context.getString(R.string.call_permission))
    }

    /** The access number from Phone Settings, or the one the SIM holds. */
    fun voicemail() {
        val mine = PhoneSettingsStore.get(context).voicemailNumber
        val telecom = context.getSystemService(TelecomManager::class.java)
        val uri = if (mine.isNotEmpty()) Uri.fromParts("tel", mine, null) else Uri.fromParts("voicemail", "", null)
        runCatching { telecom.placeCall(uri, Bundle()) }
            .onFailure { toast(context.getString(R.string.call_permission)) }
    }

    /** Hands over to the default SMS app. */
    fun sms(number: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast(context.getString(R.string.no_sms_app))
        }
    }

    /** The phone's own settings for calls: forwarding, waiting, caller ID, and so on. */
    fun callSettings() {
        runCatching { context.startActivity(Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS)) }
    }

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

@Composable
fun rememberDialerActions(): DialerActions {
    val context = LocalContext.current
    return remember(context) { DialerActions(context) }
}
