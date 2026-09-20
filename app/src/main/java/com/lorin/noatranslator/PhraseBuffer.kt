package com.lorin.noatranslator

/** Groups adjacent recognition results; never changes or guesses recognized words. */
class PhraseBuffer {
    var text: String = ""
        private set
    private var startedAt = 0L
    private var activityAt = 0L

    fun add(fragment: String, now: Long) {
        if (fragment.isBlank()) return
        if (text.isEmpty()) startedAt = now
        text = listOf(text, fragment.trim()).filter { it.isNotEmpty() }.joinToString(" ")
        activityAt = now
    }

    fun speechActivity(now: Long) { if (text.isNotEmpty()) activityAt = now }

    fun delay(now: Long): Long? {
        if (text.isEmpty()) return null
        if (text.length >= 300 || text.last() in ".!?") return 0
        return minOf(1_400 - (now - activityAt), 8_000 - (now - startedAt)).coerceAtLeast(0)
    }

    fun take(): String = text.also { text = "" }
}
