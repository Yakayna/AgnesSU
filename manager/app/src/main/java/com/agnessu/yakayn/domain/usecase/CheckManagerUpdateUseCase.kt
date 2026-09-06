package com.agnessu.yakayn.domain.usecase

import com.agnessu.yakayn.data.update.ManagerUpdateRepository
import com.agnessu.yakayn.domain.model.ManagerUpdateChannel
import com.agnessu.yakayn.domain.model.ManagerUpdateInfo

class CheckManagerUpdateUseCase(
    private val repository: ManagerUpdateRepository,
) {
    suspend operator fun invoke(channel: ManagerUpdateChannel): ManagerUpdateInfo? =
        when (channel) {
            ManagerUpdateChannel.STABLE -> repository.checkStableUpdate()
            ManagerUpdateChannel.BETA -> repository.checkBetaUpdate()
        }
}
