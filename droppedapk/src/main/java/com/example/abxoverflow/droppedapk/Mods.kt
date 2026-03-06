package com.example.abxoverflow.droppedapk

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Parcel
import android.os.ServiceManager
import android.os.SystemProperties
import android.util.Log
import android.view.SurfaceControl
import com.example.abxoverflow.droppedapk.utils.injectedPreferences
import com.example.abxoverflow.droppedapk.utils.readToString
import com.example.abxoverflow.droppedapk.utils.toast
import dalvik.system.BaseDexClassLoader
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import me.timschneeberger.reflectionexplorer.utils.reflection.setField
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Objects
import java.util.function.BiFunction


@SuppressLint("PrivateApi")
object Mods {
    @SuppressLint("SoonBlockedPrivateApi")
    private fun testOverride() {
        setField(SurfaceControl::class.java.getDeclaredField("SECURE"), 0)




        val myLoader = Mods.javaClass.classLoader!! as BaseDexClassLoader
        Log.e(TAG, "My classloader: " + myLoader)

        BaseDexClassLoader::class.java.methodFinder()
            .filterByName("addDexPath")
            .filterByParamTypes(String::class.java)
            .first()
            .invoke(myLoader, "/system/framework/services.jar")
        // TODO: this causes confusion in ReflectionExplorer's static field scanner in non-system_serverbuilds!

        Log.e(TAG, "Added services.jar to classloader: " + myLoader)

        val loaded = myLoader.loadClass("com.android.server.LocalManagerRegistry")
        Log.e(TAG, "Loaded class: " + loaded)

        com.android.server.LocalManagerRegistry.addManager(Mods::class.java, "Hello!")

        // Instantiate and invoke Test.test() using reflection so selection is done at runtime
        try {
            val testClassName = "com.example.abxoverflow.droppedapk.utils.Test"
            val cls = Class.forName(testClassName)
            val ctor = cls.getDeclaredConstructor()
            ctor.isAccessible = true
            val instance = ctor.newInstance()
            val method = cls.getMethod("test")
            method.invoke(instance)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to invoke Test.test() via reflection", e)
        }
    }

    private const val TAG = "DroppedAPK_Mods"
    private var pkgWhitelist = emptyList<String>()
    private var uidWhitelist = emptyList<Int>()

    fun runAllSystemServer() {
        enablePermissionManagerDelegate()
        try {
            // TODO testOverride()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to run test", e)
        }
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

    fun applyPermissionWhitelist() {
        pkgWhitelist = injectedPreferences.getStringSet("permission_pkg_whitelist", emptySet())?.let {
            listOf("com.android.shell", "moe.shizuku.privileged.api") + it
        } ?: emptyList()
        uidWhitelist = injectedPreferences.getStringSet("permission_uid_whitelist", emptySet())?.let {
            it.mapNotNull(String::toIntOrNull) + listOf(
                1000,
                1001 // Required for Shizuku on 1001
            )
        } ?: emptyList()

        Log.i(TAG, "Updated permission whitelist: pkg=$pkgWhitelist, uid=$uidWhitelist")
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

            applyPermissionWhitelist()

            var delegateInstance: Any? = null
            if (delegateCls.isInterface) {
                delegateInstance = Proxy.newProxyInstance(
                    svcLoader, arrayOf<Class<*>?>(delegateCls)
                ) { proxy: Any?, method: Method?, args: Array<Any?>? ->
                    if (method!!.name == "checkPermission" && !pkgWhitelist.contains(args!![0] as String)) {
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

                    if (method.name == "checkUidPermission" && !uidWhitelist.contains(args!![0] as Int)) {
                        // Forward to BiFunction.apply(Object, Object)
                        // -> Default code path for other apps
                        @Suppress("UNCHECKED_CAST")
                        return@newProxyInstance (args[2] as BiFunction<Any?, Any?, Any?>).apply(
                            args[0] as Int?,
                            args[1]
                        )
                    }

                   /* Log.i(
                         TAG,
                         method.name + "(" + (args?.contentToString() ?: "") + ") called"
                     )*/

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

    var isBuildDebuggable: Boolean
        @SuppressLint("DiscouragedPrivateApi")
        get() = Build::class.java.getDeclaredField("IS_DEBUGGABLE").getBoolean(null)
        @SuppressLint("DiscouragedPrivateApi")
        set(enabled) {
            setField(Build::class.java.getDeclaredField("IS_DEBUGGABLE"), enabled)
        }

    var isDisplayNativeMode: Boolean
        get() = SystemProperties.getInt("persist.sys.sf.native_mode", 0) != 0
        set(enabled) {
            callSurfaceFlinger(1023) {
                writeInt(if(enabled) 1 else 0)
            }
            SystemProperties.set("persist.sys.sf.native_mode", if(enabled) "1" else "0")
        }

    var displaySaturation: Float
        get() = SystemProperties.get("persist.sys.sf.color_saturation", "1.0").toFloatOrNull() ?: 1.0f
        set(value) {
            callSurfaceFlinger(1022) {
                writeFloat(value)
            }
            SystemProperties.set("persist.sys.sf.color_saturation", value.toString())
        }

    fun callSurfaceFlinger(transactionId: Int, writePayload: Parcel.() -> Unit) {
        val surfaceFlinger = ServiceManager.getService("SurfaceFlinger") ?: return

        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken("android.ui.ISurfaceComposer")
            data.writePayload()
            // using IBinder.FLAG_ONEWAY: don't need to block for a reply.
            surfaceFlinger.transact(transactionId, data, null, 1)
        } finally {
            data.recycle()
        }
    }
}
