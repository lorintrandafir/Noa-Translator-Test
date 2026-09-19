package com.lorin.noatranslator

import org.junit.Assert.*
import org.junit.Test

class RetryPolicyTest {
    @Test fun repeatedSilenceStopsInsteadOfReopeningForever() {
        val policy = RetryPolicy()
        assertEquals(1_500L, policy.withoutText())
        assertEquals(3_000L, policy.withoutText())
        assertEquals(4_500L, policy.withoutText())
        assertNull(policy.withoutText())
    }

    @Test fun transientErrorsBackOffAndStop() {
        val policy = RetryPolicy()
        listOf(2_000L, 4_000L, 8_000L, 16_000L).forEach { assertEquals(it, policy.failure()) }
        assertNull(policy.failure())
    }

    @Test fun noMatchDoesNotEraseServiceFailureBudget() {
        val policy = RetryPolicy()
        repeat(4) { policy.failure() }
        policy.withoutText()
        assertNull(policy.failure())
    }

    @Test fun serviceFailureDoesNotEraseEmptyResultBudget() {
        val policy = RetryPolicy()
        repeat(3) { policy.withoutText() }
        policy.failure()
        assertNull(policy.withoutText())
    }

    @Test fun successfulTextResetsBothBudgets() {
        val policy = RetryPolicy()
        repeat(3) { policy.withoutText() }
        repeat(4) { policy.failure() }
        policy.reset()
        assertEquals(1_500L, policy.withoutText())
        assertEquals(2_000L, policy.failure())
    }

    @Test fun rateLimitWaitsThirtySeconds() {
        assertEquals(30_000L, RetryPolicy().failure(rateLimited = true))
    }
}
