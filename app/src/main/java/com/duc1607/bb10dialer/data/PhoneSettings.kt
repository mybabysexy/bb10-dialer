package com.duc1607.bb10dialer.data

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val REPLY_COUNT = 3
val LED_COLORS = listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFFFF.toInt())

enum class ThemeMode { Light, Dark, System }

/** What the user chose in Phone Settings. What the network keeps is not here: see `SimCodes`. */
@Immutable
data class PhoneSettings(
    val allNotifications: Boolean = true,
    val sound: Boolean = true,
    /** Null for the phone's own ringtone. */
    val ringtone: String? = null,
    /** Null to ring as the first SIM does. */
    val ringtoneSim2: String? = null,
    val overrideVolume: Boolean = false,
    val vibrate: Boolean = false,
    val led: Boolean = true,
    val ledColor: Int = 0,
    val silenceByTap: Boolean = false,
    val voicemailNumber: String = "",
    val autoAnswerBluetooth: Boolean = false,
    val replyNow: Boolean = true,
    /** Blank for the built-in message of that place. */
    val replies: List<String> = List(REPLY_COUNT) { "" },
    val areaCode: String = "",
    val nationalLength: Int = 0,
    val extensionAccess: String = "",
    val pauseSeconds: Int = 3,
    val extensionLength: Int = 0,
    /** Call Summary counts from here. */
    val summaryFromMillis: Long = 0,
    val proximityLock: Boolean = false,
    val phoneModel: PhoneModel = PhoneModel.Generic,
    /** Whole numbers and prefixes, all treated as prefixes: see [isBlacklisted]. */
    val blacklist: List<String> = emptyList(),
    val showAnswerSlider: Boolean = true,
    val showEndButton: Boolean = true,
    /** Off: quick contacts and the call list leave out the numbers of saved contacts. */
    val showNumbers: Boolean = true,
    /** The Missed tab was last opened with the missed call of this time at the top. */
    val missedSeenMillis: Long = 0,
    /** What the user chose; recording also takes root: see [canRecordCalls]. */
    val callRecording: Boolean = false,
    /** Start recording as soon as a call connects. */
    val autoRecord: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.Light,
    /** Which of the kit's accent colours. */
    val accentIndex: Int = 0,
    /** `su` worked the last time the app asked. Only then does it ask again unprompted. */
    val rootGranted: Boolean = false,
) {
    /** Recording is for phones with root. Without this the call screen has no Record action. */
    val canRecordCalls: Boolean get() = callRecording && rootGranted
}

/** The ringtone of a call on SIM [slot], from the settings; null for the phone's own. */
fun ringtoneFor(settings: PhoneSettings, slot: Int?): String? =
    if (slot == 2) settings.ringtoneSim2 ?: settings.ringtone else settings.ringtone

/** How a typed number goes out with Smart Dialing: short ones get what the user set up for them. */
fun smartDialed(number: String, settings: PhoneSettings): String {
    if (number.any { !it.isDigit() }) return number
    val access = settings.extensionAccess
    if (access.isNotEmpty() && settings.extensionLength > 0 && number.length == settings.extensionLength) {
        val pauses = ",".repeat((settings.pauseSeconds + PAUSE_SECONDS - 1) / PAUSE_SECONDS)
        return "$access$pauses$number"
    }
    val area = settings.areaCode
    if (area.isNotEmpty() && settings.nationalLength > 0 && number.length + area.length == settings.nationalLength) {
        return area + number
    }
    return number
}

/** What one "," waits for in a dialed number. */
private const val PAUSE_SECONDS = 2

/**
 * Kept in device protected storage: a call before the first unlock after a restart reads these too.
 */
object PhoneSettingsStore {
    private const val FILE = "phone_settings"
    private var flow: MutableStateFlow<PhoneSettings>? = null

    fun state(context: Context): StateFlow<PhoneSettings> {
        val current = flow ?: MutableStateFlow(read(context)).also { flow = it }
        return current.asStateFlow()
    }

    fun get(context: Context): PhoneSettings = state(context).value

