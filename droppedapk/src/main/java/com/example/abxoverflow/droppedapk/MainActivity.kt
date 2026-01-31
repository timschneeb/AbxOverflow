package com.example.abxoverflow.droppedapk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.example.abxoverflow.droppedapk.SystemProcessTrampolineActivity.Companion.EXTRA_EXPLICIT_PROCESS
import com.example.abxoverflow.droppedapk.SystemProcessTrampolineActivity.Companion.EXTRA_TARGET_INTENT
import com.example.abxoverflow.droppedapk.databinding.ActivityMainBinding
import com.example.abxoverflow.droppedapk.fragment.DebugAppListFragment
import com.example.abxoverflow.droppedapk.fragment.InstallSourceAppListFragment
import com.example.abxoverflow.droppedapk.fragment.RootFragment
import com.example.abxoverflow.droppedapk.fragment.TerminalFragment
import com.example.abxoverflow.droppedapk.utils.currentProcessName
import com.example.abxoverflow.droppedapk.utils.toast
import me.timschneeberger.reflectionexplorer.ReflectionExplorer
import me.timschneeberger.reflectionexplorer.ReflectionExplorer.IActivityLauncher

class MainActivity : AppCompatActivity() {
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
            RootFragment().also {
                supportFragmentManager.beginTransaction().replace(R.id.container, it).commit()
            }
        }

        binding.toolbar.apply {
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            setSupportActionBar(this)
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(supportFragmentManager.backStackEntryCount > 0)

        supportFragmentManager.addOnBackStackChangedListener {
            supportActionBar?.setDisplayHomeAsUpEnabled(
                supportFragmentManager.backStackEntryCount > 0
            )

            updateActionBarTitle()
        }

        updateActionBarTitle()
    }

    private fun updateActionBarTitle() {
        val frag = supportFragmentManager.findFragmentById(R.id.container)
        val title = when (frag) {
            is TerminalFragment -> getString(R.string.shell_terminal)
            is DebugAppListFragment -> getString(R.string.debug_app_list_title)
            is InstallSourceAppListFragment -> getString(R.string.install_source_title)
            else -> "${getString(R.string.app_name)} ($currentProcessName)"
        }
        supportActionBar?.title = title
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

    companion object {
        private const val TAG = "DroppedAPK"

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
