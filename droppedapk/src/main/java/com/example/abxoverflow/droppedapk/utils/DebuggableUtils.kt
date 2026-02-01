package com.example.abxoverflow.droppedapk.utils

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.os.ServiceManager
import android.util.Log
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.helper.ObjectHelper.`-Static`.objectHelper
import me.timschneeberger.reflectionexplorer.utils.cast

object DebuggableUtils {
    enum class PackageMode { OFF, RUN_AS, DEBUGGABLE, ERROR }

    @SuppressLint("NotifyDataSetChanged")
    fun setPackageState(pkgName: String, mode: PackageMode) {
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
    }

    fun getPackageState(pkgName: String): PackageMode {
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
                isFullyDebuggable -> PackageMode.DEBUGGABLE
                hasRunAs -> PackageMode.RUN_AS
                else -> PackageMode.OFF
            }
        } catch (e: Exception) {
            Log.e("DebuggableUtils", "Failed to query package status for $pkgName", e)
            return PackageMode.ERROR
        }
    }
}