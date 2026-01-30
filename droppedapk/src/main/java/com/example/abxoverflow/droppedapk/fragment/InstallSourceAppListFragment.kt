package com.example.abxoverflow.droppedapk.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import android.os.ServiceManager
import android.view.View
import com.example.abxoverflow.droppedapk.R
import com.example.abxoverflow.droppedapk.utils.showAlert
import com.example.abxoverflow.droppedapk.utils.showConfirmDialog
import com.example.abxoverflow.droppedapk.utils.toast
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.helper.ObjectHelper.`-Static`.objectHelper
import me.timschneeberger.reflectionexplorer.utils.cast

/**
 * System-only fragment that lists side-loaded packages and allows
 * setting their install source to com.android.vending to circumvent the Sideloading Play Integrity check.
 */

class InstallSourceAppListFragment : BaseAppListFragment() {
    private val playStorePackage = "com.android.vending"

    @SuppressLint("NotifyDataSetChanged")
    override fun onAppClicked(target: View, pkg: String) {
        context?.let { ctx ->
            ctx.showConfirmDialog(
                getString(R.string.install_source_change_confirm_title),
                getString(
                    R.string.install_source_change_confirm,
                    pkg,
                    ctx.getPackageInstallerCompat(pkg).toString()
                )
            ) {
                try {
                    val svc = ServiceManager.getService("package")
                        .objectHelper()
                        .getObject("this$0")!!

                    val pkgSetting = svc.objectHelper()
                        .getObject("mSettings")!!
                        .objectHelper()
                        .getObject("mPackages")!!
                        .cast<Map<Any, Any>>() // <String, PackageSetting>
                        .getOrElse(pkg) { throw IllegalStateException("Package not found") }

                    // Update the install source to Play Store
                    pkgSetting.objectHelper()
                        .getObjectUntilSuperclass("installSource")!!
                        .objectHelper()
                        .setObject("mInstallerPackageName", playStorePackage)

                    // Write updated packages.xml
                    svc.javaClass.methodFinder()
                        .filterByName("writeSettings")
                        .filterByParamTypes(Boolean::class.javaPrimitiveType)
                        .first()
                        .invoke(svc, /* sync */ true)

                    adapter.removeItem(pkg)
                    ctx.toast(getString(R.string.install_source_change_success, pkg))
                } catch (e: Exception) {
                    ctx.showAlert(getString(R.string.error), e.stackTraceToString())
                }
            }
        }
    }

    override fun shouldShowPackage(info: PackageInfo): Boolean {
        val installerPkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requireContext().packageManager.getInstallSourceInfo(info.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            requireContext().packageManager.getInstallerPackageName(info.packageName)
        }
        return installerPkg != playStorePackage && info.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) == 0
    }

    override fun queryPackageStatus(pkgName: String) = ""

    companion object {
        private fun Context.getPackageInstallerCompat(pkgName: String): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(pkgName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(pkgName)
        }
    }
}
