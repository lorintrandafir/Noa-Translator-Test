package com.lorin.noatranslator

/** Latest-only preview queue; all methods run on the main thread. */
internal class LiveTranslation {
    data class Job(val revision: Long, val source: String, val epoch: Long)
    private var epoch = 0L
    private var revision = 0L
    private var source = ""
    private var attempted = -1L
    private var running: Job? = null
    private var lastStart: Long? = null

    fun update(text: String) {
        val clean = text.trim()
        if (clean != source) { source = clean; revision++ }
    }

    fun delay(now: Long): Long? {
        if (running != null || source.isBlank() || attempted == revision) return null
        return lastStart?.let { (1_200L - (now - it)).coerceAtLeast(0) } ?: 0L
    }

    fun next(now: Long): Job? {
        if (delay(now) != 0L) return null
        return Job(revision, source, epoch).also {
            running = it; attempted = revision; lastStart = now
        }
    }

    /** Returns true for this utterance only. The UI must display job.source with its result. */
    fun complete(job: Job): Boolean {
        if (running != job) return false
        running = null
        return job.epoch == epoch
    }

    fun clear() {
        source = ""; revision++; epoch++; lastStart = null
        // Keep the in-flight slot until its callback to prevent parallel preview jobs.
    }
}
