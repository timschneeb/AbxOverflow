package com.example.abxoverflow.droppedapk.fragment

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import com.example.abxoverflow.droppedapk.R
import com.example.abxoverflow.droppedapk.databinding.ItemToolbuttonBinding
import com.example.abxoverflow.droppedapk.shizuku.ShizukuWrapper
import com.example.abxoverflow.droppedapk.utils.DebuggableUtils
import com.example.abxoverflow.droppedapk.utils.showAlert
import com.google.android.material.button.MaterialButton
import java.io.File

/**
 * System-only fragment that lists debuggable packages and exporting/importing their data.
 */
// TODO: system process causes issues with Shizuku. Run this in a normal process?
class AppDataListFragment : BaseAppListFragment() {

    private fun runShell(command: String, throwError: Boolean = true): String {
        return ShizukuWrapper.useServiceSafely { srv ->
            val result = srv.run(command)
            if (result.code != 0 && throwError) {
                context?.showAlert(
                    title = getString(R.string.error),
                    message = getString(
                        R.string.shizuku_error_while_executing_command,
                        command,
                        result.error
                    )
                )
                throw ShizukuWrapper.InvalidShizukuStateException()
            }
            // Return error message if set
            return@useServiceSafely result.error?.plus("\n") ?: ""
        }
    }

    @SuppressLint("SdCardPath")
    private val sdCard = "/sdcard"
    private val backupDir = "$sdCard/AppData"
    private fun writableTempDir(packageName: String, deviceEncryptedStorage: Boolean): String =
        sdCard + "/Android/data/$packageName/private_data".let {
            if (deviceEncryptedStorage) it + "_de" else it
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ShizukuWrapper.onCreate(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ShizukuWrapper.onDestroy()
    }

    override fun onAppClicked(target: View, pkg: String) {}

    override fun bindToolButtons(holder: VH, pkg: String): List<View>? =
        holder.itemView.context?.let { ctx ->
            listOf(
                newIconButton(
                    contentDescription = getString(R.string.app_data_import),
                    iconRes = R.drawable.ic_download
                ) {
                    try {
                        // TODO: Add "clean restore" option as dialog
                        val isCleanRestore = false

                        val temp = writableTempDir(pkg, false)
                        val tempDe = writableTempDir(pkg, true)

                        ShizukuWrapper.ensureRunAs()

                        if (!File("$backupDir/$pkg").exists()) {
                            ctx.showAlert(
                                title = ctx.getString(R.string.error),
                                message = getString(
                                    R.string.no_data_backup_found,
                                    pkg,
                                    backupDir,
                                    pkg
                                )
                            )
                            return@newIconButton
                        }

                        // TODO: show progress dialog

                        // copy from sdcard to public temp location first
                        runShell("run-as $pkg mkdir -p $temp")
                        runShell("run-as $pkg mkdir -p $tempDe")
                        runShell(
                            "cp -r $backupDir/$pkg/private_data/. $temp; " +
                                    "cp -r $backupDir/$pkg/private_data_de/. $tempDe"
                        )

                        var errors = ""
                        if (isCleanRestore) {
                            // clean restore: delete existing data first
                            errors += runShell("run-as $pkg sh -c 'rm -rf /data/data/$pkg/*'", false)
                            errors += runShell("run-as $pkg sh -c 'rm -rf /data/user_de/0/$pkg/*'", false)
                        }

                        // import: copy recursively from sdcard location into the app data dir
                        // command executed as the app user via run-as so it can write into its data dir
                        errors += runShell("run-as $pkg sh -c 'cp -r $temp/. /data/data/$pkg/'", false)
                        errors += runShell("run-as $pkg sh -c 'cp -r $tempDe/. /data/user_de/0/$pkg/'", false)

                        if (errors.isNotBlank())
                            ctx.showAlert(
                                title = getString(R.string.warnings),
                                message = getString(R.string.shizuku_shell_warnings, errors)
                            )
                    }
                    catch (_: ShizukuWrapper.InvalidShizukuStateException) {
                        // Handled in ShizukuWrapper
                    }
                },
                newIconButton(
                    contentDescription = getString(R.string.app_data_export),
                    iconRes = R.drawable.ic_upload
                ) {
                    try {
                        val temp = writableTempDir(pkg, false)
                        val tempDe = writableTempDir(pkg, true)

                        ShizukuWrapper.ensureRunAs()

                        runShell("mkdir -p $temp; mkdir -p $tempDe")

                        // export: copy app data directory recursively to the sdcard location
                        var errors = ""
                        errors += runShell("run-as $pkg sh -c 'cp -r /data/data/$pkg/. $temp'", false)
                        errors += runShell("run-as $pkg sh -c 'cp -r /data/user_de/0/$pkg/. $tempDe'", false)
                        errors += runShell("run-as $pkg sh -c 'chmod -R 777 $temp'", false)
                        errors += runShell("run-as $pkg sh -c 'chmod -R 777 $tempDe'", false)

                        // move to sdcard (ensure target exists)
                        errors += runShell("rm -rf '$backupDir/$pkg/'; mkdir -p '$backupDir/$pkg/'; " +
                                "mv -f $temp $backupDir/$pkg/; mv -f $tempDe $backupDir/$pkg", false)

                        if (errors.isNotBlank())
                            ctx.showAlert(
                                title = getString(R.string.warnings),
                                message = getString(R.string.shizuku_shell_warnings, errors)
                            )
                    }
                    catch (_: ShizukuWrapper.InvalidShizukuStateException) {
                        // Handled in ShizukuWrapper
                    }
                }
            )
        }

    private fun newIconButton(
        contentDescription: String,
        @DrawableRes iconRes: Int,
        onClick: () -> Unit
    ): MaterialButton = ItemToolbuttonBinding.inflate(layoutInflater).root.apply {
        this.contentDescription = contentDescription
        this.tooltipText = contentDescription
        setIconResource(iconRes)
        setOnClickListener { onClick() }
    }

    override fun shouldShowPackage(info: PackageInfo) =
        DebuggableUtils.getPackageState(info.packageName).let {
            it == DebuggableUtils.PackageMode.RUN_AS || it == DebuggableUtils.PackageMode.DEBUGGABLE
        }

    override fun queryPackageStatus(pkgName: String): String = ""
}
