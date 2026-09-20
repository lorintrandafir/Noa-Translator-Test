package com.lorin.noatranslator

data class TranscriptEntry(
    val id: Long,
    val german: String,
    val romanian: String? = null,
    val provisional: Boolean = false,
    val failed: Boolean = false
)

/** Main-thread queue. IDs are never reused, even after Clear. */
class TranslationQueue(initial: List<TranscriptEntry> = emptyList()) {
    var entries = initial.toList()
        private set
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1
    private var running: Long? = null

    fun add(text: String, provisional: Boolean = false): Long? {
        if (text.isBlank()) return null
        val id = nextId++
        entries = entries + TranscriptEntry(id, text.trim(), provisional = provisional)
        return id
    }

    fun next(): TranscriptEntry? {
        if (running != null) return null
        return entries.firstOrNull { it.romanian == null && !it.provisional && !it.failed }
            ?.also { running = it.id }
    }

    fun complete(id: Long, translated: String?): Boolean {
        if (running != id) return false
        running = null
        entries = entries.map {
            if (it.id == id) it.copy(romanian = translated?.takeIf(String::isNotBlank), failed = translated.isNullOrBlank()) else it
        }
        return true
    }

    fun retryFailures() { entries = entries.map { it.copy(failed = false) } }
    // Keep the in-flight slot occupied until its callback, but discard its result.
    fun clear() { entries = emptyList() }
}
