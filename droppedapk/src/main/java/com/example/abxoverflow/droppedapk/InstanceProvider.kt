package com.example.abxoverflow.droppedapk

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityThread
import android.app.Application
import android.app.Service
import android.os.ServiceManager
import android.util.Log
import io.github.kyuubiran.ezxhelper.core.helper.ObjectHelper.`-Static`.objectHelper
import me.timschneeberger.reflectionexplorer.ErrorInstance
import me.timschneeberger.reflectionexplorer.Group
import me.timschneeberger.reflectionexplorer.Instance
import me.timschneeberger.reflectionexplorer.ReflectionExplorer
import me.timschneeberger.reflectionexplorer.utils.cast
import me.timschneeberger.reflectionexplorer.utils.dex.ParamNames.additionalDexSearchPaths

object InstanceProvider {
    private const val TAG = "DroppedAPK_InstanceProvider"

    private inline fun <T> T.runCollector(name: String, block: T.() -> Unit){
        runCatching(block).onFailure {
            "Failed to list ${name}: ${it.message}".let { msg ->
                Log.e(TAG, msg, it)
                ReflectionExplorer.instances.add(ErrorInstance(msg, it))
            }
        }
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    fun collectInstances() {
        additionalDexSearchPaths.add("/system/framework/framework.jar")

        ReflectionExplorer.instances.clear()

        // Collect ActivityThread internals via reflection
        try {
            val currentActivityThread = ActivityThread.currentActivityThread()
                ?: throw IllegalStateException("ActivityThread.currentActivityThread() returned null")

            // mActivities -> Map<IBinder, ActivityClientRecord>
            runCollector("active activities") {
                currentActivityThread
                    .objectHelper()
                    .getObject("mActivities")!!
                    .cast<Map<*, *>>() // <ActivityRecord$Token, ActivityClientRecord>
                    .values
                    .forEach { clientRecord ->
                        clientRecord!!.objectHelper()
                            .getObject("activity")
                            ?.cast<Activity>()
                            ?.let { activity ->
                                ReflectionExplorer.instances.add(Instance(activity, activity.javaClass.simpleName, Group("Active Activities", null)))
                            }
                }
            }

            // mAllApplications -> List<Application>
            runCollector("applications") {
                currentActivityThread
                    .objectHelper()
                    .getObject("mAllApplications")!!
                    .cast<List<Application>>()
                    .map { obj -> Instance(obj, obj.javaClass.simpleName, Group("Applications", null)) }
                    .let(ReflectionExplorer.instances::addAll)
            }

            // mServices -> Map<IBinder, Service>
            runCollector("services") {
                currentActivityThread
                    .objectHelper()
                    .getObject("mServices")!!
                    .cast<Map<*, *>>() // <ServiceRecord, Service>
                    .values
                    .mapNotNull { svc -> svc as? Service }
                    .map { svc -> Instance(svc, svc.javaClass.simpleName, Group("Services", null)) }
                    .let(ReflectionExplorer.instances::addAll)
            }

            // mLocalProvidersByName -> Map<ComponentName, ProviderClientRecord>
            runCollector("local providers") {
                currentActivityThread
                    .objectHelper()
                    .getObject("mLocalProvidersByName")!!
                    .cast<Map<*, *>>() // <ComponentName, ProviderClientRecord>
                    .values
                    .mapNotNull { it?.objectHelper()?.getObject("mLocalProvider") }
                    .map { obj -> Instance(obj, obj.javaClass.simpleName, Group("Local Providers", null)) }
                    .let(ReflectionExplorer.instances::addAll)
            }

        } catch (e: Exception) {
            Log.w(TAG, "Failed collecting ActivityThread instances", e)
            ReflectionExplorer.instances.add(ErrorInstance("Failed collecting ActivityThread instances: ${e.message}", e))
        }

        // Get all system services hosted in this process
        runCollector("system services") {
            ServiceManager
                .listServices()
                .mapNotNull { name ->
                    ServiceManager.getService(name)
                        ?.let { Instance(it, name, Group("System Services", null)) }
                }
                .filter { it.instance.javaClass.name != "android.os.BinderProxy" }
                .let(ReflectionExplorer.instances::addAll)
        }
    }
}
