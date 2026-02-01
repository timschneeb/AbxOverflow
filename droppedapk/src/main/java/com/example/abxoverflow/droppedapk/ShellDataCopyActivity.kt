package com.example.abxoverflow.droppedapk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.example.abxoverflow.droppedapk.utils.showAlert
import com.example.abxoverflow.droppedapk.utils.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

@SuppressLint("SetTextI18n")
class ShellDataCopyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
        val isImport = intent.getStringExtra(EXTRA_DIRECTION) == DIRECTION_IMPORT
        val isCleanRestore = intent.getBooleanExtra(EXTRA_CLEAN_RESTORE, false)

        if (packageName.isNullOrEmpty()) {
            Log.e(TAG, "Missing package name, finishing")
            finish()
            return
        }

        // Show a Material dialog for progress while keeping activity transparent.
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(36, 24, 36, 24)

            val progress = ProgressBar(this@ShellDataCopyActivity).apply { isIndeterminate = true }
            val status = TextView(this@ShellDataCopyActivity).apply {
                text = if (isImport) "Importing data..." else "Exporting data..."
                setPadding(0, 16, 0, 0)
            }

            addView(progress)
            addView(status)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .show()

        // Keep a reference to the status TextView so the background thread can update it.
        val statusText = (dialogView.getChildAt(1) as? TextView)

        // This process is running as shell, so we can use 'run-as' to access app data of debuggable apps
        // Start async execution of a run-as shell command to copy data (and set permissions maybe)
        try {
            // run the heavy work off the UI thread
            Thread {
                runOnUiThread { /* keep UI responsive */ }

                executeShell("id")

                val sdCardLocation = Environment.getExternalStorageDirectory().path + "/Android/data/$packageName/private_data"
                val sdCardDeLocation = Environment.getExternalStorageDirectory().path + "/Android/data/$packageName/private_data_de"
                val result = try {
                    File(sdCardLocation).mkdirs()
                    File(sdCardDeLocation).mkdirs()

                    // TODO: use shizuku as shell here. launch if not running
                    // TODO: check if shizuku's process has everybody group (appdomain), if yes,
                    //       we cannot use run-as as run-as is only allowed to be launched by an interactive shell not attached to appdomain.
                    if (isImport) {
                        // import: copy recursively from sdcard location into the app data dir
                        // command executed as the app user via run-as so it can write into its data dir
                        if (isCleanRestore) {
                            // clean restore: delete existing data first
                            executeShell("run-as $packageName rm -rf /data/data/$packageName/*")
                            executeShell("run-as $packageName rm -rf /data/user_de/0/$packageName/*")
                        }
                        executeShell("run-as $packageName cp -r $sdCardLocation/* /data/data/$packageName/")
                        executeShell("run-as $packageName cp -r $sdCardDeLocation/* /data/user_de/0/$packageName/")
                    } else {
                        // export: copy app data directory recursively to the sdcard location
                        executeShell("run-as $packageName cp -r /data/data/$packageName/* $sdCardLocation")
                        executeShell("run-as $packageName cp -r /data/user_de/0/$packageName/* $sdCardDeLocation")
                    }
                } catch (e: Exception) {
                    Pair(-1, "Exception: ${e.message}")
                }

                val exitCode = result.first
                val output = result.second

                // TODO: copy back to /sdcard as shell

                runOnUiThread {
                    if (exitCode == 0) {
                        statusText?.text = if (isImport) "Import completed" else "Export completed"
                        Log.i(TAG, "Shell copy completed: $output")
                        toast(if (isImport) "Import finished" else "Export finished ($sdCardLocation)")
                    } else {
                        statusText?.text = "Failed: exit=$exitCode"
                        Log.e(TAG, "Shell copy failed (exit=$exitCode): $output")
                        toast("Shell operation failed: exit=$exitCode")
                    }

                    // Dismiss dialog and finish after a brief delay so user can read message
                    dialog.dismiss()
                    finish()
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "Launch failed", e)
            toast(e)
            dialog.dismiss()
            finish()
        }
    }

    companion object {
        private const val TAG = "DroppedAPK_ShellDataCopy"

        val COMMON_NAMESPACE: String = BuildConfig.APPLICATION_ID.substringBeforeLast('.')
        val EXTRA_TARGET_PACKAGE: String = "$COMMON_NAMESPACE.EXTRA_TARGET_PACKAGE"
        val EXTRA_CLEAN_RESTORE: String = "$COMMON_NAMESPACE.CLEAN_RESTORE"
        val EXTRA_DIRECTION: String = "$COMMON_NAMESPACE.EXTRA_DIRECTION"
        val DIRECTION_EXPORT: String = "export"
        val DIRECTION_IMPORT: String = "import"

        val component: ComponentName =
            ComponentName(
                // We must specifically access the shell process variant, otherwise we can't get shell access
                "$COMMON_NAMESPACE.shell",
                ShellDataCopyActivity::class.java.name
            )

        fun startActivity(context: Context, packageName: String, isImport: Boolean, cleanRestore: Boolean) {
            // Check if COMMON_NAMESPACE.shell is installed
            try {
                context.packageManager.getPackageInfo("$COMMON_NAMESPACE.shell", 0)
            } catch (_: PackageManager.NameNotFoundException) {
                context.showAlert(
                    context.getString(R.string.error),
                    "Shell variant (UID 2000) of the app is not installed. It is required to execute run-as. Please compile and install the shell build variant after injecting its signature into the android.uid.shell UID."
                )
                return
            }

            try {
                // Call com.android.shell activity/receiver to make sure the shell process is started
                context.startActivity(
                    Intent().setComponent(
                        ComponentName(
                            "com.android.shell",
                                // This activity is not exported, needs system permission
                            "com.android.shell.HeapDumpActivity"
                        )
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            catch (e: Exception) {
                Log.w(TAG, "Failed to start shell process via com.android.shell", e)
                context.toast(e)
                return
            }

            context.startActivity(
                Intent()
                    .setComponent(component)
                    .putExtra(EXTRA_TARGET_PACKAGE, packageName)
                    .putExtra(EXTRA_CLEAN_RESTORE, cleanRestore)
                    .putExtra(
                        EXTRA_DIRECTION,
                        if (isImport) DIRECTION_IMPORT else DIRECTION_EXPORT
                    )
            )
        }
    }

    // Helper that runs a shell command and returns Pair(exitCode, output)
    private fun executeShell(command: String): Pair<Int, String> {
        val output = StringBuilder()
        try {
            Log.e(TAG, "Executing shell command: $command")
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))

            // read stdout
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val errReader = BufferedReader(InputStreamReader(proc.errorStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append('\n')
                Log.i(TAG, line!!)
            }
            while (errReader.readLine().also { line = it } != null) {
                output.append(line).append('\n')
                Log.e(TAG, line!!)
            }

            val exit = proc.waitFor()
            return Pair(exit, output.toString())
        } catch (e: Exception) {
            Log.e(TAG, "executeShell error", e)
            return Pair(-1, "${e::class.java.simpleName}: ${e.message}")
        }
    }
}
