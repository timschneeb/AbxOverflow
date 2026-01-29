package com.example.abxoverflow.droppedapk

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.abxoverflow.droppedapk.databinding.FragmentTerminalBinding
import com.example.abxoverflow.droppedapk.utils.isSystemServer
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.Executors

class TerminalFragment : Fragment() {
    private lateinit var binding: FragmentTerminalBinding

    // Wrap state (true = word wrap enabled)
    private var wrapEnabled: Boolean = true

    private val io = Executors.newSingleThreadExecutor()

    private val MAX_HISTORY = 100
    private var history = mutableListOf<String>()
    private var historyIndex = -1 // -1 means no selection

    // Current working directory for internal/basic shell (kept in-memory)
    private var cwd: File = File("/")

    @Volatile
    private var currentProc: Process? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentTerminalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val spacer = binding.terminalSpacer

        // Restore saved UI/state (if any) after configuration change
        savedInstanceState?.let { bs ->
            // cwd
            bs.getString(KEY_CWD)?.let { cwd = File(it) }
            // wrap
            wrapEnabled = bs.getBoolean(KEY_WRAP, wrapEnabled)
            // history
            (bs.getStringArrayList(KEY_HISTORY))?.let { hist ->
                history = hist.toMutableList()
                if (history.size > MAX_HISTORY) history = history.subList(0, MAX_HISTORY).toMutableList()
            }
            historyIndex = bs.getInt(KEY_HISTORY_INDEX, historyIndex)
            // output
            bs.getString(KEY_OUTPUT)?.let { binding.terminalOutput.text = it }
            // input
            bs.getString(KEY_INPUT)?.let { binding.terminalInput.setText(it) }
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
                stopCurrentProcess()
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

         appendOutput("\n$ $cmd\n")

         // Builtins (cd/pwd/help) handled internally so we can update in-memory cwd
         val parts = splitArgs(cmd)
         val first = parts.firstOrNull() ?: ""
         val isBuiltin = first == "cd" || first == "pwd" || first == "help" || first == "?"

         if (isBuiltin) {
             io.execute { runInternalShellStream(cmd) }
             return
         }

         // If we're in system_server we must use internal handler; otherwise spawn a fresh sh -c in cwd
         if (isSystemServer) {
             io.execute { runInternalShellStream(cmd) }
         } else {
             io.execute { runShOnce(cmd) }
         }
     }

