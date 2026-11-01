package com.duc1607.bb10dialer.telecom

import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TIMEOUT_MILLIS = 30_000L
private val NumberInReply = Regex("\\+?\\d[\\d ]{2,}\\d")

/**
 * Settings kept by the network, not the phone: Android gives an app no call for them, so they go
 * out as the standard codes (`*#21#`, `**21*number#`), which is what typing them would do. The
 * replies are sentences in the phone's language, told apart here with the system's own wording.
 */
object SimCodes {
    /** The network's reply, or null when it gave none: not supported, no service, or a timeout. */
    suspend fun run(context: Context, slot: Int?, code: String): String? = withTimeoutOrNull(TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { continuation ->
            val base = context.getSystemService(TelephonyManager::class.java)
            val telephony = slot?.let { simAccount(context, it) }?.let { base.createForPhoneAccountHandle(it) } ?: base
            val callback = object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(t: TelephonyManager, request: String, response: CharSequence) {
                    if (continuation.isActive) continuation.resume(response.toString())
                }

                override fun onReceiveUssdResponseFailed(t: TelephonyManager, request: String, failure: Int) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
            runCatching { telephony.sendUssdRequest(code, callback, Handler(Looper.getMainLooper())) }
                .onFailure { if (continuation.isActive) continuation.resume(null) }
        }
    }

    /** Whether a service is on, by the reply to its query; null when it cannot be told. */
    fun isEnabled(reply: String?): Boolean? = enabledIn(
        reply,
        on = listOf(systemText("serviceEnabled"), systemText("serviceEnabledFor")),
        off = listOf(systemText("serviceDisabled")),
    )

    /** Whether the user's number shows; null when it cannot be told. */
    fun showsNumber(reply: String?): Boolean? = enabledIn(
        reply,
        on = listOf(systemText("CLIRDefaultOffNextCallOn"), systemText("CLIRDefaultOffNextCallOff")),
        off = listOf(systemText("CLIRDefaultOnNextCallOn"), systemText("CLIRDefaultOnNextCallOff")),
    )

    private fun systemText(name: String): String {
        val system = Resources.getSystem()
        val id = system.getIdentifier(name, "string", "android")
        return if (id == 0) "" else system.getString(id)
    }
}

/** See [SimCodes.isEnabled]. The wording goes in as parameters, the system holds the real ones. */
internal fun enabledIn(reply: String?, on: List<String>, off: List<String>): Boolean? {
    val text = reply?.trim().orEmpty()
    return when {
        text.isEmpty() -> null
        off.any { it.isNotBlank() && text.startsWith(it.trim()) } -> false
        on.any { it.isNotBlank() && text.startsWith(it.trim()) } -> true
        else -> null
    }
}

/**
 * The number voice calls are forwarded to, from the reply to a forwarding query. Its first line is
 * about voice ("Voice: +84912345678" or "Voice: Not forwarded"). Empty means not forwarded, null
 * that there was no reply to read.
 */
internal fun forwardedTo(reply: String?): String? {
    val line = reply?.trim()?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
    return NumberInReply.find(line)?.value?.replace(" ", "").orEmpty()
}
