package com.example.abxoverflow.droppedapk.fragment

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import com.example.abxoverflow.droppedapk.R
import com.example.abxoverflow.droppedapk.databinding.FragmentTerminalBinding
import com.example.abxoverflow.droppedapk.terminal.InternalShell
import com.example.abxoverflow.droppedapk.terminal.LocalShell
import com.example.abxoverflow.droppedapk.terminal.TerminalShell
import com.example.abxoverflow.droppedapk.utils.currentProcessName
import com.example.abxoverflow.droppedapk.utils.isSystemServer
import com.example.abxoverflow.droppedapk.utils.truncateUtf8Bytes
import java.io.File
import java.util.concurrent.Executors

class TerminalFragment : BaseFragment() {
    private lateinit var binding: FragmentTerminalBinding

    private var shell: TerminalShell? = null

    var wrapEnabled: Boolean = true
        set(value) {
            field = value
            applyWrap()
        }

    private val io = Executors.newSingleThreadExecutor()

    private val MAX_HISTORY = 100
    private var history = mutableListOf<String>()
    private var historyIndex = -1 // -1 means no selection

    // Current working directory for internal/basic shell
    private var cwd: File = File("/")
    private val hostname: String? by lazy { currentProcessName }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = FragmentTerminalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val spacer = binding.terminalSpacer

        // Restore saved UI/state (if any) after configuration change
        if (savedInstanceState != null) {
            savedInstanceState.let { bs ->
                // cwd
                bs.getString(KEY_CWD)?.let { cwd = File(it) }
                // wrap
                wrapEnabled = bs.getBoolean(KEY_WRAP, wrapEnabled)
                // history
                (bs.getStringArrayList(KEY_HISTORY))?.let { hist ->
                    history = hist.toMutableList()
                    if (history.size > MAX_HISTORY) history =
                        history.subList(0, MAX_HISTORY).toMutableList()
                }
                historyIndex = bs.getInt(KEY_HISTORY_INDEX, historyIndex)
                // output
                bs.getString(KEY_OUTPUT)?.let { binding.terminalOutput.text = it }
                // input
                bs.getString(KEY_INPUT)?.let { binding.terminalInput.setText(it) }
            }
        } else {
            printInput("")
        }

        // Apply initial wrap setting (after restore)
        applyWrap()

        binding.terminalSend.setOnClickListener { onSend() }
        binding.terminalUp.setOnClickListener { onHistoryUp() }
        binding.terminalDown.setOnClickListener { onHistoryDown() }

        binding.terminalInput.setOnEditorActionListener { _, _, _ ->
            onSend(); true
        }

        // Keyboard handling for physical keyboard: Up/Down to navigate history, Ctrl-C to stop process
        binding.terminalInput.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false

