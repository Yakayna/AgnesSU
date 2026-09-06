package com.agnessu.yakayn.domain.usecase

import com.agnessu.yakayn.data.susfs.SuSFSRepository

class GetSuSFSStatusUseCase(private val repository: SuSFSRepository) {
    suspend operator fun invoke() = repository.getStatus()
}

