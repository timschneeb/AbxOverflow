package com.example.abxoverflow.droppedapk

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.ServiceManager
import android.util.Log
import com.example.abxoverflow.droppedapk.utils.readToString
import com.example.abxoverflow.droppedapk.utils.toast
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Objects
import java.util.function.BiFunction

@SuppressLint("PrivateApi")
object Mods {
    private const val TAG = "DroppedAPK_Mods"

    fun runAllSystemServer() {
        enablePermissionManagerDelegate()
    }

    /**
     * Finds the path to the Shizuku starter binary.
     * Throws PackageManager.NameNotFoundException if Shizuku is not installed.
     */
    fun findShizukuStarterPath(context: Context): String? {
        // Get base directory from apk path
        val info: ApplicationInfo =
            context.packageManager.getApplicationInfo("moe.shizuku.privileged.api", 0)
        val path = info.sourceDir
            .substring(0, info.sourceDir.lastIndexOf('/'))
            .let { dir ->
                "$dir/lib/arm64/libshizuku.so"
            }

        if(File(path).exists())
            return path
        return null
    }

    fun startShizuku(context: Context) {
        try {
            findShizukuStarterPath(context).let { starter ->
                Runtime.getRuntime().exec(starter).run {
                    // Implicitly logs output
                    inputStream.readToString(false)
                    errorStream.readToString(true)
                }
            }

            context.toast("Shizuku launched")
        } catch (_: PackageManager.NameNotFoundException) {
            // Ignored
        } catch (e: Exception) {
            context.toast("Exception while starting Shizuku")
            Log.e(TAG, "Failed to start Shizuku", e)
        }
    }

    fun enablePermissionManagerDelegate() {
        try {
            val serviceImpl = ServiceManager.getService("permissionmgr")
            if (serviceImpl == null) {
                Log.w(TAG, "service binder is null")
                return
            }

            val permMgrCls: Class<*> = serviceImpl.javaClass
            val innerBinaryName = $$"$${permMgrCls.getName()}$CheckPermissionDelegate"
            val svcLoader = permMgrCls.getClassLoader()
            val delegateCls = Class.forName(innerBinaryName, false, svcLoader)
            Log.i(TAG, "Found CheckPermissionDelegate via service classloader: " + delegateCls.getName())

            var delegateInstance: Any? = null
            if (delegateCls.isInterface) {
                delegateInstance = Proxy.newProxyInstance(
                    svcLoader, arrayOf<Class<*>?>(delegateCls)
                ) { proxy: Any?, method: Method?, args: Array<Any?>? ->
                    if (method!!.name == "checkPermission" && !(args!![0] as String).let {
                        it != "com.android.shell" && it != "moe.shizuku.privileged.api"
                        }
                    ) {
                        // Forward to TriFunction.apply(Object, Object, Integer)
                        // -> Default code path for other apps
                        val triCls =
                            Class.forName("com.android.internal.util.function.TriFunction")
                        val apply = triCls.getMethod(
                            "apply",
                            Any::class.java,
                            Any::class.java,
                            Any::class.java
                        )
                        return@newProxyInstance (apply.invoke(
                            args[3],
                            args[0],
                            args[1],
                            args[2] as Int?
                        ) as Int)
                    }
                    val uidWhitelist: MutableList<Int?> = ArrayList()
                    uidWhitelist.add(1000) // TODO
                    uidWhitelist.add(1001) // Required for Shizuku on 1001

                    if (method.name == "checkUidPermission" && !uidWhitelist.contains(
                            args!![0] as Int
                        )
                    ) {
                        // Forward to BiFunction.apply(Object, Object)
                        // -> Default code path for other apps
                        @Suppress("UNCHECKED_CAST")
                        return@newProxyInstance (args[2] as BiFunction<Any?, Any?, Any?>).apply(
                            args[0] as Int?,
                            args[1]
                        )
                    }

                    Log.i(
                        TAG,
                        method.name + "(" + (args?.contentToString() ?: "") + ") called"
                    )

                    val rt = method.returnType
                    if (rt == Boolean::class.javaPrimitiveType) return@newProxyInstance true
                    if (rt == Byte::class.javaPrimitiveType) return@newProxyInstance 0.toByte()
                    if (rt == Short::class.javaPrimitiveType) return@newProxyInstance 0.toShort()
                    if (rt == Int::class.javaPrimitiveType) return@newProxyInstance 0 // treat as PERMISSION_GRANTED

                    if (rt == Long::class.javaPrimitiveType) return@newProxyInstance 0L
                    if (rt == Float::class.javaPrimitiveType) return@newProxyInstance 0f
                    if (rt == Double::class.javaPrimitiveType) return@newProxyInstance 0.0
                    if (rt == Char::class.javaPrimitiveType) return@newProxyInstance '\u0000'
                    null
                }
            } else {
                Log.e(TAG, "CheckPermissionDelegate type not found, passing null")
            }

            // Find and invoke the setter
            val setter = serviceImpl.javaClass.getDeclaredMethod(
                "setCheckPermissionDelegateLocked",
                delegateCls
            )
            setter.isAccessible = true
            setter.invoke(serviceImpl, delegateInstance)
            Log.i(TAG, "PermissionManagerService.setCheckPermissionDelegateLocked invoked")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to set PermissionManagerService delegate", t)
        }
    }

