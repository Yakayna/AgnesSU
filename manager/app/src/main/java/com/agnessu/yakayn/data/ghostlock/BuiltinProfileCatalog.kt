package com.agnessu.yakayn.data.ghostlock

import android.util.Log

data class KernelProfile(
    val id: String,
    val displayName: String,
    val kernelGlob: String,
    val profilePath: String,
    val config: Map<String, Any?>,
)

class BuiltinProfileCatalog(private val loader: AssetConfigLoader) {

    private var profiles: List<KernelProfile> = emptyList()
    private var loaded = false

    fun loadIndex(): List<KernelProfile> {
        if (loaded) return profiles
        loaded = true

        val index = loader.load(INDEX_PATH)
        @Suppress("UNCHECKED_CAST")
        val entriesRaw = index["profiles"] as? List<*> ?: run {
            Log.w(TAG, "no 'profiles' list in index.conf")
            return emptyList()
        }

        profiles = entriesRaw.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            val release = map["release"] as? String ?: return@mapNotNull null
            val file = map["file"] as? String ?: return@mapNotNull null
            val profilePath = "$PROFILES_DIR/$file"
            val config = loader.load(profilePath)
            if (config.isEmpty()) {
                Log.w(TAG, "empty config for profile $release at $profilePath")
                return@mapNotNull null
            }
            KernelProfile(
                id = release,
                displayName = release,
                kernelGlob = release,
                profilePath = profilePath,
                config = config,
            )
        }
        Log.i(TAG, "loaded ${profiles.size} builtin profiles")
        return profiles
    }

    fun match(kernelRelease: String): KernelProfile? {
        val list = loadIndex()
        return list.firstOrNull { it.kernelGlob == kernelRelease }
            ?: templateName(kernelRelease)?.let { template ->
                list.firstOrNull { it.kernelGlob == template }
            }
    }

    fun matches(kernelRelease: String, profile: KernelProfile): Boolean =
        profile.kernelGlob == kernelRelease || templateName(kernelRelease) == profile.kernelGlob

    /** Exact kernel-release match only; no `X.Y-template` fallback. */
    fun exactMatch(kernelRelease: String): KernelProfile? =
        loadIndex().firstOrNull { it.kernelGlob == kernelRelease }

    fun allProfiles(): List<KernelProfile> = loadIndex()

    companion object {
        private const val TAG = "BuiltinProfileCatalog"
        private const val PROFILES_DIR = "kernel_profiles"
        private const val INDEX_PATH = "$PROFILES_DIR/index.conf"

        fun templateName(kernelRelease: String): String? {
            val match = Regex("""^(\d+)\.(\d+)""").find(kernelRelease) ?: return null
            return "${match.groupValues[1]}.${match.groupValues[2]}-template"
        }
    }
}
