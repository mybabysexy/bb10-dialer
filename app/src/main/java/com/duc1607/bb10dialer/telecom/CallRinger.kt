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
import com.duc1607.bb10dialer.data.PhoneSettingsStore
import com.duc1607.bb10dialer.data.ringtoneFor

private const val WAITING_TONE_EVERY_MILLIS = 4000L
private const val WAITING_TONE_MILLIS = 300
private const val WAITING_TONE_VOLUME = 60
private val VIBRATION = longArrayOf(0, 1000, 1000)

/**
 * Rings for a call. As the phone's ringing app this one has to do it itself: Telecom stays silent.
 * Follows the ringer mode, and Do Not Disturb by way of [NotificationManager.matchesCallFilter].
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
        if (settings.sound && settings.overrideVolume) {
            startRingtone(chosen, AudioAttributes.USAGE_ALARM)
        } else if (settings.sound && mode == AudioManager.RINGER_MODE_NORMAL) {
            startRingtone(chosen, AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        }
        if (mode == AudioManager.RINGER_MODE_VIBRATE || (mode == AudioManager.RINGER_MODE_NORMAL && settings.vibrate)) {
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

    private fun startRingtone(custom: Uri?, usage: Int) {
        val uri = custom ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE) ?: return
        ringtone = runCatching {
            RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                isLooping = true
                play()
            }
        }.getOrNull()
    }
}
