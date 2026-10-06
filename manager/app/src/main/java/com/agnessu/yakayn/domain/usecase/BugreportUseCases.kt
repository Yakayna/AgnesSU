package com.agnessu.yakayn.domain.usecase

import java.io.File
import com.agnessu.yakayn.data.logging.BugreportRepository

class GenerateBugreportUseCase(
    private val repository: BugreportRepository,
) {
    operator fun invoke(): File = repository.create()
}
