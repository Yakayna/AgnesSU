package com.agnessu.yakayn.domain.text

fun interface TextTransliterator {
    fun transliterate(value: String): String
}
