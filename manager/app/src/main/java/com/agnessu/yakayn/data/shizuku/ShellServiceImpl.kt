package com.agnessu.yakayn.data.shizuku

import android.content.Context
import android.system.Os
import androidx.annotation.Keep
import com.agnessu.yakayn.shizuku.IShellService
import java.io.File
import java.io.RandomAccessFile

/**
 * Shizuku UserService: executes commands in the shell domain (uid=2000,
 * u:r:shell:s0) and writes file chunks for /data/local/tmp staging. The
 * canonical pattern is IShellService.Stub + a @Keep(Context) constructor.
 */
class ShellServiceImpl : IShellService.Stub {

    constructor()

    @Keep
    constructor(context: Context)

    override fun destroy() = System.exit(0)

    override fun exit() = System.exit(0)

    override fun ping(): String = "pong uid=${Os.getuid()}"

    override fun exec(command: String): String = try {
        val process = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        "EXIT=$exit\n$output"
    } catch (e: Exception) {
        "EXIT=-1\n${e.message ?: "error"}"
    }

    override fun writeFileChunk(path: String, offset: Long, data: ByteArray): Boolean = try {
        val file = File(path)
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(offset)
            raf.write(data)
        }
        true
    } catch (_: Exception) {
        false
    }
}
