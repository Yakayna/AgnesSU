package com.agnessu.yakayn.data.samsung

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin Shizuku transport for the Samsung engine. Mirrors Root-My-Galaxy's
 * ShizukuController: it obtains the Shizuku service binder and calls
 * `IShizukuService.newProcess` through the `moe.shizuku.server` AIDL, wrapping
 * the returned `IRemoteProcess` in a `java.lang.Process` so every command runs
 * in the shell domain (uid 2000).
 */
object SamsungShizuku {
    private const val PERMISSION_REQUEST_CODE = 0x5341

    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    suspend fun pingUntilRunning(timeoutMillis: Long = 3_000): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            if (isRunning()) return true
            delay(100)
        }
        return isRunning()
    }

    fun isGranted(): Boolean = try {
        isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    suspend fun requestPermission(): Boolean {
        if (isGranted()) return true
        if (!isRunning()) return false
        return suspendCancellableCoroutine { continuation ->
            lateinit var listener: Shizuku.OnRequestPermissionResultListener
            listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                if (requestCode == PERMISSION_REQUEST_CODE) {
                    Shizuku.removeRequestPermissionResultListener(listener)
                    continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            continuation.invokeOnCancellation {
                Shizuku.removeRequestPermissionResultListener(listener)
            }
            try {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            } catch (error: Throwable) {
                Shizuku.removeRequestPermissionResultListener(listener)
                continuation.resumeWithException(error)
            }
        }
    }

    fun exec(cmd: Array<String>, env: Array<String>? = null, dir: String? = null): Process {
        if (!isRunning()) throw IllegalStateException("Shizuku is not running")
        val binder = Shizuku.getBinder()
            ?: throw IllegalStateException("Shizuku binder is not available")
        return RemoteProcess(IShizukuService.Stub.asInterface(binder).newProcess(cmd, env, dir))
    }

    /**
     * Streams [source] into a shell-owned remote file, then chmods it. Used to
     * stage the helper/payload into /data/local/tmp, which the app process is
     * SELinux-confined away from.
     */
    fun writeFile(remotePath: String, mode: String, source: InputStream) {
        val process = exec(arrayOf("sh", "-c", "cat > '$remotePath' && chmod $mode '$remotePath'"))
        val exitCode = try {
            process.outputStream.use { output ->
                source.use { input -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            }
            process.waitFor()
        } finally {
            if (process.isAlive) process.destroy()
        }
        check(exitCode == 0) { "Failed to stage $remotePath (exit $exitCode)" }
    }

    private class RemoteProcess(private val remote: IRemoteProcess) : Process() {
        private val input by lazy { ParcelFileDescriptor.AutoCloseInputStream(remote.getInputStream()) }
        private val output by lazy { ParcelFileDescriptor.AutoCloseOutputStream(remote.getOutputStream()) }
        private val error by lazy { ParcelFileDescriptor.AutoCloseInputStream(remote.getErrorStream()) }

        override fun getInputStream(): InputStream = input
        override fun getOutputStream(): OutputStream = output
        override fun getErrorStream(): InputStream = error
        override fun waitFor(): Int = remote.waitFor()
        override fun exitValue(): Int = remote.exitValue()

        override fun destroy() {
            runCatching { remote.destroy() }
        }

        override fun destroyForcibly(): Process {
            destroy()
            return this
        }

        override fun isAlive(): Boolean = remote.alive()
    }

    private const val DEFAULT_BUFFER_SIZE = 8 * 1024
}
