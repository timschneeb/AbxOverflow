package com.example.abxoverflow.droppedapk

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityThread
import android.app.Application
import android.app.Service
import android.content.Context
import android.os.ServiceManager
import android.util.ArrayMap
import android.util.Log
import com.example.abxoverflow.droppedapk.utils.unwrapContext
import io.github.kyuubiran.ezxhelper.core.helper.ObjectHelper.`-Static`.objectHelper
import me.timschneeberger.reflectionexplorer.ErrorInstance
import me.timschneeberger.reflectionexplorer.Group
import me.timschneeberger.reflectionexplorer.Instance
import me.timschneeberger.reflectionexplorer.ReflectionExplorer
import me.timschneeberger.reflectionexplorer.utils.cast
import me.timschneeberger.reflectionexplorer.utils.dex.ParamNames.additionalDexSearchPaths

object InstanceProvider : ReflectionExplorer.IInstancesProvider {
    private const val TAG = "DroppedAPK_InstanceProvider"

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    override fun provide(context: Context): List<Instance> {
        additionalDexSearchPaths.add("/system/framework/framework.jar")

        Log.e(TAG, "Collecting instances...")

        val instances = mutableListOf<Instance>()

        fun runCollector(name: String, block: () -> Unit){
            runCatching(block).onFailure {
                "Failed to list ${name}: ${it.message}".let { msg ->
                    Log.e(TAG, msg, it)
                    instances.add(ErrorInstance(msg, it))
                }
            }
        }

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
                                instances.add(Instance(activity, activity.javaClass.simpleName, Group("Active Activities", null)))
                            }
                    }
            }

            // mAllApplications -> List<Application>
            runCollector("applications") {
                currentActivityThread
                    .objectHelper()
                    .getObject("mAllApplications")!!
                    .cast<List<Application>>()
                    .filter { it.packageName != BuildConfig.APPLICATION_ID }
                    .map { obj -> Instance(obj, obj.javaClass.simpleName, Group("Applications", null)) }
                    .let(instances::addAll)
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
                    .let(instances::addAll)
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
                    .let(instances::addAll)
            }

            // ArrayMap<Context, ArrayMap<BroadcastReceiver, ReceiverDispatcher>> mReceivers
            runCollector("receivers") {
                currentActivityThread
                    .objectHelper()
                    .getObject("mAllApplications")!!
                    .cast<List<Application>>()
                    .flatMap { app ->
                        app.unwrapContext()
                            .objectHelper()
                            .getObject("mPackageInfo")!!
                            .objectHelper()
                            .getObject("mReceivers")!!
                            .cast<ArrayMap<Context, Any>>() // <Context, ArrayMap<BroadcastReceiver, ReceiverDispatcher>>
                            .flatMap {
                                it.value
                                    .cast<ArrayMap<Any, Any>>()
                                    .values
                            }
                    }
                    .map { it.objectHelper().getObject("mReceiver")!! }
                    .map { receivers ->
                        Instance(
                            receivers,
                            receivers.javaClass.simpleName,
                            Group("Receivers", null)
                        )
                    }
                    .let(instances::addAll)
            }

        } catch (e: Exception) {
            Log.w(TAG, "Failed collecting ActivityThread instances", e)
            instances.add(ErrorInstance("Failed collecting ActivityThread instances: ${e.message}", e))
        }

        // Probe ContentService -> mRootNode -> mChildren (ObserverNode list)
        runCollector("observers (ContentService)") {
            ServiceManager.getService("content")!!
                // Return if not system_server
                .also { if(it.javaClass.name == "android.os.BinderProxy") return@runCollector }
                .objectHelper()
                .getObject("mRootNode")
                ?.objectHelper()
                ?.getObject("mChildren")
                ?.cast<List<*>>()
                ?.map { Instance(it!!, it.objectHelper().getObject("mName").toString(), Group("Observers", null)) }
                ?.let(instances::addAll)
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
                .let(instances::addAll)
        }

        Log.e(TAG, "Provided ${instances.size} instances")

        return instances
    }
}
