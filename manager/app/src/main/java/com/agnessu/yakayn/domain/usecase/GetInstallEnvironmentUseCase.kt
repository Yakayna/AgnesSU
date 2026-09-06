package com.agnessu.yakayn.domain.usecase

import com.agnessu.yakayn.data.flash.FlashRepository
import com.agnessu.yakayn.domain.model.InstallEnvironment

class GetInstallEnvironmentUseCase(
    private val repository: FlashRepository,
) {
    fun cached(): InstallEnvironment? = repository.installEnvironment.value

    suspend operator fun invoke(forceRefresh: Boolean = false) =
        repository.getInstallEnvironment(forceRefresh)
}
