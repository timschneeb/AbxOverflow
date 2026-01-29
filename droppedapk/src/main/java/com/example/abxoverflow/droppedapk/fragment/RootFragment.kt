package com.example.abxoverflow.droppedapk.fragment

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.preference.Preference
import com.example.abxoverflow.droppedapk.InstanceProvider
import com.example.abxoverflow.droppedapk.MainActivity
import com.example.abxoverflow.droppedapk.preference.MaterialSwitchPreference
import com.example.abxoverflow.droppedapk.Mods
import com.example.abxoverflow.droppedapk.R
import com.example.abxoverflow.droppedapk.SystemProcessTrampolineActivity
import com.example.abxoverflow.droppedapk.SystemProcessTrampolineActivity.Companion.EXTRA_EXPLICIT_PROCESS
import com.example.abxoverflow.droppedapk.SystemProcessTrampolineActivity.Companion.EXTRA_SELECT_PROCESS
import com.example.abxoverflow.droppedapk.SystemProcessTrampolineActivity.Companion.EXTRA_TARGET_INTENT
import com.example.abxoverflow.droppedapk.utils.currentProcessName
import com.example.abxoverflow.droppedapk.utils.isSystemServer
import com.example.abxoverflow.droppedapk.utils.toast
import me.timschneeberger.reflectionexplorer.ReflectionExplorer
import me.timschneeberger.reflectionexplorer.ReflectionExplorer.launch
import java.io.InputStreamReader

class RootFragment : BasePreferenceFragment() {

    private val shellPref: Preference by lazy { findPreference(getString(R.string.pref_key_shell_run))!! }
    private val reflPref: Preference by lazy { findPreference(getString(R.string.pref_key_reflection_explorer))!! }
    private val shizukuPref: Preference by lazy { findPreference(getString(R.string.pref_key_shizuku))!! }
    private val dexPref: MaterialSwitchPreference by lazy { findPreference(getString(R.string.pref_key_internal_dex))!! }
    private val switchPref: Preference by lazy { findPreference(getString(R.string.pref_key_switch_process))!! }
    private val infoPref: Preference by lazy { findPreference(getString(R.string.pref_key_info))!! }
    private val infoIdPref: Preference by lazy { findPreference(getString(R.string.pref_key_id_info))!! }

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
                Intent(requireContext(), SystemProcessTrampolineActivity::class.java)
                    .putExtra(EXTRA_SELECT_PROCESS, true)
                    .putExtra(
                        EXTRA_TARGET_INTENT,
                        Intent(requireContext(), MainActivity::class.java)
                    )
            )
            true
        }

        refreshInfo()
        refreshShizukuPref()
        refreshDexPref()
    }

    override fun onResume() {
        super.onResume()
        refreshShizukuPref()
        refreshDexPref()
        refreshInfo()
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
                summary = getString(R.string.internal_dex_screen_not_system_server)
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
            process=${currentProcessName}
            explicit_process=${requireActivity().intent.getStringExtra(EXTRA_EXPLICIT_PROCESS)}
            """.trimIndent()

        infoIdPref.summary = runCatching {
            InputStreamReader(Runtime.getRuntime().exec("id").inputStream)
                .readLines()
                .joinToString("\n")
        }.getOrElse(Throwable::stackTraceToString)
    }

    companion object {
        private const val TAG = "DroppedApk_PrefsFragment"
    }
}
