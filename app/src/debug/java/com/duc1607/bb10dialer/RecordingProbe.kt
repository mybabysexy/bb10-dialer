package com.duc1607.bb10dialer

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.abs

/**
 * Tries each audio source and logs whether it opens and how loud it is. Run it during a live call
 * to see which sources this phone gives an app: adb logcat -s RecordingProbe.
 */
object RecordingProbe {
    private const val TAG = "RecordingProbe"
    private const val RATE = 16000
    private const val SECONDS = 2

    private val SOURCES = listOf(
        "VOICE_CALL" to MediaRecorder.AudioSource.VOICE_CALL,
        "VOICE_DOWNLINK" to MediaRecorder.AudioSource.VOICE_DOWNLINK,
        "VOICE_UPLINK" to MediaRecorder.AudioSource.VOICE_UPLINK,
        "VOICE_COMMUNICATION" to MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        "VOICE_RECOGNITION" to MediaRecorder.AudioSource.VOICE_RECOGNITION,
        "MIC" to MediaRecorder.AudioSource.MIC,
    )

    @SuppressLint("MissingPermission")
    fun run() {
        Thread {
            val size = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 4
            for ((name, source) in SOURCES) {
                Log.i(TAG, "$name: ${probe(source, size)}")
            }
            Log.i(TAG, "done")
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun probe(source: Int, size: Int): String {
        val record = try {
            AudioRecord(source, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
        } catch (e: Exception) {
            return "constructor failed: ${e.javaClass.simpleName} ${e.message}"
        }
        try {
            if (record.state != AudioRecord.STATE_INITIALIZED) return "not initialized"
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) return "did not start"
            val buffer = ShortArray(size / 2)
            var left = RATE * SECONDS
            var peak = 0
            var total = 0
            while (left > 0) {
                val read = record.read(buffer, 0, minOf(buffer.size, left))
                if (read <= 0) return "read error $read"
                for (i in 0 until read) peak = maxOf(peak, abs(buffer[i].toInt()))
                left -= read
                total += read
            }
            record.stop()
            return "ok, $total samples, peak $peak, silenced=${record.activeRecordingConfiguration?.isClientSilenced}"
        } catch (e: Exception) {
            return "failed: ${e.javaClass.simpleName} ${e.message}"
        } finally {
            record.release()
        }
    }
}
