package com.duc1607.bb10dialer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.duc1607.bb10dialer.data.ContactBook
import com.duc1607.bb10dialer.ui.call.CallParty
import com.duc1607.bb10dialer.ui.call.CallStatus
import com.duc1607.bb10dialer.ui.call.InCallScreen
import com.duc1607.bb10dialer.ui.call.IncomingCallScreen
import com.duc1607.bb10dialer.data.SimLine
import com.duc1607.bb10dialer.ui.common.LocalTabs
import com.duc1607.bb10dialer.ui.common.SimHeader
import com.duc1607.bb10dialer.ui.theme.DialerTheme
import duc1607.bb10.designsystem.components.Bb10Button
import duc1607.bb10.designsystem.theme.Bb10
import duc1607.bb10.designsystem.theme.rememberBb10UiScale

private const val SAMPLE_MY_NUMBER = "0900 000 000"
private const val SAMPLE_NUMBER = "0912 345 678"

private enum class PreviewScreen { Menu, Incoming, Dialing, Active, Busy, Unreachable }

/** Opens the call screens by hand: nothing here touches a real call. */
class CallPreviewActivity : ComponentActivity() {

    private val viewModel: DialerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        viewModel.reload()
        // adb shell am start -n <this> --ez probe true
        if (intent.getBooleanExtra("probe", false)) RecordingProbe.run()
        setContent {
            val state by viewModel.state.collectAsState()
            DialerTheme(scale = rememberBb10UiScale()) { CallPreview(state.book) }
        }
    }
}

@Composable
private fun CallPreview(book: ContactBook) {
    var screen by remember { mutableStateOf(PreviewScreen.Menu) }
    var saved by remember { mutableStateOf(true) }
    var silenced by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var connectedAt by remember { mutableLongStateOf(0L) }

    val contact = remember(book) { book.contacts.firstOrNull { it.largePhoto != null } }
    val party = if (saved && contact != null) {
        val phone = contact.numbers.firstOrNull()
        CallParty(
            contact.name,
            phone?.let { stringResource(R.string.quick_contact_number, it.number, it.label) },
            contact.largePhoto,
        )
    } else {
        CallParty(SAMPLE_NUMBER, stringResource(R.string.unknown_caller))
    }

    if (screen != PreviewScreen.Menu) BackHandler { screen = PreviewScreen.Menu }
    fun open(next: PreviewScreen) {
        silenced = false
        speaker = false
        muted = false
        recording = false
        note = ""
        connectedAt = System.currentTimeMillis()
        screen = next
    }

    when (screen) {
        PreviewScreen.Menu -> Menu(saved, { saved = it }, ::open)
        PreviewScreen.Incoming -> IncomingCallScreen(
            party = party,
            myNumber = SAMPLE_MY_NUMBER,
            ringerSilenced = silenced,
            onSilence = { silenced = true },
            onSms = {},
            onAnswer = { open(PreviewScreen.Active) },
            onDecline = { screen = PreviewScreen.Menu },
        )
        else -> InCallScreen(
            party = party,
            myNumber = SAMPLE_MY_NUMBER,
            status = when (screen) {
                PreviewScreen.Dialing -> CallStatus.Dialing
                PreviewScreen.Busy -> CallStatus.Busy
                PreviewScreen.Unreachable -> CallStatus.Unreachable
                else -> CallStatus.Active
            },
            connectedAtMillis = connectedAt,
            speakerOn = speaker,
            muted = muted,
            recording = recording,
            note = note,
            onNoteChange = { note = it },
            onSpeaker = { speaker = !speaker },
            onMute = { muted = !muted },
            onDigit = {},
            onRecord = { recording = !recording },
            onAddCall = {},
            onEnd = { screen = PreviewScreen.Menu },
            onCallAgain = { open(PreviewScreen.Dialing) },
        )
    }
}

@Composable
private fun Menu(saved: Boolean, onSaved: (Boolean) -> Unit, onOpen: (PreviewScreen) -> Unit) {
    val d = Bb10.dimens
    Column(
        Modifier
            .fillMaxSize()
            .background(Bb10.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(d.spaceM),
        verticalArrangement = Arrangement.spacedBy(d.spaceS),
    ) {
        LocalTabs(
            tabs = listOf(stringResource(R.string.preview_saved), stringResource(R.string.preview_unknown)),
            selected = if (saved) 0 else 1,
            onSelect = { onSaved(it == 0) },
        )
        // No phone here has two SIMs to show this with.
        SimHeader(
            listOf(
                SimLine(1, "+84 912 345 678", "Viettel"),
                SimLine(2, null, "Vinaphone"),
            ),
        )
        val entries = listOf(
            R.string.preview_incoming to PreviewScreen.Incoming,
            R.string.preview_dialing to PreviewScreen.Dialing,
            R.string.preview_active to PreviewScreen.Active,
            R.string.preview_busy to PreviewScreen.Busy,
            R.string.preview_unreachable to PreviewScreen.Unreachable,
        )
        for ((label, target) in entries) {
            Bb10Button(stringResource(label), modifier = Modifier.fillMaxWidth(), onClick = { onOpen(target) })
        }
    }
}