    fun update(context: Context, change: (PhoneSettings) -> PhoneSettings) {
        state(context)
        val next = change(flow!!.value)
        flow!!.value = next
        write(context, next)
    }

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun read(context: Context): PhoneSettings {
        val p = prefs(context)
        val d = PhoneSettings()
        return PhoneSettings(
            allNotifications = p.getBoolean("all", d.allNotifications),
            sound = p.getBoolean("sound", d.sound),
            ringtone = p.getString("ringtone", d.ringtone),
            ringtoneSim2 = p.getString("ringtone_sim2", d.ringtoneSim2),
            overrideVolume = p.getBoolean("override", d.overrideVolume),
            vibrate = p.getBoolean("vibrate", d.vibrate),
            led = p.getBoolean("led", d.led),
            ledColor = p.getInt("led_color", d.ledColor),
            silenceByTap = p.getBoolean("silence_tap", d.silenceByTap),
            voicemailNumber = p.getString("voicemail", d.voicemailNumber).orEmpty(),
            autoAnswerBluetooth = p.getBoolean("bt_answer", d.autoAnswerBluetooth),
            replyNow = p.getBoolean("reply_now", d.replyNow),
            replies = List(REPLY_COUNT) { p.getString("reply_$it", "").orEmpty() },
            areaCode = p.getString("area", d.areaCode).orEmpty(),
            nationalLength = p.getInt("national", d.nationalLength),
            extensionAccess = p.getString("ext_access", d.extensionAccess).orEmpty(),
            pauseSeconds = p.getInt("pause", d.pauseSeconds),
            extensionLength = p.getInt("ext_length", d.extensionLength),
            summaryFromMillis = p.getLong("summary_from", d.summaryFromMillis),
            proximityLock = p.getBoolean("proximity", d.proximityLock),
            phoneModel = PhoneModel.entries.firstOrNull { it.name == p.getString("phone_model", null) } ?: d.phoneModel,
            blacklist = p.getString("blacklist", "").orEmpty().split('\n').filter { it.isNotEmpty() },
            showAnswerSlider = p.getBoolean("show_slider", d.showAnswerSlider),
            showEndButton = p.getBoolean("show_end", d.showEndButton),
            showNumbers = p.getBoolean("show_numbers", d.showNumbers),
            missedSeenMillis = p.getLong("missed_seen", d.missedSeenMillis),
            callRecording = p.getBoolean("recording_allowed", d.callRecording),
            autoRecord = p.getBoolean("auto_record", d.autoRecord),
            themeMode = ThemeMode.entries.firstOrNull { it.name == p.getString("theme_mode", null) } ?: d.themeMode,
            accentIndex = p.getInt("accent", d.accentIndex),
            rootGranted = p.getBoolean("root_granted", d.rootGranted),
        )
    }

    private fun write(context: Context, s: PhoneSettings) {
        prefs(context).edit().apply {
            putBoolean("all", s.allNotifications)
            putBoolean("sound", s.sound)
            putString("ringtone", s.ringtone)
            putString("ringtone_sim2", s.ringtoneSim2)
            putBoolean("override", s.overrideVolume)
            putBoolean("vibrate", s.vibrate)
            putBoolean("led", s.led)
            putInt("led_color", s.ledColor)
            putBoolean("silence_tap", s.silenceByTap)
            putString("voicemail", s.voicemailNumber)
            putBoolean("bt_answer", s.autoAnswerBluetooth)
            putBoolean("reply_now", s.replyNow)
            s.replies.forEachIndexed { i, text -> putString("reply_$i", text) }
            putString("area", s.areaCode)
            putInt("national", s.nationalLength)
            putString("ext_access", s.extensionAccess)
            putInt("pause", s.pauseSeconds)
            putInt("ext_length", s.extensionLength)
            putLong("summary_from", s.summaryFromMillis)
            putBoolean("proximity", s.proximityLock)
            putString("phone_model", s.phoneModel.name)
            putString("blacklist", s.blacklist.joinToString("\n"))
            putBoolean("show_slider", s.showAnswerSlider)
            putBoolean("show_end", s.showEndButton)
            putBoolean("show_numbers", s.showNumbers)
            putLong("missed_seen", s.missedSeenMillis)
            putBoolean("recording_allowed", s.callRecording)
            putBoolean("auto_record", s.autoRecord)
            putString("theme_mode", s.themeMode.name)
            putInt("accent", s.accentIndex)
            putBoolean("root_granted", s.rootGranted)
        }.apply()
    }
}
