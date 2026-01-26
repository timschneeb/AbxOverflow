package com.example.abxoverflow.droppedapk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.util.Log
import com.example.abxoverflow.droppedapk.process.ProcessActivityLauncher
import com.example.abxoverflow.droppedapk.process.ProcessLocator
import com.example.abxoverflow.droppedapk.utils.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.collections.sortedBy

class SystemProcessTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @SuppressLint("UnsafeIntentLaunch")
        val intent = intent.getParcelableExtra<Intent?>(EXTRA_TARGET_INTENT)
        val explicitProcess = getIntent().getStringExtra(EXTRA_EXPLICIT_PROCESS)
        val selectProcess = getIntent().getBooleanExtra(EXTRA_SELECT_PROCESS, false)

        if (intent == null) {
            toast("No target intent provided")
            finish()
            return
        }

        try {
            if (explicitProcess != null) {
                ProcessActivityLauncher.launch(this, intent, Process.myUid(), explicitProcess)
                finish()
            } else if (selectProcess) {
                selectProcessAndLaunch(intent)
                // Do not finish here, wait for user selection
            } else {
                startActivity(intent)
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Process-aware launch failed", e)
            toast("Error: " + e + " (" + e.message + ")")
            finish()
        }
    }

    private fun selectProcessAndLaunch(intent: Intent) {
        val processes = ProcessLocator.listActiveProcessNamesForUid(Process.myUid())
            .sortedBy { it }
            .toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("Select process to switch to...")
            .setOnDismissListener { _ -> finish() }
            .setItems(processes) { _: DialogInterface?, which: Int ->
                try {
                    ProcessActivityLauncher.launch(
                        this,
                        intent,
                        Process.myUid(),
                        processes[which]
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch activity in process", e)
                    toast("Error: $e (${e.message})")
                }
            }.show()
    }

    companion object {
        private const val TAG = "DroppedAPK"

        const val EXTRA_EXPLICIT_PROCESS: String = "${BuildConfig.APPLICATION_ID}.EXTRA_EXPLICIT_PROCESS"
        const val EXTRA_TARGET_INTENT: String = "${BuildConfig.APPLICATION_ID}.EXTRA_TARGET_INTENT"
        const val EXTRA_SELECT_PROCESS: String = "${BuildConfig.APPLICATION_ID}.EXTRA_SELECT_PROCESS"
    }
}
