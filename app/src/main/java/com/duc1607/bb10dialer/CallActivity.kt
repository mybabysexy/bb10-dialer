package com.duc1607.bb10dialer

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.PowerManager
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.duc1607.bb10dialer.data.PhoneSettingsStore
import com.duc1607.bb10dialer.telecom.CallCenter
import com.duc1607.bb10dialer.telecom.CallCenterState
import com.duc1607.bb10dialer.telecom.CallPhase
import com.duc1607.bb10dialer.telecom.placeCall
import com.duc1607.bb10dialer.ui.call.CallHost
import com.duc1607.bb10dialer.ui.common.dispatchCallKey
import com.duc1607.bb10dialer.ui.common.dispatchSpeakerKey
import com.duc1607.bb10dialer.ui.theme.DialerTheme
import com.duc1607.bb10dialer.ui.theme.ProvideDialerScale
import kotlinx.coroutines.delay

private const val EMPTY_GRACE_MILLIS = 400L

/**
 * The call screen, over the lock screen when need be (see the manifest). It can run before the
 * first unlock after a restart, when only device protected storage exists.
 */
class CallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val state by CallCenter.state.collectAsState()
            // Nothing left to show: the call ended, and did not fail. Not at once, as Telecom may be
            // just about to report the call that opened this screen.
            LaunchedEffect(state.calls.isEmpty() && state.failed == null) {
                if (state.calls.isEmpty() && state.failed == null) {
                    delay(EMPTY_GRACE_MILLIS)
                    finishAndRemoveTask()
                }
            }
            ProximityLock(state)
            ProvideDialerScale { scale ->
                DialerTheme(scale = scale) {
                    CallHost(
                        state = state,
                        onCallAgain = { failed ->
                            CallCenter.dismissFailed()
                            placeCall(this, failed.number, failed.account)
                        },
                        onAddCall = ::openDialPad,
                        onClose = {
                            CallCenter.dismissFailed()
                            finishAndRemoveTask()
                        },
                    )
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        dispatchSpeakerKey(this, event) || dispatchCallKey(this, event) { super.dispatchKeyEvent(it) }

    override fun onResume() {
        super.onResume()
        CallCenter.screenShown(true)
    }

    override fun onPause() {
        CallCenter.screenShown(false)
        super.onPause()
    }

    /** The rest of the app stays behind the lock: the user has to unlock to get there. */
    private fun openDialPad() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) {
            startActivity(MainActivity.dial(this, null))
            return
        }
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = startActivity(MainActivity.dial(this@CallActivity, null))
            },
        )
    }

    /** Turns the screen off against the ear, unless the sound is coming from elsewhere anyway. */
    @Composable
    private fun ProximityLock(state: CallCenterState) {
        val call = state.foreground
        val settings by PhoneSettingsStore.state(this).collectAsState()
        val wanted = settings.proximityLock && call != null && state.ringing == null && state.audio.onEarpiece &&
            call.phase in setOf(CallPhase.Active, CallPhase.Dialing, CallPhase.Connecting, CallPhase.Holding)
        DisposableEffect(wanted) {
            val power = getSystemService(PowerManager::class.java)
            val lock = if (wanted && power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "bb10dialer:proximity").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            } else {
                null
            }
            onDispose { lock?.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, CallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
