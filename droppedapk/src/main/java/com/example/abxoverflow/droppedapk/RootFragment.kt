package com.example.abxoverflow.droppedapk

import android.app.ActivityThread
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.ServiceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.abxoverflow.droppedapk.SystemProcessTrampolineActivity.Companion.EXTRA_EXPLICIT_PROCESS
import com.example.abxoverflow.droppedapk.SystemProcessTrampolineActivity.Companion.EXTRA_SELECT_PROCESS
import com.example.abxoverflow.droppedapk.SystemProcessTrampolineActivity.Companion.EXTRA_TARGET_INTENT
import com.example.abxoverflow.droppedapk.databinding.FragmentMainBinding
import com.example.abxoverflow.droppedapk.utils.readToString
import com.example.abxoverflow.droppedapk.utils.showAlert
import com.example.abxoverflow.droppedapk.utils.showInputAlert
import com.example.abxoverflow.droppedapk.utils.toast
import me.timschneeberger.reflectionexplorer.ReflectionExplorer
import me.timschneeberger.reflectionexplorer.ReflectionExplorer.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter

class RootFragment : Fragment() {
    private lateinit var binding: FragmentMainBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMainBinding.inflate(inflater, container, false)
            .also { binding = it }
            .apply {
                appText.text = getInfoString()

                btnShell.setOnClickListener {
                    requireContext().showInputAlert(
                        layoutInflater,
                        "Run command",
                        "Shell command",
                    ) {
                        try {
                            val process = Runtime.getRuntime().exec(it)
                            val out = process.inputStream.readToString(false)
                            val err = process.errorStream.readToString(true)

                            requireContext().showAlert("Result", out + "\n" + err)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start shell", e)

                            val sw = StringWriter()
                            val pw = PrintWriter(sw)
                            e.printStackTrace(pw)
                            requireContext().showAlert("Error", sw.toString())
                        }
                    }
                }

                btnInspect.setOnClickListener { _ ->
                    Log.e(TAG, "Launching Reflection Explorer into current process")
                    ReflectionExplorer.instancesProvider = InstanceProvider
                    launch(requireContext())
                }

                updateShizukuButton()
                btnShizuku.setOnClickListener {
                    Mods.startShizuku(requireContext())
                }

                updateInternalDexButtonText()
                btnInternalDex.setOnClickListener { v: View ->
                    try {
                        val enabled = Mods.forcedInternalDexScreenModeEnabled
                        Mods.forcedInternalDexScreenModeEnabled = !enabled

                        if (!enabled) {
                            // Turning on... give some time for DEX to initialize
                            btnInternalDex.setEnabled(false)
                            btnInternalDex.text = "Starting internal Samsung DEX screen..."
                            v.postDelayed({
                                btnInternalDex.setEnabled(true)
                                Mods.setDexExternalMouseConnected(true)
                                updateInternalDexButtonText()
                            }, 3000)
                        } else {
                            // Turning off...
                            updateInternalDexButtonText()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start/stop internal dex", e)
                        requireContext().toast(
                            "Failed to start/stop internal DEX screen: " + e + " (" + e.message + ")",
                        )
                    }
                }

                btnSwitchProcess.setOnClickListener { _: View? ->
                    startActivity(
                        Intent(requireContext(), SystemProcessTrampolineActivity::class.java)
                            .putExtra(EXTRA_SELECT_PROCESS, true)
                            .putExtra(
                                EXTRA_TARGET_INTENT,
                                Intent(requireContext(), MainActivity::class.java)
                            )
                    )
                }
            }.run {
                root
            }
    }

    override fun onResume() {
        super.onResume()
        updateShizukuButton()
        updateInternalDexButtonText()
    }

    private fun updateShizukuButton() {
        binding.btnShizuku.apply {
            try {
                val noStarter = Mods.findShizukuStarterPath(requireContext()) == null
                text = if (noStarter) "libshizuku.so not found or missing permission"
                else "Start Shizuku in current process"
                isEnabled = !noStarter
            }
            catch (_: PackageManager.NameNotFoundException) {
                text = "Shizuku not installed"
                isEnabled = false
            }
            catch (e: Exception) {
                Log.e(TAG, "updateShizukuButton: ", e)
                text = "Error while locating Shizuku starter"
                isEnabled = false
            }
        }

    }

    private fun updateInternalDexButtonText() = binding.btnInternalDex.apply {
        try {
            text = if (Mods.forcedInternalDexScreenModeEnabled)
                "Stop internal Samsung DEX screen (ID=${Mods.dexDisplayId})"
            else
                "Start internal Samsung DEX screen"
        } catch (e: Exception) {
            Log.e(TAG, "updateInternalDexButtonText: ", e)
            text = "Internal Samsung DEX screen unsupported"
            isEnabled = false
        }
    }

    private val currentProcessName: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Process.myProcessName()
        } else {
            ActivityThread.currentProcessName()
        }

    private fun getInfoString(): String {
        val s = StringBuilder()
            .append("uid=").append(Process.myUid())
            .append("\npid=").append(Process.myPid())
            .append("\nprocess=").append(currentProcessName)
            .append("\nexplicit_process=")
            .append(requireActivity().intent.getStringExtra(EXTRA_EXPLICIT_PROCESS))
            .append("\n\n").append(
                runCatching {
                    BufferedReader(
                        InputStreamReader(
                            Runtime.getRuntime().exec("id").inputStream
                        )
                    ).readLine()
                }.getOrDefault("?")
            )
            .append("\n\nBelow is list of system services, as this app loads into system_server it can directly tamper with local ones (those that are non-null and non-BinderProxy)")

        try {
            for (serviceName in ServiceManager.listServices()) {
                s.append("\n\n")
                    .append(serviceName)
                    .append(":\n")
                    .append(ServiceManager.getService(serviceName).toString())
            }
        } catch (_: Exception) {
            s.append("\n\nFailed listing services")
        }
        return s.toString()
    }

    companion object {
        private const val TAG = "DroppedApk_RootFragment"
    }
}
