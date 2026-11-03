package com.duc1607.bb10dialer

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.duc1607.bb10dialer.data.CallBook
import com.duc1607.bb10dialer.data.CallEntry
import com.duc1607.bb10dialer.data.CallKind
import com.duc1607.bb10dialer.data.Contact
import com.duc1607.bb10dialer.data.ContactBook
import com.duc1607.bb10dialer.data.PhoneNumber
import com.duc1607.bb10dialer.ui.DialerActions
import com.duc1607.bb10dialer.ui.call.CallParty
import com.duc1607.bb10dialer.ui.call.CallStatus
import com.duc1607.bb10dialer.ui.call.InCallScreen
import com.duc1607.bb10dialer.ui.call.IncomingCallScreen
import com.duc1607.bb10dialer.ui.calls.CallsScreen
import com.duc1607.bb10dialer.ui.calls.dayFormatter
import com.duc1607.bb10dialer.ui.calls.title
import com.duc1607.bb10dialer.ui.common.DialerTabBar
import com.duc1607.bb10dialer.ui.contacts.ContactsScreen
import com.duc1607.bb10dialer.ui.dialpad.DialPadScreen
import com.duc1607.bb10dialer.ui.settings.PhoneSettingsScreen
import com.duc1607.bb10dialer.ui.theme.DialerTheme
import com.duc1607.bb10dialer.ui.theme.ProvideDialerScale
import duc1607.bb10.designsystem.theme.Bb10
import java.util.Locale

private const val FAVORITE = "Xua nè"
private const val PUBLIC_NUMBER = "0346697174"
private const val MINUTE = 60_000L
private const val DAY = 24 * 60 * MINUTE
private val MISSED_SEEN_BEFORE = 3 * MINUTE

private val Names = listOf(
    "An Nguyễn", "Bảo Trần", "Chi Lê", "Dũng Phạm", "Giang Hoàng", "Hà Vũ", "Hải Đặng", "Hoa Bùi", "Hùng Đỗ", "Khánh Ngô",
    "Lan Dương", "Linh Lý", "Long Trịnh", "Mai Đinh", "Minh Cao", "Nam Hồ", "Ngọc Tô", "Phong Mai", "Quân Võ", "Sơn Lâm",
    "Tâm Tạ", "Thảo Hà", "Trang Châu", "Tú Kiều", "Vân Quách", "Yến Thái",
)

/** Not a line anyone has: it is here to be seen, not to be called. */
private fun fakeNumber(i: Int) = "0900 000 " + (100 + i)

private val Contacts = listOf(Contact(1, FAVORITE, listOf(PhoneNumber(fakeNumber(0), "Di động")), favorite = true)) +
    Names.mapIndexed { i, name -> Contact(i + 2L, name, listOf(PhoneNumber(fakeNumber(i + 1), "Di động"))) }

private fun fakeCalls(now: Long): List<CallEntry> {
    val kinds = listOf(CallKind.Missed, CallKind.Incoming, CallKind.Outgoing, CallKind.Outgoing, CallKind.Missed, CallKind.Incoming)
    return List(28) { i ->
        val contact = (i * 5) % Contacts.size
        // Every fifth one has no contact, to show a number that is not saved.
        val number = if (i % 5 == 3) "0900 111 " + (200 + i) else Contacts[contact].numbers[0].number
        CallEntry(i.toLong(), number, now - 2 * MINUTE - i * (i + 8) * 7 * MINUTE, 40L + i * 17, kinds[i % kinds.size])
    }
}

/**
 * The screens for the pictures in the README, on made up data: nothing is read from or written to the
 * phone's own contacts and call log. Debug builds only.
 * `adb shell am start -n <pkg>/.ReadmeShotsActivity --ei screen 1` (1 to 6).
 */
class ReadmeShotsActivity : ComponentActivity() {

    override fun attachBaseContext(base: Context) {
        val locale = Locale("vi")
        Locale.setDefault(locale)
        super.attachBaseContext(base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(locale) }))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val screen = intent.getIntExtra("screen", 1)
        val bars = if (screen >= 5) Color.TRANSPARENT else Color.BLACK
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(bars), navigationBarStyle = SystemBarStyle.dark(bars))
        super.onCreate(savedInstanceState)
        setContent {
            ProvideDialerScale { scale ->
                DialerTheme(scale = scale) { Shot(screen) }
            }
        }
    }

    @Composable
    private fun Shot(screen: Int) {
        val now = remember { System.currentTimeMillis() }
        val calls = remember { fakeCalls(now) }
        val book = remember { ContactBook(Contacts) }
        val callBook = remember { CallBook(calls, Locale.getDefault()) { dayFormatter().title(it) } }
        // A slip of the finger on a row must not call anyone.
        val actions = remember { DialerActions(NoCalls(this)) }
        when (screen) {
            1 -> WithTabs(0) {
                CallsScreen(
                    book = callBook,
                    noted = setOf(1L, 8L),
                    recorded = setOf(2L, 8L),
                    showNumbers = true,
                    showVoicemail = false,
                    missedSeenMillis = now - MISSED_SEEN_BEFORE * 30,
                    canReadCalls = true,
                    index = book.index,
                    quick = listOf(fakeNumber(0), null, null, null),
                    listState = rememberLazyListState(),
                    actions = actions,
                    onPickQuick = {},
                    onQuickMenu = {},
                    onDetails = {},
                    onCallMenu = {},
                    onDeleteCall = {},
                    onAllowAccess = {},
                )
            }
            2 -> WithTabs(1) {
                ContactsScreen(book, emptyList(), canReadContacts = true, refocus = 0, actions = actions, onOpenCard = {}, onContactMenu = {}, onDeleteContact = {}, onAllowAccess = {})
            }
            3 -> WithTabs(2) {
                DialPadScreen(PUBLIC_NUMBER, {}, book, calls, listOf(1), 0, actions, {}, hasVoicemail = true, onSpeedDial = {})
            }
            4 -> Inset { PhoneSettingsScreen(onOpen = {}, onBack = {}) }
            5 -> IncomingCallScreen(
                party = CallParty(FAVORITE, "Di động"),
                myNumber = PUBLIC_NUMBER,
                ringerSilenced = false,
                onSilence = {},
                onSms = {},
                onAnswer = {},
                onDecline = {},
            )
            else -> InCallScreen(
                party = CallParty(FAVORITE),
                myNumber = PUBLIC_NUMBER,
                status = CallStatus.Active,
                connectedAtMillis = now - 83_000,
                speakerOn = false,
                muted = false,
                recording = false,
                note = "",
                onNoteChange = {},
                onSpeaker = {},
                onMute = {},
                onDigit = {},
                onRecord = {},
                onAddCall = {},
                onEnd = {},
                onCallAgain = {},
            )
        }
    }

    @Composable
    private fun Inset(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(Bb10.colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) { content() }
    }

    @Composable
    private fun WithTabs(tab: Int, content: @Composable () -> Unit) {
        Inset {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                DialerTabBar(selected = tab, onSelect = {}, onMore = {})
            }
        }
    }

    private class NoCalls(base: Context) : ContextWrapper(base) {
        override fun getSystemService(name: String): Any? =
            if (name == TELECOM_SERVICE || name == TELEPHONY_SERVICE) null else super.getSystemService(name)
    }
}
