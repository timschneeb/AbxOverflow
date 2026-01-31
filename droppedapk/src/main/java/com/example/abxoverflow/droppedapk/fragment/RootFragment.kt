package com.example.abxoverflow.droppedapk.fragment

import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.os.ServiceManager
import android.system.Os
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.preference.Preference
import com.example.abxoverflow.droppedapk.InstanceProvider
import com.example.abxoverflow.droppedapk.MainActivity
import com.example.abxoverflow.droppedapk.Mods
import com.example.abxoverflow.droppedapk.R
import com.example.abxoverflow.droppedapk.SystemProcessTrampolineActivity
import com.example.abxoverflow.droppedapk.SystemProcessTrampolineActivity.Companion.EXTRA_EXPLICIT_PROCESS
import com.example.abxoverflow.droppedapk.SystemProcessTrampolineActivity.Companion.EXTRA_SELECT_PROCESS
import com.example.abxoverflow.droppedapk.SystemProcessTrampolineActivity.Companion.EXTRA_TARGET_INTENT
import com.example.abxoverflow.droppedapk.preference.MaterialSwitchPreference
import com.example.abxoverflow.droppedapk.utils.SignatureInjector
import com.example.abxoverflow.droppedapk.utils.canEditPersistProperties
import com.example.abxoverflow.droppedapk.utils.currentProcessName
import com.example.abxoverflow.droppedapk.utils.executeShell
import com.example.abxoverflow.droppedapk.utils.executeShellCatching
import com.example.abxoverflow.droppedapk.utils.isSamsungDevice
import com.example.abxoverflow.droppedapk.utils.isSystemServer
import com.example.abxoverflow.droppedapk.utils.packageSeInfo
import com.example.abxoverflow.droppedapk.utils.seInfo
import com.example.abxoverflow.droppedapk.utils.showAlert
import com.example.abxoverflow.droppedapk.utils.showConfirmDialog
import com.example.abxoverflow.droppedapk.utils.toast
import me.timschneeberger.reflectionexplorer.ReflectionExplorer
import me.timschneeberger.reflectionexplorer.ReflectionExplorer.launch

class RootFragment : BasePreferenceFragment() {

