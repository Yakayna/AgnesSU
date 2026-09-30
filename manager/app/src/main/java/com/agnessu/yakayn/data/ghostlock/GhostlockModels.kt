package com.agnessu.yakayn.data.ghostlock

import com.agnessu.yakayn.data.shizuku.ShizukuStatus
import com.agnessu.yakayn.profile.NativeProfileDocument
import com.agnessu.yakayn.profile.route.RouteKind

data class KernelSnapshot(
    val release: String,
    val arch: String,
    val soc: String,
    val deviceName: String,
)

data class CpuPair(
    val primary: Int,
    val consumer: Int,
) {
    override fun toString(): String = "CPU $primary / $consumer"
}

data class ProfileConfig(
    val profileId: String,
    val displayName: String,
    val source: ProfileSource,
    val routeKind: RouteKind,
    val document: NativeProfileDocument?,
    val errors: List<String>,
) {
    val isValid: Boolean get() = document != null && errors.isEmpty()
}

enum class ProfileSource {
    BUILTIN,
    USER_IMPORTED,
}

data class ExploitSettings(
    val cpuPair: CpuPair,
    val safeMode: Boolean,
    val forceAttack: Boolean,
    val useShizuku: Boolean,
    val debugDir: String?,
)

sealed interface ExploitState {
    data object Idle : ExploitState
    data object Preparing : ExploitState
    data class Running(val step: String = "", val status: String = "") : ExploitState
    data class Finished(val exitCode: Int, val logs: List<String>) : ExploitState
    data class Failed(val error: String) : ExploitState
}

data class GhostlockSnapshot(
    val kernel: KernelSnapshot,
    val profileConfig: ProfileConfig?,
    val availableProfiles: List<ProfileSummary>,
    val settings: ExploitSettings,
    val shizukuStatus: ShizukuStatus,
    val exploitState: ExploitState,
)

data class ProfileSummary(
    val id: String,
    val displayName: String,
    val source: ProfileSource,
    val matched: Boolean,
)

data class UserProfileFile(
    val id: String,
    val name: String,
    val path: String,
)
