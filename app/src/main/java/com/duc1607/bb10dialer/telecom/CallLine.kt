package com.duc1607.bb10dialer.telecom

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.duc1607.bb10dialer.data.ContactPhoto

/** Who owns a number, read directly: the contact cache may not exist when a call wakes the app. */
data class Caller(
    val name: String,
    val label: String?,
    val photo: ContactPhoto?,
    val ringtone: Uri?,
)

/** Null when nobody saved has this number, or contacts cannot be read (no permission, not unlocked yet). */
fun lookUpCaller(context: Context, number: String): Caller? = runCatching {
    if (number.isEmpty()) return null
    val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    context.contentResolver.query(
        uri,
        arrayOf(
            PhoneLookup.DISPLAY_NAME, PhoneLookup.TYPE, PhoneLookup.LABEL, PhoneLookup.PHOTO_ID,
            PhoneLookup.PHOTO_URI, PhoneLookup.CUSTOM_RINGTONE,
        ),
        null,
        null,
        null,
    )?.use { c ->
        if (!c.moveToFirst()) return null
        val label = if (c.isNull(1)) null else Phone.getTypeLabel(context.resources, c.getInt(1), c.getString(2).orEmpty()).toString()
        Caller(
            name = c.getString(0).orEmpty(),
            label = label,
            photo = c.getString(4)?.let { ContactPhoto(it, c.getLong(3)) },
            ringtone = c.getString(5)?.let(Uri::parse),
        )
    }
}.getOrNull()

/**
 * Calls [number], on [account] when there is a choice. Without the phone role Telecom shows its own
 * call screen. False when the permission is missing. Keeps to the number alone for an emergency:
 * no SIM to choose.
 */
fun placeCall(context: Context, number: String, account: PhoneAccountHandle?): Boolean {
    val telecom = context.getSystemService(TelecomManager::class.java)
    val telephony = context.getSystemService(TelephonyManager::class.java)
    val emergency = runCatching { telephony.isEmergencyNumber(number) }.getOrDefault(false)
    val extras = Bundle()
    if (account != null && !emergency) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
    return runCatching { telecom.placeCall(Uri.fromParts("tel", number, null), extras) }.isSuccess
}

/** The SIM slots' accounts, by slot counted from 1. */
fun simAccount(context: Context, slot: Int): PhoneAccountHandle? = runCatching {
    val subscriptions = context.getSystemService(SubscriptionManager::class.java)
    val telecom = context.getSystemService(TelecomManager::class.java)
    val telephony = context.getSystemService(TelephonyManager::class.java)
    val sub = subscriptions.getActiveSubscriptionInfoForSimSlotIndex(slot - 1)?.subscriptionId ?: return null
    telecom.callCapablePhoneAccounts.firstOrNull { telephony.createForPhoneAccountHandle(it)?.subscriptionId == sub }
}.getOrNull()

/** The slot of an account, counted from 1. */
fun simSlot(context: Context, account: PhoneAccountHandle?): Int? = runCatching {
    if (account == null) return null
    val telephony = context.getSystemService(TelephonyManager::class.java)
    val sub = telephony.createForPhoneAccountHandle(account)?.subscriptionId ?: return null
    context.getSystemService(SubscriptionManager::class.java).getActiveSubscriptionInfo(sub)?.simSlotIndex?.plus(1)
}.getOrNull()

/** The user's own number on an account; blank on many SIMs, which do not know it. */
fun ownNumber(context: Context, account: PhoneAccountHandle?): String? = runCatching {
    if (account == null) return null
    val telephony = context.getSystemService(TelephonyManager::class.java)
    val sub = telephony.createForPhoneAccountHandle(account)?.subscriptionId ?: return null
    context.getSystemService(SubscriptionManager::class.java).getPhoneNumber(sub).takeIf { it.isNotEmpty() }
}.getOrNull()
