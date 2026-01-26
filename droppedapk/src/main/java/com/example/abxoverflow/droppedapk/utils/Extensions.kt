package com.example.abxoverflow.droppedapk.utils

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.example.abxoverflow.droppedapk.databinding.DialogTextinputBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

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


fun InputStream?.readToString(isError: Boolean): String {
    BufferedReader(InputStreamReader(this)).use { reader ->
        var line: String
        val output = StringBuilder()
        while ((reader.readLine().also { line = it }) != null) {
            output.append(line).append("\n")
            if (isError) {
                Log.e("DroppedAPK", line)
            } else {
                Log.i("DroppedAPK", line)
            }
        }
        return output.toString()
    }
}