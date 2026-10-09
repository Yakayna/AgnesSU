package com.agnessu.yakayn.data.samsung

import android.content.Context
import android.os.SystemClock
import com.agnessu.yakayn.data.shizuku.ShizukuExploitRunner
import com.agnessu.yakayn.data.shizuku.ShizukuStatus
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One-tap Samsung root orchestrator: resolves a Root-My-Galaxy payload for the
 * running device, downloads the pinned artifacts, runs the CVE-2026-43499
 * exploit, then late-loads KernelSU. Two transports are supported — the app
 * domain (`--run-payload`) and the Shizuku shell domain (`LD_PRELOAD`).
 */
class SamsungRootRepository(
    private val context: Context,
    private val shizukuRunner: ShizukuExploitRunner,
    private val payloadRepository: SamsungPayloadRepository,
) {

    private val _state = MutableStateFlow(SamsungUiState())
    val state: StateFlow<SamsungUiState> = _state.asStateFlow()

    private val _useShizuku = MutableStateFlow(false)
    val useShizuku: StateFlow<Boolean> = _useShizuku.asStateFlow()

    private val _matchedProfile = MutableStateFlow<SamsungTargetProfile?>(null)
    val matchedProfile: StateFlow<SamsungTargetProfile?> = _matchedProfile.asStateFlow()

    val shizukuStatus: StateFlow<ShizukuStatus> = shizukuRunner.status

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var installJob: Job? = null

    val snapshot: SamsungDeviceSnapshot by lazy { SamsungDeviceSnapshot.current() }

    init {
        shizukuRunner.init()
        refresh()
    }

    fun refreshShizuku() = shizukuRunner.refreshStatus()

    fun requestShizukuPermission() = shizukuRunner.requestPermission()

    fun setUseShizuku(value: Boolean) {
        _useShizuku.value = value
    }

    fun refresh() {
        if (installJob?.isActive == true) return
        _state.value = SamsungUiState(phase = SamsungInstallPhase.Checking)
        scope.launch {
            try {
                val profile = payloadRepository.resolveTarget(snapshot)
                _matchedProfile.value = profile
                _state.value = SamsungUiState(
                    phase = SamsungInstallPhase.Ready,
                    log = "profile: ${profile.profileId}",
                )
            } catch (error: Throwable) {
                _matchedProfile.value = null
                _state.value = SamsungUiState(
                    phase = SamsungInstallPhase.Failed,
                    log = "[-] ${error.message ?: error.javaClass.simpleName}",
                )
            }
        }
    }

    fun install() {
        if (installJob?.isActive == true) return
        val profile = _matchedProfile.value
        if (profile == null) {
            refresh()
            return
        }
        // Freeze the transport for the whole run so a mid-run toggle cannot mix
        // Shizuku and standalone execution between the exploit and KSU staging.
        val shizuku = _useShizuku.value
        installJob = scope.launch {
            _state.value = SamsungUiState(phase = SamsungInstallPhase.Checking, log = "")
            try {
                if (shizuku) {
                    appendLog("[*] Preparing Shizuku")
                    if (!SamsungShizuku.isRunning() && !SamsungShizuku.pingUntilRunning()) {
                        error("Shizuku is not running")
                    }
                    if (!SamsungShizuku.isGranted() && !SamsungShizuku.requestPermission()) {
                        error("Shizuku permission not granted")
                    }
                    appendLog("[*] Shizuku ready")
                }

                setPhase(SamsungInstallPhase.Downloading, "[*] Downloading payload")
                val payloads = payloadRepository.download(profile) { appendLog("[*] $it") }
                appendLog("[*] Download verified")

                setPhase(SamsungInstallPhase.Exploiting, "[*] Running exploit")
                executeExploit(payloads.exploit, shizuku)

                setPhase(SamsungInstallPhase.LoadingKernelSu, "[*] Loading KernelSU")
                installKernelSu(payloads, shizuku)

                setPhase(SamsungInstallPhase.Installed, "[*] KernelSU active")
                appendLog("[*] Install complete")
            } catch (error: Throwable) {
                appendLog("[-] ${error.message ?: error.javaClass.simpleName}")
                setPhase(SamsungInstallPhase.Failed, "[*] Root failed")
            }
        }
    }

    private suspend fun executeExploit(payload: File, shizuku: Boolean) {
        val logFile = if (shizuku) File(SHIZUKU_LOG_PATH) else File(context.filesDir, "exploit.log")
        if (shizuku) {
            SamsungShizuku.exec(arrayOf("rm", "-f", SHIZUKU_LOG_PATH)).waitFor()
        } else {
            logFile.delete()
        }
        val helper = helperFile(shizuku)
        if (!shizuku) {
            require(helper.canExecute()) { "Helper is not executable: ${helper.absolutePath}" }
        }
        val logPrefix = _state.value.log
        val bootToken = currentBootToken()
        val process = if (shizuku) {
            val stagedPayload = shizukuStage(payload, SHIZUKU_PAYLOAD_PATH, "755")
            SamsungShizuku.exec(
                arrayOf("/system/bin/sh", "-c", "true"),
                shizukuEnvironment(bootToken, stagedPayload.absolutePath, helper.absolutePath),
            )
        } else {
            val processBuilder = ProcessBuilder(
                helper.absolutePath,
                "--run-payload",
                payload.absolutePath,
                helper.absolutePath,
                logFile.absolutePath,
            ).redirectErrorStream(true)
            processBuilder.environment().apply {
                put("EXPLOIT_ATTEMPTS", EXPLOIT_ATTEMPTS)
                put("P0_ATTEMPT_TIMEOUT_SEC", P0_ATTEMPT_TIMEOUT_SEC)
                put("EXPLOIT_ATTEMPT_TIMEOUT_SEC", EXPLOIT_ATTEMPT_TIMEOUT_SEC)
                cachedP0Offset(bootToken)?.let { put(P0_OFFSET_ENV, it) }
            }
            processBuilder.start()
        }
        val captured = StringBuilder()
        val readLog: () -> String = if (shizuku) {
            { drainProcessOutput(process, captured) }
        } else {
            { drainProcessOutput(process, captured); logFile.readTextIfPresent() }
        }

        try {
            val startedAt = SystemClock.elapsedRealtime()
            var lastProgressAt = startedAt
            var lastRawLog = ""
            while (process.isAlive) {
                val rawLog = readLog()
                if (rawLog != lastRawLog) {
                    cacheP0Offset(bootToken, rawLog)
                    publishExploitLog(logPrefix, rawLog)
                    lastRawLog = rawLog
                    lastProgressAt = SystemClock.elapsedRealtime()
                }
                val now = SystemClock.elapsedRealtime()
                require(now - lastProgressAt < EXPLOIT_STALL_MILLIS) { "Exploit stalled" }
                require(now - startedAt < EXPLOIT_TOTAL_MILLIS) { "Exploit timed out" }
                delay(if (shizuku) SHIZUKU_LOG_POLL_INTERVAL_MS else LOG_POLL_INTERVAL_MS)
            }

            val exitCode = process.waitFor()
            val rawLog = readLog()
            cacheP0Offset(bootToken, rawLog)
            publishExploitLog(logPrefix, rawLog)
            val earlyOutput = captured.toString().trim()
            require(exitCode == 0) {
                "Exploit exited $exitCode" + (earlyOutput.takeIf(String::isNotBlank)?.let { " ($it)" } ?: "")
            }
            require(rawLog.contains("exploit completed") && rawLog.contains("done=1 root=1")) {
                "Success markers not found in exploit output"
            }
        } finally {
            if (process.isAlive) {
                process.destroy()
                delay(500)
                if (process.isAlive) process.destroyForcibly()
            }
        }
        appendLog("[*] Bootstrap root")
    }

    private suspend fun installKernelSu(payloads: SamsungVerifiedPayloads, shizuku: Boolean) {
        if (shizuku) {
            shizukuStage(payloads.kernelSu, SHIZUKU_KSUD_PATH, "755")
            shizukuStage(payloads.kernelSu, SHIZUKU_KSUD_STAGE_PATH, "755")
            appendLog("[*] KernelSU staged")
        } else {
            val source = shellQuote(payloads.kernelSu.absolutePath)
            val stageCommand =
                "/system/bin/cp $source $SHIZUKU_KSUD_PATH && " +
                    "/system/bin/cp $source $SHIZUKU_KSUD_STAGE_PATH && " +
                    "/system/bin/chmod 755 $SHIZUKU_KSUD_PATH $SHIZUKU_KSUD_STAGE_PATH"
            val stage = runHelper(shizuku, "-c", stageCommand)
            require(stage.code == 0) { "Failed to stage KernelSU: ${stage.output}" }
            appendLog("[*] KernelSU staged")
        }

        val lateLoad = runHelper(shizuku, "--late-load")
        require(lateLoad.code == 0) { "KernelSU late-load failed (${lateLoad.code}): ${lateLoad.output}" }
        if (lateLoad.output.isNotBlank()) appendLog(lateLoad.output)
        appendLog("[*] KernelSU control verified")
    }

    private fun helperFile(shizuku: Boolean): File =
        if (shizuku) {
            shizukuStage(nativeHelperFile(), SHIZUKU_HELPER_PATH, "755")
        } else {
            nativeHelperFile()
        }

    private fun nativeHelperFile() = File(context.applicationInfo.nativeLibraryDir, "libcve43499root.so")

    private fun shizukuStage(source: File, target: String, mode: String): File {
        val staged = File(target)
        if (stagedFileIsCurrent(staged, source)) return staged
        try {
            SamsungShizuku.writeFile(target, mode, source.inputStream())
        } catch (error: Throwable) {
            throw IllegalStateException("Failed to stage $target: ${error.message}", error)
        }
        return staged
    }

    private fun shizukuEnvironment(
        bootToken: String?,
        payloadPath: String,
        helperPath: String,
    ): Array<String> = buildList {
        add("EXPLOIT_ATTEMPTS=$EXPLOIT_ATTEMPTS")
        add("P0_ATTEMPT_TIMEOUT_SEC=$P0_ATTEMPT_TIMEOUT_SEC")
        add("EXPLOIT_ATTEMPT_TIMEOUT_SEC=$EXPLOIT_ATTEMPT_TIMEOUT_SEC")
        add("CVE43499_ROOT_HELPER=$helperPath")
        add("LD_PRELOAD=$payloadPath")
        cachedP0Offset(bootToken)?.let { add("$P0_OFFSET_ENV=$it") }
    }.toTypedArray()

    private suspend fun runHelper(shizuku: Boolean, vararg arguments: String): SamsungCommandResult {
        val helper = helperFile(shizuku)
        val process = if (shizuku) {
            SamsungShizuku.exec(arrayOf(helper.absolutePath) + arguments)
        } else {
            ProcessBuilder(listOf(helper.absolutePath) + arguments)
                .redirectErrorStream(true)
                .start()
        }
        val captured = StringBuilder()
        val startedAt = SystemClock.elapsedRealtime()
        try {
            while (process.isAlive) {
                drainProcessOutput(process, captured)
                require(SystemClock.elapsedRealtime() - startedAt < HELPER_TIMEOUT_MILLIS) {
                    "Helper timed out" + (captured.toString().trim().takeIf(String::isNotBlank)?.let { ": $it" } ?: "")
                }
                delay(HELPER_POLL_INTERVAL_MS)
            }
            drainProcessOutput(process, captured)
            val exitCode = process.waitFor()
            return SamsungCommandResult(exitCode, stripAnsi(captured.toString().trim()))
        } finally {
            if (process.isAlive) {
                process.destroy()
                delay(500)
                if (process.isAlive) process.destroyForcibly()
            }
        }
    }

    private fun drainProcessOutput(process: Process, buffer: StringBuilder): String {
        return try {
            drainStream(process.inputStream, buffer)
            drainStream(process.errorStream, buffer)
            buffer.toString()
        } catch (_: Throwable) {
            buffer.toString()
        }
    }

    private fun drainStream(stream: InputStream, buffer: StringBuilder) {
        val data = ByteArray(4096)
        while (stream.available() > 0) {
            val count = stream.read(data)
            if (count <= 0) break
            buffer.append(String(data, 0, count, Charsets.UTF_8))
        }
    }

    private fun publishExploitLog(prefix: String, rawLog: String) {
        _state.value = _state.value.copy(
            log = listOf(prefix, stripAnsi(rawLog))
                .filter(String::isNotBlank)
                .joinToString("\n"),
        )
    }

    private fun currentBootToken(): String? = runCatching {
        File("/proc/sys/kernel/random/boot_id")
            .readText(Charsets.US_ASCII)
            .trim()
            .takeIf(String::isNotBlank)
    }.getOrNull()

    private fun cachedP0Offset(bootToken: String?): String? {
        if (bootToken == null) return null
        val stored = context.getSharedPreferences(P0_CACHE, Context.MODE_PRIVATE)
        if (stored.getString(P0_CACHE_BOOT_TOKEN, null) != bootToken) return null
        return stored.getString(P0_CACHE_OFFSET, null)
    }

    private fun cacheP0Offset(bootToken: String?, log: String) {
        if (bootToken == null) return
        val match = P0_OFFSET_PATTERN.findAll(log).lastOrNull() ?: return
        val offset = match.groupValues[1].toLongOrNull(16) ?: return
        if (offset !in 0..P0_OFFSET_MAX || offset and P0_OFFSET_MASK != 0L) return
        val value = "0x${offset.toString(16)}"
        val stored = context.getSharedPreferences(P0_CACHE, Context.MODE_PRIVATE)
        if (stored.getString(P0_CACHE_BOOT_TOKEN, null) == bootToken &&
            stored.getString(P0_CACHE_OFFSET, null) == value
        ) return
        stored.edit()
            .putString(P0_CACHE_BOOT_TOKEN, bootToken)
            .putString(P0_CACHE_OFFSET, value)
            .apply()
    }

    private fun stagedFileIsCurrent(staged: File, source: File): Boolean {
        if (!staged.exists()) return false
        val stagedDigest = sha256OrNull(staged) ?: return false
        return stagedDigest == sha256OrNull(source)
    }

    private fun sha256OrNull(file: File): String? = runCatching {
        file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }.getOrNull()

    private fun shellQuote(value: String) = "'${value.replace("'", "'\\''")}'"

    private fun setPhase(phase: SamsungInstallPhase, message: String) {
        _state.value = _state.value.copy(phase = phase)
        appendLog(message)
    }

    private fun appendLog(line: String) {
        val cleanLine = stripAnsi(line).trim()
        if (cleanLine.isBlank()) return
        _state.value = _state.value.copy(
            log = (_state.value.log + "\n" + cleanLine).trim(),
        )
    }

    private fun File.readTextIfPresent(): String = if (exists()) readText() else ""

    companion object {
        private const val EXPLOIT_ATTEMPTS = "24"
        private const val P0_ATTEMPT_TIMEOUT_SEC = "45"
        private const val EXPLOIT_ATTEMPT_TIMEOUT_SEC = "120"
        private const val EXPLOIT_STALL_MILLIS = 90_000L
        private const val EXPLOIT_TOTAL_MILLIS = 900_000L
        private const val HELPER_TIMEOUT_MILLIS = 120_000L
        private const val P0_CACHE = "samsung_p0_cache"
        private const val P0_CACHE_BOOT_TOKEN = "kernel_boot_id"
        private const val P0_CACHE_OFFSET = "offset"
        private const val P0_OFFSET_ENV = "SLIDE_P0_OFFSET"
        private const val P0_OFFSET_MAX = 0x1f0000L
        private const val P0_OFFSET_MASK = 0xffffL
        private const val SHIZUKU_LOG_PATH = "/data/local/tmp/ksu-exploit.log"
        private const val SHIZUKU_HELPER_PATH = "/data/local/tmp/ksu-helper"
        private const val SHIZUKU_PAYLOAD_PATH = "/data/local/tmp/ksu-payload"
        private const val SHIZUKU_KSUD_PATH = "/data/local/tmp/ksud-s25u-kdp"
        private const val SHIZUKU_KSUD_STAGE_PATH = "/data/local/tmp/.ksud-stage"
        private const val LOG_POLL_INTERVAL_MS = 250L
        private const val HELPER_POLL_INTERVAL_MS = 250L
        private const val SHIZUKU_LOG_POLL_INTERVAL_MS = 1_000L
        private val ANSI_ESCAPE = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
        private val P0_OFFSET_PATTERN = Regex(
            "slide-kaslr-ok[^\\n]*slide=([0-9a-fA-F]{16})",
        )

        private fun stripAnsi(value: String): String =
            ANSI_ESCAPE.replace(value, "").replace("\r", "")
    }
}
