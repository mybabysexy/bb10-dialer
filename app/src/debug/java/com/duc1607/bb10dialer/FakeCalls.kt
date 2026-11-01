package com.duc1607.bb10dialer

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import kotlin.concurrent.thread

private const val BUSY_NUMBER = "111"
private const val UNREACHABLE_NUMBER = "222"
private const val POST_DIAL_NUMBER = "333"
private const val DELAY_MILLIS = 1500L
private const val SEED_COUNT = 1000
private const val SEED_NUMBERS = 37
private const val SEED_STEP_MILLIS = 47 * 60 * 1000L

/**
 * Calls that never leave the emulator, for what its modem cannot do: a second incoming call, a busy
 * line, an unreachable one. Debug builds only. Set up with
 * `adb shell am broadcast -n <pkg>/.FakeCallReceiver -a fake.REGISTER`, then
 * `adb shell telecom set-phone-account-enabled <pkg>/.FakeCallService fake 0`.
 */
class FakeCallService : ConnectionService() {
    override fun onCreateOutgoingConnection(account: PhoneAccountHandle?, request: ConnectionRequest): Connection {
        val number = request.address?.schemeSpecificPart.orEmpty()
        return call(request).apply {
            setDialing()
            Handler(Looper.getMainLooper()).postDelayed({
                when (number) {
                    BUSY_NUMBER -> end(DisconnectCause.BUSY)
                    UNREACHABLE_NUMBER -> end(DisconnectCause.ERROR)
                    POST_DIAL_NUMBER -> {
                        setActive()
                        setPostDialWait("1234")
                    }
                    else -> setActive()
                }
            }, DELAY_MILLIS)
        }
    }

    override fun onCreateIncomingConnection(account: PhoneAccountHandle?, request: ConnectionRequest): Connection =
        call(request).apply { setRinging() }

    private fun call(request: ConnectionRequest) = FakeCall().apply {
        setAddress(request.address, TelecomManager.PRESENTATION_ALLOWED)
        connectionCapabilities = Connection.CAPABILITY_HOLD or Connection.CAPABILITY_SUPPORT_HOLD or
            Connection.CAPABILITY_MUTE or Connection.CAPABILITY_RESPOND_VIA_TEXT
        audioModeIsVoip = false
    }

    private class FakeCall : Connection() {
        fun end(cause: Int) {
            setDisconnected(DisconnectCause(cause))
            destroy()
        }
        override fun onAnswer() = setActive()
        override fun onReject() = end(DisconnectCause.REJECTED)
        override fun onReject(replyMessage: String?) = end(DisconnectCause.REJECTED)
        override fun onDisconnect() = end(DisconnectCause.LOCAL)
        override fun onAbort() = end(DisconnectCause.CANCELED)
        override fun onHold() = setOnHold()
        override fun onUnhold() = setActive()
    }
}

class FakeCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val telecom = context.getSystemService(TelecomManager::class.java)
        val handle = PhoneAccountHandle(ComponentName(context, FakeCallService::class.java), "fake")
        val number = Uri.fromParts("tel", intent.getStringExtra("number").orEmpty(), null)
        when (intent.action) {
            "fake.REGISTER" -> telecom.registerPhoneAccount(
                PhoneAccount.builder(handle, "Fake")
                    .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                    .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                    .build(),
            )
            "fake.INCOMING" -> telecom.addNewIncomingCall(
                handle,
                Bundle().apply { putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, number) },
            )
            // A long history, on an emulator: scrolling and reloading get measured against it.
            "fake.SEED" -> {
                val now = System.currentTimeMillis()
                val rows = Array(intent.getIntExtra("count", SEED_COUNT)) { i ->
                    ContentValues().apply {
                        put(CallLog.Calls.NUMBER, "09${(10000000 + i % SEED_NUMBERS * 7919)}")
                        put(CallLog.Calls.DATE, now - i * SEED_STEP_MILLIS)
                        put(CallLog.Calls.DURATION, i % 300)
                        put(CallLog.Calls.TYPE, 1 + i % 3)
                    }
                }
                val pending = goAsync()
                thread {
                    context.contentResolver.bulkInsert(CallLog.Calls.CONTENT_URI, rows)
                    pending.finish()
                }
            }
            "fake.OUTGOING" -> telecom.placeCall(
                number,
                Bundle().apply { putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle) },
            )
        }
    }
}