    var forcedInternalDexScreenModeEnabled: Boolean
        get() {
            try {
                val state: Any = dexState
                val enabledMethod =
                    state.javaClass.getDeclaredMethod("isForcedInternalScreenModeEnabled")
                return enabledMethod.invoke(state) as Boolean
            } catch (e: Exception) {
                Log.e(TAG, "isDexModeEnabled: ", e)
                throw RuntimeException(e)
            }
        }
        set(enabled) {
            try {
                val dexStateMgr =
                    Objects.requireNonNull<Any>(dexStateManager)
                val setMethod = dexStateMgr.javaClass.getMethod(
                    "setForcedInternalScreenModeEnabled",
                    Boolean::class.javaPrimitiveType
                )
                setMethod.invoke(dexStateMgr, enabled)
            } catch (e: Exception) {
                Log.e(TAG, "enableDexMode: ", e)
                throw RuntimeException(e)
            }
        }

    val dexDisplayId: Int
        get() {
            try {
                val state: Any = dexState
                val getMethod =
                    state.javaClass.getDeclaredMethod("getDesktopDisplayId")
                return getMethod.invoke(state) as Int
            } catch (e: Exception) {
                Log.e(TAG, "getDexDisplayId: ", e)
                throw RuntimeException(e)
            }
        }

    private val dexState: Any
        get() {
            try {
                val dexStateMgr =
                    Objects.requireNonNull<Any>(dexStateManager)
                val getMethod =
                    dexStateMgr.javaClass.getDeclaredMethod("getState")
                return getMethod.invoke(dexStateMgr)!!
            } catch (e: Exception) {
                Log.e(TAG, "getDexState: ", e)
                throw RuntimeException(e)
            }
        }

    fun setDexExternalMouseConnected(enabled: Boolean) {
        try {
            val dexStateMgr = Objects.requireNonNull<Any>(dexStateManager)
            val setMouseConn = dexStateMgr.javaClass.getMethod(
                "setMouseConnected",
                Boolean::class.javaPrimitiveType
            )
            setMouseConn.invoke(dexStateMgr, enabled)
            val setTouchpadOn = dexStateMgr.javaClass.getMethod(
                "setTouchpadEnabled",
                Boolean::class.javaPrimitiveType
            )
            setTouchpadOn.invoke(dexStateMgr, !enabled)
        } catch (e: Exception) {
            Log.e(TAG, "connectDexMouse: ", e)
            throw RuntimeException(e)
        }
    }

    private val dexStateManager: Any?
        get() {
            try {
                val serviceManager =
                    Class.forName("android.os.ServiceManager")
                val serviceObj = serviceManager
                    .getMethod("getService", String::class.java)
                    .invoke(null, "desktopmode")
                if (serviceObj == null) {
                    Log.e(TAG, "getDexStateManager: serviceObj is null")
                    return null
                }

                val stateMgrField =
                    serviceObj.javaClass.getDeclaredField("mStateManager")
                stateMgrField.isAccessible = true
                return stateMgrField.get(serviceObj)
            } catch (e: Exception) {
                Log.e(TAG, "getDexStateManager: ", e)
                throw RuntimeException(e)
            }
        }
}
