package com.agnessu.yakayn.domain.usecase

import com.agnessu.yakayn.data.startup.StartupRepository

class ObserveStartupStateUseCase(
    private val repository: StartupRepository,
) {
    operator fun invoke() = repository.state
}
