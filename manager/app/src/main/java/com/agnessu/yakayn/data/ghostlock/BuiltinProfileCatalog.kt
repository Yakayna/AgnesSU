package com.agnessu.yakayn.data.ghostlock

import android.util.Log
import com.agnessu.yakayn.profile.ValueModel

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
        val kernelsRaw = ValueModel.getList(index, "kernels") ?: run {
            Log.w(TAG, "no 'kernels' list in index.conf")
            return emptyList()
        }

        profiles = kernelsRaw.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            val typed = map as Map<String, Any?>
            val id = typed["id"] as? String ?: return@mapNotNull null
            val name = typed["name"] as? String ?: id
            val glob = typed["kernel"] as? String ?: return@mapNotNull null
            val file = typed["file"] as? String ?: return@mapNotNull null
            val profilePath = "$PROFILES_DIR/$file"
            val config = loader.load(profilePath)
            if (config.isEmpty()) {
                Log.w(TAG, "empty config for profile $id at $profilePath")
                return@mapNotNull null
            }
            KernelProfile(id, name, glob, profilePath, config)
        }
        Log.i(TAG, "loaded ${profiles.size} builtin profiles")
        return profiles
    }

    fun match(kernelRelease: String): KernelProfile? {
        val list = loadIndex()
        return list.firstOrNull { matchGlob(it.kernelGlob, kernelRelease) }
    }

    fun allProfiles(): List<KernelProfile> = loadIndex()

    companion object {
        private const val TAG = "BuiltinProfileCatalog"
        private const val PROFILES_DIR = "kernel_profiles"
        private const val INDEX_PATH = "$PROFILES_DIR/index.conf"

        fun matchGlob(pattern: String, text: String): Boolean {
            val regex = buildString {
                append("^")
                for (c in pattern) {
                    when (c) {
                        '*' -> append(".*")
                        '?' -> append(".")
                        '.', '(', ')', '[', ']', '{', '}', '+', '^', '$', '|', '\\' -> {
                            append("\\")
                            append(c)
                        }
                        else -> append(c)
                    }
                }
                append("$")
            }
            return Regex(regex).matches(text)
        }
    }
}
