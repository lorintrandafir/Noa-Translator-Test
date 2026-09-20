package com.lorin.noatranslator

import java.util.Locale

/** Whole-expression translations only. Never replace words inside arbitrary sentences. */
object ReviewedPhrases {
    fun translate(source: String): String? = when (source.trim().trimEnd('.', '!', '?')
        .trim().lowercase(Locale.GERMAN).replace(Regex("\\s+"), " ")) {
        "danke schön", "dankeschön", "danke sehr" -> "Mulțumesc frumos."
        "vielen dank" -> "Mulțumesc mult."
        "guten morgen" -> "Bună dimineața."
        "guten tag" -> "Bună ziua."
        "guten abend" -> "Bună seara."
        "auf wiedersehen" -> "La revedere."
        else -> null
    }
}
