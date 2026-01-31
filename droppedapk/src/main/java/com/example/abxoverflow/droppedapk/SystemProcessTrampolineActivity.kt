package com.example.abxoverflow.droppedapk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.util.Log
import com.example.abxoverflow.droppedapk.process.ProcessActivityLauncher
import com.example.abxoverflow.droppedapk.process.ProcessLocator
import com.example.abxoverflow.droppedapk.utils.getParcelableExtraCompat
import com.example.abxoverflow.droppedapk.utils.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SystemProcessTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @SuppressLint("UnsafeIntentLaunch")
        val intent = intent.getParcelableExtraCompat<Intent>(EXTRA_TARGET_INTENT)
        val explicitProcess = getIntent().getStringExtra(EXTRA_EXPLICIT_PROCESS)
        val uid = getIntent().getIntExtra(EXTRA_TARGET_UID, Process.myUid())
        val selectProcess = getIntent().getBooleanExtra(EXTRA_SELECT_PROCESS, false)

        Log.d(
            TAG,
            "Trampoline started with uid=$uid, explicitProcess=$explicitProcess, selectProcess=$selectProcess"
        )

        if (uid != Process.myUid() && !selectProcess && explicitProcess == null) {
            Log.e(TAG, "Process name not specified for different uid. Finishing")
            finish()
            return
        }

        if (intent == null) {
            Log.e(TAG, "No target intent provided, finishing")
            finish()
            return
        }

        try {
            if (explicitProcess != null) {
                ProcessActivityLauncher.launch(this, intent, uid, explicitProcess)
                Log.d(TAG, "Launched activity in explicit process: $explicitProcess")
                finish()
            } else if (selectProcess) {
                selectProcessAndLaunch(intent, uid)
                // Do not finish here, wait for user selection
            } else {
                startActivity(intent)
                Log.d(TAG, "Launched activity in current process")
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Process-aware launch failed", e)
            toast(e)
            finish()
        }
    }

    private fun selectProcessAndLaunch(intent: Intent, uid: Int) {
        val processes = ProcessLocator.listActiveProcessNamesForUid(uid)
            .sortedBy { it }
            .toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.process_switcher_title))
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
                    toast(e)
                }
            }.show()
    }

    companion object {
        private const val TAG = "DroppedAPK"

        val COMMON_NAMESPACE: String = BuildConfig.APPLICATION_ID.substringBeforeLast('.')
        val EXTRA_EXPLICIT_PROCESS: String = "$COMMON_NAMESPACE.EXTRA_EXPLICIT_PROCESS"
        val EXTRA_TARGET_INTENT: String = "$COMMON_NAMESPACE.EXTRA_TARGET_INTENT"
        val EXTRA_TARGET_UID: String = "$COMMON_NAMESPACE.EXTRA_TARGET_UID"
        val EXTRA_SELECT_PROCESS: String = "$COMMON_NAMESPACE.EXTRA_SELECT_PROCESS"

        val component: ComponentName =
            ComponentName(
                // We must specifically access the system process variant, otherwise we can't get system_server access
                "$COMMON_NAMESPACE.system",
                SystemProcessTrampolineActivity::class.java.name
            )
    }
}
