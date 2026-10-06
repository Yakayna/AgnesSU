package com.agnessu.yakayn.domain.usecase

import com.agnessu.yakayn.data.kernel.UmountRepository
import com.agnessu.yakayn.domain.model.UmountPath

class ObserveUmountStateUseCase(private val repository: UmountRepository) {
    operator fun invoke() = repository.state
}

class RefreshUmountPathsUseCase(private val repository: UmountRepository) {
    suspend operator fun invoke() = repository.refresh()
}

class AddUmountPathUseCase(private val repository: UmountRepository) {
    suspend operator fun invoke(path: String, flags: Int) = repository.add(path, flags)
}

class RemoveUmountPathUseCase(private val repository: UmountRepository) {
    suspend operator fun invoke(entry: UmountPath) = repository.remove(entry)
}
