package com.example.sonotes.data

import java.text.Normalizer

private val accentMarks = Regex("\\p{M}+")

fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(accentMarks, "")
        .lowercase()
        .replace('ς', 'σ')