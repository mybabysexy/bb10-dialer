package com.duc1607.bb10dialer.ui.settings

import android.app.Activity
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.duc1607.bb10dialer.BuildConfig
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.CallSummary
import com.duc1607.bb10dialer.data.DialerRepository
import com.duc1607.bb10dialer.data.LED_COLORS
import com.duc1607.bb10dialer.data.LINE_CAPTURE_PERMISSION
import com.duc1607.bb10dialer.data.LineRecordingModule
import com.duc1607.bb10dialer.data.ModuleState
import com.duc1607.bb10dialer.data.PhoneModel
import com.duc1607.bb10dialer.data.PhoneSettings
import com.duc1607.bb10dialer.data.PhoneSettingsStore
import com.duc1607.bb10dialer.data.REPLY_COUNT
import com.duc1607.bb10dialer.data.RootAccess
import com.duc1607.bb10dialer.data.RootStatus
import com.duc1607.bb10dialer.data.SimLine
import com.duc1607.bb10dialer.data.ThemeMode
import com.duc1607.bb10dialer.data.blacklistEntry
import com.duc1607.bb10dialer.data.ringtoneFor
import com.duc1607.bb10dialer.ui.Route
import com.duc1607.bb10dialer.ui.calls.IconAction
import com.duc1607.bb10dialer.ui.calls.callDuration
import com.duc1607.bb10dialer.ui.common.PageBarAction
import com.duc1607.bb10dialer.ui.common.SectionHeader
import com.duc1607.bb10dialer.ui.theme.DialerColors
import com.duc1607.bb10dialer.ui.theme.DialerIcons
import duc1607.bb10.designsystem.components.Bb10Button
import duc1607.bb10.designsystem.components.Bb10Dialog
import duc1607.bb10.designsystem.components.Bb10Divider
import duc1607.bb10.designsystem.components.Bb10Dropdown
import duc1607.bb10.designsystem.components.Bb10Icon
import duc1607.bb10.designsystem.components.Bb10LazyColumn
import duc1607.bb10.designsystem.components.Bb10OnHighlight
import duc1607.bb10.designsystem.components.Bb10ScalePicker
import duc1607.bb10.designsystem.components.Bb10Text
import duc1607.bb10.designsystem.components.Bb10Toggle
import duc1607.bb10.designsystem.components.bb10Clickable
import duc1607.bb10.designsystem.components.rememberBb10Highlight
import duc1607.bb10.designsystem.icons.Bb10Icons
import duc1607.bb10.designsystem.theme.Bb10
import duc1607.bb10.designsystem.theme.Bb10ScaleState
import duc1607.bb10.designsystem.tokens.Bb10Accents
import duc1607.bb10.designsystem.tokens.touchHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** One page of Phone Settings, as a route's name. */
enum class SettingPage {
    Display, Notifications, SilenceRinger, Voicemail, Bluetooth, CallForwarding, CallWaiting, CallBlocking,
    ShowMyNumber, ReplyNow, SmartDialing, FixedDialing, CallSummary, HearingAid, Tty, Blacklist, Recording, Advanced,
}

private class SettingRow(val title: Int, val icon: ImageVector, val tint: Color?, val page: SettingPage)

private val Yellow = Color(0xFFEDBB16)
private val Magenta = Color(0xFFB72F90)
private val Blue = Color(0xFF0092CC)
private val Green = Color(0xFF6B8E23)
private val Red = Color(0xFFE5202E)

