@file:Suppress("unused")

package com.example.abxoverflow.droppedapk.utils

import android.app.ActivityThread
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Parcelable
import android.os.Process
import android.util.Log
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes
import androidx.core.content.res.ResourcesCompat
import io.github.kyuubiran.ezxhelper.core.helper.ObjectHelper.`-Static`.objectHelper
import java.io.InputStream

val currentProcessName: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Process.myProcessName()
    } else {
        ActivityThread.currentProcessName()
    }

val isSystemServer: Boolean
    get() = currentProcessName == "system_server"

val Context.canEditPersistProperties: Boolean
get() = (Process.myUid() == 1000 || packageSeInfo.contains("privapp")) && !seInfo.contains(":shell:")

val Context.packageSeInfo: String
    get() = runCatching {
        (packageManager.getApplicationInfo(packageName, 0)
            .objectHelper()
            .getObject("seInfo") as? String)
            .toString()
            .plus(
                (packageManager.getApplicationInfo(packageName, 0)
                    .objectHelper()
                    .getObject("seInfoUser") as? String).toString()
            )
    }.getOrDefault("<error>")


val seInfo: String
    get() = executeShellCatching("id -Z").trim().trim('\n')

val isSamsungDevice: Boolean
    get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

fun executeShellCatching(cmdline: String): String = runCatching {
    Runtime.getRuntime().exec(cmdline).readAllToString()
}.getOrElse(Throwable::stackTraceToString)

fun executeShell(cmdline: String): String =
    Runtime.getRuntime().exec(cmdline).readAllToString()

fun java.lang.Process?.readAllToString(): String = StringBuilder().let {
    it.append(this?.inputStream.readToString(isError = false))
    it.append(this?.errorStream.readToString(isError = true))
    it.toString()
}

fun InputStream?.readToString(isError: Boolean): String {
    return this?.bufferedReader()?.use { reader ->
        val output = StringBuilder()
        reader.forEachLine { line ->
            output.append(line).append('\n')
            if (isError) {
                Log.e("DroppedAPK", line)
            } else {
                Log.i("DroppedAPK", line)
            }
        }
        output.toString()
    } ?: ""
}

inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? {
    return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }
}

fun View.setBackgroundFromAttribute(@AttrRes attrRes: Int) {
    val a = TypedValue()
    context.theme.resolveAttribute(attrRes, a, true)
    if (a.isColorType) {
        setBackgroundColor(a.data)
    } else {
        background = ResourcesCompat.getDrawable(context.resources, a.resourceId, context.theme)
    }
}


/**
 * Truncate UTF-8 string to at most `capBytes` bytes by keeping the tail portion and
 * prefixing a small truncation notice. Ensures we don't split multibyte UTF-8 sequences.
 */
fun String?.truncateUtf8Bytes(capBytes: Int): String {
    val rawOutput = this ?: return ""
    val rawBytes = rawOutput.toByteArray(Charsets.UTF_8)
    if (rawBytes.size <= capBytes) return rawOutput

    val notice = "[...output truncated: showing last $capBytes bytes]\n"
    val noticeBytes = notice.toByteArray(Charsets.UTF_8)

    var start = rawBytes.size - capBytes
    // Advance start while it's a UTF-8 continuation byte (10xxxxxx)
    while (start < rawBytes.size && (rawBytes[start].toInt() and 0xC0) == 0x80) {
        start++
    }
    if (start >= rawBytes.size) {
        // Fallback: take the last capBytes bytes
        start = rawBytes.size - capBytes
        if (start < 0) start = 0
    }

    val tailBytes = rawBytes.copyOfRange(start, rawBytes.size)
    val combined = ByteArray(noticeBytes.size + tailBytes.size)
    System.arraycopy(noticeBytes, 0, combined, 0, noticeBytes.size)
    System.arraycopy(tailBytes, 0, combined, noticeBytes.size, tailBytes.size)
    return String(combined, Charsets.UTF_8)
}
