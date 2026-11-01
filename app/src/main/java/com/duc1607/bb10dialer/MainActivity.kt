package com.duc1607.bb10dialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.duc1607.bb10dialer.telecom.CallCenter
import com.duc1607.bb10dialer.telecom.CallNotifications
import com.duc1607.bb10dialer.ui.DialRequest
import com.duc1607.bb10dialer.ui.DialerApp
import com.duc1607.bb10dialer.ui.common.dispatchCallKey
import com.duc1607.bb10dialer.ui.theme.DialerTheme
import com.duc1607.bb10dialer.ui.theme.ProvideDialerScale

class MainActivity : ComponentActivity() {

    private val viewModel: DialerViewModel by viewModels()

    /** What opened the app and asks for a tab, until the screens have taken it. */
    private var request by mutableStateOf<DialRequest?>(null)
    private var isPhoneApp by mutableStateOf(false)

    private val roleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshRole()
            viewModel.reload()
        }

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.reload()
            askForRoleOnce()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        refreshRole()
        if (savedInstanceState == null) {
            request = requestOf(intent)
            when {
                missingPermissions().isNotEmpty() -> permissionRequest.launch(missingPermissions().toTypedArray())
                else -> askForRoleOnce()
            }
        }
        setContent {
            val state by viewModel.state.collectAsState()
            val calls by CallCenter.state.collectAsState()
            ProvideDialerScale { scale ->
              DialerTheme(scale = scale) {
                DialerApp(
                    state = state,
                    scale = scale,
                    viewModel = viewModel,
                    onAllowAccess = ::askForPermissions,
                    request = request,
                    onRequestTaken = { request = null },
                    inCall = calls.calls.isNotEmpty(),
                    onReturnToCall = { startActivity(CallActivity.intent(this)) },
                    onSetDefaultApp = if (isPhoneApp) null else ::askForRole,
                )
              }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        dispatchCallKey(this, event) { super.dispatchKeyEvent(it) }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        request = requestOf(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshRole()
        viewModel.reload()
        if (isPhoneApp) CallNotifications.clearMissed(this)
    }

    private fun requestOf(intent: Intent?): DialRequest? {
        intent ?: return null
        if (intent.getBooleanExtra(EXTRA_SHOW_CALLS, false)) return DialRequest.ShowCalls
        if (intent.action != Intent.ACTION_DIAL && intent.action != Intent.ACTION_VIEW) return null
        val number = intent.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart
        return DialRequest.Dial(number.orEmpty())
    }

    private fun refreshRole() {
        isPhoneApp = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER)
    }

    /** On its own the app only asks the first time: after that it is in the menu. */
    private fun askForRoleOnce() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (isPhoneApp || prefs.getBoolean(KEY_ASKED_ROLE, false)) return
        prefs.edit().putBoolean(KEY_ASKED_ROLE, true).apply()
        askForRole()
    }

    /** The system's own dialog: the user says yes or no, and can change it in Settings later. */
    private fun askForRole() {
        val roles = getSystemService(RoleManager::class.java)
        if (roles.isRoleAvailable(RoleManager.ROLE_DIALER) && !roles.isRoleHeld(RoleManager.ROLE_DIALER)) {
            roleRequest.launch(roles.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        }
    }

    private fun missingPermissions(): List<String> =
        PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    /** After a second refusal Android shows no prompt anymore: only the app's settings can help. */
    private fun askForPermissions() {
        val missing = missingPermissions()
        if (missing.any { shouldShowRequestPermissionRationale(it) }) {
            permissionRequest.launch(missing.toTypedArray())
        } else {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        }
    }

    companion object {
        private const val EXTRA_SHOW_CALLS = "show_calls"
        private const val PREFS = "main"
        private const val KEY_ASKED_ROLE = "asked_role"

        private val PERMISSIONS = listOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.RECORD_AUDIO,
        )

        fun showCalls(context: Context): Intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_SHOW_CALLS, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** The dial pad with [number] filled in. */
        fun dial(context: Context, number: String?): Intent =
            Intent(Intent.ACTION_DIAL, number?.let { Uri.fromParts("tel", it, null) }, context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
