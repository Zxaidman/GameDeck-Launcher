package io.github.zxaidman.kestrel.platform.shizuku

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Runs shell commands in a process Shizuku starts with shell privilege.
 *
 * Nothing here may block without a limit. Output is drained on its own thread while the caller
 * waits with a timeout, because a backgrounded child keeps the output pipe open after its parent
 * exits and a plain read-to-end never returns — the failure that froze the Phase 0 harness.
 */
class PrivilegedShellService : IPrivilegedShell.Stub {

    @Suppress("unused")
    constructor() : super()

    @Suppress("unused", "UNUSED_PARAMETER")
    constructor(context: Context) : super()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String, timeoutMs: Int): String = try {
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        runCatching { process.outputStream.close() }

        val output = StringBuilder()
        val drain = Thread {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line ->
                    synchronized(output) { output.appendLine(line) }
                }
            }
        }
        drain.isDaemon = true
        drain.start()

        val finished = process.waitFor(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        drain.join(300)

        val text = synchronized(output) { output.toString() }.trim()
        when {
            !finished -> if (text.isEmpty()) "(timed out after ${timeoutMs}ms)" else "$text\n(timed out)"
            text.isEmpty() -> "(no output, exit=${process.exitValue()})"
            else -> text
        }
    } catch (e: Exception) {
        "(failed: ${e.javaClass.simpleName}: ${e.message})"
    }
}
