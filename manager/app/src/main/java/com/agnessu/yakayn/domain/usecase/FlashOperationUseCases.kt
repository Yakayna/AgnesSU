package com.agnessu.yakayn.domain.usecase

import com.agnessu.yakayn.data.flash.FlashRepository
import com.agnessu.yakayn.domain.model.FlashOperation

class ExecuteFlashOperationUseCase(private val repository: FlashRepository) {
    operator fun invoke(operation: FlashOperation) = repository.execute(operation)
}