private val Rows = listOf(
    SettingRow(R.string.setting_display, DialerIcons.Device, Blue, SettingPage.Display),
    SettingRow(R.string.setting_notifications, DialerIcons.Bell, Yellow, SettingPage.Notifications),
    SettingRow(R.string.setting_silence_ringer, DialerIcons.RingerOff, null, SettingPage.SilenceRinger),
    SettingRow(R.string.voicemail, DialerIcons.Voicemail, Magenta, SettingPage.Voicemail),
    SettingRow(R.string.setting_bluetooth, DialerIcons.Bluetooth, Blue, SettingPage.Bluetooth),
    SettingRow(R.string.setting_forwarding, DialerIcons.Forward, Yellow, SettingPage.CallForwarding),
    SettingRow(R.string.setting_waiting, DialerIcons.CallWaiting, Yellow, SettingPage.CallWaiting),
    SettingRow(R.string.setting_blocking, DialerIcons.Blocking, Red, SettingPage.CallBlocking),
    SettingRow(R.string.setting_blacklist, DialerIcons.Block, Red, SettingPage.Blacklist),
    SettingRow(R.string.setting_show_number, DialerIcons.Person, Magenta, SettingPage.ShowMyNumber),
    SettingRow(R.string.setting_reply_now, DialerIcons.ReplyCall, Green, SettingPage.ReplyNow),
    SettingRow(R.string.setting_smart_dialing, DialerIcons.Hash, Green, SettingPage.SmartDialing),
    SettingRow(R.string.setting_fixed_dialing, DialerIcons.LockedCall, Magenta, SettingPage.FixedDialing),
    SettingRow(R.string.setting_summary, DialerIcons.Clipboard, Green, SettingPage.CallSummary),
    SettingRow(R.string.setting_hearing_aid, DialerIcons.Ear, null, SettingPage.HearingAid),
    SettingRow(R.string.setting_tty, DialerIcons.Tty, Yellow, SettingPage.Tty),
    SettingRow(R.string.advanced_recording, DialerIcons.Record, Red, SettingPage.Recording),
    SettingRow(R.string.setting_advanced, DialerIcons.Settings, null, SettingPage.Advanced),
)

/** Phone Settings. */
@Composable
fun PhoneSettingsScreen(onOpen: (Route) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    SettingsFrame(
        stringResource(R.string.setting_title),
        onBack,
        action = PageBarAction(Bb10Icons.Info, stringResource(R.string.about)) { aboutOpen = true },
    ) {
        Bb10LazyColumn(Modifier.fillMaxSize()) {
            items(Rows, key = { it.title }) { row -> SettingsRow(row, onOpen) }
        }
    }
    if (aboutOpen) AboutDialog(onDismiss = { aboutOpen = false })
}

/** Who made this and which version it is. Leaves by Back or a tap outside. */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val d = Bb10.dimens
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Bb10.colors.surface)
                .padding(d.spaceL),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Bb10Text(stringResource(R.string.about_made_by), style = Bb10.typography.title, textAlign = TextAlign.Center)
            Spacer(Modifier.height(d.spaceS))
            Bb10Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                color = Bb10.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(d.spaceS))
            IconAction(DialerIcons.GitHub, stringResource(R.string.about_github)) {
                openFirst(context, Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL)))
            }
        }
    }
}

private const val REPOSITORY_URL = "https://github.com/mybabysexy/bb10-dialer"

@Composable
private fun SettingsRow(row: SettingRow, onOpen: (Route) -> Unit) {
    val d = Bb10.dimens
    val highlight = rememberBb10Highlight()
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = d.listItemMinHeight)
                .bb10Clickable(
                    role = Role.Button,
                    highlight = highlight,
                    flat = true,
                    onClick = { onOpen(Route.Setting(row.page)) },
                )
                .padding(horizontal = d.spaceM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Bb10OnHighlight(highlight) {
                val wide = row.icon === DialerIcons.Voicemail
                Box(Modifier.width(RowIconWidth), contentAlignment = Alignment.CenterStart) {
                    val size = if (wide) 34.dp else 30.dp
                    if (row.tint != null) Bb10Icon(row.icon, tint = row.tint, size = size) else Bb10Icon(row.icon, size = size)
                }
                Spacer(Modifier.width(d.spaceS))
                Bb10Text(stringResource(row.title), style = Bb10.typography.title, maxLines = 2)
            }
        }
        Bb10Divider()
    }
}

private val RowIconWidth = 44.dp

