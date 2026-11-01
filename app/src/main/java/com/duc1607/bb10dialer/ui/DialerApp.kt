package com.duc1607.bb10dialer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.duc1607.bb10dialer.DialerState
import com.duc1607.bb10dialer.DialerViewModel
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.CallNotes
import com.duc1607.bb10dialer.data.PhoneSettingsStore
import com.duc1607.bb10dialer.data.callsWith
import com.duc1607.bb10dialer.data.notedCalls
import com.duc1607.bb10dialer.telecom.CallRecorder
import com.duc1607.bb10dialer.ui.calls.CallDetailsScreen
import com.duc1607.bb10dialer.ui.calls.CallsScreen
import com.duc1607.bb10dialer.ui.common.ActionMenu
import com.duc1607.bb10dialer.ui.common.DialerTabBar
import com.duc1607.bb10dialer.ui.common.MenuEntry
import com.duc1607.bb10dialer.ui.common.PullDownHeader
import com.duc1607.bb10dialer.ui.common.SimHeader
import com.duc1607.bb10dialer.ui.contacts.ContactCardScreen
import com.duc1607.bb10dialer.ui.contacts.ContactEditorScreen
import com.duc1607.bb10dialer.ui.contacts.ContactsScreen
import com.duc1607.bb10dialer.ui.contacts.QuickContactPickerScreen
import com.duc1607.bb10dialer.ui.dialpad.DialPadScreen
import com.duc1607.bb10dialer.ui.settings.PhoneSettingsScreen
import com.duc1607.bb10dialer.ui.settings.SettingPageScreen
import com.duc1607.bb10dialer.ui.theme.DialerColors
import com.duc1607.bb10dialer.ui.theme.DialerIcons
import duc1607.bb10.designsystem.components.Bb10Dialog
import duc1607.bb10.designsystem.components.Bb10Icon
import duc1607.bb10.designsystem.components.Bb10PushHost
import duc1607.bb10.designsystem.components.Bb10Text
import duc1607.bb10.designsystem.components.bb10Clickable
import duc1607.bb10.designsystem.icons.Bb10Icons
import duc1607.bb10.designsystem.theme.Bb10
import duc1607.bb10.designsystem.theme.Bb10ScaleState
import kotlinx.coroutines.launch

private const val TAB_CALLS = 0
private const val TAB_CONTACTS = 1
private const val TAB_DIAL_PAD = 2

/** What something outside the app wants shown: a number to dial, or the call list. */
sealed interface DialRequest {
    data class Dial(val number: String) : DialRequest
    data object ShowCalls : DialRequest
}

/**
 * The phone app: three tabs over a bottom bar, each with its own right-hand menu.
 *
 * This is not a `Bb10Scaffold`: the phone app has no header on its tabs and switches them from the
 * bottom bar, which the scaffold does not offer. The parts are the kit's where it has them.
 */
