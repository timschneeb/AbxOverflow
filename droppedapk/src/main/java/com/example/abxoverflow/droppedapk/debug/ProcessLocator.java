package com.example.abxoverflow.droppedapk.debug;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.util.SparseArray;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProcessLocator {
    private static String normalizeProcessName(String pkg, String proc) {
        if (proc == null || proc.isEmpty()) {
            return pkg;
        }
        if (proc.startsWith(":")) {
            return pkg + proc;
        }
        return proc;
    }

    public static Set<String> listActiveProcessNamesForUid(int uid) throws Exception {
        Set<String> result = new HashSet<>();

        // Get AMS
        Class<?> amClass = Class.forName("android.app.ActivityManager");
        Method getService = amClass.getDeclaredMethod("getService");
        Object ams = getService.invoke(null);

        // AMS.mProcessList
        Field plField = ams.getClass().getDeclaredField("mProcessList");
        plField.setAccessible(true);
        Object processList = plField.get(ams);

        // ProcessList.mProcessNames (ProcessMap)
        Field namesField = processList.getClass().getDeclaredField("mProcessNames");
        namesField.setAccessible(true);
        Object processMap = namesField.get(processList);

        // ProcessMap.getMap()
        @SuppressLint("BlockedPrivateApi")
        Method getMap = processMap.getClass().getClassLoader().loadClass("com.android.internal.app.ProcessMap")
                .getDeclaredMethod("getMap");
        getMap.setAccessible(true);
        Map<?, ?> map = (Map<?, ?>) getMap.invoke(processMap);

        for (Object entryObj : map.values()) {
            // entry is SparseArray<ProcessRecord>
            SparseArray<?> entry = (SparseArray<?>) entryObj;

            for (int i = 0; i < entry.size(); i++) {
                Object proc = entry.valueAt(i);

                Field uidField = proc.getClass().getDeclaredField("uid");
                uidField.setAccessible(true);
                if (uidField.getInt(proc) == uid) {
                    Field nameField = proc.getClass().getDeclaredField("processName");
                    nameField.setAccessible(true);
                    result.add((String) nameField.get(proc));
                }
            }
        }
        return result;
    }


    public static Set<String> listAllProcessNamesForSystemUid(Context context, int uid) {

        Set<String> result = new HashSet<>();

        PackageManager pm = context.getPackageManager();

        // Query *everything* for system apps
        List<PackageInfo> pkgs = pm.getInstalledPackages(
                PackageManager.GET_ACTIVITIES
                        | PackageManager.GET_SERVICES
                        | PackageManager.GET_RECEIVERS
                        | PackageManager.GET_PROVIDERS
                        | PackageManager.MATCH_SYSTEM_ONLY
        );

        for (PackageInfo pi : pkgs) {

            ApplicationInfo app = pi.applicationInfo;
            if (app == null || app.uid != uid) continue;

            String pkg = app.packageName;

            // Application default process
            result.add(normalizeProcessName(pkg, app.processName));

            // Activities
            if (pi.activities != null) {
                for (ActivityInfo ai : pi.activities) {
                    result.add(normalizeProcessName(pkg, ai.processName));
                }
            }

            // Services
            if (pi.services != null) {
                for (ServiceInfo si : pi.services) {
                    result.add(normalizeProcessName(pkg, si.processName));
                }
            }

            // Receivers
            if (pi.receivers != null) {
                for (ActivityInfo ri : pi.receivers) {
                    result.add(normalizeProcessName(pkg, ri.processName));
                }
            }

            // Providers
            if (pi.providers != null) {
                for (ProviderInfo pi2 : pi.providers) {
                    result.add(normalizeProcessName(pkg, pi2.processName));
                }
            }
        }

        return result;
    }

}

