package com.agnessu.yakayn.data.samsung

import android.os.Build
import android.system.Os
import android.system.OsConstants
import org.json.JSONArray
import org.json.JSONObject

/** A downloadable artifact pinned in the support feed. */
data class RemoteArtifact(
    val url: String,
    val size: Long,
)

/**
 * One Root-My-Galaxy payload entry. A payload matches a device when its model
 * and the leading numeric part of the kernel release both line up.
 */
data class SamsungTargetProfile(
    val profileId: String,
    val displayName: String,
    val models: Set<String>,
    val kernelVersions: Set<String>,
    val exploit: RemoteArtifact,
    val kernelSu: RemoteArtifact,
) {
    init {
        require(models.isNotEmpty()) { "Payload must support at least one model" }
        require(kernelVersions.isNotEmpty()) { "Payload must support at least one kernel version" }
    }

    fun matchesDevice(snapshot: SamsungDeviceSnapshot): Boolean =
        models.any { it.equals(snapshot.model, ignoreCase = true) }

    fun matchesKernelVersion(snapshot: SamsungDeviceSnapshot): Boolean =
        snapshot.kernelVersion in kernelVersions

    fun matches(snapshot: SamsungDeviceSnapshot): Boolean =
        matchesDevice(snapshot) && matchesKernelVersion(snapshot)
}

data class SamsungSupportManifest(
    val schemaVersion: Int,
    val targets: List<SamsungTargetProfile>,
) {
    companion object {
        fun parse(bytes: ByteArray): SamsungSupportManifest {
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            val schemaVersion = root.getInt("schemaVersion")
            require(schemaVersion == 3) { "Unsupported support manifest schema" }
            val payloadsJson = root.getJSONArray("payloads")
            val payloads = buildList {
                for (index in 0 until payloadsJson.length()) {
                    val payload = payloadsJson.getJSONObject(index)
                    val exploit = payload.getJSONObject("exploit")
                    val kernelSu = payload.getJSONObject("kernelsu")
                    add(
                        SamsungTargetProfile(
                            profileId = payload.getString("payloadId"),
                            displayName = payload.getString("displayName"),
                            models = payload.getJSONArray("models").strings(),
                            kernelVersions = payload.getJSONArray("kernelVersions").strings(),
                            exploit = RemoteArtifact(
                                url = exploit.getString("url"),
                                size = exploit.getLong("size"),
                            ),
                            kernelSu = RemoteArtifact(
                                url = kernelSu.getString("url"),
                                size = kernelSu.getLong("size"),
                            ),
                        ),
                    )
                }
            }
            return SamsungSupportManifest(schemaVersion, payloads)
        }

        private fun JSONArray.strings(): Set<String> = buildSet {
            for (index in 0 until length()) add(getString(index))
        }
    }
}

data class SamsungDeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val device: String,
    val kernelRelease: String,
    val kernelVersionInfo: String,
    val machine: String,
    val buildId: String,
    val fingerprint: String,
    val androidRelease: String,
    val sdk: Int,
    val abi: String,
    val pageSize: Long,
) {
    val kernelVersion: String
        get() = kernelRelease.takeWhile { it.isDigit() || it == '.' }

    val kernelVersionFull: String
        get() = listOf(kernelRelease, kernelVersionInfo, machine)
            .filter(String::isNotBlank)
            .joinToString(" ")

    val isAarch64: Boolean
        get() = machine == "aarch64"

    companion object {
        fun current(): SamsungDeviceSnapshot {
            val uname = Os.uname()
            return SamsungDeviceSnapshot(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                device = Build.DEVICE,
                kernelRelease = uname.release,
                kernelVersionInfo = uname.version,
                machine = uname.machine,
                buildId = Build.DISPLAY,
                fingerprint = Build.FINGERPRINT,
                androidRelease = Build.VERSION.RELEASE,
                sdk = Build.VERSION.SDK_INT,
                abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
                pageSize = Os.sysconf(OsConstants._SC_PAGESIZE),
            )
        }
    }
}

data class SamsungVerifiedPayloads(
    val profile: SamsungTargetProfile,
    val exploit: java.io.File,
    val kernelSu: java.io.File,
)

enum class SamsungInstallPhase {
    Checking,
    Ready,
    Downloading,
    Exploiting,
    LoadingKernelSu,
    Installed,
    Failed,
}

data class SamsungUiState(
    val phase: SamsungInstallPhase = SamsungInstallPhase.Checking,
    val message: String = "",
    val log: String = "",
) {
    val busy: Boolean
        get() = phase in setOf(
            SamsungInstallPhase.Checking,
            SamsungInstallPhase.Downloading,
            SamsungInstallPhase.Exploiting,
            SamsungInstallPhase.LoadingKernelSu,
        )
}

data class SamsungCommandResult(val code: Int, val output: String)