    private val shellPref: Preference by lazy { findPreference(getString(R.string.pref_key_shell_run))!! }
    private val reflPref: Preference by lazy { findPreference(getString(R.string.pref_key_reflection_explorer))!! }
    private val shizukuPref: Preference by lazy { findPreference(getString(R.string.pref_key_shizuku))!! }
    private val dexPref: MaterialSwitchPreference by lazy { findPreference(getString(R.string.pref_key_internal_dex))!! }
    private val switchPref: Preference by lazy { findPreference(getString(R.string.pref_key_switch_process))!! }
    private val systemListPref: Preference by lazy { findPreference(getString(R.string.pref_key_debug_app_list))!! }
    private val installSourcePref: Preference by lazy { findPreference(getString(R.string.pref_key_install_source))!! }
    private val infoPref: Preference by lazy { findPreference(getString(R.string.pref_key_info))!! }
    private val infoIdPref: Preference by lazy { findPreference(getString(R.string.pref_key_id_info))!! }
    private val multiuserPref: MaterialSwitchPreference by lazy { findPreference(getString(R.string.pref_key_multiuser))!! }
    private val injectSharedUidKeysPref: Preference by lazy { findPreference(getString(R.string.pref_key_inject_shared_uid_keyset))!! }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        shellPref.setOnPreferenceClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, TerminalFragment())
                .addToBackStack("terminal")
                .commit()
            true
        }

        reflPref.setOnPreferenceClickListener {
            Log.e(TAG, "Launching Reflection Explorer into current process")
            ReflectionExplorer.instancesProvider = InstanceProvider
            launch(requireContext())
            true
        }

        shizukuPref.setOnPreferenceClickListener {
            Mods.startShizuku(requireContext())
            refreshShizukuPref()
            true
        }

        dexPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            try {
                Mods.forcedInternalDexScreenModeEnabled = value as Boolean

                if (value) {
                    // give some time to initialize
                    dexPref.isEnabled = false
                    dexPref.summary = getString(R.string.internal_dex_screen_subtitle)
                    view?.postDelayed({
                        Mods.setDexExternalMouseConnected(true)
                        refreshDexPref()
                    }, 3000)
                } else {
                    refreshDexPref()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle internal dex", e)
                requireContext().toast(getString(R.string.internal_dex_screen_error, e.toString(), e.message ?: "?"))
            }
            true
        }

        switchPref.setOnPreferenceClickListener {
            startActivity(
                Intent()
                    .setComponent(SystemProcessTrampolineActivity.component)
                    .putExtra(EXTRA_SELECT_PROCESS, true)
                    .putExtra(
                        EXTRA_TARGET_INTENT,
                        Intent(requireContext(), MainActivity::class.java)
                    )
            )
            true
        }

        systemListPref.apply {
            setOnPreferenceClickListener {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.container, DebugAppListFragment())
                    .addToBackStack("system_app_list")
                    .commit()
                true
            }

            if (!isSystemServer) {
                summary = getString(R.string.system_server_only_feature)
                isEnabled = false
            } else {
                summary = getString(R.string.debug_app_list_subtitle)
                isEnabled = true
            }
        }

        installSourcePref.apply {
            setOnPreferenceClickListener {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.container, InstallSourceAppListFragment())
                    .addToBackStack("install_source_app_list")
                    .commit()
                true
            }

            if (!isSystemServer) {
                summary = getString(R.string.system_server_only_feature)
                isEnabled = false
            } else {
                summary = getString(R.string.install_source_subtitle)
                isEnabled = true
            }
        }

        multiuserPref.apply {
            if (!isSamsungDevice) {
                // Only Samsung devices have accessible persist.* properties for multiuser. AOSP only has fw.*
                summary = getString(R.string.samsung_only_feature)
                isEnabled = false
            } else if (!context.canEditPersistProperties) {
                summary = getString(R.string.system_only_feature)
                isEnabled = false
            }

            setOnPreferenceClickListener {
                try {
                    val currentlyEnabled = executeShell("getprop persist.sys.show_multiuserui")
                        .trim()
                        .let { it == "1" || it == "true" }

                    if (currentlyEnabled) {
                        executeShell("setprop persist.sys.max_users 1")
                        executeShell("setprop persist.sys.show_multiuserui 0")
                    } else {
                        executeShell("setprop persist.sys.max_users 8")
                        executeShell("setprop persist.sys.show_multiuserui 1")
                    }
                } catch (e: Exception) {
                    context.showAlert(getString(R.string.error), e.toString())
                }

                refreshMultiuserPref()
                true
            }
        }

        injectSharedUidKeysPref.apply {
            if (!isSystemServer) {
                summary = getString(R.string.system_server_only_feature)
                isEnabled = false
            }
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                SignatureInjector.showDialog(context)
                true
            }
        }

        refreshInfo()
        refreshShizukuPref()
        refreshDexPref()
        refreshMultiuserPref()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: android.view.MenuInflater) {
                menuInflater.inflate(R.menu.menu_root, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.uninstall) {
                    context?.showConfirmDialog(title = R.string.uninstall, message = R.string.uninstall_confirm, onConfirm = ::performUninstall)
                    return true
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onResume() {
        super.onResume()
        refreshShizukuPref()
        refreshDexPref()
        refreshInfo()
        refreshMultiuserPref()
    }

    private fun refreshMultiuserPref() {
        try {
            multiuserPref.isChecked = executeShellCatching("getprop persist.sys.show_multiuserui")
                .trim()
                .let { it == "1" || it == "true" }
        } catch (e: Exception) {
            multiuserPref.summary = getString(R.string.system_server_only_feature)
            Log.e(TAG, "refreshMultiuserPref: ", e)
        }
    }

    private fun refreshShizukuPref() {
        shizukuPref.apply {
            if(isSystemServer) {
                summary = getString(R.string.shizuku_is_system_server)
                isEnabled = false
                return@apply
            }

            try {
                val noStarter = Mods.findShizukuStarterPath(requireContext()) == null
                title = getString(R.string.shizuku_title)
                isEnabled = !noStarter
                summary = if (noStarter) getString(R.string.shizuku_no_starter) else getString(R.string.shizuku_subtitle)
            } catch (_: PackageManager.NameNotFoundException) {
                title = getString(R.string.shizuku_title)
                summary = getString(R.string.shizuku_not_installed)
                isEnabled = false
            } catch (e: Exception) {
                title = getString(R.string.shizuku_title)
                summary = getString(R.string.shizuku_error_find_starter, e)
                isEnabled = false
                Log.e(TAG, "refreshShizukuPref: ", e)
            }
        }

    }

    private fun refreshDexPref() {
        dexPref.apply {
            if(!isSystemServer) {
                summary = getString(R.string.system_server_only_feature)
                isEnabled = false
                isChecked = false
                return@apply
            }

            try {
                title = getString(R.string.internal_dex_screen_title)
                summary = if (Mods.forcedInternalDexScreenModeEnabled)
                    getString(R.string.internal_dex_screen_subtitle_on, Mods.dexDisplayId)
                else
                    getString(R.string.internal_dex_screen_subtitle)
                isEnabled = true
                isChecked = Mods.forcedInternalDexScreenModeEnabled
            } catch (e: Exception) {
                summary = getString(R.string.internal_dex_screen_unsupported)
                isEnabled = false
                Log.e(TAG, "refreshDexPref: ", e)
            }
        }
    }

    private fun refreshInfo() {
        infoPref.summary = """
            uid=${Process.myUid()}
            pid=${Process.myPid()}
            ppid=${Os.getppid()}
            seinfo=${seInfo}
            seinfo_pkg=${requireContext().packageSeInfo}
            process=${currentProcessName}
            explicit_process=${requireActivity().intent.getStringExtra(EXTRA_EXPLICIT_PROCESS)}
            """.trimIndent()

        executeShellCatching("id").let { out ->
            infoIdPref.summary = out
            infoIdPref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                requireContext().showAlert(getString(R.string.result), out)
                true
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun performUninstall() {
        try {
            val packManImplService: Any = ServiceManager.getService("package")
            val packManService = packManImplService.javaClass.getDeclaredField("this$0").run {
                isAccessible = true
                get(packManImplService)
            }

            val settings = packManService.javaClass.getDeclaredField("mSettings").run {
                isAccessible = true
                get(packManService)
            }
            val sharedUser = settings.javaClass.getDeclaredField("mSharedUsers").run {
                isAccessible = true
                (get(settings) as MutableMap<*, *>)["android.uid.system"]!!
            }

            val signingDetails = sharedUser.javaClass.getMethod("getSigningDetails").invoke(sharedUser)
            signingDetails.javaClass.getDeclaredField("mPastSigningCertificates").apply {
                isAccessible = true
                set(signingDetails, null)
            }

            val nullArg: IntentSender = null!!
            requireContext().run {
                packageManager.packageInstaller.uninstall(packageName, nullArg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "performUninstall: ", e)
            requireContext().toast(getString(R.string.uninstall_failed))
        }
    }

    companion object {
        private const val TAG = "DroppedApk_PrefsFragment"
    }
}