@Composable
fun DialerApp(
    state: DialerState,
    /** The display size, which Phone Settings changes. */
    scale: Bb10ScaleState,
    viewModel: DialerViewModel,
    onAllowAccess: () -> Unit,
    request: DialRequest?,
    onRequestTaken: () -> Unit,
    inCall: Boolean,
    onReturnToCall: () -> Unit,
    /** Null when this already is the phone app. */
    onSetDefaultApp: (() -> Unit)?,
) {
    val actions = rememberDialerActions()
    val scope = rememberCoroutineScope()
    val index = state.book.index
    val context = LocalContext.current
    val notes by CallNotes.state(context).collectAsState()
    val noted = remember(state.calls, notes) { notedCalls(state.calls, notes) }
    val recordings by CallRecorder.all(context).collectAsState()
    val recorded = remember(state.calls, recordings) {
        callsWith(state.calls, recordings, { it.number }, { it.startedAtMillis })
    }
    val phoneSettings by PhoneSettingsStore.state(context).collectAsState()
    val tabs = rememberSaveableStateHolder()
    val callsList = rememberLazyListState()

    var tab by rememberSaveable { mutableIntStateOf(TAB_CALLS) }
    var dialed by rememberSaveable { mutableStateOf("") }
    var pages by rememberSaveable(stateSaver = RouteStackSaver) { mutableStateOf(emptyList<Route>()) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    // Which quick contact the menu is about, when it was opened by holding one.
    var quickMenuSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    // Bumped when the menu closes, so the tab can take its focus back.
    var refocus by remember { mutableIntStateOf(0) }

    LaunchedEffect(request) {
        when (request) {
            is DialRequest.Dial -> {
                pages = emptyList()
                tab = TAB_DIAL_PAD
                if (request.number.isNotEmpty()) dialed = request.number
            }
            DialRequest.ShowCalls -> {
                pages = emptyList()
                tab = TAB_CALLS
            }
            null -> return@LaunchedEffect
        }
        onRequestTaken()
    }

    // The Contacts tab's search field keeps the on-screen keyboard up: not over a page.
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(pages.size) {
        if (pages.isNotEmpty()) keyboard?.hide()
    }

    fun closeMenu() {
        menuOpen = false
        refocus++
    }

    fun openMenu() {
        quickMenuSlot = null
        menuOpen = true
    }

    fun push(route: Route) {
        pages = pages + route
    }

    fun pop() {
        pages = pages.dropLast(1)
    }

    val changeLine = MenuEntry(stringResource(R.string.menu_change_line), DialerIcons.ChangeLine, enabled = false) {}
    val settings = MenuEntry(stringResource(R.string.menu_settings), DialerIcons.Settings) { push(Route.Settings) }
    val setDefault = onSetDefaultApp?.let { MenuEntry(stringResource(R.string.set_default_app), DialerIcons.Phone) { it() } }
    val menu = listOfNotNull(setDefault) + when (tab) {
        TAB_CALLS -> listOf(
            MenuEntry(stringResource(R.string.menu_to_top), DialerIcons.ToTop) {
                scope.launch { callsList.scrollToItem(0) }
            },
            MenuEntry(stringResource(R.string.menu_to_bottom), DialerIcons.ToBottom) {
                scope.launch {
                    val total = callsList.layoutInfo.totalItemsCount
                    if (total > 0) callsList.scrollToItem(total - 1)
                }
            },
            MenuEntry(stringResource(R.string.menu_clear_calls), DialerIcons.ClearAll) { confirmClear = true },
            changeLine,
            settings,
        )
        TAB_CONTACTS -> listOf(
            MenuEntry(stringResource(R.string.add_contact), DialerIcons.AddContact) { push(Route.Editor()) },
            changeLine,
            settings,
        )
        else -> listOf(changeLine, settings)
    }
    val quickMenu = listOf(
        MenuEntry(stringResource(R.string.remove_quick_contact), Bb10Icons.Delete) {
            quickMenuSlot?.let { viewModel.setQuickContact(it, null) }
        },
    )

    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
      Column(Modifier.fillMaxSize()) {
        if (inCall) ReturnToCallBar(onReturnToCall)
        Box(Modifier.weight(1f).fillMaxWidth()) {
        // The host keeps the focused row of a list pinned while focus is away, and Compose crashes
        // on letting go if the list went with its tab. A new host for each tab has nothing pinned.
        key(tab) {
        RouteHost(
            pages = pages,
            level = 0,
            base = {
                Box(
                    Modifier
                        .fillMaxSize()
                        // Focus can stay on a row behind a page, where a key must not call it.
                        .onPreviewKeyEvent { e -> pages.isNotEmpty() && e.key != Key.Back }
                        .onKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.Menu) {
                                if (menuOpen) closeMenu() else openMenu()
                                true
                            } else false
                        }
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            tabs.SaveableStateProvider(tab) {
                                when (tab) {
                                    TAB_CALLS -> PullDownHeader(header = { SimHeader(state.simLines) }) {
                                        CallsScreen(
                                            book = state.callBook,
                                            noted = noted,
                                            recorded = recorded,
                                            showNumbers = phoneSettings.showNumbers,
                                            showVoicemail = phoneSettings.voicemailNumber.isNotEmpty(),
                                            missedSeenMillis = phoneSettings.missedSeenMillis,
                                            canReadCalls = state.canReadCalls,
                                            index = index,
                                            quick = state.quick,
                                            listState = callsList,
                                            actions = actions,
                                            onPickQuick = { push(Route.PickQuick(it)) },
                                            onQuickMenu = {
                                                quickMenuSlot = it
                                                menuOpen = true
                                            },
                                            onDetails = { push(Route.CallDetails(it)) },
                                            onAllowAccess = onAllowAccess,
                                        )
                                    }
                                    TAB_CONTACTS -> ContactsScreen(
                                        book = state.book,
                                        simLines = state.simLines,
                                        canReadContacts = state.canReadContacts,
                                        refocus = refocus,
                                        actions = actions,
                                        onOpenCard = { push(Route.Card(it)) },
                                        onAllowAccess = onAllowAccess,
                                    )
                                    else -> DialPadScreen(
                                        number = dialed,
                                        onNumberEdit = { edit -> dialed = edit(dialed) },
                                        book = state.book,
                                        calls = state.calls,
                                        sims = state.sims,
                                        refocus = refocus,
                                        actions = actions,
                                        onAddContact = { push(Route.Editor(number = it)) },
                                    )
                                }
                            }
                        }
                        DialerTabBar(
                            selected = tab,
                            onSelect = { tab = it },
                            onMore = ::openMenu,
                        )
                    }
                    ActionMenu(
                        open = menuOpen,
                        entries = if (quickMenuSlot != null) quickMenu else menu,
                        onDismiss = ::closeMenu,
                    )
                    if (confirmClear) {
                        Bb10Dialog(
                            title = stringResource(R.string.clear_calls_title),
                            message = stringResource(R.string.clear_calls_message),
                            confirmText = stringResource(R.string.clear),
                            dismissText = stringResource(R.string.cancel),
                            onConfirm = {
                                confirmClear = false
                                viewModel.clearCalls()
                            },
                            onDismiss = { confirmClear = false },
                        )
                    }
                }
            },
            page = { route ->
                when (route) {
                    is Route.CallDetails -> CallDetailsScreen(
                        number = route.number,
                        calls = state.calls,
                        index = index,
                        actions = actions,
                        onOpenCard = { push(Route.Card(it)) },
                        onAddContact = { push(Route.Editor(number = route.number)) },
                        onBack = ::pop,
                    )
                    is Route.Card -> ContactCardScreen(
                        contactId = route.contactId,
                        book = state.book,
                        calls = state.calls,
                        revision = state.revision,
                        loadData = viewModel::contactData,
                        actions = actions,
                        onEdit = { push(Route.Editor(contactId = route.contactId)) },
                        // A card whose contact was deleted closes itself from under the form.
                        onBack = { pages = pages - route },
                    )
                    is Route.Editor -> ContactEditorScreen(
                        contactId = route.contactId,
                        number = route.number,
                        contact = state.book.contacts.firstOrNull { it.id == route.contactId },
                        viewModel = viewModel,
                        onCancel = ::pop,
                        onSaved = { id ->
                            // A contact made for the picker goes back there; others show their card.
                            pages = if (route.contactId == null && route.openCard) {
                                pages.dropLast(1) + Route.Card(id)
                            } else {
                                pages.dropLast(1)
                            }
                        },
                        onDeleted = { pages = pages - route },
                    )
                    is Route.PickQuick -> QuickContactPickerScreen(
                        book = state.book,
                        onCancel = ::pop,
                        onPick = { number ->
                            viewModel.setQuickContact(route.slot, number)
                            pop()
                        },
                        onNewContact = { push(Route.Editor(openCard = false)) },
                    )
                    Route.Settings -> PhoneSettingsScreen(onOpen = ::push, onBack = ::pop)
                    is Route.Setting -> SettingPageScreen(route.page, state.simLines, scale, onBack = ::pop)
                }
            },
        )
        }
        }
      }
    }
}

/** On top of everything while a call goes on behind the app. */
@Composable
private fun ReturnToCallBar(onClick: () -> Unit) {
    val d = Bb10.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .height(d.minTouchTarget)
            .background(DialerColors.CallGreen)
            .bb10Clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = d.spaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Bb10Icon(DialerIcons.Phone, tint = Bb10.colors.onAccent, size = 22.dp)
        Spacer(Modifier.width(d.spaceS))
        Bb10Text(stringResource(R.string.return_to_call), color = Bb10.colors.onAccent, maxLines = 1)
    }
}

/** The pages over the tabs. Each one is pushed over the one before it. */
@Composable
private fun RouteHost(
    pages: List<Route>,
    level: Int,
    base: @Composable () -> Unit,
    page: @Composable (Route) -> Unit,
) {
    Bb10PushHost(
        pushed = pages.getOrNull(level),
        content = base,
        page = { route ->
            RouteHost(pages, level + 1, base = { page(route) }, page = page)
        },
    )
}
