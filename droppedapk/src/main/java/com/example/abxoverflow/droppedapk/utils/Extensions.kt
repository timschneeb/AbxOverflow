@file:Suppress("unused")

package com.example.abxoverflow.droppedapk.utils

import android.app.ActivityThread
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Parcelable
import android.os.Process
import android.text.Editable
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.example.abxoverflow.droppedapk.R
import com.example.abxoverflow.droppedapk.databinding.DialogTextinputBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.InputStream

val currentProcessName: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Process.myProcessName()
    } else {
        ActivityThread.currentProcessName()
    }

val isSystemServer: Boolean
    get() = currentProcessName == "system_server"

fun Context.showAlert(@StringRes title: Int, @StringRes message: Int) {
    showAlert(getString(title), getString(message))
}

fun Context.showAlert(title: String, message: String) {
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, null)
        .show()
}

fun Context.showInputAlert(
    layoutInflater: LayoutInflater,
    @StringRes title: Int,
    @StringRes hint: Int,
    value: String = "",
    isNumberInput: Boolean = false,
    callback: ((String) -> Unit)
) {
    showInputAlert(layoutInflater, getString(title), getString(hint), value, isNumberInput, callback)
}

fun Context.showInputAlert(
    layoutInflater: LayoutInflater,
    title: String?,
    hint: String?,
    value: String = "",
    isNumberInput: Boolean = false,
    callback: ((String) -> Unit)
) {
    val content = DialogTextinputBinding.inflate(layoutInflater).apply {
        textInputLayout.hint = hint
        text1.text = Editable.Factory.getInstance().newEditable(value)
        if(isNumberInput)
            text1.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }

    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setView(content.root)
        .setPositiveButton(android.R.string.ok) { inputDialog, _ ->
            (inputDialog as AlertDialog)
                .findViewById<TextView>(android.R.id.text1)
                ?.let {
                    callback.invoke(it.text.toString())
                }
        }
        .setNegativeButton(android.R.string.cancel) { _, _ -> }
        .create()
        .show()
}



fun Context.showConfirmDialog(@StringRes title: Int, @StringRes message: Int, onConfirm: (() -> Unit)) {
    showConfirmDialog(getString(title), getString(message), onConfirm)
}

fun Context.showConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit
) {
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok) { _, _ -> onConfirm() }
        .setNegativeButton(android.R.string.cancel) { _, _ -> }
        .show()
}

fun Context.toast(message: String, long: Boolean = true) = Toast.makeText(this, message,
    if(long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

fun Context.toast(@StringRes message: Int, long: Boolean = true) = toast(getString(message), long)

fun Context.toast(e: Throwable, long: Boolean = true) =
    toast(getString(R.string.error_template, e, e.message), long)

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

fun Context.unwrapContext(): Context {
    // Contexts may be wrapped, unwrap them to get the ContextImpl instance
    val context = this
    if(context is ContextWrapper) {
        val baseContext = context.baseContext
        if(baseContext != null && baseContext != context) {
            return baseContext.unwrapContext()
        }
        throw IllegalStateException("Unable to locate base context from ContextWrapper")
    }

    if (context.javaClass.name != "android.app.ContextImpl") {
        throw IllegalStateException("Expected ContextImpl but found ${context.javaClass.name}")
    }

    return context
}

inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? {
    return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
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
