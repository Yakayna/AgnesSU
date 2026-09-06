package com.agnessu.yakayn.domain.usecase

import com.agnessu.yakayn.data.logging.BugreportRepository
import java.io.File

class GenerateBugreportUseCase(
    private val repository: BugreportRepository,
) {
    operator fun invoke(): File = repository.create()
}