/** The page of [page]. */
@Composable
fun SettingPageScreen(page: SettingPage, simLines: List<SimLine>, scale: Bb10ScaleState, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val settings by PhoneSettingsStore.state(context).collectAsState()
    fun change(edit: (PhoneSettings) -> PhoneSettings) = PhoneSettingsStore.update(context, edit)
    when (page) {
        SettingPage.Display -> DisplayPage(settings, ::change, scale, onBack)
        SettingPage.Notifications -> NotificationsPage(settings, ::change, simLines, onBack)
        SettingPage.SilenceRinger -> OneToggle(
            R.string.setting_silence_ringer, R.string.silence_tap, R.string.silence_tap_note,
            settings.silenceByTap, { on -> change { it.copy(silenceByTap = on) } }, onBack,
        )
        SettingPage.Voicemail -> VoicemailPage(settings, ::change, onBack)
        SettingPage.Bluetooth -> OneToggle(
            R.string.setting_bluetooth, R.string.bt_answer, R.string.bt_answer_note,
            settings.autoAnswerBluetooth, { on -> change { it.copy(autoAnswerBluetooth = on) } }, onBack,
        )
        SettingPage.CallForwarding -> CallForwardingPage(simLines, onBack)
        SettingPage.CallWaiting -> CallWaitingPage(simLines, onBack)
        SettingPage.CallBlocking -> CallBlockingPage(simLines, onBack)
        SettingPage.ShowMyNumber -> ShowMyNumberPage(simLines, onBack)
        SettingPage.ReplyNow -> ReplyNowPage(settings, ::change, onBack)
        SettingPage.SmartDialing -> SmartDialingPage(settings, ::change, onBack)
        SettingPage.FixedDialing -> FixedDialingPage(onBack)
        SettingPage.CallSummary -> CallSummaryPage(settings, ::change, simLines, onBack)
        SettingPage.HearingAid -> AccessibilityPage(R.string.hac_title, R.string.setting_hearing_aid, R.string.hac_note, onBack)
        SettingPage.Tty -> AccessibilityPage(R.string.setting_tty, R.string.setting_tty, R.string.tty_note, onBack)
        SettingPage.Blacklist -> BlacklistPage(settings, ::change, onBack)
        SettingPage.Recording -> RecordingPage(settings, ::change, onBack)
        SettingPage.Advanced -> AdvancedPage(settings, ::change, onBack)
    }
}

@Composable
private fun OneToggle(title: Int, label: Int, note: Int, checked: Boolean, onChange: (Boolean) -> Unit, onBack: () -> Unit) {
    SettingsPage(stringResource(title), onBack) {
        Bb10Toggle(checked, onChange, label = stringResource(label))
        SettingNote(stringResource(note))
        Bb10Divider()
    }
}

@Composable
private fun NotificationsPage(
    settings: PhoneSettings,
    change: ((PhoneSettings) -> PhoneSettings) -> Unit,
    simLines: List<SimLine>,
    onBack: () -> Unit,
) {
    val d = Bb10.dimens
    val all = settings.allNotifications
    SettingsPage(
        stringResource(R.string.setting_notifications),
        onBack,
        action = PageBarAction(DialerIcons.ClearAll, stringResource(R.string.notifications_reset)) {
            change {
                val d0 = PhoneSettings()
                it.copy(
                    allNotifications = d0.allNotifications, sound = d0.sound, ringtone = d0.ringtone, ringtoneSim2 = d0.ringtoneSim2,
                    overrideVolume = d0.overrideVolume, vibrate = d0.vibrate, led = d0.led, ledColor = d0.ledColor,
                )
            }
        },
    ) {
        Bb10Toggle(all, { on -> change { it.copy(allNotifications = on) } }, label = stringResource(R.string.notifications_all))
        Bb10Divider()
        Bb10Toggle(settings.sound, { on -> change { it.copy(sound = on) } }, label = stringResource(R.string.notifications_sound), enabled = all)
        Bb10Divider()
        RingtoneButton(R.string.notifications_ringtone, settings.ringtone, all && settings.sound) { uri ->
            change { it.copy(ringtone = uri) }
        }
        Bb10Divider()
        if (simLines.any { it.slot == 2 }) {
            RingtoneButton(R.string.notifications_ringtone_sim2, ringtoneFor(settings, 2), all && settings.sound) { uri ->
                change { it.copy(ringtoneSim2 = uri) }
            }
            Bb10Divider()
        }
        Bb10Toggle(settings.overrideVolume, { on -> change { it.copy(overrideVolume = on) } }, label = stringResource(R.string.notifications_override), enabled = all && settings.sound)
        Bb10Divider()
        Bb10Toggle(settings.vibrate, { on -> change { it.copy(vibrate = on) } }, label = stringResource(R.string.notifications_vibrate), enabled = all)
        Bb10Divider()
        Bb10Toggle(settings.led, { on -> change { it.copy(led = on) } }, label = stringResource(R.string.notifications_led), enabled = all)
        Bb10Divider()
        Box(Modifier.fillMaxWidth().padding(d.spaceM)) {
            Bb10Dropdown(
                stringResource(R.string.notifications_led_color),
                stringArrayResource(R.array.led_colors).toList().take(LED_COLORS.size),
                settings.ledColor,
                { index -> change { it.copy(ledColor = index) } },
                enabled = all && settings.led,
            )
        }
        SettingNote(stringResource(R.string.notifications_led_note))
    }
}

