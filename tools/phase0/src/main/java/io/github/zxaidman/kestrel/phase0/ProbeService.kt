package io.github.zxaidman.kestrel.phase0

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * EXPERIMENTAL — Phase 0 harness only.
 *
 * Shizuku starts this class in a separate process holding shell privileges. It runs one shell
 * command and returns its combined output.
 *
 * **Nothing here may block indefinitely.** An earlier version read the child's output to end of
 * file and waited for the process without a limit. Both assumptions are wrong in the presence of a
 * backgrounded child: a process that inherits the output pipe keeps it open after its parent exits,
 * so the read never ends, and on a real run this froze the harness completely — the worker thread
 * never returned, every control stayed locked, and the session produced no evidence at all.
 *
 * Output is therefore drained on a separate thread while the main path waits with a timeout, and a
 * command that overruns is killed and reported as having overrun. A reading that says "timed out"
 * is a result. A frozen instrument is not.
 */
class ProbeService : IProbeService.Stub {

    @Suppress("unused")
    constructor() : super()

    @Suppress("unused", "UNUSED_PARAMETER")
    constructor(context: Context) : super()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String, timeoutMs: Int): String = try {
        val process = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()

        // Nothing is ever written to the command's input. Left open, any child that reads standard
        // input waits for a line that will never arrive.
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
        // Long enough to collect what was already buffered, short enough not to reintroduce a wait.
        drain.join(300)

        val text = synchronized(output) { output.toString() }.trim()
        when {
            !finished -> if (text.isEmpty()) {
                "(timed out after ${timeoutMs}ms, killed, no output)"
            } else {
                "$text\n(timed out after ${timeoutMs}ms, killed)"
            }
            text.isEmpty() -> "(no output, exit=${process.exitValue()})"
            else -> text
        }
    } catch (e: Exception) {
        "(failed: ${e.javaClass.simpleName}: ${e.message})"
    }
}
