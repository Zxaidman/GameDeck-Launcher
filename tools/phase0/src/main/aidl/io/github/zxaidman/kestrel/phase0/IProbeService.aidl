// EXPERIMENTAL — Phase 0 harness only.
//
// The interface to a small service that Shizuku starts with shell privileges. It runs one shell
// command and hands back its raw output — nothing more.
//
// The harness never synthesises events into its own window. Stimulus is produced by the platform's
// own tools in this separate process and travels the ordinary system input path, and every command
// issued is written into the same log as the events that follow, so stimulus and response can
// always be told apart in the record.
package io.github.zxaidman.kestrel.phase0;

interface IProbeService {
    // Shizuku calls this to tear the service down. The transaction id is fixed by Shizuku.
    void destroy() = 16777114;

    // Runs a command and returns combined stdout and stderr.
    //
    // The timeout is not optional and not a nicety. A command that never returns froze the harness
    // solid on a real run: the worker thread blocked forever, every control stayed disabled, and
    // the session produced no evidence at all. An instrument must fail with a reading, not hang.
    String exec(String command, int timeoutMs) = 1;
}
