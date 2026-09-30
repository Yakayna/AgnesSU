package com.agnessu.yakayn.data.ghostlock

import android.content.Context
import com.agnessu.yakayn.profile.HoconSupport
import java.io.IOException

class AssetConfigLoader(private val context: Context) {

    private val cache = mutableMapOf<String, Any?>()

    fun load(assetPath: String, visited: Set<String> = emptySet()): Map<String, Any?> {
        if (assetPath in visited) return emptyMap()
        val nextVisited = visited + assetPath

        val text = readAsset(assetPath) ?: return emptyMap()
        val resolved = resolveIncludes(text, assetPath, nextVisited)
        return HoconSupport.parseValue(resolved) as? Map<String, Any?> ?: emptyMap()
    }

    private fun resolveIncludes(text: String, basePath: String, visited: Set<String>): String {
        val baseDir = basePath.substringBeforeLast('/', "")
        val sb = StringBuilder()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("include")) {
                val quoted = INCLUDE_PATTERN.find(trimmed)?.groupValues?.get(1)
                if (quoted != null) {
                    val includePath = if (baseDir.isEmpty()) quoted else "$baseDir/$quoted"
                    val included = load(includePath, visited)
                    sb.appendLine(HoconSupport.render(included))
                    continue
                }
            }
            sb.appendLine(line)
        }
        return sb.toString()
    }

    fun readAsset(path: String): String? {
        if (path in cache) return cache[path] as? String
        return try {
            val text = context.assets.open(path).bufferedReader().readText()
            cache[path] = text
            text
        } catch (_: IOException) {
            cache[path] = null
            null
        }
    }

    fun listAssets(dir: String): List<String> = try {
        context.assets.list(dir)?.toList() ?: emptyList()
    } catch (_: IOException) {
        emptyList()
    }

    fun clearCache() {
        cache.clear()
    }

    companion object {
        private val INCLUDE_PATTERN = Regex("""include\s+"([^"]+)"""")
    }
}
