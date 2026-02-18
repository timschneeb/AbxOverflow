package com.example.abxoverflow.droppedapk.fragment

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
import android.content.pm.PackageManager
import android.os.Build
import android.os.ServiceManager
import android.util.ArrayMap
import android.util.Log
import android.view.View
import com.example.abxoverflow.droppedapk.BuildConfig
import com.example.abxoverflow.droppedapk.utils.createProgressDialog
import com.example.abxoverflow.droppedapk.utils.showAlert
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.helper.ObjectHelper.`-Static`.objectHelper
import me.timschneeberger.reflectionexplorer.utils.cast
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * System-only fragment that lists installed packages and allows overriding an app's
 * network security config to disable certificate pinning. Only available when running in system_server.
 */
class CertPinningAppListFragment : BaseAppListFragment() {
    override fun onAppClicked(target: View, pkg: String, position: Int) =
        disableCertPinning(pkg, position)

    override fun shouldShowPackage(info: PackageInfo) = true

    override fun queryPackageStatus(pkgName: String): String = when (getNetworkSecurityState(pkgName)) {
        NetworkSecurityConfigState.ORIGINAL_UNSET -> ""
        NetworkSecurityConfigState.ORIGINAL_SET -> "built-in"
        NetworkSecurityConfigState.INJECTED -> "injected"
        NetworkSecurityConfigState.ERROR -> "<error>"
    }

    @SuppressLint("NotifyDataSetChanged", "RequestInstallPackagesPolicy", "WrongConstant",
        "DiscouragedApi"
    )
    private fun disableCertPinning(pkgName: String, position: Int) {
        context?.let { ctx ->
            val progressDialog = ctx.createProgressDialog("Preparing system...").apply {
                show()
            }

            Thread {
                try {
                    val pm = ServiceManager.getService("package")
                        .objectHelper()
                        .getObject("this$0")!!

                    // Set this app as the source of the overlay config signature.
                    // We can then install an overlay config with the same signature as this app.
                    pm.objectHelper()
                        .setObject("mOverlayConfigSignaturePackage", BuildConfig.APPLICATION_ID)

                    val overlayPkgName = "com.networksecurity.resinject"

                    // Install overlay APK from assets using PackageInstaller if not present.
                    var installed = true
                    try {
                        ctx.packageManager.getPackageInfo(overlayPkgName, 0)
                    } catch (_: PackageManager.NameNotFoundException) {
                        installed = false
                    }

                    if (!installed) {
                        val overlayAssetName = "networksecurity-overlay-aligned-signed.apk"

                        val packageInstaller = ctx.packageManager.packageInstaller
                        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            params.setRequireUserAction(USER_ACTION_NOT_REQUIRED)
                        }
                        val sessionId = packageInstaller.createSession(params)
                        val latch = CountDownLatch(1)
                        val resultCode = AtomicInteger(PackageInstaller.STATUS_FAILURE)

                        /*
                         * Necessary to avoid exception due to unprotected broadcast from system.
                         * 'android.net.netmon.lingerExpired' prefix will be treated as a protected system broadcast.
                         * Ref: https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java;l=5515;drc=61197364367c9e404c7da6900658f1b16c42d0da
                         */
                        val action = "android.net.netmon.lingerExpired.com.example.abxoverflow.INSTALL_COMPLETE_$sessionId"
                        val intent = Intent(action).apply {
                            putExtra("sessionId", sessionId)
                        }

                        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        val pendingIntent = PendingIntent.getBroadcast(ctx, sessionId, intent, piFlags)

                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(c: Context, i: Intent) {
                                val status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
                                Log.i("CertPinning", "Received install result for session $sessionId: $status")
                                resultCode.set(status)
                                latch.countDown()
                            }
                        }

                        ctx.registerReceiver(receiver, IntentFilter(action))

                        activity?.runOnUiThread { progressDialog.setTitle("Installing resource overlay...") }

                        val session = packageInstaller.openSession(sessionId)
                        session.use { s ->
                            ctx.assets.open(overlayAssetName).use { assetStream ->
                                val out = s.openWrite("overlay.apk", 0, -1)
                                assetStream.copyTo(out)
                                out.flush()
                                out.close()
                            }
                            s.commit(pendingIntent.intentSender)
                        }

                        // Wait for install result (timeout after 30s)
                        val ok = latch.await(30, TimeUnit.SECONDS)
                        try {
                            ctx.unregisterReceiver(receiver)
                        } catch (_: Exception) {
                        }

                        if (!ok || resultCode.get() != PackageInstaller.STATUS_SUCCESS) {
                            throw Exception("Failed to install overlay apk, status=${resultCode.get()}")
                        }

                        Log.i("CertPinning", "Overlay APK installed successfully")
                    } else {
                        Log.i("CertPinning", "Overlay $overlayPkgName already installed")
                    }

                    activity?.runOnUiThread { progressDialog.setTitle("Patching network config...") }

                    // Repeat the same step for the idmap manager, so we can enable the overlay.
                    val om = ServiceManager.getService("overlay")
                    om
                        .objectHelper().getObject("this$0")!!
                        .objectHelper().getObject("mImpl")!!
                        .objectHelper().getObject("mIdmapManager")!!
                        .objectHelper().setObject("mConfigSignaturePackage", BuildConfig.APPLICATION_ID)

                    om.javaClass
                        .methodFinder()
                        .filterByName("setEnabled")
                        .filterByParamTypes(String::class.java, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                        .first()
                        .invoke(om, overlayPkgName, true, /* userId */ 0)

                    // Finally, patch the target app's network security config to point to our injected config in the overlay.
                    pm.objectHelper().getObject("mLiveComputer")!!
                        .objectHelper().getObjectUntilSuperclass("mPackages")!!
                        .objectHelper().getObject("mStorage")!!
                        .cast<ArrayMap<String, Any>>() // <String, PackageImpl>
                        .getValue(pkgName)
                        .objectHelper()
                        .setObject(
                            "networkSecurityConfigRes",
                            ctx.resources.getIdentifier("power_profile_test", "xml", "android")
                        )

                } catch (e: Exception) {
                    Log.e("CertPinning", "Failed to disable cert pinning for $pkgName", e)
                    activity?.runOnUiThread { ctx.showAlert(e) }
                } finally {
                    activity?.runOnUiThread {
                        progressDialog.dismiss()
                        adapter.notifyItemChanged(position)
                    }
                }
            }.start()

        }
    }

    @SuppressLint("DiscouragedApi")
    private fun getNetworkSecurityState(pkgName: String): NetworkSecurityConfigState = try {
        val res = ServiceManager.getService("package")
            .objectHelper()
            .getObject("this$0")!!
            .objectHelper().getObject("mLiveComputer")!!
            .objectHelper().getObjectUntilSuperclass("mPackages")!!
            .objectHelper().getObject("mStorage")!!
            .cast<ArrayMap<String, Any>>() // <String, PackageImpl>
            .getValue(pkgName)
            .objectHelper()
            .getObject("networkSecurityConfigRes") as Int

        when (res) {
            0 -> NetworkSecurityConfigState.ORIGINAL_UNSET
            resources.getIdentifier("power_profile_test", "xml", "android")
                -> NetworkSecurityConfigState.INJECTED
            else -> NetworkSecurityConfigState.ORIGINAL_SET
        }
    } catch (e: Exception) {
        Log.e("CertPinning", "Failed to get network security config state for $pkgName", e)
        NetworkSecurityConfigState.ERROR
    }

    enum class NetworkSecurityConfigState {
        ORIGINAL_UNSET, ORIGINAL_SET, INJECTED, ERROR
    }
}
