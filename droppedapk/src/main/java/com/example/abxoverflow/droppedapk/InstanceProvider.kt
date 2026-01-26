package com.example.abxoverflow.droppedapk

import android.annotation.SuppressLint
import android.os.ServiceManager
import android.util.Log
import me.timschneeberger.reflectionexplorer.Group
import me.timschneeberger.reflectionexplorer.Instance
import me.timschneeberger.reflectionexplorer.ReflectionExplorer
import me.timschneeberger.reflectionexplorer.utils.dex.ParamNames.additionalDexSearchPaths

object InstanceProvider {
    private const val TAG = "DroppedAPK_InstanceProvider"

    @SuppressLint("PrivateApi")
    fun collectInstances() {
        additionalDexSearchPaths.add("/system/framework/framework.jar")

        val serviceGroup = Group("Accessible System Services", null)

        // Get all services
        try {
            for (serviceName in ServiceManager.listServices()) {
                val serviceObj = ServiceManager.getService(serviceName)

                if (serviceObj == null) {
                    Log.w(TAG, "Service $serviceName is null, skipping")
                    continue
                }

                if (serviceObj.javaClass.getName() != "android.os.BinderProxy") ReflectionExplorer.instances.add(
                    Instance(serviceObj, serviceName, serviceGroup)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed listing services", e)
        }
    }
}