@Composable
private fun DisplayPage(
    settings: PhoneSettings,
    change: ((PhoneSettings) -> PhoneSettings) -> Unit,
    scale: Bb10ScaleState,
    onBack: () -> Unit,
) {
    SettingsPage(stringResource(R.string.setting_display), onBack) {
        Bb10ScalePicker(scale)
        Bb10Divider()
        Box(Modifier.fillMaxWidth().padding(Bb10.dimens.spaceM)) {
            Bb10Dropdown(
                stringResource(R.string.display_theme),
                stringArrayResource(R.array.theme_modes).toList(),
                settings.themeMode.ordinal,
                { i -> change { it.copy(themeMode = ThemeMode.entries[i]) } },
            )
        }
        Bb10Divider()
        AccentPicker(settings.accentIndex) { i -> change { it.copy(accentIndex = i) } }
        Bb10Divider()
        Bb10Toggle(settings.showAnswerSlider, { on -> change { it.copy(showAnswerSlider = on) } }, label = stringResource(R.string.display_slider))
        SettingNote(stringResource(R.string.display_slider_note))
        Bb10Divider()
        Bb10Toggle(settings.showEndButton, { on -> change { it.copy(showEndButton = on) } }, label = stringResource(R.string.display_end_button))
        Bb10Divider()
        Bb10Toggle(settings.showNumbers, { on -> change { it.copy(showNumbers = on) } }, label = stringResource(R.string.display_numbers))
        SettingNote(stringResource(R.string.display_numbers_note))
        Bb10Divider()
    }
}

/** The kit's accent colours, each a square, laid out as its display size picker is. */
@Composable
private fun AccentPicker(selected: Int, onSelect: (Int) -> Unit) {
    val d = Bb10.dimens
    val names = stringArrayResource(R.array.accent_names)
    Column(Modifier.fillMaxWidth().padding(horizontal = d.spaceL, vertical = d.spaceS)) {
        Bb10Text(stringResource(R.string.display_accent), style = Bb10.typography.secondary)
        Spacer(Modifier.height(d.spaceXs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d.spaceXs)) {
            Bb10Accents.all.forEachIndexed { i, color ->
                // Not the theme's accent, so it darkens its own colour when focused.
                val highlight = rememberBb10Highlight()
                Box(
                    Modifier
                        .weight(1f)
                        .height(d.touchHeight)
                        .background(if (highlight.active) lerp(color, Color.Black, ACCENT_FOCUS_DARKEN) else color)
                        .semantics {
                            contentDescription = names[i]
                            this.selected = i == selected
                        }
                        .bb10Clickable(
                            role = Role.RadioButton,
                            highlight = highlight,
                            paintHighlight = false,
                            onClick = { onSelect(i) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (i == selected) Bb10Icon(Bb10Icons.Check, tint = Color.White)
                }
            }
        }
    }
}

private const val ACCENT_FOCUS_DARKEN = 0.22f

/** Opens the phone's list of ringtones. [current] is null for the phone's own. */
@Composable
private fun RingtoneButton(label: Int, current: String?, enabled: Boolean, onPick: (String?) -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onPick(result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)?.toString())
        }
    }
    val uri = current?.let(Uri::parse)
        ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
    val name = remember(uri) {
        uri?.let { runCatching { RingtoneManager.getRingtone(context, it)?.getTitle(context) }.getOrNull() }.orEmpty()
    }
    Box(Modifier.fillMaxWidth().padding(Bb10.dimens.spaceM)) {
        Bb10Button(
            stringResource(label, name),
            {
                picker.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false),
                )
            },
            enabled = enabled,
        )
    }
}

