package com.agnessu.yakayn.data.ghostlock

import android.content.Context
import android.net.Uri
import android.util.Log
import com.agnessu.yakayn.profile.HoconSupport
import com.agnessu.yakayn.profile.ValueList
import com.agnessu.yakayn.profile.ValueMap
import com.agnessu.yakayn.profile.asValueMap
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

class UserProfileStore(
    context: Context,
    private val assetLoader: AssetConfigLoader,
) {

    private val storageDir = File(context.filesDir, "ghostlock_profiles").also { it.mkdirs() }

    class MissingIncludes(val files: List<String>) : Exception()

    private fun files(): List<File> = storageDir.listFiles().orEmpty()
        .filter { it.isFile && it.extension == "conf" }
        .sortedByDescending { it.lastModified() }

    // ---- existing (id-based) profile API, preserved ------------------------

    fun list(): List<UserProfileFile> {
        val byName = documents()
        return files().map { file ->
            val text = runCatching { file.readText() }.getOrDefault("")
            val entries = runCatching { parseWith(text, byName) }.getOrDefault(emptyList())
            UserProfileFile(
                id = file.nameWithoutExtension,
                name = extractProfileName(file) ?: file.nameWithoutExtension,
                path = file.absolutePath,
                releases = entries.mapNotNull { it["release"] as? String },
            )
        }
    }

    fun importFromUri(context: Context, uri: Uri): UserProfileFile? {
        return try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?: return null
            val parsed = HoconSupport.parseValue(text)
            if (parsed !is Map<*, *>) return null
            val id = UUID.randomUUID().toString().take(8)
            val file = File(storageDir, "$id.conf")
            file.writeText(text)
            UserProfileFile(
                id = id,
                name = extractProfileName(file) ?: id,
                path = file.absolutePath,
            )
        } catch (e: Exception) {
            Log.e(TAG, "import failed", e)
            null
        }
    }

    fun importFromText(name: String, text: String): UserProfileFile? {
        return try {
            val parsed = HoconSupport.parseValue(text)
            if (parsed !is Map<*, *>) return null
            val id = sanitizeName(name)
            val file = File(storageDir, "$id.conf")
            file.writeText(text)
            UserProfileFile(
                id = id,
                name = name,
                path = file.absolutePath,
            )
        } catch (e: Exception) {
            Log.e(TAG, "importFromText failed", e)
            null
        }
    }

    fun load(id: String): Map<String, Any?>? {
        val file = File(storageDir, "$id.conf")
        if (!file.isFile) return null
        return try {
            val text = file.readText()
            HoconSupport.parseValue(text) as? Map<String, Any?>
        } catch (e: Exception) {
            Log.e(TAG, "load $id failed", e)
            null
        }
    }

    fun readText(id: String): String? {
        val file = File(storageDir, "$id.conf")
        return if (file.isFile) file.readText() else null
    }

    fun delete(id: String): Boolean {
        val file = File(storageDir, "$id.conf")
        return file.delete()
    }

    private fun extractProfileName(file: File): String? {
        return try {
            val config = HoconSupport.parseValue(file.readText()) as? Map<*, *>
            config?.get("name") as? String
        } catch (_: Exception) {
            null
        }
    }

    // ---- document-store API (used by the offset-extraction pipeline) -------

    /** Saves verbatim under a unique sanitized name derived from [originalName]; returns the stored id. */
    fun save(originalName: String, text: String): String {
        val target = uniqueFile(sanitizeName(originalName))
        target.writeText(text, StandardCharsets.UTF_8)
        return target.nameWithoutExtension
    }

    fun rawText(id: String): String? = readText(id)

    fun containsRelease(release: String): Boolean = findFileForRelease(release) != null

    fun releasesOf(id: String): List<String> {
        val text = readText(id) ?: return emptyList()
        return runCatching { parseWith(text, documents()) }.getOrNull()
            ?.mapNotNull { it["release"] as? String }
            ?: emptyList()
    }

    /** Replaces an existing document with [text]; false when it is missing. */
    fun overwrite(id: String, text: String): Boolean {
        val file = File(storageDir, "$id.conf")
        if (!file.isFile) return false
        return runCatching {
            file.writeText(text, StandardCharsets.UTF_8)
            true
        }.getOrDefault(false)
    }

    /** Renames a stored document; returns the new id, or null when missing or taken. */
    fun rename(id: String, newName: String): String? {
        val file = File(storageDir, "$id.conf")
        if (!file.isFile) return null
        val sanitized = sanitizeName(newName)
        if (sanitized == id) return id
        val target = File(storageDir, "$sanitized.conf")
        if (target.exists()) return null
        return if (file.renameTo(target)) sanitized else null
    }

    /** Parses a stored document (includes resolved) and renders it as HOCON for export. */
    fun exportHocon(id: String): String? {
        val text = readText(id) ?: return null
        val entries = runCatching { parseEntries(text) }.getOrNull() ?: return null
        if (entries.isEmpty()) return null
        return HoconSupport.render(
            if (entries.size == 1) entries.first() else ValueList().apply { addAll(entries) },
        )
    }

    /** Filename/stem to verbatim text, for include resolution. */
    fun documents(): Map<String, String> {
        val byName = linkedMapOf<String, String>()
        for (file in files()) {
            val text = runCatching { file.readText() }.getOrNull() ?: continue
            byName[file.name] = text
            byName[file.nameWithoutExtension] = text
        }
        return byName
    }

    /**
     * Parses one document, resolving HOCON includes against the other stored
     * documents first and the bundled assets second. [extraDocuments] are
     * picked files not stored yet (import path). Only entries carrying a
     * `release` are returned; a missing include throws [MissingIncludes].
     */
    fun parseEntries(
        text: String,
        extraDocuments: Map<String, String> = emptyMap(),
    ): List<ValueMap> {
        val byName = linkedMapOf<String, String>().apply {
            putAll(documents())
            for ((name, text) in extraDocuments) {
                put(name, text)
                put(name.substringAfterLast('/').removeSuffix(".conf"), text)
            }
        }
        return parseWith(text, byName)
    }

    private fun parseWith(text: String, byName: Map<String, String>): List<ValueMap> {
        val expanded = expandIncludes(text, byName, emptyList())
        val entries = ValueList()
        when (val value = HoconSupport.parseValue(expanded)) {
            is Map<*, *> -> value.asValueMap()
                ?.takeIf { it.containsKey("release") }
                ?.let(entries::add)

            is List<*> -> value.forEach { item ->
                item.asValueMap()?.takeIf { it.containsKey("release") }?.let(entries::add)
            }
        }
        return entries.mapNotNull { it.asValueMap() }
    }

    private fun findFileForRelease(release: String): File? {
        val byName = documents()
        return files().firstOrNull { file ->
            val text = runCatching { file.readText() }.getOrNull() ?: return@firstOrNull false
            runCatching { parseWith(text, byName) }.getOrNull()
                ?.any { it["release"] == release } == true
        }
    }

    private fun expandIncludes(
        text: String,
        byName: Map<String, String>,
        visiting: List<String>,
    ): String {
        if (!text.contains("include ")) return text
        val missing = linkedSetOf<String>()
        val expanded = StringBuilder()
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("include ")) {
                expanded.append(line).append('\n')
                continue
            }
            val target = trimmed.removePrefix("include ").trim().trim('"')
            val key = target.substringAfterLast('/').removeSuffix(".conf")
            val local = byName[key]
            if (local != null) {
                if (key !in visiting) {
                    expanded.append(expandIncludes(local, byName, visiting + key))
                }
            } else {
                val asset = assetLoader.load("$PROFILES_DIR/$target")
                if (asset.isEmpty()) missing += target else expanded.append(HoconSupport.render(asset))
            }
            expanded.append('\n')
        }
        if (missing.isNotEmpty()) throw MissingIncludes(missing.toList())
        return expanded.toString()
    }

    private fun sanitizeName(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\').removeSuffix(".conf")
        val cleaned = ILLEGAL_NAME_CHARS.replace(base, "_").trim()
        return cleaned.ifEmpty { "profile" }
    }

    private fun uniqueFile(base: String): File {
        val candidate = File(storageDir, "$base.conf")
        if (!candidate.exists()) return candidate
        var index = 1
        while (true) {
            val next = File(storageDir, "$base ($index).conf")
            if (!next.exists()) return next
            index += 1
        }
    }

    companion object {
        private const val TAG = "UserProfileStore"
        private const val PROFILES_DIR = "kernel_profiles"
        private val ILLEGAL_NAME_CHARS = Regex("[\\\\/:*?\"<>|\\x00-\\x1f]")
    }
}
