package com.example.abxoverflow.droppedapk

import android.app.ActivityThread
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.ServiceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.abxoverflow.droppedapk.SystemProcessTrampolineActivity.Companion.EXTRA_EXPLICIT_PROCESS
import com.example.abxoverflow.droppedapk.SystemProcessTrampolineActivity.Companion.EXTRA_SELECT_PROCESS
import com.example.abxoverflow.droppedapk.SystemProcessTrampolineActivity.Companion.EXTRA_TARGET_INTENT
import com.example.abxoverflow.droppedapk.databinding.FragmentMainBinding
import com.example.abxoverflow.droppedapk.utils.readToString
import com.example.abxoverflow.droppedapk.utils.showAlert
import me.timschneeberger.reflectionexplorer.ReflectionExplorer.launchMainActivity
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter

class RootFragment : Fragment() {
    private lateinit var binding: FragmentMainBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMainBinding.inflate(inflater, container, false)

        binding.btnShell.setOnClickListener {
            val input = EditText(requireContext())
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Run command")
                .setPositiveButton(
                    "Run"
                ) { _, _ ->
                    // Prevent dialog from closing automatically
                    try {
                        val process = Runtime.getRuntime().exec(input.getText().toString())
                        val out = process.inputStream.readToString(false)
                        val err = process.errorStream.readToString(true)

                        input.setText("")
                        requireContext().showAlert("Result", out + "\n" + err)
                    } catch (e: IOException) {
                        Toast.makeText(
                            requireContext(),
                            "IOException while starting",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.e(TAG, "Failed to start shell", e)

                        val sw = StringWriter()
                        val pw = PrintWriter(sw)
                        e.printStackTrace(pw)
                        requireContext().showAlert("Error", sw.toString())
                    }
                }
                .setNegativeButton("Close", null)
                .create()
            dialog.setView(input)
            dialog.show()
        }

        binding.btnInspect.setOnClickListener { _ -> launchMainActivity(requireContext()) }

        updateInternalDexButtonText(binding.btnInternalDex)
        binding.btnInternalDex.setOnClickListener { v: View ->
            try {
                val enabled = Mods.forcedInternalDexScreenModeEnabled
                Mods.forcedInternalDexScreenModeEnabled = !enabled

                if (!enabled) {
                    // Turning on... give some time for DEX to initialize
                    binding.btnInternalDex.setEnabled(false)
                    binding.btnInternalDex.text = "Starting internal Samsung DEX screen..."
                    v.postDelayed({
                        binding.btnInternalDex.setEnabled(true)
                        Mods.setDexExternalMouseConnected(true)
                        updateInternalDexButtonText(binding.btnInternalDex)
                    }, 3000)
                } else {
                    // Turning off...
                    updateInternalDexButtonText(binding.btnInternalDex)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start/stop internal dex", e)
                Toast.makeText(
                    requireContext(),
                    "Failed to start/stop internal DEX screen: " + e + " (" + e.message + ")",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnSwitchProcess.setOnClickListener { _: View? ->
            startActivity(
                Intent(requireContext(), SystemProcessTrampolineActivity::class.java)
                    .putExtra(EXTRA_SELECT_PROCESS, true)
                    .putExtra(
                        EXTRA_TARGET_INTENT,
                        Intent(requireContext(), MainActivity::class.java)
                    )
            )
        }

        val s = StringBuilder()
            .append(
                "Note: Installation of this app involved registering new signature trusted for sharedUserId=android.uid.system," +
                        " if you uninstall usual way it will stay in system" +
                        " and you will be able to reinstall this app despite mismatched signature." +
                        " To fully uninstall use \"Uninstall\" button within this app" +
                        "\n\nuid="
            ).append(Process.myUid())
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

        binding.appText.text = s.toString()

        return binding.root
    }

    private fun updateInternalDexButtonText(btn: Button) = try {
        btn.text = if (Mods.forcedInternalDexScreenModeEnabled)
            "Stop internal Samsung DEX screen (ID=${Mods.dexDisplayId})"
        else
            "Start internal Samsung DEX screen"
    } catch (e: Exception) {
        Log.e(TAG, "updateInternalDexButtonText: ", e)
        btn.text = "Internal Samsung DEX screen unsupported"
        btn.isEnabled = false
    }

    private val currentProcessName: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Process.myProcessName()
        } else {
            ActivityThread.currentProcessName()
        }


    companion object {
        private const val TAG = "DroppedApk_RootFragment"
    }
}
