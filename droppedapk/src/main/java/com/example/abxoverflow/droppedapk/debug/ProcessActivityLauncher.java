package com.example.abxoverflow.droppedapk.debug;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.ServiceManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ProcessActivityLauncher {

    public static final String EXTRA_EXPLICIT_PROCESS = "com.example.abxoverflow.droppedapk.EXTRA_EXPLICIT_PROCESS";

    private static IBinder getAMS() {
        return ServiceManager.getService("activity");
    }

    @SuppressLint("BlockedPrivateApi")
    private static IBinder getATMS() {
        return ServiceManager.getService("activity_task");
    }


    public static void launch(
            Context context,
            String pkg,
            String activityCls,
            int userId,
            String processName) throws Exception {
        launch(
                context,
                new Intent().setComponent(new ComponentName(pkg, activityCls)),
                userId,
                processName
        );
    }

    public static void launch(
            Context context,
            Intent intent,
            int userId,
            String processName) throws Exception {

        if (intent.getComponent() == null) {
            throw new IllegalArgumentException("Intent must have explicit component");
        }

        PackageManager pm = context.getPackageManager();
        ActivityInfo ai = pm.getActivityInfo(intent.getComponent(), PackageManager.MATCH_ALL);
        ApplicationInfo appInfo = ai.applicationInfo;

        Object ams = getAMS();
        Class<?> amsCls = ams.getClass();

        Class<?> hostingRecordCls =
                Class.forName("com.android.server.am.HostingRecord", true, amsCls.getClassLoader());

        Constructor<?> hostingCtor =
                hostingRecordCls.getDeclaredConstructor(
                        String.class,
                        String.class
                );

        Object hostingRecord =
                hostingCtor.newInstance(
                        "activity", // hosting type
                        intent.getComponent().flattenToShortString() // hosting name
                );

        Method startProcessLocked = null;

        for (Method m : amsCls.getDeclaredMethods()) {
            if (m.getName().equals("startProcessLocked")) {
                m.setAccessible(true);
                startProcessLocked = m;
                break;
            }
        }

        if (startProcessLocked == null) {
            throw new IllegalStateException("startProcessLocked not found");
        }

        synchronized (ams) {
            startProcessLocked.invoke(
                    ams, // this
                    processName,
                    appInfo,
                    false, // knownToBeDead
                    0, // intentFlags
                    hostingRecord,
                    0, // zygotePolicyFlags
                    false, // allowWhileBooting
                    false // isolated
            );
        }


        // ------------------------------------------------------------
        // 5. Build Intent
        // ------------------------------------------------------------

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Forward explicit process info so the launched activity can persist and detect it
        intent.putExtra(EXTRA_EXPLICIT_PROCESS, processName);

        // ------------------------------------------------------------
        // 6. Create ActivityRecord reflectively
        // ------------------------------------------------------------

        Object atms = getATMS();
        Class<?> atmsCls = atms.getClass();

        Class<?> arCls =
                Class.forName("com.android.server.wm.ActivityRecord", true, atmsCls.getClassLoader());

        Class<?> builderCls =
                Class.forName("com.android.server.wm.ActivityRecord$Builder", true, atmsCls.getClassLoader());

        Constructor<?> builderCtor = builderCls.getDeclaredConstructor(atmsCls);
        builderCtor.setAccessible(true);
        Object builder = builderCtor.newInstance(atms);

// 3Set required fields
        builderCls.getDeclaredMethod("setActivityInfo", ActivityInfo.class).invoke(builder, ai);
        builderCls.getDeclaredMethod("setIntent", Intent.class).invoke(builder, intent);
        builderCls.getDeclaredMethod("setLaunchedFromPid", int.class).invoke(builder, android.os.Process.myPid());
        builderCls.getDeclaredMethod("setLaunchedFromUid", int.class).invoke(builder, userId);
        builderCls.getDeclaredMethod("setLaunchedFromPackage", String.class).invoke(builder, intent.getPackage());
        builderCls.getDeclaredMethod("setComponentSpecified", boolean.class).invoke(builder, intent.getComponent() != null);

// Build ActivityRecord
        Method build = builderCls.getDeclaredMethod("build");
        build.setAccessible(true);
        Object activityRecord = build.invoke(builder);

        // Override activityRecord process name
        Field processNameField = arCls.getDeclaredField("processName");
        processNameField.setAccessible(true);
        processNameField.set(activityRecord, processName);

        // ------------------------------------------------------------
        // 7. startActivityUnchecked
        // ------------------------------------------------------------

        Class<?> actStarterCls =
                Class.forName("com.android.server.wm.ActivityStarter", true, atmsCls.getClassLoader());
        Method startActivityUnchecked = null;
        for (Method m : actStarterCls.getDeclaredMethods()) {
            if (m.getName().equals("startActivityUnchecked")) {
                m.setAccessible(true);
                startActivityUnchecked = m;
                break;
            }
        }

        Object startCtrl = atmsCls.getDeclaredMethod("getActivityStartController").invoke(atms);
        Method obtainStarter = startCtrl.getClass().getDeclaredMethod("obtainStarter", Intent.class, String.class);
        Object starter = obtainStarter.invoke(startCtrl, intent, "DebugActivityLauncher");

        startActivityUnchecked.invoke(
                starter,
                activityRecord,
                null, // sourceRecord (ActivityRecord)
                null, // voiceSession (IVoiceInteractionSession)
                null, // voiceInteractor (IVoiceInteractor)
                0, // startFlags
                null, // options (ActivityOptions)
                null, // inTask (Task)
                null, // inTaskFragment (TaskFragment)
                0, // balVerdict (BalVerdict)
                null, // intentGrants (NeededUriGrants)
                userId // realCallingUid

                // Only on newer versions:
                // null, // transition (Transition)
                // true  isIndependentLaunch
        );

    }

}
