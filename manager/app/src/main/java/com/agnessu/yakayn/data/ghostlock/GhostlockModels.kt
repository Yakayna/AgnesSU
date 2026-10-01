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
    /** Kernel releases this document resolves to, empty when it is not parseable. */
    val releases: List<String> = emptyList(),
)

/** One flattened set of kernel offsets extracted from a boot image. */
data class KernelOffsets(
    val release: String,
    val scalars: Map<String, Long?>,
    val symbols: Map<String, Long?>,
    val structFields: Map<String, Long?>,
)

/** A profile document ready to be shared/exported. */
data class OffsetCandidate(val release: String, val document: String)

sealed interface OffsetImportResult {
    data class Imported(val releases: List<String>) : OffsetImportResult
    data class RequiresOverwrite(val releases: List<String>) : OffsetImportResult
    data object AlreadyPresent : OffsetImportResult
    data class MissingIncludes(val files: List<String>) : OffsetImportResult
    data class Failed(val reason: String) : OffsetImportResult
}

sealed interface ParseResult {
    /** Extraction produced a flattened profile (auto-saved to the user store). */
    data class Parsed(
        val releases: List<String>,
        /** Sidecar-only fields (e.g. kernel_phys_load) still missing. */
        val missing: Set<String> = emptySet(),
        /** Id of the stored document, so the caller can auto-load it. */
        val documentName: String? = null,
    ) : ParseResult

    data class RequiresOverwrite(
        val releases: List<String>,
        val missing: Set<String> = emptySet(),
    ) : ParseResult

    data object AlreadyPresent : ParseResult

    data class Failed(val code: Int, val reason: String? = null) : ParseResult
}
