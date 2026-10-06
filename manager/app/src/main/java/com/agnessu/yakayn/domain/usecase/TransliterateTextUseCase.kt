package com.agnessu.yakayn.domain.usecase

import com.agnessu.yakayn.domain.text.TextTransliterator

class TransliterateTextUseCase(private val transliterator: TextTransliterator) {
    operator fun invoke(value: String): String = transliterator.transliterate(value)
}
