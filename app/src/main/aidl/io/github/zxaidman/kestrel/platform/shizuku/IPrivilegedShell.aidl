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
}
