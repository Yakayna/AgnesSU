package com.agnessu.yakayn.data.shizuku

import android.os.Process
import android.system.Os
import com.agnessu.yakayn.shizuku.IGhostlockCallback
import com.agnessu.yakayn.shizuku.IGhostlockStatusCallback
import com.agnessu.yakayn.shizuku.IGhostlockUserService
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class GhostlockUserService : IGhostlockUserService.Stub() {

    private val running = AtomicBoolean(false)

    override fun destroy() {
        Process.killProcess(Process.myPid())
    }

    override fun runExploit(
        primaryCpu: Int,
        consumerCpu: Int,
        safeMode: Boolean,
        forceAttack: Boolean,
        profileBlob: ByteArray,
        debugDir: String?,
        callback: IGhostlockCallback,
        statusCallback: IGhostlockStatusCallback,
    ) {
        if (!running.compareAndSet(false, true)) {
            callback.onLog("<s> error: exploit already running")
            callback.onComplete(1)
            return
        }
        Thread {
            try {
                doRunExploit(
                    primaryCpu, consumerCpu, safeMode, forceAttack,
                    profileBlob, debugDir, callback, statusCallback,
                )
            } catch (e: Exception) {
                runCatching { callback.onLog("<s> error: ${e.message}") }
                runCatching { callback.onComplete(1) }
            } finally {
                running.set(false)
            }
        }.apply {
            name = "ghostlock-exploit"
            start()
        }
    }

    private fun doRunExploit(
        primaryCpu: Int,
        consumerCpu: Int,
        safeMode: Boolean,
        forceAttack: Boolean,
        profileBlob: ByteArray,
        debugDir: String?,
        callback: IGhostlockCallback,
        statusCallback: IGhostlockStatusCallback,
    ) {
        val uid = Process.myUid()
        callback.onLog("<s> uid=$uid pid=${Process.myPid()}")
        if (uid != SHELL_UID) {
            callback.onLog("<s> error: not running as shell (uid=$uid)")
            callback.onComplete(1)
            return
        }

        val nativeLibDir = findNativeLibDir() ?: run {
            callback.onLog("<s> error: cannot locate native lib dir")
            callback.onComplete(1)
            return
        }
        val binary = File(nativeLibDir, "libghostlock.so")
        if (!binary.isFile) {
            callback.onLog("<s> error: missing ${binary.absolutePath}")
            callback.onComplete(1)
            return
        }

        val workDir = File("/data/local/tmp/.ghostlock")
        workDir.mkdirs()

        // Stage AgnesSU's own ksud into the home dir before the exploit runs so
        // the root script's $HOME_DIR/ksud resolves here instead of falling back
        // to another manager's ksud (whose module UAPI version mismatches the
        // manager and shows "kernel upgrade required").
        stageKsud(nativeLibDir, workDir, callback)

        val logFile = File(workDir, ".ghostlock_native.log")
        logFile.writeText("")

        val argv = mutableListOf(
            binary.absolutePath,
            "--ghostlock-app-call",
            "--enable-status-record",
        )
        if (forceAttack) argv += "--force-attack"
        if (!debugDir.isNullOrEmpty()) argv += listOf("--dump-kernel-log", debugDir)

        callback.onLog("<s> starting: ${binary.name}")

        val process = ProcessBuilder(argv)
            .directory(workDir)
            .redirectErrorStream(true)
            .redirectOutput(logFile)
            .apply {
                environment()["GHOSTLOCK_HOME"] = workDir.absolutePath
                environment()["TMPDIR"] = workDir.absolutePath
                environment()["HOME"] = workDir.absolutePath
            }
            .start()

        val stdinStream = process.outputStream
        val lengthHeader = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            .putInt(profileBlob.size).array()
        stdinStream.write(lengthHeader)
        stdinStream.write(profileBlob)
        stdinStream.flush()

        val tailer = Thread {
            try {
                val raf = RandomAccessFile(logFile, "r")
                var offset = 0L
                while (!Thread.currentThread().isInterrupted) {
                    raf.seek(offset)
                    val line = raf.readLine()
                    if (line != null) {
                        offset = raf.filePointer
                        if (line.startsWith(STATUS_MARKER)) {
                            handleStatusLine(line, stdinStream, statusCallback)
                        } else {
                            runCatching { callback.onLog(line) }
                        }
                    } else {
                        Thread.sleep(100)
                    }
                }
                raf.close()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: Exception) {}
        }.apply {
            name = "ghostlock-tailer"
            isDaemon = true
            start()
        }

        val exitCode = process.waitFor()
        Thread.sleep(300)
        tailer.interrupt()
        tailer.join(1000)

        callback.onLog("<s> native exited code=$exitCode")
        callback.onComplete(exitCode)
    }

    private fun stageKsud(
        nativeLibDir: String,
        workDir: File,
        callback: IGhostlockCallback,
    ) {
        val source = File(nativeLibDir, "libksud.so")
        if (!source.isFile) {
            callback.onLog("<s> warning: libksud.so missing")
            return
        }
        val output = File(workDir, "ksud")
        if (output.isFile && output.length() == source.length()) {
            callback.onLog("<s> ksud already staged")
            return
        }
        runCatching {
            source.inputStream().use { inp -> output.outputStream().use { inp.copyTo(it) } }
            Os.chmod(output.absolutePath, 0b111101101)
            callback.onLog("<s> ksud staged -> ${output.absolutePath}")
        }.onFailure {
            callback.onLog("<s> ksud copy failed: ${it.message}")
        }
    }

    private fun handleStatusLine(
        line: String,
        stdin: java.io.OutputStream,
        statusCallback: IGhostlockStatusCallback,
    ) {
        if (line.contains(STATUS_DISABLED)) return
        val parts = line.removePrefix(STATUS_MARKER).trim().split(' ')
        if (parts.size >= 2) {
            runCatching { statusCallback.onStatus(parts[0], parts[1]) }
            runCatching {
                stdin.write(STATUS_ACK.toByteArray(StandardCharsets.UTF_8))
                stdin.flush()
            }
        }
    }

    private fun findNativeLibDir(): String? {
        val candidates = listOf(
            "/data/app/~~*/com.agnessu.yakayn-*/lib/arm64",
            "/data/app/com.agnessu.yakayn-*/lib/arm64-v8a",
        )
        for (pattern in candidates) {
            val base = pattern.substringBefore("*")
            val dir = File(base).parentFile ?: continue
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { child ->
                    val libDir = File(child, pattern.substringAfter("*/").let { suffix ->
                        if (suffix.contains("*")) {
                            child.listFiles()?.firstOrNull()?.let { inner ->
                                suffix.substringAfter("*/").let { rest -> "${inner.name}/$rest" }
                            } ?: return@forEach
                        } else suffix
                    })
                    if (libDir.isDirectory) return libDir.absolutePath
                }
            }
        }
        val selfLib = "/proc/self/exe"
        return runCatching { File(Os.readlink(selfLib)).parent }.getOrNull()
    }

    companion object {
        private const val SHELL_UID = 2000
        private const val STATUS_MARKER = "\u001eGLK_STATUS"
        private const val STATUS_ACK = "\u001eGLK_STATUS_ACK\n"
        private const val STATUS_DISABLED = "\u001eGLK_STATUS_DISABLED"
    }
}
