package com.example.abxoverflow.droppedapk

import android.annotation.SuppressLint
import android.os.ServiceManager
import android.util.Log
import me.timschneeberger.reflectionexplorer.ErrorInstance
import me.timschneeberger.reflectionexplorer.Group
import me.timschneeberger.reflectionexplorer.Instance
import me.timschneeberger.reflectionexplorer.ReflectionExplorer
import me.timschneeberger.reflectionexplorer.utils.dex.ParamNames.additionalDexSearchPaths

object InstanceProvider {
    private const val TAG = "DroppedAPK_InstanceProvider"

    @SuppressLint("PrivateApi")
    fun collectInstances() {
        additionalDexSearchPaths.add("/system/framework/framework.jar")

        // Get all system services
        try {
            ServiceManager
                .listServices()
                .mapNotNull { name ->
                    ServiceManager.getService(name)
                        ?.let { Instance(it, name, Group("Accessible System Services", null)) }
                }
                .filter { it.instance.javaClass.name != "android.os.BinderProxy" }
                .forEach(ReflectionExplorer.instances::add)

        } catch (e: Exception) {
            Log.e(TAG, "Failed listing services", e)
            ReflectionExplorer.instances.add(
                ErrorInstance("Failed listing local system services: ${e.message}", e)
            )
        }
    }
}
