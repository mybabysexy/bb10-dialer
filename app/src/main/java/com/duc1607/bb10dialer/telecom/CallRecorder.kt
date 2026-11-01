package com.duc1607.bb10dialer.telecom

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import com.duc1607.bb10dialer.data.CallEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

private const val MIN_FREE_BYTES = 50L * 1024 * 1024
private const val FILE_EXTENSION = "ogg"
private const val SEPARATOR = "_"

/** One saved recording of a call. */
data class Recording(val file: File, val number: String, val startedAtMillis: Long)

/** The recordings of one number, sorted out by the call they were made in. */
data class CallRecordings(val byCall: Map<Long, List<Recording>>, val unmatched: List<Recording>)

/**
 * Hands each recording to the call it was made in: the newest call that began before it did.
 * [calls] are one number's, newest first. A recording older than them all has lost its call, which
 * the log dropped or the user cleared: it is still the user's, so it stays [CallRecordings.unmatched].
 */
fun matchRecordings(calls: List<CallEntry>, recordings: List<Recording>): CallRecordings {
    val (matched, unmatched) = recordings
        .map { recording -> recording to calls.firstOrNull { it.dateMillis <= recording.startedAtMillis } }
        .partition { it.second != null }
    return CallRecordings(
        matched.groupBy({ it.second!!.id }, { it.first }),
        unmatched.map { it.first },
    )
}

/**
 * Records a call to the app's private storage. A normal app cannot open the phone line itself on
 * this Android, so each source is tried from best to worst; what the last ones pick up is the
 * microphone. Ogg, because a file cut short by a crash still plays.
 */
class CallRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    /** False when there is no room, or no source would open. */
    fun start(number: String): Boolean {
        stop()
        val dir = recordingsDir(context)
        if (dir.usableSpace < MIN_FREE_BYTES) return false
        val target = File(dir, "${number.filter { it.isDigit() || it == '+' }}$SEPARATOR${System.currentTimeMillis()}.$FILE_EXTENSION")
        for (source in SOURCES) {
            val attempt = MediaRecorder(context)
            try {
                attempt.setAudioSource(source)
                attempt.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                attempt.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                attempt.setAudioEncodingBitRate(BIT_RATE)
                attempt.setAudioSamplingRate(SAMPLE_RATE)
                attempt.setOutputFile(target)
                attempt.prepare()
                attempt.start()
                recorder = attempt
                file = target
                return true
            } catch (_: Exception) {
                attempt.release()
                target.delete()
            }
        }
        return false
    }

    fun stop() {
        val active = recorder ?: return
        recorder = null
        // Fails when it stopped before any sound came in: then there is nothing to keep.
        val kept = runCatching { active.stop() }.isSuccess
        active.release()
        if (!kept) file?.delete()
        file = null
        reload(context)
    }

    companion object {
        private const val BIT_RATE = 32000
        private const val SAMPLE_RATE = 16000
        private val SOURCES = listOf(
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
        )

        private fun recordingsDir(context: Context) = File(context.filesDir, "recordings").apply { mkdirs() }

        private var flow: MutableStateFlow<List<Recording>>? = null

        /** All the recordings there are, for the marks in the call list. */
        fun all(context: Context): StateFlow<List<Recording>> {
            val current = flow ?: MutableStateFlow(readAll(context)).also { flow = it }
            return current.asStateFlow()
        }

        fun delete(context: Context, recording: Recording) {
            recording.file.delete()
            reload(context)
        }

        private fun reload(context: Context) {
            all(context)
            flow!!.value = readAll(context)
        }

        private fun readAll(context: Context) = recordingsDir(context).listFiles().orEmpty().mapNotNull(::parse)

        /** Off the main thread. 0 when the file does not say. */
        fun durationOf(recording: Recording): Long = runCatching {
            MediaMetadataRetriever().use {
                it.setDataSource(recording.file.path)
                it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            }
        }.getOrNull() ?: 0L

        private fun parse(file: File): Recording? {
            val name = file.nameWithoutExtension
            val at = name.substringAfterLast(SEPARATOR, "").toLongOrNull() ?: return null
            return Recording(file, name.substringBeforeLast(SEPARATOR), at)
        }
    }
}
