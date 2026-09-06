package com.agnessu.yakayn.data.application

import com.agnessu.yakayn.Natives
import com.agnessu.yakayn.data.shell.KsuCliRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ApplicationControlRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    suspend fun ensureManagerInstalled(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (Natives.isManager && !Natives.requireNewKernel()) ksuCliRepository.install()
        }
    }

    suspend fun reboot(reason: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { ksuCliRepository.reboot(reason) }
    }
}
