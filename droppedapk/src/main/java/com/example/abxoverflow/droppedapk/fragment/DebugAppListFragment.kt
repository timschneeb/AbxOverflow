package com.example.abxoverflow.droppedapk.fragment

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.ServiceManager
import android.util.Log
import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.PopupMenu
import com.example.abxoverflow.droppedapk.R
import com.example.abxoverflow.droppedapk.utils.showAlert
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.helper.ObjectHelper.`-Static`.objectHelper
import me.timschneeberger.reflectionexplorer.utils.cast

/**
 * System-only fragment that lists installed packages and allows toggling debug/run-as state
 * via reflection into the package manager service. Only available when running in system_server.
 */
class DebugAppListFragment : BaseAppListFragment() {
    enum class PackageMode { OFF, RUN_AS, DEBUGGABLE }

    override fun onAppClicked(target: View, pkg: String) {
        context?.let {
            PopupMenu(it, target) .run {
                gravity = Gravity.END
                menuInflater.inflate(R.menu.menu_debug_app_actions, menu)
                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_off -> applyPackageMode(pkg, PackageMode.OFF)
                        R.id.action_run_as -> applyPackageMode(pkg, PackageMode.RUN_AS)
                        R.id.action_debuggable -> applyPackageMode(pkg, PackageMode.DEBUGGABLE)
                    }
                    true
                }
                show()
            }
        }
    }

    override fun shouldShowPackage(info: PackageInfo) = true

    override fun queryPackageStatus(pkgName: String): String {
        try {
            val svc = ServiceManager.getService("package") ?:
            throw IllegalStateException("Package service not found")

            val pkgSetting = svc.objectHelper()
                .getObject("this$0")!!
                .objectHelper()
                .getObject("mSettings")!!
                .objectHelper()
                .getObject("mPackages")!!
                .cast<Map<Any, Any>>() // <String, PackageSetting>
                .getOrElse(pkgName) { throw IllegalStateException("Package not found") }

            val pkg = pkgSetting.objectHelper()
                .getObject("pkg")!! // PackageImpl

            val hasRunAs = pkg.javaClass.methodFinder()
                .filterByName("isDebuggable")
                .first()
                .invoke(pkg) as Boolean

            val flags = pkgSetting.objectHelper().getObjectUntilSuperclass("mPkgFlags")!! as Int
            val isFullyDebuggable = (flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

            return when {
                isFullyDebuggable -> getString(R.string.debug_app_action_debuggable)
                hasRunAs -> getString(R.string.debug_app_action_run_as)
                else -> getString(R.string.debug_app_action_off)
            }
        } catch (e: Exception) {
            Log.e("DebugAppListFragment", "Failed to query package status for $pkgName", e)
            return "<error>"
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun applyPackageMode(pkgName: String, mode: PackageMode) {
        try {
            val svc = ServiceManager.getService("package")
                .objectHelper()
                .getObject("this$0")!!

            val pkgSetting = svc.objectHelper()
                .getObject("mSettings")!!
                .objectHelper()
                .getObject("mPackages")!!
                .cast<Map<Any, Any>>() // <String, PackageSetting>
                .getOrElse(pkgName) { throw IllegalStateException("Package not found") }

            val pkg = pkgSetting.objectHelper()
                .getObject("pkg")!! // PackageImpl

            // pkgSetting.pkg.isDebuggable is used by packages.list (native code) -> enables run-as
            pkg.javaClass.methodFinder()
                .filterByName("setDebuggable")
                .filterByParamTypes(Boolean::class.javaPrimitiveType)
                .first()
                .invoke(pkg, mode != PackageMode.OFF)

            // Update the package flags with FLAG_DEBUGGABLE
            val flags = pkgSetting.objectHelper().getObjectUntilSuperclass("mPkgFlags")!! as Int
            pkgSetting.objectHelper()
                .setObjectUntilSuperclass(
                    "mPkgFlags",
                    if (mode == PackageMode.DEBUGGABLE) flags or ApplicationInfo.FLAG_DEBUGGABLE
                    else flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
                )

            // Write updated packages.list and packages.xml
            svc.javaClass.methodFinder()
                .filterByName("writeSettings")
                .filterByParamTypes(Boolean::class.javaPrimitiveType)
                .first()
                .invoke(svc, /* sync */ true)

            adapter.notifyDataSetChanged()
        } catch (e: Exception) {
            context?.showAlert(getString(R.string.debug_app_update_failed), e.stackTraceToString())
        }
    }
}
