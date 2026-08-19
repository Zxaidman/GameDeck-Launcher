// The narrow interface to a shell-privileged process, per PROJECT_STRUCTURE.md §558: Shizuku is
// reached through one capability boundary and never scattered through features.
package io.github.zxaidman.kestrel.platform.shizuku;

interface IPrivilegedShell {
    // Fixed by Shizuku, which calls this to tear the service down.
    void destroy() = 16777114;

    // Runs one command and returns combined output. The timeout is not optional: a command that
    // never returns once froze the Phase 0 harness solid, and an instrument that hangs is worse
    // than one that fails (docs/phase0/results/tier5-orphan-report.md).
    String exec(String command, int timeoutMs) = 1;

    // Holds the controller's event stream open for writing.
    //
    // Sending a control through exec() would spawn a shell per event. At the rate a thumb moves a
    // stick that is hundreds of processes a second, which is not a tuning problem but a wrong
    // design. This service already runs as shell, so it opens the stream once and writes to it.
    boolean openDeviceStream(String path) = 2;

    // Appends one report. Returns false when the stream is not open, so a caller can tell the
    // difference between input that went nowhere and input that was delivered.
    boolean writeDeviceStream(String data) = 3;

    void closeDeviceStream() = 4;
}
