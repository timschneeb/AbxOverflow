package com.example.abxoverflow.droppedapk.terminal

import java.io.File

/**
 * Non-system shell implementation: runs `sh -c <cmd>` in the remembered cwd and streams output.
 */
class LocalShell(initialCwd: File, onOutput: (String) -> Unit) :
    TerminalShell(initialCwd, onOutput) {

    override fun execute(cmdLine: String) {
        var proc: Process? = null
        try {
            val pb = ProcessBuilder("sh", "-c", cmdLine)
            pb.directory(cwd)
            pb.redirectErrorStream(true)
            proc = pb.start()
            streamProcess(proc)
        } catch (e: Exception) {
            onOutput("Command error: ${e}\n")
        } finally {
            try { proc?.destroy() } catch (_: Exception) {}
        }
    }
}
