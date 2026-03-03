package com.example.abxoverflow.droppedapk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.example.abxoverflow.droppedapk.SystemProcessTrampolineActivity.Companion.EXTRA_EXPLICIT_PROCESS
import com.example.abxoverflow.droppedapk.SystemProcessTrampolineActivity.Companion.EXTRA_TARGET_INTENT
import com.example.abxoverflow.droppedapk.SystemProcessTrampolineActivity.Companion.EXTRA_TARGET_UID
import com.example.abxoverflow.droppedapk.databinding.ActivityMainBinding
import com.example.abxoverflow.droppedapk.fragment.RootFragment
import com.example.abxoverflow.droppedapk.utils.currentProcessName
import com.example.abxoverflow.droppedapk.utils.toast
import me.timschneeberger.reflectionexplorer.ReflectionExplorer
import me.timschneeberger.reflectionexplorer.ReflectionExplorer.IActivityLauncher

class MainActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load frida-gadget if requested
        if (intent.getBooleanExtra(EXTRA_ATTACH_FRIDA_GADGET, false)) {
            try {
                System.loadLibrary("frida-gadget-android")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load frida-gadget-android library", e)
                toast(getString(R.string.frida_gadget_load_failed, e.message))
            }
        }

        // Needed to override activity launching to support explicit process selection at runtime
        ReflectionExplorer.activityLauncher = IActivityLauncher(::startActivityWithProcess)

        // Apply various runtime modifications
        Mods.runAllSystemServer()

        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater).also { setContentView(it.root) }

        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> { leftMargin = insets.left; topMargin = insets.top; rightMargin = insets.right }
            WindowInsetsCompat.CONSUMED
        }

        if (savedInstanceState == null) {
            val fragment = RootFragment()
            @Suppress("DEPRECATION")
            fragment.setTargetFragment(null, 0)

            supportFragmentManager
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit()
        }
        else {
            supportActionBar?.title = savedInstanceState.getString(PERSIST_TITLE)
        }

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                supportActionBar?.title = "${getString(R.string.app_name)} ($currentProcessName)"
            }
            else {
                supportActionBar?.title = supportFragmentManager.getBackStackEntryAt(supportFragmentManager.backStackEntryCount - 1).name
            }
            supportActionBar?.setDisplayHomeAsUpEnabled(supportFragmentManager.backStackEntryCount > 0)
        }

        binding.toolbar.apply {
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            setSupportActionBar(this)
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(supportFragmentManager.backStackEntryCount > 0)
        requestDisableBatteryOptimizationsIfNeeded()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(PERSIST_TITLE, supportActionBar?.title.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle the action bar Up/Home button explicitly so it always navigates back
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove our activity launcher to avoid leaking this Activity instance into a static field
        ReflectionExplorer.activityLauncher = null
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = pref.fragment?.let {
            supportFragmentManager.fragmentFactory.instantiate(
                classLoader,
                it)
        }
        fragment ?: return false

        fragment.arguments = args
        @Suppress("DEPRECATION")
        fragment.setTargetFragment(caller, 0)

        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.container, fragment)
            .addToBackStack(pref.title.toString())
            .commit()
        return true
    }

    /**
     * Check whether the app is already whitelisted from battery optimizations and, if not,
     * launch the system dialog to request the user to add the app to the whitelist.
     */
    @SuppressLint("BatteryLife")
    private fun requestDisableBatteryOptimizationsIfNeeded() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val isIgnoring = pm.isIgnoringBatteryOptimizations(packageName)

        if (!isIgnoring) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:$packageName".toUri()
                }
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                // Fallback to the general battery optimization settings screen
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    companion object {
        private const val TAG = "DroppedAPK"

        private const val PERSIST_TITLE = "title"
        private const val EXTRA_ATTACH_FRIDA_GADGET = "com.example.abxoverflow.droppedapk.extra.ATTACH_FRIDA_GADGET"

        fun startActivityWithProcess(context: Context, intent: Intent) {
            // Assuming the caller is an activity context, copy the process extra if present.
            var explicitProcess = intent.getStringExtra(EXTRA_EXPLICIT_PROCESS)
            if (context is Activity) {
                explicitProcess = context.intent.getStringExtra(EXTRA_EXPLICIT_PROCESS)
            } else {
                Log.w(TAG, "Context is not an Activity, cannot copy explicit process extra")
            }

            try {
                if (explicitProcess != null) {
                    context.startActivity(
                        Intent()
                            .setComponent(SystemProcessTrampolineActivity.component)
                            .putExtra(EXTRA_EXPLICIT_PROCESS, explicitProcess)
                            .putExtra(EXTRA_TARGET_UID, Process.myUid())
                            .putExtra(EXTRA_TARGET_INTENT, intent)
                    )
                } else {
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Process-aware launch failed", e)
                context.toast(e)
            }
        }
    }
}
