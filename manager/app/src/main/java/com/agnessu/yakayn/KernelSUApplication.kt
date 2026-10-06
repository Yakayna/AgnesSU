package com.agnessu.yakayn

import android.app.Application
import android.os.Build
import com.agnessu.yakayn.data.shizuku.ShizukuExploitRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import com.agnessu.yakayn.di.appModules
import com.agnessu.yakayn.domain.usecase.InitializeApplicationUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class KernelSUApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val processName = getProcessName()
            // Skip heavy init for background processes: the Magica isolated
            // process and the Shizuku shell-service process.
            if (processName.endsWith("MagicaService") || processName.endsWith(":service")) {
                return
            }
        }

        val koin = startKoin {
            androidLogger()
            androidContext(this@KernelSUApplication)
            modules(appModules)
        }.koin
        koin.get<ShizukuExploitRunner>().init()

        runBlocking(Dispatchers.IO) {
            koin.get<InitializeApplicationUseCase>()()
        }
    }
}
