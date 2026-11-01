package com.duc1607.bb10dialer.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.TelecomManager
import com.duc1607.bb10dialer.CallActivity
import com.duc1607.bb10dialer.MainActivity
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.LED_COLORS
import com.duc1607.bb10dialer.data.PhoneSettingsStore
import kotlin.concurrent.thread

internal const val ONGOING_NOTIFICATION = 1
internal const val INCOMING_NOTIFICATION = 2
private const val MISSED_NOTIFICATION = 3
private const val CHANNEL_INCOMING = "incoming_v1"
private const val CHANNEL_INCOMING_QUIET = "incoming_quiet_v1"
private const val CHANNEL_ONGOING = "ongoing_v1"
private const val CHANNEL_MISSED = "missed_v1"
private const val ACTION_ANSWER = "com.duc1607.bb10dialer.ANSWER"
private const val ACTION_DECLINE = "com.duc1607.bb10dialer.DECLINE"
private const val ACTION_HANG_UP = "com.duc1607.bb10dialer.HANG_UP"
private const val EXTRA_CALL = "call"
// Telecom sends these, without making their names public. Each is optional.
private const val EXTRA_CALL_BACK_INTENT = "android.telecom.extra.CALL_BACK_INTENT"
private const val EXTRA_CLEAR_MISSED_CALLS_INTENT = "android.telecom.extra.CLEAR_MISSED_CALLS_INTENT"

/** The notifications of the call screens, and the channels they go through. */
internal class CallNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        createChannels(context)
    }

    /**
     * [quiet] once the call screen is up: the call stays in the shade but must not drop down over
     * the screen. Otherwise it is the way there if opening the screen directly was refused, with
     * the full screen intent the system acts on when the phone is locked or asleep.
     */
    fun incoming(call: LiveCall, name: String, quiet: Boolean) {
        val person = Person.Builder().setName(name.ifEmpty { context.getString(R.string.unknown_caller) }).setImportant(true).build()
        val notification = Notification.Builder(context, if (quiet) CHANNEL_INCOMING_QUIET else CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setStyle(
                Notification.CallStyle.forIncomingCall(
                    person,
                    action(ACTION_DECLINE, call.id),
                    action(ACTION_ANSWER, call.id),
                ),
            )
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(openCall(call.id))
            .setFullScreenIntent(openCall(call.id), true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        manager.notify(INCOMING_NOTIFICATION, notification)
    }

    fun cancelIncoming() = manager.cancel(INCOMING_NOTIFICATION)

    /** For the foreground service, which is what the call style needs. */
    fun ongoing(call: LiveCall, name: String): Notification {
        val person = Person.Builder().setName(name.ifEmpty { context.getString(R.string.unknown_caller) }).setImportant(true).build()
        return Notification.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setStyle(Notification.CallStyle.forOngoingCall(person, action(ACTION_HANG_UP, call.id)))
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(openCall(call.id))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .apply {
                call.connectedAtMillis?.let { setWhen(it).setShowWhen(true).setUsesChronometer(true) }
            }
            .build()
    }

    private fun action(name: String, id: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        name.hashCode() + id,
        Intent(context, CallActionReceiver::class.java).setAction(name).putExtra(EXTRA_CALL, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openCall(id: Int): PendingIntent = PendingIntent.getActivity(
        context,
        id,
        CallActivity.intent(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(
                listOf(
                    // CallRinger rings: a sound here would play over it.
                    NotificationChannel(CHANNEL_INCOMING, context.getString(R.string.channel_incoming), NotificationManager.IMPORTANCE_HIGH).apply {
                        setSound(null, null)
                        enableVibration(false)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    },
                    NotificationChannel(CHANNEL_INCOMING_QUIET, context.getString(R.string.channel_incoming_quiet), NotificationManager.IMPORTANCE_LOW).apply {
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    },
                    NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.channel_ongoing), NotificationManager.IMPORTANCE_LOW),
                ),
            )
        }

        /** A channel's light is fixed once it exists: one channel for each LED choice. */
        private fun missedChannel(context: Context): String {
            val manager = context.getSystemService(NotificationManager::class.java)
            val settings = PhoneSettingsStore.get(context)
            val id = if (settings.led) "$CHANNEL_MISSED-led-${settings.ledColor}" else CHANNEL_MISSED
            manager.notificationChannels
                .filter { it.id.startsWith(CHANNEL_MISSED) && it.id != id }
                .forEach { manager.deleteNotificationChannel(it.id) }
            manager.createNotificationChannel(
                NotificationChannel(id, context.getString(R.string.channel_missed), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    enableLights(settings.led)
                    if (settings.led) lightColor = LED_COLORS[settings.ledColor]
                },
            )
            return id
        }

        fun clearMissed(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(MISSED_NOTIFICATION)
            runCatching { context.getSystemService(TelecomManager::class.java).cancelMissedCallsNotification() }
        }

        /** [number] is null for several missed calls from different numbers. */
        internal fun showMissed(context: Context, count: Int, number: String?, callBack: PendingIntent?, clear: PendingIntent?) {
            if (!PhoneSettingsStore.get(context).allNotifications) return
            val caller = number?.let { lookUpCaller(context, it) }
            val open = PendingIntent.getActivity(
                context,
                0,
                MainActivity.showCalls(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val title = if (count > 1) context.getString(R.string.missed_calls, count) else context.getString(R.string.call_missed)
            val builder = Notification.Builder(context, missedChannel(context))
                .setSmallIcon(R.drawable.ic_stat_call)
                .setCategory(Notification.CATEGORY_MISSED_CALL)
                .setContentTitle(title)
                .setContentText(caller?.name ?: number ?: "")
                .setContentIntent(open)
                .setAutoCancel(true)
            if (clear != null) builder.setDeleteIntent(clear)
            if (number != null && number.isNotEmpty()) {
                val telUri = Uri.fromParts("tel", number, null)
                builder.addAction(
                    Notification.Action.Builder(
                        null,
                        context.getString(R.string.callback),
                        callBack ?: PendingIntent.getActivity(
                            context, 1, MainActivity.dial(context, number),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        ),
                    ).build(),
                )
                builder.addAction(
                    Notification.Action.Builder(
                        null,
                        context.getString(R.string.action_sms),
                        PendingIntent.getActivity(
                            context, 2, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", telUri.schemeSpecificPart, null)),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        ),
                    ).build(),
                )
            }
            context.getSystemService(NotificationManager::class.java).notify(MISSED_NOTIFICATION, builder.build())
        }
    }
}

/** The buttons of the call notifications. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_CALL, -1)
        when (intent.action) {
            ACTION_ANSWER -> CallCenter.answer(id)
            ACTION_DECLINE -> CallCenter.decline(id)
            ACTION_HANG_UP -> CallCenter.hangUp(id)
        }
    }
}

/** Telecom leaves the missed call notification to the phone's default dialer. */
class MissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
        val number = intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER)
        val callBack = intent.getParcelableExtra(EXTRA_CALL_BACK_INTENT, PendingIntent::class.java)
        val clear = intent.getParcelableExtra(EXTRA_CLEAR_MISSED_CALLS_INTENT, PendingIntent::class.java)
        if (count <= 0) {
            // Only ours: telling Telecom again would have it send this right back.
            context.getSystemService(NotificationManager::class.java).cancel(MISSED_NOTIFICATION)
            return
        }
        val pending = goAsync()
        // Looking the caller up is a query: not on the main thread.
        thread {
            try {
                CallNotifications.showMissed(context, count, number, callBack, clear)
            } finally {
                pending.finish()
            }
        }
    }
}
