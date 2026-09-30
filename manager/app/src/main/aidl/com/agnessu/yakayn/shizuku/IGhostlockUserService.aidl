package com.agnessu.yakayn.shizuku;

import com.agnessu.yakayn.shizuku.IGhostlockCallback;
import com.agnessu.yakayn.shizuku.IGhostlockStatusCallback;

interface IGhostlockUserService {
    void destroy() = 16777114;

    void runExploit(
        int primaryCpu,
        int consumerCpu,
        boolean safeMode,
        boolean forceAttack,
        in byte[] profileBlob,
        @nullable String debugDir,
        IGhostlockCallback callback,
        IGhostlockStatusCallback statusCallback
    ) = 1;
}
