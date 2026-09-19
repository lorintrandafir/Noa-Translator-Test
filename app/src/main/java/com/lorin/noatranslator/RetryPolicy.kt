package com.lorin.noatranslator

/** A ready/beginning/RMS callback is NOT recognition success. Only text resets retries. */
internal class RetryPolicy {
    private var emptyCount = 0
    private var failureCount = 0

    fun reset() {
        emptyCount = 0
        failureCount = 0
    }

    fun withoutText(): Long? {
        emptyCount++
        return if (emptyCount >= 4) null else emptyCount * 1_500L
    }

    fun failure(rateLimited: Boolean = false): Long? {
        failureCount++
        if (failureCount >= 5) return null
        return if (rateLimited) 30_000L else (2_000L shl (failureCount - 1)).coerceAtMost(16_000L)
    }
}
