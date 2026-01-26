package com.example.abxoverflow.droppedapk

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.widget.Toast
import com.example.abxoverflow.droppedapk.process.ProcessActivityLauncher
import com.example.abxoverflow.droppedapk.process.ProcessLocator
import kotlin.collections.sortedBy

class SystemProcessTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @SuppressLint("UnsafeIntentLaunch") val intent = getIntent().getParcelableExtra<Intent?>(
            EXTRA_TARGET_INTENT
        )
        val explicitProcess = getIntent().getStringExtra(EXTRA_EXPLICIT_PROCESS)
        val selectProcess = getIntent().getBooleanExtra(EXTRA_SELECT_PROCESS, false)

        if (intent == null) {
            Toast.makeText(this, "No target intent provided", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Error: " + e + " (" + e.message + ")", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun selectProcessAndLaunch(intent: Intent) {
        val proc = ProcessLocator.listActiveProcessNamesForUid(Process.myUid())

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select process to switch to...")
        val processArray = proc.sortedBy { it }.toTypedArray()
        builder.setItems(
            processArray
        ) { dialog: DialogInterface?, which: Int ->
            val selectedProcess = processArray[which]
            try {
                ProcessActivityLauncher.launch(
                    this,
                    intent,
                    Process.myUid(),
                    selectedProcess
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch activity in process " + selectedProcess, e)
                Toast.makeText(this, "Error: " + e + " (" + e.message + ")", Toast.LENGTH_SHORT)
                    .show()
            }
        }
        builder.setOnDismissListener { dialog: DialogInterface? -> finish() }
        builder.show()
    }

    companion object {
        private const val TAG = "DroppedAPK"

        const val EXTRA_EXPLICIT_PROCESS: String =
            "com.example.abxoverflow.droppedapk.EXTRA_EXPLICIT_PROCESS"
        const val EXTRA_TARGET_INTENT: String =
            "com.example.abxoverflow.droppedapk.EXTRA_TARGET_INTENT"
        const val EXTRA_SELECT_PROCESS: String =
            "com.example.abxoverflow.droppedapk.EXTRA_SELECT_PROCESS"
    }
}
