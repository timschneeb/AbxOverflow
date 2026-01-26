package com.example.abxoverflow.droppedapk.process

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Process
import android.os.ServiceManager
import java.lang.reflect.Constructor
import java.lang.reflect.Method

object ProcessActivityLauncher {
    const val EXTRA_EXPLICIT_PROCESS: String =
        "com.example.abxoverflow.droppedapk.EXTRA_EXPLICIT_PROCESS"

    private val aMS: IBinder
        get() = ServiceManager.getService("activity")

    @get:SuppressLint("BlockedPrivateApi")
    private val aTMS: IBinder
        get() = ServiceManager.getService("activity_task")


    @Throws(Exception::class)
    fun launch(
        context: Context,
        pkg: String,
        activityCls: String,
        userId: Int,
        processName: String?
    ) {
        launch(
            context,
            Intent().setComponent(ComponentName(pkg, activityCls)),
            userId,
            processName
        )
    }

    @Throws(Exception::class)
    fun launch(
        context: Context,
        intent: Intent,
        userId: Int,
        processName: String?
    ) {
        requireNotNull(intent.getComponent()) { "Intent must have explicit component" }

        val pm = context.getPackageManager()
        val ai = pm.getActivityInfo(intent.getComponent()!!, PackageManager.MATCH_ALL)
        val appInfo = ai.applicationInfo

        val ams: Any = aMS
        val amsCls: Class<*> = ams.javaClass

        val hostingRecordCls =
            Class.forName("com.android.server.am.HostingRecord", true, amsCls.getClassLoader())

        val hostingCtor: Constructor<*> =
            hostingRecordCls.getDeclaredConstructor(
                String::class.java,
                String::class.java
            )

        val hostingRecord: Any =
            hostingCtor.newInstance(
                "activity",  // hosting type
                intent.getComponent()!!.flattenToShortString() // hosting name
            )

        var startProcessLocked: Method? = null

        for (m in amsCls.getDeclaredMethods()) {
            if (m.getName() == "startProcessLocked") {
                m.setAccessible(true)
                startProcessLocked = m
                break
            }
        }

        checkNotNull(startProcessLocked) { "startProcessLocked not found" }

        synchronized(ams) {
            startProcessLocked.invoke(
                ams,  // this
                processName,
                appInfo,
                false,  // knownToBeDead
                0,  // intentFlags
                hostingRecord,
                0,  // zygotePolicyFlags
                false,  // allowWhileBooting
                false // isolated
            )
        }


        // ------------------------------------------------------------
        // 5. Build Intent
        // ------------------------------------------------------------
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Forward explicit process info so the launched activity can persist and detect it
        intent.putExtra(EXTRA_EXPLICIT_PROCESS, processName)

        // ------------------------------------------------------------
        // 6. Create ActivityRecord reflectively
        // ------------------------------------------------------------
        val atms: Any = aTMS
        val atmsCls: Class<*> = atms.javaClass

        val arCls =
            Class.forName("com.android.server.wm.ActivityRecord", true, atmsCls.getClassLoader())

        val builderCls =
            Class.forName(
                "com.android.server.wm.ActivityRecord\$Builder",
                true,
                atmsCls.getClassLoader()
            )

        val builderCtor: Constructor<*> = builderCls.getDeclaredConstructor(atmsCls)
        builderCtor.setAccessible(true)
        val builder: Any = builderCtor.newInstance(atms)

        // 3Set required fields
        builderCls.getDeclaredMethod("setActivityInfo", ActivityInfo::class.java)
            .invoke(builder, ai)
        builderCls.getDeclaredMethod("setIntent", Intent::class.java).invoke(builder, intent)
        builderCls.getDeclaredMethod("setLaunchedFromPid", Int::class.javaPrimitiveType)
            .invoke(builder, Process.myPid())
        builderCls.getDeclaredMethod("setLaunchedFromUid", Int::class.javaPrimitiveType)
            .invoke(builder, userId)
        builderCls.getDeclaredMethod("setLaunchedFromPackage", String::class.java)
            .invoke(builder, intent.getPackage())
        builderCls.getDeclaredMethod("setComponentSpecified", Boolean::class.javaPrimitiveType)
            .invoke(builder, intent.getComponent() != null)

        // Build ActivityRecord
        val build = builderCls.getDeclaredMethod("build")
        build.setAccessible(true)
        val activityRecord = build.invoke(builder)

        // Override activityRecord process name
        val processNameField = arCls.getDeclaredField("processName")
        processNameField.setAccessible(true)
        processNameField.set(activityRecord, processName)

        // ------------------------------------------------------------
        // 7. startActivityUnchecked
        // ------------------------------------------------------------
        val actStarterCls =
            Class.forName("com.android.server.wm.ActivityStarter", true, atmsCls.getClassLoader())
        var startActivityUnchecked: Method? = null
        for (m in actStarterCls.getDeclaredMethods()) {
            if (m.getName() == "startActivityUnchecked") {
                m.setAccessible(true)
                startActivityUnchecked = m
                break
            }
        }

        val startCtrl = atmsCls.getDeclaredMethod("getActivityStartController").invoke(atms)
        val obtainStarter = startCtrl!!.javaClass.getDeclaredMethod(
            "obtainStarter",
            Intent::class.java,
            String::class.java
        )
        val starter = obtainStarter.invoke(startCtrl, intent, "DebugActivityLauncher")

        startActivityUnchecked!!.invoke(
            starter,
            activityRecord,
            null,  // sourceRecord (ActivityRecord)
            null,  // voiceSession (IVoiceInteractionSession)
            null,  // voiceInteractor (IVoiceInteractor)
            0,  // startFlags
            null,  // options (ActivityOptions)
            null,  // inTask (Task)
            null,  // inTaskFragment (TaskFragment)
            0,  // balVerdict (BalVerdict)
            null,  // intentGrants (NeededUriGrants)
            userId // realCallingUid
            // Only on newer versions:
            // null, // transition (Transition)
            // true  isIndependentLaunch

        )
    }
}
