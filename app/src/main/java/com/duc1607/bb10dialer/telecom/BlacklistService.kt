package com.duc1607.bb10dialer.telecom

import android.content.Context
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import com.duc1607.bb10dialer.data.NumberForms
import com.duc1607.bb10dialer.data.PhoneSettingsStore
import com.duc1607.bb10dialer.data.isBlacklisted

private const val MAX_COUNTRY_CODE_DIGITS = 3

/**
 * Telecom asks the default phone app about each call before it rings. A call on the Blacklist is
 * turned away without ringing or a notification, and stays in the call log.
 */
class BlacklistService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val entries = PhoneSettingsStore.get(this).blacklist
        val refused = details.callDirection == Call.Details.DIRECTION_INCOMING &&
            number.isNotEmpty() && entries.isNotEmpty() &&
            isBlacklisted(numberForms(this, number), entries)
        val response = CallResponse.Builder()
        if (refused) {
            response
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
        }
        respondToCall(details, response.build())
    }
}

/** Android has no public way to split a country code off, so each length is tried against the SIM's country. */
private fun numberForms(context: Context, number: String): NumberForms {
    val digits = number.filter { it.isDigit() }
    val telephony = context.getSystemService(TelephonyManager::class.java)
    val country = runCatching { telephony.simCountryIso.ifEmpty { telephony.networkCountryIso } }.getOrNull().orEmpty().uppercase()
    fun e164(candidate: String) = runCatching { PhoneNumberUtils.formatNumberToE164(candidate, country) }.getOrNull()
    if (number.startsWith("+")) {
        val national = (1..MAX_COUNTRY_CODE_DIGITS).firstNotNullOfOrNull { length ->
            digits.drop(length).takeIf { rest -> e164("0$rest") == number || e164(rest) == number }
        }
        return NumberForms(digits, national, digits)
    }
    return NumberForms(digits, digits.trimStart('0'), e164(number)?.filter { it.isDigit() })
}
