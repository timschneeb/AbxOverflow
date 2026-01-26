package com.example.abxoverflow.droppedapk;

import android.annotation.SuppressLint;
import android.os.IBinder;
import android.os.ServiceManager;
import android.util.Log;

import me.timschneeberger.reflectionexplorer.Group;
import me.timschneeberger.reflectionexplorer.Instance;
import me.timschneeberger.reflectionexplorer.ReflectionExplorer;
import me.timschneeberger.reflectionexplorer.utils.dex.ParamNames;

public class InstanceProvider {

    @SuppressLint("PrivateApi")
    static void collectInstances() {
        ParamNames.INSTANCE.getAdditionalDexSearchPaths().add("/system/framework/framework.jar");

        Group serviceGroup = new Group("Accessible System Services", null);

        // Get all services
        try {
            for (String serviceName : ServiceManager.listServices()) {
                IBinder serviceObj = ServiceManager.getService(serviceName);

                if (serviceObj == null) {
                    Log.w(TAG, "Service " + serviceName + " is null, skipping");
                    continue;
                }

                if (!serviceObj.getClass().getName().equals("android.os.BinderProxy"))
                    ReflectionExplorer.instances.add(new Instance(serviceObj, serviceName, serviceGroup));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed listing services", e);
        }
    }
}
