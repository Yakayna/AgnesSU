// IShellService.aidl
package com.agnessu.yakayn.shizuku;

/**
 * Shizuku UserService bridge running in the shell domain (uid=2000,
 * u:r:shell:s0). Lets the app stage files into /data/local/tmp and launch the
 * LD_PRELOAD exploit trigger the same way `adb shell` would.
 */
interface IShellService {
    String ping();
    void exit();
    void destroy();
    String exec(String command);
    boolean writeFileChunk(String path, long offset, in byte[] data);
}
