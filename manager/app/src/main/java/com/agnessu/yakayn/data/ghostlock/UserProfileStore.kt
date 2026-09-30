package com.agnessu.yakayn.data.ghostlock

import android.content.Context
import android.net.Uri
import android.util.Log
import com.agnessu.yakayn.profile.HoconSupport
import java.io.File
import java.util.UUID

class UserProfileStore(context: Context) {

    private val storageDir = File(context.filesDir, "ghostlock_profiles").also { it.mkdirs() }

    fun list(): List<UserProfileFile> {
        return storageDir.listFiles { f -> f.extension == "conf" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                UserProfileFile(
                    id = file.nameWithoutExtension,
                    name = extractProfileName(file) ?: file.nameWithoutExtension,
                    path = file.absolutePath,
                )
            } ?: emptyList()
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
            val id = name.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32).ifEmpty { "profile" }
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

    companion object {
        private const val TAG = "UserProfileStore"
    }
}