@Composable
private fun BlacklistPage(settings: PhoneSettings, change: ((PhoneSettings) -> PhoneSettings) -> Unit, onBack: () -> Unit) {
    val d = Bb10.dimens
    var typed by rememberSaveable { mutableStateOf("") }
    SettingsPage(
        stringResource(R.string.setting_blacklist),
        onBack,
        action = PageBarAction(Bb10Icons.Add, stringResource(R.string.blacklist_add)) {
            val entry = blacklistEntry(typed)
            if (entry.isNotEmpty()) {
                change { if (entry in it.blacklist) it else it.copy(blacklist = listOf(entry) + it.blacklist) }
            }
            typed = ""
        },
    ) {
        SettingField(null, typed, stringResource(R.string.blacklist_hint), { typed = it })
        SettingNote(stringResource(R.string.blacklist_note))
        Bb10Divider()
        if (settings.blacklist.isEmpty()) SettingNote(stringResource(R.string.blacklist_empty))
        for (entry in settings.blacklist) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = d.listItemMinHeight).padding(start = d.spaceM),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Bb10Text(entry, Modifier.weight(1f), style = Bb10.typography.title, maxLines = 1)
                IconAction(Bb10Icons.Delete, stringResource(R.string.delete)) {
                    change { it.copy(blacklist = it.blacklist - entry) }
                }
            }
            Bb10Divider()
        }
    }
}

/**
 * Most of it is what the system decides about this app. A toggle shows how things stand and takes
 * the user to where that is changed, which only Android may do; it is read again on the way back.
 */
@Composable
private fun AdvancedPage(settings: PhoneSettings, change: ((PhoneSettings) -> PhoneSettings) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val d = Bb10.dimens
    val app = remember { Uri.fromParts("package", context.packageName, null) }
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose {}
    }
    val unrestrictedBattery = remember(checks) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    val background = remember(checks) { !context.getSystemService(ActivityManager::class.java).isBackgroundRestricted }
    val fullScreen = remember(checks) { context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() }
    SettingsPage(stringResource(R.string.setting_advanced), onBack) {
        Bb10Toggle(settings.proximityLock, { on -> change { it.copy(proximityLock = on) } }, label = stringResource(R.string.advanced_proximity))
        Bb10Divider()
        Box(Modifier.fillMaxWidth().padding(d.spaceM)) {
            Bb10Dropdown(
                stringResource(R.string.advanced_device),
                stringArrayResource(R.array.phone_models).toList(),
                settings.phoneModel.ordinal,
                { i -> change { it.copy(phoneModel = PhoneModel.entries[i]) } },
            )
        }
        SectionHeader(stringResource(R.string.advanced_background))
        Bb10Toggle(
            unrestrictedBattery,
            {
                val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, app)
                val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                if (unrestrictedBattery) openFirst(context, list) else openFirst(context, ask, list)
            },
            label = stringResource(R.string.advanced_battery),
        )
        Bb10Divider()
        Bb10Toggle(
            background,
            { openFirst(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)) },
            label = stringResource(R.string.advanced_allow_background),
        )
        Bb10Divider()
        Bb10Toggle(
            fullScreen,
            {
                openFirst(
                    context,
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, app),
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app),
                )
            },
            label = stringResource(R.string.advanced_full_screen),
        )
        Bb10Divider()
    }
}

/**
 * Recording takes root, so the switches stay off without it. The status comes from the last time
 * `su` was asked: asking unprompted would bring up its prompt.
 */
