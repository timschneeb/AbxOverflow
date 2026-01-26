package com.example.abxoverflow.droppedapk.utils

import android.content.Context
import android.util.Log
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

fun Context.showAlert(title: String, message: String) {
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, null)
        .show()
}

fun Context.showConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit
) {
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(android.R.string.yes) { _, _ -> onConfirm() }
        .setNegativeButton(android.R.string.no) { _, _ -> }
        .show()
}

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