package io.github.zxaidman.kestrel.phase0

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

/**
 * EXPERIMENTAL — Phase 0 harness only.
 *
 * Shizuku starts this class in a separate process holding shell privileges. Everything it runs is
 * read-only inspection: does a device node exist, what are its permissions, which identity are we,
 * is a given command present.
 *
 * It deliberately cannot inject input. The harness's value as evidence depends on it being unable
 * to manufacture the result it is measuring.
 */
class ProbeService : IProbeService.Stub {

    @Suppress("unused")
    constructor() : super()

    @Suppress("unused", "UNUSED_PARAMETER")
    constructor(context: Context) : super()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String = try {
        val process = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        process.waitFor()
        val trimmed = output.trim()
        if (trimmed.isEmpty()) "(no output, exit=${process.exitValue()})" else trimmed
    } catch (e: Exception) {
        "(failed: ${e.javaClass.simpleName}: ${e.message})"
    }
}
