package com.duc1607.bb10dialer.data

import android.content.Context
import android.content.pm.PackageManager
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class RootStatus { Granted, Refused, Missing }

/** Whether `su` works for this app. Asking can bring up the root manager's prompt. */
object RootAccess {
    /** Long enough for the user to answer that prompt. */
    private const val PROMPT_SECONDS = 30L

    /** Off the main thread: it waits for the user. */
    fun ask(): RootStatus {
        val process = try {
            ProcessBuilder("su", "-c", "id -u").redirectErrorStream(true).start()
        } catch (_: IOException) {
            return RootStatus.Missing
        }
        return try {
            if (!process.waitFor(PROMPT_SECONDS, TimeUnit.SECONDS)) return RootStatus.Refused
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (process.exitValue() == 0 && output.trim() == "0") RootStatus.Granted else RootStatus.Refused
        } finally {
            process.destroy()
        }
    }
}

enum class ModuleState { Missing, Installed, Removing, Unsupported }

private const val MODULES_DIR = "/data/adb/modules"
private const val MODULE_ID = "bb10dialer_privapp"
const val LINE_CAPTURE_PERMISSION = "android.permission.CAPTURE_AUDIO_OUTPUT"

/** The app may record the phone line: a ROM built it in, or the module below made it a system app. */
fun hasLinePermission(context: Context): Boolean =
    context.checkSelfPermission(LINE_CAPTURE_PERMISSION) == PackageManager.PERMISSION_GRANTED

fun PhoneSettings.canRecordCalls(context: Context): Boolean = canRecordCalls(hasLinePermission(context))

/**
 * Only a system app can record the phone line itself. Magisk and KernelSU can make this one look
 * like a system app from the next start on, without touching the system: that takes a module holding
 * a copy of this app, and the list of what it may do, without which Android refuses to start.
 * Android keeps using the installed app as long as it is signed with the same key as that copy.
 */
internal fun moduleInstallScript(apkPath: String, packageName: String, modulesDir: String = MODULES_DIR): String {
    val xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?><permissions><privapp-permissions package=\"$packageName\">" +
        "<permission name=\"$LINE_CAPTURE_PERMISSION\"/></privapp-permissions></permissions>"
    val prop = listOf(
        "id=$MODULE_ID",
        "name=BB10 Dialer line recording",
        "version=1",
        "versionCode=1",
        "author=duc1607",
        "description=Makes BB10 Dialer a system app so that it can record the phone line.",
    )
    val module = "$modulesDir/$MODULE_ID"
    return (
        listOf(
            "set -e",
            "rm -rf '$module'",
            "mkdir -p '$module/system/priv-app/BB10Dialer' '$module/system/etc/permissions'",
            "cp '$apkPath' '$module/system/priv-app/BB10Dialer/BB10Dialer.apk'",
            "printf '%s\\n' '$xml' > '$module/system/etc/permissions/privapp-permissions-$packageName.xml'",
        ) + prop.map { "printf '%s\\n' '$it' >> '$module/module.prop'" } + listOf(
            "chown -R 0:0 '$module'",
            "chmod -R u=rwX,go=rX '$module'",
            "chcon -R u:object_r:system_file:s0 '$module/system'",
        )
        ).joinToString("\n")
}

internal fun moduleStateScript(modulesDir: String = MODULES_DIR): String {
    val module = "$modulesDir/$MODULE_ID"
    return listOf(
        "if [ ! -d '$modulesDir' ]; then echo Unsupported",
        "elif [ -f '$module/remove' ]; then echo Removing",
        "elif [ -f '$module/module.prop' ]; then echo Installed",
        "else echo Missing; fi",
    ).joinToString("\n")
}

/** Each of these runs as root, off the main thread. The changes take effect at the next start. */
object LineRecordingModule {
    private const val TIMEOUT_SECONDS = 30L

    private fun run(script: String): String? = runCatching {
        val process = ProcessBuilder("su", "-c", script).redirectErrorStream(true).start()
        try {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) return null
            val output = process.inputStream.bufferedReader().use { it.readText() }
            output.takeIf { process.exitValue() == 0 }
        } finally {
            process.destroy()
        }
    }.getOrNull()

    fun state(): ModuleState {
        val answer = run(moduleStateScript())?.trim()
        return ModuleState.entries.firstOrNull { it.name == answer } ?: ModuleState.Unsupported
    }

    /** False when it did not work. A split install has no single file to copy, so it never does. */
    fun install(context: Context): Boolean {
        val info = context.applicationInfo
        if (!info.splitSourceDirs.isNullOrEmpty()) return false
        return run(moduleInstallScript(info.sourceDir, context.packageName)) != null
    }

    fun remove(): Boolean = run("touch '$MODULES_DIR/$MODULE_ID/remove'") != null

    fun reboot(): Boolean = run("svc power reboot || reboot") != null
}