    // Run a one-shot sh -c command while preserving cwd by running `cd <cwd> && <cmd>` inside the shell.
    private fun runShOnce(command: String) {
        var proc: Process? = null
        try {
            val pb = ProcessBuilder("sh", "-c", command)
            pb.directory(cwd)
            pb.redirectErrorStream(true)
            proc = pb.start()
            currentProc = proc

            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val out = (line ?: "") + "\n"
                activity?.runOnUiThread { appendOutput(out) }
            }
            proc.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "sh command failed", e)
            activity?.runOnUiThread { appendOutput("Command error: ${e}\n") }
        } finally {
            if (currentProc === proc) currentProc = null
            try { proc?.destroy() } catch (_: Exception) {}
        }
    }

    // Stream internal shell output (works for builtins and executing external binaries while preserving cwd).
    private fun runInternalShellStream(cmdLine: String) {
        val parts = splitArgs(cmdLine)
        if (parts.isEmpty()) return
        val cmd = parts[0]
        val args = parts.drop(1)

        fun post(s: String) { activity?.runOnUiThread { appendOutput(s) } }

        when (cmd) {
            "pwd" -> post(cwd.absolutePath + "\n")
            "cd" -> {
                val target = if (args.isEmpty()) File(System.getProperty("user.home") ?: "/") else resolvePath(args[0])
                try {
                    if (target.exists() && target.isDirectory) {
                        cwd = target.canonicalFile
                    } else {
                        post("cd: no such file or directory: ${args.getOrNull(0)}\n")
                    }
                } catch (_: SecurityException) {
                    post("cd: permission denied: ${args.getOrNull(0)}\n")
                }
            }
            "help", "?" -> post("Builtins: pwd cd help\n")
            else -> {
                // For non-builtin, attempt to execute similarly to earlier implementation but stream the process output
                var proc: Process? = null
                try {
                    val cmdList = parts.toMutableList()
                    val exe = parts[0]

                    fun streamProcess(p: Process) {
                        currentProc = p
                        try {
                            val reader = BufferedReader(InputStreamReader(p.inputStream))
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                post(line + "\n")
                            }
                            p.waitFor()
                        } finally {
                            if (currentProc === p) currentProc = null
                        }
                    }

                    // If contains slash -> treat as path
                    if (exe.contains('/')) {
                        val resolved = if (exe.startsWith("/")) File(exe) else File(cwd, exe).canonicalFile
                        if (!resolved.exists()) { post("error: no such file: $exe\n"); return }
                        if (!resolved.canExecute()) { post("error: cannot execute: ${resolved.path}\n"); return }
                        cmdList[0] = resolved.absolutePath
                        val pb = ProcessBuilder(cmdList)
                        pb.directory(cwd)
                        pb.redirectErrorStream(true)
                        proc = pb.start()
                        streamProcess(proc)
                        return
                    }

                    // Try as-is
                    try {
                        val pb = ProcessBuilder(cmdList)
                        pb.directory(cwd)
                        pb.redirectErrorStream(true)
                        proc = pb.start()
                        streamProcess(proc)
                        return
                    } catch (_: Exception) {
                    } finally {
                        try { proc?.destroy() } catch (_: Exception) {}
                    }

                    // Search PATH
                    val pathEnv = System.getenv("PATH")
                    val searchDirs = (pathEnv?.split(':')?.filter { it.isNotBlank() } ?: listOf(
                        "/system/bin", "/system/xbin", "/vendor/bin", "/sbin", "/bin"
                    ))

                    for (dir in searchDirs) {
                        try {
                            val candidate = File(dir, exe)
                            if (candidate.exists() && candidate.canExecute()) {
                                cmdList[0] = candidate.absolutePath
                                val pb2 = ProcessBuilder(cmdList)
                                pb2.directory(cwd)
                                pb2.redirectErrorStream(true)
                                proc = pb2.start()
                                streamProcess(proc)
                                return
                            }
                        } catch (_: Exception) {
                        } finally {
                            try { proc?.destroy() } catch (_: Exception) {}
                        }
                    }

                    post("command not found: $exe\n")
                } catch (e: Exception) {
                    post("error: failed to execute '${parts.joinToString(" ")}': ${e}\n")
                } finally {
                    try { proc?.destroy() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun resolvePath(path: String): File {
        val p = if (path.startsWith("/")) File(path) else File(cwd, path)
        return p
    }

    // Very simple splitter: handles quoted strings (double or single) and whitespace
    private fun splitArgs(line: String): List<String> {
        val parts = mutableListOf<String>()
        var i = 0
        val n = line.length
        while (i < n) {
            while (i < n && line[i].isWhitespace()) i++
            if (i >= n) break
            val c = line[i]
            if (c == '"' || c == '\'') {
                val quote = c
                i++
                val sb = StringBuilder()
                while (i < n && line[i] != quote) {
                    sb.append(line[i++])
                }
                i++
                parts.add(sb.toString())
            } else {
                val sb = StringBuilder()
                while (i < n && !line[i].isWhitespace()) {
                    sb.append(line[i++])
                }
                parts.add(sb.toString())
            }
        }
        return parts
    }

    private fun appendOutput(s: String) {
        binding.terminalOutput.append(s)
        // scroll to bottom
        binding.terminalScroll.post { binding.terminalScroll.fullScroll(View.FOCUS_DOWN) }
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

    // Toggle wrap state and return the new state
    fun toggleWrap(): Boolean {
        wrapEnabled = !wrapEnabled
        applyWrap()
        return wrapEnabled
    }

    fun isWrapEnabled(): Boolean = wrapEnabled

    private fun applyWrap() {
        activity?.runOnUiThread {
            binding.terminalOutput.let { tv ->
                if (wrapEnabled) {
                    // Enable wrapping
                    tv.setHorizontallyScrolling(false)
                    tv.isHorizontalScrollBarEnabled = false
                    tv.movementMethod = null
                } else {
                    // Disable wrapping: allow horizontal scrolling
                    tv.setHorizontallyScrolling(true)
                    tv.isHorizontalScrollBarEnabled = true
                    tv.movementMethod = ScrollingMovementMethod()
                }
            }
        }
    }

    private fun stopCurrentProcess() {
        try {
            // If a specific process is running (e.g. started by runInternalShellStream), destroy it
            currentProc?.let {
                try { it.destroy() } catch (_: Exception) {}
                currentProc = null
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop process", e)
        }
    }

    fun killProcess() {
        stopCurrentProcess()
    }

    fun clearOutput() {
        activity?.runOnUiThread { binding.terminalOutput.text = "" }
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
            val rawOutput = binding.terminalOutput.text?.toString() ?: ""
            outState.putString(KEY_OUTPUT, truncatedOutputForSave(rawOutput, 500 * 1024 /* 500 KB */))
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
