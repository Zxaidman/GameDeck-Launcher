// EXPERIMENTAL — Phase 0 harness only.
//
// The interface to a small service that Shizuku starts with shell privileges. It exists purely to
// run read-only probe commands and hand back their raw output. It must never grow the ability to
// inject input: the harness measures, and a measuring instrument that can also produce the thing it
// measures is worthless as evidence.
package io.github.zxaidman.kestrel.phase0;

interface IProbeService {
    // Shizuku calls this to tear the service down. The transaction id is fixed by Shizuku.
    void destroy() = 16777114;

    // Runs a command and returns combined stdout and stderr.
    String exec(String command) = 1;
}
