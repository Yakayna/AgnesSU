package com.agnessu.yakayn.shizuku;

oneway interface IGhostlockCallback {
    void onLog(String line) = 1;
    void onComplete(int exitCode) = 2;
}
