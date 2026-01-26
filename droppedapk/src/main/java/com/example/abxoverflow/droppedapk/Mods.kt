package com.example.abxoverflow.droppedapk;

import android.annotation.SuppressLint;
import android.os.IBinder;
import android.os.ServiceManager;
import android.util.Log;

import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

@SuppressLint("PrivateApi")
public class Mods {
    private static final String TAG = "DroppedAPK_Mods";

    public static void runAll() {
        enablePermissionManagerDelegate();
    }

    public static void enablePermissionManagerDelegate() {
        try {
            IBinder serviceImpl = ServiceManager.getService("permissionmgr");
            if (serviceImpl == null) {
                Log.w(TAG, "service binder is null");
                return;
            }

            Class<?> permMgrCls = serviceImpl.getClass();
            String innerBinaryName = permMgrCls.getName() + "$CheckPermissionDelegate";
            ClassLoader svcLoader = permMgrCls.getClassLoader();
            Class<?> delegateCls = Class.forName(innerBinaryName, false, svcLoader);
            Log.i(TAG, "Found CheckPermissionDelegate via service classloader: " + delegateCls.getName());

            Object delegateInstance = null;
            if (delegateCls.isInterface()) {
                delegateInstance = java.lang.reflect.Proxy.newProxyInstance(svcLoader, new Class[]{delegateCls},
                        (proxy, method, args) -> {

                            if (method.getName().equals("checkPermission") && !((String)args[0]).startsWith("me.timschneeberger.unrestricted.")) {
                                // Forward to TriFunction.apply(Object, Object, Integer)
                                // -> Default code path for other apps
                                Class<?> triCls = Class.forName("com.android.internal.util.function.TriFunction");
                                Method apply = triCls.getMethod("apply", Object.class, Object.class, Object.class);
                                return ((Integer)apply.invoke(args[3], args[0], args[1], Integer.valueOf((Integer)args[2]))).intValue();
                            }

                            List<Integer> uidWhitelist = new ArrayList<>();
                            uidWhitelist.add(1000); // TODO

                            if (method.getName().equals("checkUidPermission") && !uidWhitelist.contains((int)args[0])) {
                                // Forward to BiFunction.apply(Object, Object)
                                // -> Default code path for other apps
                                return ((BiFunction)args[2]).apply(Integer.valueOf((Integer) args[0]), args[1]);
                            }

                            Log.i(TAG,  method.getName() + "(" + (args != null ? java.util.Arrays.toString(args) : "") + ") called");


                            Class<?> rt = method.getReturnType();
                            if (rt == boolean.class) return true;
                            if (rt == byte.class) return (byte) 0;
                            if (rt == short.class) return (short) 0;
                            if (rt == int.class) return 0; // treat as PERMISSION_GRANTED
                            if (rt == long.class) return 0L;
                            if (rt == float.class) return 0f;
                            if (rt == double.class) return 0d;
                            if (rt == char.class) return '\0';
                            // for Object/void, return null
                            return null;
                        });
            } else {
                Log.e(TAG, "CheckPermissionDelegate type not found, passing null");
            }

            // Find and invoke the setter
            Method setter = serviceImpl.getClass().getDeclaredMethod("setCheckPermissionDelegateLocked", delegateCls);
            setter.setAccessible(true);
            setter.invoke(serviceImpl, delegateInstance);
            Log.i(TAG, "PermissionManagerService.setCheckPermissionDelegateLocked invoked");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to set PermissionManagerService delegate", t);
        }
    }

    public static boolean getForcedInternalDexScreenModeEnabled() {
        try {
            Object state = getDexState();
            Method enabledMethod = state.getClass().getDeclaredMethod("isForcedInternalScreenModeEnabled");
            return (boolean) enabledMethod.invoke(state);
        }
        catch (Exception e) {
            Log.e(TAG, "isDexModeEnabled: ", e);
            throw new RuntimeException(e);
        }
    }

    public static int getDexDisplayId() {
        try {
            Object state = getDexState();
            Method getMethod = state.getClass().getDeclaredMethod("getDesktopDisplayId");
            return (int) getMethod.invoke(state);
        }
        catch (Exception e) {
            Log.e(TAG, "getDexDisplayId: ", e);
            throw new RuntimeException(e);
        }
    }

    private static Object getDexState() {
        try {
            Object dexStateMgr = Objects.requireNonNull(getDexStateManager());
            Method getMethod = dexStateMgr.getClass().getDeclaredMethod("getState");
            return getMethod.invoke(dexStateMgr);
        }
        catch (Exception e) {
            Log.e(TAG, "getDexState: ", e);
            throw new RuntimeException(e);
        }
    }

    public static void setForcedInternalDexScreenModeEnabled(boolean enabled) {
        try {
            Object dexStateMgr = Objects.requireNonNull(getDexStateManager());
            Method setMethod = dexStateMgr.getClass().getMethod("setForcedInternalScreenModeEnabled", boolean.class);
            setMethod.invoke(dexStateMgr, enabled);
        }
        catch (Exception e) {
            Log.e(TAG, "enableDexMode: ", e);
            throw new RuntimeException(e);
        }
    }

    public static void setDexExternalMouseConnected(boolean enabled) {
        try {
            Object dexStateMgr = Objects.requireNonNull(getDexStateManager());
            Method setMouseConn = dexStateMgr.getClass().getMethod("setMouseConnected", boolean.class);
            setMouseConn.invoke(dexStateMgr, enabled);
            Method setTouchpadOn = dexStateMgr.getClass().getMethod("setTouchpadEnabled", boolean.class);
            setTouchpadOn.invoke(dexStateMgr, !enabled);
        }
        catch (Exception e) {
            Log.e(TAG, "connectDexMouse: ", e);
            throw new RuntimeException(e);
        }
    }

    @Nullable
    private static Object getDexStateManager() {
        try {
            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            Object serviceObj = serviceManager
                    .getMethod("getService", String.class)
                    .invoke(null, "desktopmode");
            if (serviceObj == null) {
                Log.e(TAG, "getDexStateManager: serviceObj is null");
                return null;
            }

            Field stateMgrField = serviceObj.getClass().getDeclaredField("mStateManager");
            stateMgrField.setAccessible(true);
            return stateMgrField.get(serviceObj);
        }
        catch (Exception e) {
            Log.e(TAG, "getDexStateManager: ", e);
            throw new RuntimeException(e);
        }
    }
}
