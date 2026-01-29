package com.example.abxoverflow.droppedapk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.os.ServiceManager
import android.util.Log
import android.view.Menu
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
import com.example.abxoverflow.droppedapk.utils.showConfirmDialog
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
            PrefsFragment().also {
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

            invalidateOptionsMenu()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val result = super.onPrepareOptionsMenu(menu)
        val frag = supportFragmentManager.findFragmentById(R.id.container)
        val isTerminal = frag is TerminalFragment
        menu?.findItem(R.id.action_kill)?.isVisible = isTerminal
        menu?.findItem(R.id.action_clear)?.isVisible = isTerminal
        menu?.findItem(R.id.uninstall)?.isVisible = !isTerminal
        menu?.findItem(R.id.action_toggle_wrap)?.isVisible = isTerminal
        // Update wrap toggle checked state based on current state
        if (isTerminal) {
            menu?.findItem(R.id.action_toggle_wrap)?.isChecked = frag.isWrapEnabled()
        }
        return result
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle the action bar Up/Home button explicitly so it always navigates back
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        when (item.itemId) {
            R.id.action_toggle_wrap -> {
                val tf = supportFragmentManager.findFragmentById(R.id.container) as? TerminalFragment ?: return true
                val newState = tf.toggleWrap()
                item.isChecked = newState
                return true
            }
            R.id.action_kill -> {
                (supportFragmentManager.findFragmentById(R.id.container) as? TerminalFragment)?.killProcess()
                return true
            }
            R.id.action_clear -> {
                (supportFragmentManager.findFragmentById(R.id.container) as? TerminalFragment)?.clearOutput()
                return true
            }
            R.id.uninstall -> {
                showConfirmDialog(title = R.string.uninstall, message = R.string.uninstall_confirm) {
                    uninstall()
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @SuppressLint("MissingPermission")
    private fun uninstall() {
        try {
            // Delete <pastSigs> by directly editing PackageManagerService state within system_server
            // ServiceManager.getService("package").this$0.mSettings.mSharedUsers.get("android.uid.system").getSigningDetails().mPastSigningCertificates = null
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

            // Uninstall this app (also triggers write of fixed packages.xml)
            val nullArg: IntentSender = null!!
            packageManager.packageInstaller.uninstall(packageName, nullArg)
        } catch (e: Exception) {
            e.printStackTrace()
            toast(getString(R.string.uninstall_failed))
        }
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
                        Intent(context, SystemProcessTrampolineActivity::class.java)
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
