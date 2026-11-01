package com.duc1607.bb10dialer.telecom

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers

/** The phone's own list of blocked numbers, which only the default phone app may change. */
object BlockedNumberList {
    fun canBlock(context: Context): Boolean =
        runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(context) }.getOrDefault(false)

    fun isBlocked(context: Context, number: String): Boolean =
        runCatching { BlockedNumberContract.isBlocked(context, number) }.getOrDefault(false)

    /** Off the main thread. False when the phone refused. */
    fun setBlocked(context: Context, number: String, blocked: Boolean): Boolean = runCatching {
        if (blocked) {
            val values = ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }
            context.contentResolver.insert(BlockedNumbers.CONTENT_URI, values)
        } else {
            BlockedNumberContract.unblock(context, number)
        }
    }.isSuccess
}
