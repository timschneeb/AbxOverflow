package com.example.abxoverflow.droppedapk.terminal

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Common abstract shell that executes a command line and streams output via [onOutput].
 */
abstract class TerminalShell(
    initialCwd: File,
    protected val onOutput: (String) -> Unit
) {
    var cwd: File = initialCwd
        protected set

    @Volatile
    protected var currentProc: Process? = null

    /**
     * Execute the given command line. This call may block until the command completes.
     */
    abstract fun execute(cmdLine: String)

    /**
     * Stop the currently running process (if any).
     */
    open fun stop() {
        currentProc?.let(Process::destroy)
    }

    protected fun streamProcess(p: Process) {
        currentProc = p
        try {
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                onOutput((line ?: "") + "\n")
            }
            p.waitFor()
        } finally {
            if (currentProc === p) currentProc = null
        }
    }

    companion object {
        fun splitArgs(line: String): List<String> {
            val parts = mutableListOf<String>()
            var i = 0
            val n = line.length
            while (i < n) {
                while (i < n && line[i].isWhitespace()) i++
                if (i >= n) break
                val c = line[i]
                if (c == '"' || c == '\'') {
                    i++
                    val sb = StringBuilder()
                    while (i < n && line[i] != c) {
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
    }
}
