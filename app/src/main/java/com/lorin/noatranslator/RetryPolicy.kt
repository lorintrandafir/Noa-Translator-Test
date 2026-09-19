package com.lorin.noatranslator

/** Silence is normal in hands-free use; only fast empty loops and actual faults back off. */
internal class RetryPolicy {
    private var rapidEmptyCount = 0
    private var failureCount = 0

    companion object {
        const val RESTART_DELAY_MS = 150L
        const val RAPID_EMPTY_THRESHOLD_MS = 750L
    }

    fun reset() {
        rapidEmptyCount = 0
        failureCount = 0
    }

    fun withoutText(utteranceDurationMs: Long): Long? {
        if (utteranceDurationMs >= RAPID_EMPTY_THRESHOLD_MS) {
            rapidEmptyCount = 0
            return RESTART_DELAY_MS
        }
        // A provider rejecting immediately must not create a tight restart loop.
        rapidEmptyCount++
        if (rapidEmptyCount >= 6) return null
        return (500L shl (rapidEmptyCount - 1)).coerceAtMost(8_000L)
    }

    fun failure(rateLimited: Boolean = false): Long? {
        failureCount++
        if (failureCount >= 5) return null
        return if (rateLimited) 30_000L else (2_000L shl (failureCount - 1)).coerceAtMost(16_000L)
    }
}
