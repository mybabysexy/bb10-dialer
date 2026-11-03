package com.duc1607.bb10dialer

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.duc1607.bb10dialer.data.CallBook
import com.duc1607.bb10dialer.data.CallEntry
import com.duc1607.bb10dialer.data.ContactAccount
import com.duc1607.bb10dialer.data.ContactBook
import com.duc1607.bb10dialer.data.ContactData
import com.duc1607.bb10dialer.data.ContactDraft
import com.duc1607.bb10dialer.data.ContactPhotos
import com.duc1607.bb10dialer.data.DialerRepository
import com.duc1607.bb10dialer.data.QuickContacts
import com.duc1607.bb10dialer.data.SimLine
import com.duc1607.bb10dialer.ui.calls.dayFormatter
import com.duc1607.bb10dialer.ui.calls.title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** What the screens show. A list stays empty while its permission is missing. */
data class DialerState(
    val calls: List<CallEntry> = emptyList(),
    val callBook: CallBook = CallBook(),
    val book: ContactBook = ContactBook(),
    val sims: List<Int> = emptyList(),
    val simLines: List<SimLine> = emptyList(),
    /** The number on each quick contact tile, null for an empty one. */
    val quick: List<String?> = emptyList(),
    /** The number that holding each key 2 to 9 of the dial pad calls. */
    val speedKeys: Map<Int, String> = emptyMap(),
    /** Goes up with every load: what is not in the book (notes, addresses) may have changed. */
    val revision: Int = 0,
    val canReadCalls: Boolean = false,
    val canReadContacts: Boolean = false,
)

/**
 * Holds the data for as long as the app lives, so a tab shows it the moment it opens. The book is
 * and the call list are only rebuilt when their data really changed, which spares the screens from
 * starting over.
 */
class DialerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DialerRepository(application)
    private val quickContacts = QuickContacts(application)
    private val _state = MutableStateFlow(DialerState())
    val state: StateFlow<DialerState> = _state.asStateFlow()
    private var loading: Job? = null

    /** The system writes a call to the log a moment after it ends, with the app in full view. */
    private val callLogObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = reload()
    }

    init {
        runCatching {
            application.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, callLogObserver)
        }
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(callLogObserver)
    }

    /** Called on every return to the app: calls were made and contacts edited meanwhile. */
    fun reload() {
        loading?.cancel()
        loading = viewModelScope.launch {
            refresh()
            withContext(Dispatchers.IO) {
                ContactPhotos.preload(getApplication<Application>().contentResolver, _state.value.book)
            }
        }
    }

    private suspend fun refresh() {
        val canReadCalls = granted(Manifest.permission.READ_CALL_LOG)
        val canReadContacts = granted(Manifest.permission.READ_CONTACTS)
        val known = _state.value
        _state.value = withContext(Dispatchers.IO) {
            val contacts = if (canReadContacts) repository.loadContacts() else emptyList()
            val calls = if (canReadCalls) repository.loadCalls() else emptyList()
            val locale = Locale.getDefault()
            val callBook = if (calls == known.callBook.calls && locale == known.callBook.locale) {
                known.callBook
            } else {
                val days = dayFormatter()
                CallBook(calls, locale) { days.title(it) }
            }
            DialerState(
                calls = callBook.calls,
                callBook = callBook,
                book = if (contacts == known.book.contacts) known.book else ContactBook(contacts),
                sims = repository.loadSims(),
                simLines = repository.loadSimLines(),
                quick = quickContacts.load(),
                speedKeys = quickContacts.loadKeys(),
                revision = known.revision + 1,
                canReadCalls = canReadCalls,
                canReadContacts = canReadContacts,
            )
        }
    }

    fun clearCalls() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { repository.clearCalls() } }
            reload()
        }
    }

    suspend fun contactData(contactId: Long): ContactData? =
        withContext(Dispatchers.IO) { runCatching { repository.loadContactData(contactId) }.getOrNull() }

    suspend fun contactAccounts(): List<ContactAccount> =
        withContext(Dispatchers.IO) { repository.loadAccounts() }

    /** Saves, then refreshes the lists. [onDone] gets the contact's id, or why it failed. */
    fun saveContact(draft: ContactDraft, onDone: (Result<Long>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repository.saveContact(draft) } }
            result.exceptionOrNull()?.let { Log.w(TAG, "Saving a contact failed", it) }
            refresh()
            onDone(result)
        }
    }

    /** Takes calls out of the call log, then refreshes the lists. */
    fun deleteCalls(ids: List<Long>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { repository.deleteCalls(ids) } }
            refresh()
        }
    }

    /** Deletes, then refreshes the lists. [onDone] says whether it worked. */
    fun deleteContact(contactId: Long, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { runCatching { repository.deleteContact(contactId) }.isSuccess }
            refresh()
            onDone(deleted)
        }
    }

    fun setQuickContact(slot: Int, number: String?) {
        quickContacts.set(slot, number)
        _state.update { it.copy(quick = quickContacts.load()) }
    }

    fun setSpeedKey(digit: Int, number: String?) {
        quickContacts.setKey(digit, number)
        _state.update { it.copy(speedKeys = quickContacts.loadKeys()) }
    }

    private companion object {
        const val TAG = "DialerViewModel"
    }

    private fun granted(permission: String) =
        getApplication<Application>().checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
