package com.agnessu.yakayn.data.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.agnessu.yakayn.shizuku.IShellService
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Shizuku transport for the Iqoo/Vivo preload exploit. The exploit's trigger is
 * a shell-level command (`LD_PRELOAD=... /system/bin/true`), which must run in
 * the shell domain rather than the app's own seccomp/SELinux context. Shizuku
 * gives us exactly that through a UserService bound to [ShellServiceImpl].
 */
object ShellTransport {

    private const val TAG = "AgnesShellTransport"
    private const val SHIZUKU_CHUNK = 512 * 1024

    @Volatile
    var alive: Boolean = false
        private set

    private var registered = false

    @Volatile
    private var service: IShellService? = null

    /** Register binder lifecycle listeners (once, from Application). */
    fun init() {
        if (registered) return
        registered = true
        try {
            Shizuku.addBinderReceivedListenerSticky {
                alive = true
                Log.i(TAG, "Shizuku binder received")
            }
            Shizuku.addBinderDeadListener {
                alive = false
                service = null
                Log.i(TAG, "Shizuku binder dead")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "init failed", e)
        }
    }

    fun permissionGranted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /** Request permission; [onGranted] fires after the user confirms. */
    fun requestPermission(requestCode: Int = 100, onGranted: () -> Unit) {
        try {
            if (Shizuku.getVersion() < 11) return
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                onGranted()
                return
            }
            if (Shizuku.shouldShowRequestPermissionRationale()) return
            lateinit var listener: Shizuku.OnRequestPermissionResultListener
            listener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
                    onGranted()
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(requestCode)
        } catch (e: Throwable) {
            Log.e(TAG, "requestPermission failed", e)
        }
    }

    @Synchronized
    fun bindService(context: Context): Boolean {
        service?.let { return true }
        if (!alive || !permissionGranted()) return false
        return try {
            var binder: IBinder? = null
            val latch = CountDownLatch(1)
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, b: IBinder?) {
                    binder = b
                    latch.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    service = null
                }
            }
            Shizuku.bindUserService(
                Shizuku.UserServiceArgs(ComponentName(context, ShellServiceImpl::class.java))
                    .daemon(false)
                    .processNameSuffix("service")
                    .version(1),
                conn,
            )
            latch.await(15, TimeUnit.SECONDS)
            val b = binder
            service = if (b != null && b.pingBinder()) IShellService.Stub.asInterface(b) else null
            service != null
        } catch (e: Throwable) {
            Log.e(TAG, "bindUserService failed", e)
            false
        }
    }

    /** Run a command in the shell domain. Returns (exitCode, output). */
    fun exec(context: Context, command: String): Pair<Int, String> {
        var svc = service
        if (svc == null && bindService(context)) svc = service
        if (svc == null) return -1 to "Shizuku service not connected"
        return try {
            val output = svc.exec(command)
            val exit = Regex("EXIT=(-?\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: -1
            exit to output.removePrefix("EXIT=$exit\n")
        } catch (e: Throwable) {
            Log.e(TAG, "exec failed", e)
            service = null
            -1 to (e.message ?: "shizuku error")
        }
    }

    /** Stage [data] to [remotePath] in /data/local/tmp via chunked writes. */
    fun deploy(context: Context, data: ByteArray, remotePath: String): Pair<Boolean, String> {
        var svc = service
        if (svc == null && bindService(context)) svc = service
        if (svc == null) return false to "Shizuku service not connected"
        return try {
            var offset = 0L
            while (offset < data.size) {
                val len = minOf(SHIZUKU_CHUNK, (data.size - offset).toInt()).toLong()
                val chunk = data.copyOfRange(offset.toInt(), (offset + len).toInt())
                if (!svc.writeFileChunk(remotePath, offset, chunk)) {
                    return false to "writeFileChunk failed at $offset/${data.size}"
                }
                offset += len
            }
            svc.exec("chmod 644 $remotePath")
            true to ""
        } catch (e: Throwable) {
            Log.e(TAG, "deploy failed", e)
            false to (e.message ?: "binder error")
        }
    }
}
