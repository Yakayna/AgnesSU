package com.agnessu.yakayn.shizuku;

oneway interface IGhostlockStatusCallback {
    void onStatus(String step, String status) = 1;
}
