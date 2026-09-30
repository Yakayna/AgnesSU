package com.agnessu.yakayn.data.ghostlock

import android.content.Context
import android.net.Uri
import android.system.Os
import android.util.Log
import com.agnessu.yakayn.data.shizuku.ShizukuExploitRunner
import com.agnessu.yakayn.data.shizuku.ShizukuStatus
import com.agnessu.yakayn.profile.NativeProfileDocument
import com.agnessu.yakayn.profile.ProfileResolver
import com.agnessu.yakayn.profile.ValueMap
import com.agnessu.yakayn.profile.asValueMap
import com.agnessu.yakayn.profile.deepMergeValues
import com.agnessu.yakayn.profile.mutableChild
import com.agnessu.yakayn.profile.route.RouteKind
import com.agnessu.yakayn.profile.valueMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File

class AndroidGhostlockRepository(
    private val context: Context,
    private val catalog: BuiltinProfileCatalog,
    private val userStore: UserProfileStore,
    private val shizukuRunner: ShizukuExploitRunner,
    private val assetLoader: AssetConfigLoader,
) {

    private val _settings = MutableStateFlow(
        ExploitSettings(
            cpuPair = CpuPair(0, 1),
            safeMode = true,
            forceAttack = false,
            useShizuku = true,
            debugDir = null,
        )
    )
    val settings: StateFlow<ExploitSettings> = _settings.asStateFlow()

    private val _exploitState = MutableStateFlow<ExploitState>(ExploitState.Idle)
    val exploitState: StateFlow<ExploitState> = _exploitState.asStateFlow()

    private val _selectedProfileId = MutableStateFlow<String?>(null)
    val selectedProfileId: StateFlow<String?> = _selectedProfileId.asStateFlow()

    val kernel: KernelSnapshot by lazy {
        KernelSnapshot(
            release = System.getProperty("os.version", "").orEmpty(),
            arch = runCatching { Os.uname().machine }.getOrDefault("unknown"),
            soc = readSysfs("/sys/devices/soc0/soc_id")
                ?: readProp("ro.soc.model")
                ?: "unknown",
            deviceName = readProp("ro.product.model")
                ?: readProp("ro.product.device")
                ?: "unknown",
        )
    }

    fun isSupportedArch(): Boolean = kernel.arch == "aarch64"

    fun matchBuiltinProfile(): KernelProfile? = catalog.match(kernel.release)

    fun resolveActiveProfile(): ProfileConfig? {
        val selectedId = _selectedProfileId.value

        if (selectedId != null) {
            val userConfig = userStore.load(selectedId)
            if (userConfig != null) {
                return buildProfileConfig(selectedId, selectedId, ProfileSource.USER_IMPORTED, userConfig)
            }
        }

        val matched = matchBuiltinProfile() ?: return null
        return buildProfileConfig(matched.id, matched.displayName, ProfileSource.BUILTIN, matched.config)
    }

    fun allAvailableProfiles(): List<ProfileSummary> {
        val builtins = catalog.allProfiles().map { p ->
            ProfileSummary(
                id = p.id,
                displayName = p.displayName,
                source = ProfileSource.BUILTIN,
                matched = catalog.matches(kernel.release, p),
            )
        }
        val userProfiles = userStore.list().map { f ->
            ProfileSummary(
                id = f.id,
                displayName = f.name,
                source = ProfileSource.USER_IMPORTED,
                matched = false,
            )
        }
        return builtins + userProfiles
    }

    fun selectProfile(id: String?) {
        _selectedProfileId.value = id
    }

    fun updateSettings(transform: ExploitSettings.() -> ExploitSettings) {
        _settings.value = _settings.value.transform()
    }

    fun availableCpuPairs(): List<CpuPair> {
        val count = Runtime.getRuntime().availableProcessors()
        if (count < 2) return listOf(CpuPair(0, 1))
        val pairs = mutableListOf<CpuPair>()
        for (primary in 0 until count) {
            for (consumer in 0 until count) {
                if (primary != consumer) pairs += CpuPair(primary, consumer)
            }
        }
        return pairs.ifEmpty { listOf(CpuPair(0, 1)) }
    }

    fun importProfile(uri: Uri): UserProfileFile? = userStore.importFromUri(context, uri)

    fun deleteProfile(id: String): Boolean = userStore.delete(id)

    fun userProfiles(): List<UserProfileFile> = userStore.list()

    suspend fun runExploit(onLog: (String) -> Unit): Result<Int> = withContext(Dispatchers.IO) {
        try {
            _exploitState.value = ExploitState.Preparing
            val profile = resolveActiveProfile()
                ?: return@withContext Result.failure(IllegalStateException("no profile available"))

            if (!profile.isValid) {
                return@withContext Result.failure(
                    IllegalStateException("profile has errors: ${profile.errors.joinToString()}")
                )
            }

            val doc = profile.document!!
            val blob = doc.toBinary()
            val currentSettings = _settings.value

            onLog("profile: ${profile.displayName} (${profile.routeKind})")
            onLog("cpu: ${currentSettings.cpuPair}, safe=${currentSettings.safeMode}")

            _exploitState.value = ExploitState.Running()

            val exitCode = if (currentSettings.useShizuku &&
                shizukuRunner.status.value == ShizukuStatus.READY
            ) {
                onLog("executing via Shizuku (shell domain)")
                val result = shizukuRunner.runExploit(
                    primaryCpu = currentSettings.cpuPair.primary,
                    consumerCpu = currentSettings.cpuPair.consumer,
                    safeMode = currentSettings.safeMode,
                    forceAttack = currentSettings.forceAttack,
                    profileBlob = blob,
                    debugDir = currentSettings.debugDir,
                    onLog = { line ->
                        onLog(line)
                    },
                    onStatus = { step, status ->
                        _exploitState.value = ExploitState.Running(step, status)
                    },
                )
                result.logs.forEach(onLog)
                result.exitCode
            } else {
                onLog("executing directly (app domain)")
                runDirect(blob, currentSettings, onLog)
            }

            _exploitState.value = ExploitState.Finished(exitCode, emptyList())

            if (exitCode == 0) {
                prepareKsud(onLog)
            }

            Result.success(exitCode)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            _exploitState.value = ExploitState.Failed(e.message ?: "unknown error")
            Result.failure(e)
        }
    }

    private fun runDirect(
        blob: ByteArray,
        settings: ExploitSettings,
        onLog: (String) -> Unit,
    ): Int {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val binary = File(nativeLibDir, "libghostlock.so")
        if (!binary.isFile) error("missing libghostlock.so")

        val workDir = File(context.filesDir, "ghostlock_work").also { it.mkdirs() }

        val argv = mutableListOf(
            binary.absolutePath,
            "--ghostlock-app-call",
            "--enable-status-record",
        )
        if (settings.forceAttack) argv += "--force-attack"
        if (!settings.debugDir.isNullOrEmpty()) argv += listOf("--dump-kernel-log", settings.debugDir)

        val process = ProcessBuilder(argv)
            .directory(workDir)
            .redirectErrorStream(true)
            .apply {
                environment()["GHOSTLOCK_HOME"] = workDir.absolutePath
                environment()["TMPDIR"] = workDir.absolutePath
                environment()["HOME"] = workDir.absolutePath
            }
            .start()

        val stdinStream = process.outputStream
        val header = java.nio.ByteBuffer.allocate(4)
            .order(java.nio.ByteOrder.BIG_ENDIAN)
            .putInt(blob.size)
            .array()
        stdinStream.write(header)
        stdinStream.write(blob)
        stdinStream.flush()

        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.startsWith("\u001eGLK_STATUS")) {
                    val parts = line.removePrefix("\u001eGLK_STATUS").trim().split(' ')
                    if (parts.size >= 2) {
                        _exploitState.value = ExploitState.Running(parts[0], parts[1])
                        runCatching {
                            stdinStream.write("\u001eGLK_STATUS_ACK\n".toByteArray())
                            stdinStream.flush()
                        }
                    }
                } else {
                    onLog(line)
                }
            }
        }

        return process.waitFor()
    }

    private fun prepareKsud(onLog: (String) -> Unit) {
        val source = File(context.applicationInfo.nativeLibraryDir, "libksud.so")
        if (!source.isFile) {
            onLog("warning: libksud.so missing")
            return
        }
        val workDir = File(context.filesDir, "ghostlock_work")
        val output = File(workDir, "ksud")
        runCatching {
            source.inputStream().use { inp -> output.outputStream().use { inp.copyTo(it) } }
            Os.chmod(output.absolutePath, 0b111101101)
            onLog("ksud staged to ${output.absolutePath}")
        }.onFailure { onLog("ksud copy failed: ${it.message}") }
    }

    private fun buildProfileConfig(
        id: String,
        displayName: String,
        source: ProfileSource,
        config: Map<String, Any?>,
    ): ProfileConfig {
        val merged = mergeExecutionDefaults(config)
        val release = merged["release"] as? String ?: displayName
        val route = routeToken(merged) ?: RouteKind.TCP_ZEROCOPY.token
        val fallbackTo = (merged["fallback"] as? Map<*, *>)?.get("to") as? String

        val routeKind = RouteKind.fromToken(route) ?: RouteKind.TCP_ZEROCOPY
        val errors = ProfileResolver.validateMerged(merged, route, fallbackTo)
        val document = if (errors.isEmpty()) {
            runCatching {
                NativeProfileDocument.from(
                    release = release,
                    route = route,
                    fallbackTo = fallbackTo,
                    value = { path -> ProfileResolver.nativeValue(merged, route, fallbackTo, path) },
                )
            }.getOrNull()
        } else null

        return ProfileConfig(
            profileId = id,
            displayName = displayName,
            source = source,
            routeKind = routeKind,
            document = document,
            errors = errors.map { "${it.path}: ${it.message}" },
        )
    }

    /**
     * Merges the shared execution-tuning preset and the per-route presets into
     * the profile before serialization. The bundled .conf profiles carry only
     * kernel geometry; execution tuning ships in kernel_profiles/execution-*.conf
     * (upstream ProfileMerger.resolveMerged). Without this merge every execution
     * value (w1_attempts, heap.prepare_max_attempts, route attempts, ...) decodes
     * to 0 and the native retry loops never make an attempt ("Write 1 failed").
     */
    private fun mergeExecutionDefaults(config: Map<String, Any?>): ValueMap {
        val defaults = valueMapOf()
        executionTuning()?.let { defaults["execution"] = it }
        val merged = deepMergeValues(defaults, config)
        fillRouteExecutionDefaults(merged)
        applySelectedCpus(merged)
        return merged
    }

    private fun executionTuning(): ValueMap? =
        assetLoader.load("$PROFILES_DIR/execution-tuning.conf")
            .asValueMap()?.get("execution").asValueMap()

    private fun executionRoutePreset(route: String): ValueMap? =
        assetLoader.load("$PROFILES_DIR/execution-${route.replace('_', '-')}.conf")
            .asValueMap()?.get("execution").asValueMap()
            ?.get("routes").asValueMap()?.get(route).asValueMap()

    private fun fillRouteExecutionDefaults(profile: ValueMap) {
        val routes = profile.mutableChild("execution").mutableChild("routes")
        for (route in RouteKind.entries) {
            val preset = executionRoutePreset(route.token) ?: continue
            val existing = routes[route.token].asValueMap()
            if (existing == null) {
                routes[route.token] = preset
            } else {
                for ((key, value) in preset) {
                    if (!existing.containsKey(key)) existing[key] = value
                }
            }
        }
    }

    private fun applySelectedCpus(profile: ValueMap) {
        val pair = _settings.value.cpuPair
        profile.mutableChild("execution")["selected_cpus"] = valueMapOf(
            "main" to pair.primary.toLong(),
            "consumer" to pair.consumer.toLong(),
        )
    }

    private fun routeToken(config: Map<String, Any?>): String? {
        val routeObj = config["route"] as? Map<*, *> ?: return null
        return RouteKind.entries.firstOrNull { routeObj.containsKey(it.token) }?.token
    }

    private fun readSysfs(path: String): String? = runCatching {
        File(path).readText().trim().ifBlank { null }
    }.getOrNull()

    private fun readProp(key: String): String? = runCatching {
        val process = ProcessBuilder("getprop", key).start()
        val value = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        value.ifBlank { null }
    }.getOrNull()

    companion object {
        private const val TAG = "GhostlockRepo"
        private const val PROFILES_DIR = "kernel_profiles"
    }
}
