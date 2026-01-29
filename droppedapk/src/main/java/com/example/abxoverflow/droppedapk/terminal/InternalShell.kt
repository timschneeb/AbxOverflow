package com.example.abxoverflow.droppedapk.terminal

import java.io.File

/**
 * Internal/basic shell that implements a small set of builtins and executes binaries directly.
 * Execution preserves the in-memory cwd and streams output via onOutput.
 */
class InternalShell(initialCwd: File, onOutput: (String) -> Unit) : TerminalShell(initialCwd, onOutput) {
    override fun execute(cmdLine: String) {
        val parts = splitArgs(cmdLine)
        if (parts.isEmpty()) return
        val cmd = parts[0]
        val args = parts.drop(1)

        fun post(s: String) { onOutput(s) }

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
                var proc: Process? = null
                try {
                    val cmdList = parts.toMutableList()
                    val exe = parts[0]

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
                    post("error: failed to execute '${parts.joinToString(" ") }': ${e}\n")
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
}