@Composable
private fun RecordingPage(settings: PhoneSettings, change: ((PhoneSettings) -> PhoneSettings) -> Unit, onBack: () -> Unit) {
    val d = Bb10.dimens
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(if (settings.rootGranted) RootStatus.Granted else null) }
    var asking by remember { mutableStateOf(false) }
    suspend fun ask() {
        asking = true
        val answer = withContext(Dispatchers.IO) { RootAccess.ask() }
        status = answer
        asking = false
        change { it.copy(rootGranted = answer == RootStatus.Granted) }
    }
    // Already allowed, so no prompt: this only finds out if it was taken back.
    LaunchedEffect(Unit) { if (settings.rootGranted) ask() }
    SettingsPage(stringResource(R.string.advanced_recording), onBack) {
        Bb10Toggle(
            settings.canRecordCalls,
            { on -> change { it.copy(callRecording = on) } },
            label = stringResource(R.string.advanced_recording_toggle),
            enabled = settings.rootGranted,
        )
        Bb10Divider()
        Bb10Toggle(
            settings.canRecordCalls && settings.autoRecord,
            { on -> change { it.copy(autoRecord = on) } },
            label = stringResource(R.string.recording_auto),
            enabled = settings.canRecordCalls,
        )
        Bb10Divider()
        SettingNote(
            stringResource(
                when (status) {
                    RootStatus.Granted -> R.string.root_granted
                    RootStatus.Refused -> R.string.root_refused
                    RootStatus.Missing -> R.string.root_missing
                    null -> R.string.root_unknown
                },
            ),
        )
        if (status != RootStatus.Granted) {
            Box(Modifier.fillMaxWidth().padding(horizontal = d.spaceM).padding(bottom = d.spaceM)) {
                Bb10Button(stringResource(R.string.root_request), { scope.launch { ask() } }, enabled = !asking)
            }
        } else {
            LineRecordingModuleRows()
        }
        Bb10Divider()
    }
}

/** Once `su` works. The state is read as root too, which brings up no prompt by then. */
@Composable
private fun LineRecordingModuleRows() {
    val context = LocalContext.current
    val d = Bb10.dimens
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ModuleState?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var askRestart by remember { mutableStateOf(false) }
    val hasLine = remember { context.checkSelfPermission(LINE_CAPTURE_PERMISSION) == PackageManager.PERMISSION_GRANTED }
    LaunchedEffect(Unit) { state = withContext(Dispatchers.IO) { LineRecordingModule.state() } }
    fun act(job: () -> Boolean, onDone: () -> Unit = {}) {
        busy = true
        scope.launch {
            val done = withContext(Dispatchers.IO) {
                job().also { state = LineRecordingModule.state() }
            }
            failed = !done
            busy = false
            if (done) onDone()
        }
    }
    val current = state ?: return
    val note = when {
        failed -> R.string.line_failed
        current == ModuleState.Unsupported -> R.string.line_unsupported
        current == ModuleState.Removing -> R.string.line_removing
        current == ModuleState.Installed && hasLine -> R.string.line_active
        current == ModuleState.Installed -> R.string.line_restart
        else -> R.string.line_missing
    }
    SettingNote(stringResource(note))
    if (current == ModuleState.Missing || current == ModuleState.Installed) {
        Box(Modifier.fillMaxWidth().padding(horizontal = d.spaceM).padding(bottom = d.spaceM)) {
            if (current == ModuleState.Missing) {
                Bb10Button(stringResource(R.string.line_install), { act({ LineRecordingModule.install(context) }, onDone = { askRestart = true }) }, enabled = !busy)
            } else {
                Bb10Button(stringResource(R.string.line_remove), { act({ LineRecordingModule.remove() }) }, enabled = !busy)
            }
        }
    }
    if (askRestart) {
        Bb10Dialog(
            title = stringResource(R.string.restart_title),
            message = stringResource(R.string.line_restart),
            confirmText = stringResource(R.string.restart_confirm),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                askRestart = false
                act({ LineRecordingModule.reboot() })
            },
            onDismiss = { askRestart = false },
        )
    }
}

