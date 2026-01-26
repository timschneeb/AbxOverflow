package com.example.abxoverflow.droppedapk.process

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.SparseArray
import androidx.core.util.size

object ProcessLocator {
    private fun normalizeProcessName(pkg: String?, proc: String?): String? {
        if (proc.isNullOrEmpty()) {
            return pkg
        }
        if (proc.startsWith(":")) {
            return pkg + proc
        }
        return proc
    }

    @SuppressLint("DiscouragedPrivateApi", "PrivateApi")
    fun listActiveProcessNamesForUid(uid: Int): MutableSet<String?> {
        val result: MutableSet<String?> = HashSet()

        // Get AMS
        val amClass = Class.forName("android.app.ActivityManager")
        val getService = amClass.getDeclaredMethod("getService")
        val ams = getService.invoke(null)

        // AMS.mProcessList
        val plField = ams!!.javaClass.getDeclaredField("mProcessList")
        plField.isAccessible = true
        val processList = plField.get(ams)

        // ProcessList.mProcessNames (ProcessMap)
        val namesField = processList!!.javaClass.getDeclaredField("mProcessNames")
        namesField.isAccessible = true
        val processMap = namesField.get(processList)

        // ProcessMap.getMap()
        @SuppressLint("BlockedPrivateApi")
        val getMap = processMap!!.javaClass.getClassLoader()!!
            .loadClass("com.android.internal.app.ProcessMap")
            .getDeclaredMethod("getMap")
        getMap.isAccessible = true
        val map = getMap.invoke(processMap) as MutableMap<*, *>?

        for (entryObj in map!!.values) {
            // entry is SparseArray<ProcessRecord>
            val entry = entryObj as SparseArray<*>

            for (i in 0..<entry.size) {
                val proc: Any = entry.valueAt(i)

                val uidField = proc.javaClass.getDeclaredField("uid")
                uidField.isAccessible = true
                if (uidField.getInt(proc) == uid) {
                    val nameField = proc.javaClass.getDeclaredField("processName")
                    nameField.isAccessible = true
                    result.add(nameField.get(proc) as String?)
                }
            }
        }
        return result
    }


    fun listAllProcessNamesForSystemUid(context: Context, uid: Int): MutableSet<String?> {
        val result: MutableSet<String?> = HashSet()

        val pm = context.packageManager

        // Query *everything* for system apps
        val pkgs = pm.getInstalledPackages(
            (PackageManager.GET_ACTIVITIES
                    or PackageManager.GET_SERVICES
                    or PackageManager.GET_RECEIVERS
                    or PackageManager.GET_PROVIDERS
                    or PackageManager.MATCH_SYSTEM_ONLY)
        )

        for (pi in pkgs) {
            val app = pi.applicationInfo
            if (app == null || app.uid != uid) continue

            val pkg = app.packageName

            // Application default process
            result.add(normalizeProcessName(pkg, app.processName))

            // Activities
            if (pi.activities != null) {
                for (ai in pi.activities) {
                    result.add(normalizeProcessName(pkg, ai.processName))
                }
            }

            // Services
            if (pi.services != null) {
                for (si in pi.services) {
                    result.add(normalizeProcessName(pkg, si.processName))
                }
            }

            // Receivers
            if (pi.receivers != null) {
                for (ri in pi.receivers) {
                    result.add(normalizeProcessName(pkg, ri.processName))
                }
            }

            // Providers
            if (pi.providers != null) {
                for (pi2 in pi.providers) {
                    result.add(normalizeProcessName(pkg, pi2.processName))
                }
            }
        }

        return result
    }
}