            // Ctrl-C to stop currently running process
            if (event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_C) {
                killProcess()
                appendOutput("^C\n")
                return@setOnKeyListener true
            }

            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    onHistoryUp()
                    return@setOnKeyListener true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    onHistoryDown()
                    return@setOnKeyListener true
                }
            }

            false
        }

       ViewCompat.setWindowInsetsAnimationCallback(binding.root, object : WindowInsetsAnimationCompat.Callback(
            DISPATCH_MODE_CONTINUE_ON_SUBTREE
        ) {
            override fun onProgress(insets: WindowInsetsCompat, runningAnimations: MutableList<WindowInsetsAnimationCompat>): WindowInsetsCompat {
                val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val sysBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
                val bottomInset = if (imeBottom > sysBottom) imeBottom else sysBottom

                spacer.layoutParams = spacer.layoutParams.apply { height = bottomInset }
                spacer.requestLayout()

                // Keep content scrolled while animation runs
                binding.terminalScroll.post { binding.terminalScroll.fullScroll(View.FOCUS_DOWN) }
                return insets
            }
        })

        ViewCompat.requestApplyInsets(binding.root)

        // After restoring output, keep scrolled to bottom and place cursor at end of input
        binding.terminalScroll.post { binding.terminalScroll.fullScroll(View.FOCUS_DOWN) }
        binding.terminalInput.setSelection(binding.terminalInput.text?.length ?: 0)

        // Provide action bar menu items for terminal (clear / kill / wrap)
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: android.view.MenuInflater) {
                menuInflater.inflate(R.menu.menu_terminal, menu)
                // initialize wrap checked state
                menu.findItem(R.id.action_toggle_wrap)?.isChecked = wrapEnabled
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when (menuItem.itemId) {
                    R.id.action_clear -> {
                        clearOutput(); return true
                    }
                    R.id.action_kill -> {
                        killProcess(); return true
                    }
                    R.id.action_toggle_wrap -> {
                        wrapEnabled = !wrapEnabled
                        menuItem.isChecked = wrapEnabled
                        return true
                    }
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun onSend() {
         val cmd = binding.terminalInput.text?.toString()?.trim() ?: return
         if (cmd.isEmpty()) return

         // Save to history (unique newest-first)
         history.remove(cmd)
         history.add(0, cmd)
         while (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
         historyIndex = -1

         binding.terminalInput.setText("")
        printInput(cmd)

        // Choose shell implementation: internal for system_server or for builtins, otherwise sh -c in cwd.
        val first = TerminalShell.splitArgs(cmd).firstOrNull() ?: ""
        val isBuiltin = first == "cd" || first == "pwd"

        io.execute {
            try {
                val newShell = if (isBuiltin || isSystemServer)
                    InternalShell(cwd, ::appendOutput)
                else
                    LocalShell(cwd, ::appendOutput)
                shell = newShell
                newShell.execute(cmd)
                cwd = newShell.cwd
            } catch (e: Exception) {
                activity?.runOnUiThread { appendOutput("${e.stackTraceToString()}\n") }
            } finally {
                shell = null
            }
        }
     }

    private fun printInput(cmd: String) {
        appendOutput("\n$hostname:${cwd.absolutePath} $ $cmd\n")
    }

    private fun applyWrap() {
        activity?.runOnUiThread {
            binding.terminalOutput.apply {
                setHorizontallyScrolling(!wrapEnabled)
                isHorizontalScrollBarEnabled = !wrapEnabled
                movementMethod = if(wrapEnabled) null else ScrollingMovementMethod()
            }
        }
    }

    private fun onHistoryUp() {
        if (history.isEmpty()) return
        if (historyIndex + 1 < history.size) {
            historyIndex++
            binding.terminalInput.setText(history.getOrNull(historyIndex))
            binding.terminalInput.setSelection(binding.terminalInput.text?.length ?: 0)
        }
    }

    private fun onHistoryDown() {
        if (history.isEmpty()) return
        if (historyIndex - 1 >= 0) {
            historyIndex--
            binding.terminalInput.setText(history.getOrNull(historyIndex))
            binding.terminalInput.setSelection(binding.terminalInput.text?.length ?: 0)
        } else {
            historyIndex = -1
            binding.terminalInput.setText("")
        }
    }

    fun killProcess() {
        try {
            shell?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop process", e)
        }
    }

    fun clearOutput() {
        activity?.runOnUiThread { binding.terminalOutput.text = "" }
    }

    private fun appendOutput(s: String) {
        activity?.runOnUiThread {
            binding.terminalOutput.append(s)
            // scroll to bottom
            binding.terminalScroll.post { binding.terminalScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        try { io.shutdownNow() } catch (_: Exception) {}
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        try {
            outState.putString(KEY_CWD, cwd.absolutePath)
            outState.putBoolean(KEY_WRAP, wrapEnabled)
            outState.putStringArrayList(KEY_HISTORY, ArrayList(history))
            outState.putInt(KEY_HISTORY_INDEX, historyIndex)
            // Cap terminal output to 500 KB to avoid excessively large Bundles
            outState.putString(KEY_OUTPUT,
                binding.terminalOutput.text?.toString().truncateUtf8Bytes(500 * 1024 /* 500 KB */)
            )
            outState.putString(KEY_INPUT, binding.terminalInput.text?.toString() ?: "")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save terminal state", e)
        }
    }

    companion object {
        private const val TAG = "DroppedApk_Terminal"
        private const val KEY_CWD = "TerminalFragment.cwd"
        private const val KEY_WRAP = "TerminalFragment.wrap"
        private const val KEY_HISTORY = "TerminalFragment.history"
        private const val KEY_HISTORY_INDEX = "TerminalFragment.history_index"
        private const val KEY_OUTPUT = "TerminalFragment.output"
        private const val KEY_INPUT = "TerminalFragment.input"
    }
}
