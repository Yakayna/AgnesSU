package com.agnessu.yakayn.domain.usecase

import com.agnessu.yakayn.data.AppSettingsRepository
import com.agnessu.yakayn.data.startup.ApplicationInitializationRepository
import com.agnessu.yakayn.data.startup.StartupRepository

class InitializeApplicationUseCase(
    private val settingsRepository: AppSettingsRepository,
    private val startupRepository: StartupRepository,
    private val initializationRepository: ApplicationInitializationRepository,
) {
    suspend operator fun invoke() {
        runCatching {
            settingsRepository.preload()
            initializationRepository.initialize()
        }.onSuccess {
            startupRepository.markReady()
        }.onFailure { error ->
            startupRepository.markFailed(error)
        }
    }
}