@Composable
private fun VoicemailPage(settings: PhoneSettings, change: ((PhoneSettings) -> PhoneSettings) -> Unit, onBack: () -> Unit) {
    SettingsPage(stringResource(R.string.voicemail), onBack) {
        SettingField(
            stringResource(R.string.voicemail_access),
            settings.voicemailNumber,
            stringResource(R.string.voicemail_access_hint),
            { value -> change { it.copy(voicemailNumber = value) } },
        )
        SettingNote(stringResource(R.string.voicemail_note))
        Bb10Divider()
        SystemOnly(stringResource(R.string.voicemail_password_note))
    }
}

@Composable
private fun ReplyNowPage(settings: PhoneSettings, change: ((PhoneSettings) -> PhoneSettings) -> Unit, onBack: () -> Unit) {
    val defaults = listOf(R.string.reply_later, R.string.reply_busy, R.string.reply_call_me)
    SettingsPage(stringResource(R.string.setting_reply_now), onBack) {
        Bb10Toggle(settings.replyNow, { on -> change { it.copy(replyNow = on) } }, label = stringResource(R.string.setting_reply_now))
        SettingNote(stringResource(R.string.reply_now_note))
        Bb10Divider()
        SectionHeader(stringResource(R.string.reply_now_messages))
        repeat(REPLY_COUNT) { i ->
            SettingField(
                null,
                settings.replies[i],
                stringResource(defaults[i]),
                { value -> change { s -> s.copy(replies = s.replies.toMutableList().also { it[i] = value }) } },
                numeric = false,
                enabled = settings.replyNow,
            )
        }
    }
}

@Composable
private fun SmartDialingPage(settings: PhoneSettings, change: ((PhoneSettings) -> PhoneSettings) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val d = Bb10.dimens
    val country = remember {
        val iso = runCatching { context.getSystemService(TelephonyManager::class.java).simCountryIso }.getOrNull().orEmpty()
        iso.takeIf { it.isNotEmpty() }?.let { Locale("", it).displayCountry }.orEmpty()
    }
    val lengths = listOf(0, 7, 8, 9, 10, 11, 12)
    val extensions = listOf(0, 2, 3, 4, 5, 6)
    val pauses = listOf(1, 2, 3, 5, 10)
    SettingsPage(
        stringResource(R.string.setting_smart_dialing),
        onBack,
        action = PageBarAction(Bb10Icons.Refresh, stringResource(R.string.smart_reset)) {
            change {
                val d0 = PhoneSettings()
                it.copy(
                    areaCode = d0.areaCode, nationalLength = d0.nationalLength, extensionAccess = d0.extensionAccess,
                    pauseSeconds = d0.pauseSeconds, extensionLength = d0.extensionLength,
                )
            }
        },
    ) {
        Column(Modifier.padding(horizontal = d.spaceM, vertical = d.spaceS)) {
            Bb10Text(stringResource(R.string.smart_country), style = Bb10.typography.title)
            Bb10Text(country.ifEmpty { stringResource(R.string.settings_unknown) }, color = Bb10.colors.textSecondary)
        }
        SettingField(stringResource(R.string.smart_area), settings.areaCode, stringResource(R.string.smart_area_hint), { v -> change { it.copy(areaCode = v) } })
        Box(Modifier.fillMaxWidth().padding(d.spaceM)) {
            Bb10Dropdown(
                stringResource(R.string.smart_national),
                numberOptions(lengths, zeroIsUnknown = true),
                lengths.indexOf(settings.nationalLength).coerceAtLeast(0),
                { i -> change { it.copy(nationalLength = lengths[i]) } },
            )
        }
        SettingNote(stringResource(R.string.smart_note))
        Box(Modifier.fillMaxWidth().height(d.spaceL).background(DialerColors.CardGroupGap))
        SettingField(stringResource(R.string.smart_extension), settings.extensionAccess, stringResource(R.string.smart_extension_hint), { v -> change { it.copy(extensionAccess = v) } })
        Box(Modifier.fillMaxWidth().padding(d.spaceM)) {
            Bb10Dropdown(
                stringResource(R.string.smart_pause),
                numberOptions(pauses, zeroIsUnknown = false, unit = R.string.smart_seconds),
                pauses.indexOf(settings.pauseSeconds).coerceAtLeast(0),
                { i -> change { it.copy(pauseSeconds = pauses[i]) } },
            )
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = d.spaceM).padding(bottom = d.spaceM)) {
            Bb10Dropdown(
                stringResource(R.string.smart_extension_length),
                numberOptions(extensions, zeroIsUnknown = true),
                extensions.indexOf(settings.extensionLength).coerceAtLeast(0),
                { i -> change { it.copy(extensionLength = extensions[i]) } },
            )
        }
    }
}

