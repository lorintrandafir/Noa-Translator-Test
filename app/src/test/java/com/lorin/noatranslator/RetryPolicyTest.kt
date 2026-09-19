package com.lorin.noatranslator

import org.junit.Assert.*
import org.junit.Test

class RetryPolicyTest {
    @Test fun longQuietPeriodNeverExhaustsNormalRetryBudget() {
        val policy = RetryPolicy()
        repeat(1_000) { assertEquals(150L, policy.withoutText(12_000)) }
    }

    @Test fun shortSpokenPhrasesDoNotAccumulateExtraDelay() {
        val policy = RetryPolicy()
        repeat(20) { assertEquals(150L, policy.withoutText(1_200)) }
    }

    @Test fun immediatelyRejectedSessionsBackOffAndStop() {
        val policy = RetryPolicy()
        listOf(500L, 1_000L, 2_000L, 4_000L, 8_000L).forEach {
            assertEquals(it, policy.withoutText(100))
        }
        assertNull(policy.withoutText(100))
    }

    @Test fun normalLengthSessionClearsRapidRejectionStreak() {
        val policy = RetryPolicy()
        repeat(5) { policy.withoutText(0) }
        assertEquals(150L, policy.withoutText(750))
        assertEquals(500L, policy.withoutText(100))
    }

    @Test fun rapidThresholdBoundaryIsExplicit() {
        val policy = RetryPolicy()
        assertEquals(500L, policy.withoutText(749))
        assertEquals(150L, policy.withoutText(750))
    }

    @Test fun transientErrorsBackOffAndStop() {
        val policy = RetryPolicy()
        listOf(2_000L, 4_000L, 8_000L, 16_000L).forEach { assertEquals(it, policy.failure()) }
        assertNull(policy.failure())
    }

    @Test fun silenceDoesNotEraseServiceFailureBudget() {
        val policy = RetryPolicy()
        repeat(4) { policy.failure() }
        repeat(20) { policy.withoutText(5_000) }
        assertNull(policy.failure())
    }

    @Test fun serviceFailureDoesNotEraseRapidRejectionBudget() {
        val policy = RetryPolicy()
        repeat(5) { policy.withoutText(100) }
        policy.failure()
        assertNull(policy.withoutText(100))
    }

    @Test fun successfulTextOrNewStartResetsBothBudgets() {
        val policy = RetryPolicy()
        repeat(5) { policy.withoutText(100) }
        repeat(4) { policy.failure() }
        policy.reset()
        assertEquals(500L, policy.withoutText(100))
        assertEquals(2_000L, policy.failure())
    }

    @Test fun rateLimitWaitsThirtySeconds() {
        assertEquals(30_000L, RetryPolicy().failure(rateLimited = true))
    }
}
