package com.duc1607.bb10dialer.telecom

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import com.duc1607.bb10dialer.data.PhoneSettingsStore
import com.duc1607.bb10dialer.data.ringtoneFor

private const val WAITING_TONE_EVERY_MILLIS = 4000L
private const val WAITING_TONE_MILLIS = 300
private const val WAITING_TONE_VOLUME = 60
private val VIBRATION = longArrayOf(0, 1000, 1000)

// "Vibrate first then ring gradually", with the system's own timings: it vibrates for this long,
// then the ringtone starts silent and grows to full volume over the ramp.
private const val RAMPING_RINGER_SETTING = "apply_ramping_ringer"
private const val RAMP_VIBRATION_MILLIS = 5000L
private const val RAMP_MILLIS = 10000L
private const val RAMP_STEP_MILLIS = 250L

/**
 * Whether the system's Vibration & haptics setting asks for a ring that vibrates first then grows
 * louder. Hidden from the API, so read by its name; off when it cannot be read.
 */
fun isRampingRinger(context: Context): Boolean =
    runCatching { Settings.System.getInt(context.contentResolver, RAMPING_RINGER_SETTING, 0) == 1 }.getOrDefault(false)

/** A short buzz when a call connects ([connected]), two when it ends. */
fun buzzForCall(context: Context, connected: Boolean) {
    val pattern = if (connected) longArrayOf(0, 120) else longArrayOf(0, 90, 120, 90)
    runCatching {
        context.getSystemService(Vibrator::class.java).vibrate(
            VibrationEffect.createWaveform(pattern, -1),
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION),
        )
    }
}

/**
 * Rings for a call. As the phone's ringing app this one has to do it itself: Telecom stays silent.
 * Follows the ringer mode, Do Not Disturb by way of [NotificationManager.matchesCallFilter], and
 * the system's "vibrate first then ring gradually" ([isRampingRinger]).
 */
class CallRinger(private val context: Context) {
    private var ringtone: Ringtone? = null
    private var vibrating = false
    private var tone: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val vibrator = context.getSystemService(Vibrator::class.java)

    /** [custom] is the caller's own ringtone; [slot] the SIM the call came in on. */
    fun ring(number: String, custom: Uri?, slot: Int?) {
        stop()
        val settings = PhoneSettingsStore.get(context)
        if (!settings.allNotifications) return
        val notifications = context.getSystemService(NotificationManager::class.java)
        val allowed = runCatching { notifications.matchesCallFilter(Uri.fromParts("tel", number, null)) }.getOrDefault(true)
        if (!allowed) return
        val mode = context.getSystemService(AudioManager::class.java).ringerMode
        val chosen = custom ?: ringtoneFor(settings, slot)?.let(Uri::parse)
        val usage = when {
            settings.sound && settings.overrideVolume -> AudioAttributes.USAGE_ALARM
            settings.sound && mode == AudioManager.RINGER_MODE_NORMAL -> AudioAttributes.USAGE_NOTIFICATION_RINGTONE
            else -> null
        }
        // Vibrating first is the point of the system setting: it does so even with Vibrate off here.
        val ramp = usage != null && isRampingRinger(context)
        if (usage != null && ramp) {
            handler.postDelayed({ startRingtone(chosen, usage, ramp = true) }, RAMP_VIBRATION_MILLIS)
        } else if (usage != null) {
            startRingtone(chosen, usage)
        }
        val vibrate = settings.vibrate || ramp
        if (mode == AudioManager.RINGER_MODE_VIBRATE || (mode == AudioManager.RINGER_MODE_NORMAL && vibrate)) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(VIBRATION, 0),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_RINGTONE),
            )
            vibrating = true
        }
    }

    /** A soft beep in the ear for a second call: no ringing during a conversation. */
    fun beepForWaiting() {
        stop()
        tone = runCatching { ToneGenerator(AudioManager.STREAM_VOICE_CALL, WAITING_TONE_VOLUME) }.getOrNull()
        val beep = object : Runnable {
            override fun run() {
                tone?.startTone(ToneGenerator.TONE_SUP_CALL_WAITING, WAITING_TONE_MILLIS)
                handler.postDelayed(this, WAITING_TONE_EVERY_MILLIS)
            }
        }
        handler.post(beep)
    }

    fun stop() {
        ringtone?.stop()
        ringtone = null
        if (vibrating) vibrator.cancel()
        vibrating = false
        handler.removeCallbacksAndMessages(null)
        tone?.release()
        tone = null
    }

    /** With [ramp] it starts silent and grows to full volume over [RAMP_MILLIS]. */
    private fun startRingtone(custom: Uri?, usage: Int, ramp: Boolean = false) {
        val uri = custom ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE) ?: return
        ringtone = runCatching {
            RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                isLooping = true
                if (ramp) volume = 0f
                play()
            }
        }.getOrNull()
        if (ramp) rampUp(System.currentTimeMillis())
    }

    private fun rampUp(startedAt: Long) {
        val step = object : Runnable {
            override fun run() {
                val playing = ringtone ?: return
                val level = ((System.currentTimeMillis() - startedAt).toFloat() / RAMP_MILLIS).coerceIn(0f, 1f)
                playing.volume = level
                if (level < 1f) handler.postDelayed(this, RAMP_STEP_MILLIS)
            }
        }
        handler.post(step)
    }
}