@Composable
private fun FixedDialingPage(onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsPage(stringResource(R.string.setting_fixed_dialing), onBack) {
        Bb10Toggle(false, {}, label = stringResource(R.string.setting_fixed_dialing), enabled = false)
        SystemOnly(stringResource(R.string.fdn_note), stringResource(R.string.settings_open_system)) {
            val fdn = Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.android.phone", "com.android.phone.settings.fdn.FdnSetting"))
            openFirst(context, fdn, Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS))
        }
    }
}

@Composable
private fun AccessibilityPage(title: Int, label: Int, note: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsPage(stringResource(title), onBack) {
        Bb10Toggle(false, {}, label = stringResource(label), enabled = false)
        SystemOnly(stringResource(note), stringResource(R.string.settings_open_system)) {
            openFirst(context, Intent(TelecomManager.ACTION_SHOW_CALL_ACCESSIBILITY_SETTINGS), Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS))
        }
    }
}

/** Opens the first of [intents] that this phone has. */
internal fun openFirst(context: Context, vararg intents: Intent) {
    intents.firstOrNull { runCatching { context.startActivity(it) }.isSuccess }
}

@Composable
private fun CallSummaryPage(
    settings: PhoneSettings,
    change: ((PhoneSettings) -> PhoneSettings) -> Unit,
    simLines: List<SimLine>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val d = Bb10.dimens
    val summary by produceState<CallSummary?>(null, settings.summaryFromMillis) {
        value = withContext(Dispatchers.IO) {
            runCatching { DialerRepository(context).loadCallSummary(settings.summaryFromMillis) }.getOrNull()
        }
    }
    val current = summary
    val since = current?.sinceMillis?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }
    val sinceText = since?.let { stringResource(R.string.summary_since, it) }.orEmpty()

    @Composable
    fun Band(title: String) {
        Row(
            Modifier.fillMaxWidth().background(DialerColors.CardGroupGap).padding(horizontal = d.spaceM, vertical = d.spaceS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Bb10Text(title, Modifier.weight(1f), style = Bb10.typography.title, maxLines = 1)
            Bb10Text(sinceText, style = Bb10.typography.secondary, maxLines = 1)
        }
    }

    @Composable
    fun Line(title: String, seconds: Long) {
        Column(Modifier.padding(horizontal = d.spaceM, vertical = d.spaceS)) {
            Bb10Text(title, style = Bb10.typography.title, maxLines = 1)
            Bb10Text(context.callDuration(seconds), color = Bb10.colors.textSecondary, maxLines = 1)
        }
    }

    SettingsPage(
        stringResource(R.string.setting_summary),
        onBack,
        action = PageBarAction(DialerIcons.ClearAll, stringResource(R.string.summary_reset)) {
            val now = System.currentTimeMillis()
            change { it.copy(summaryFromMillis = now) }
        },
    ) {
        Band(stringResource(R.string.summary_total))
        Line(stringResource(R.string.summary_all), current?.totalSeconds ?: 0)
        Band(stringResource(R.string.summary_phone))
        val lines = current?.bySlot.orEmpty()
        if (lines.isEmpty()) {
            Line(stringResource(R.string.settings_unknown), current?.totalSeconds ?: 0)
        } else {
            for ((slot, seconds) in lines) {
                val line = simLines.firstOrNull { it.slot == slot }
                val title = line?.number
                    ?: slot?.let { stringResource(R.string.sim_label, it) }
                    ?: stringResource(R.string.settings_unknown)
                Line(title, seconds)
            }
        }
    }
}
