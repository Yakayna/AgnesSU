package com.agnessu.yakayn.data.dirtyfrag

import android.os.Build
import android.system.Os
import android.system.OsConstants

/**
 * Kernel families the embedded dirtyfrag_ko_* blobs target: 5.10 / 5.15 / 6.1 /
 * 6.6 / 6.12 / 6.18 (Android 12-17). Families outside that list are rejected by
 * the native engine with "unsupported kernel".
 */
data class DirtyFragDeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val kernelRelease: String,
    val kernelVersionInfo: String,
    val machine: String,
    val androidRelease: String,
    val sdk: Int,
) {
    val kernelVersion: String
        get() = kernelRelease.takeWhile { it.isDigit() || it == '.' }

    val kernelVersionFull: String
        get() = listOf(kernelRelease, kernelVersionInfo, machine)
            .filter(String::isNotBlank)
            .joinToString(" ")

    val isAarch64: Boolean
        get() = machine == "aarch64"

    /** IpSecManager UDP encapsulation (the ESP primitive) needs API 29+. */
    val isApiSupported: Boolean
        get() = sdk >= Build.VERSION_CODES.Q

    companion object {
        fun current(): DirtyFragDeviceSnapshot {
            val uname = Os.uname()
            return DirtyFragDeviceSnapshot(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                kernelRelease = uname.release,
                kernelVersionInfo = uname.version,
                machine = uname.machine,
                androidRelease = Build.VERSION.RELEASE,
                sdk = Build.VERSION.SDK_INT,
            )
        }
    }
}

enum class DirtyFragInstallPhase {
    Checking,
    Ready,
    Exploiting,
    Installed,
    Failed,
}

data class DirtyFragUiState(
    val phase: DirtyFragInstallPhase = DirtyFragInstallPhase.Checking,
    val log: String = "",
) {
    val busy: Boolean
        get() = phase in setOf(
            DirtyFragInstallPhase.Checking,
            DirtyFragInstallPhase.Exploiting,
        )
}

data class DirtyFragCommandResult(val code: Int, val output: String)
