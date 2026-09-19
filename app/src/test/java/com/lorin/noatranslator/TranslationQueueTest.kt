package com.lorin.noatranslator

import org.junit.Assert.*
import org.junit.Test

class TranslationQueueTest {
    @Test fun preservesPhraseAssociationAndRepeatedPhrases() {
        val q = TranslationQueue()
        q.add("danke"); q.add("danke")
        val first = q.next()!!
        assertNull(q.next())
        q.complete(first.id, "mulțumesc")
        val second = q.next()!!
        assertNotEquals(first.id, second.id)
        assertFalse(q.complete(first.id, "stale"))
        assertNull(q.next())
        q.complete(second.id, "mulțumesc")
        assertEquals(listOf("mulțumesc", "mulțumesc"), q.entries.map { it.romanian })
    }

    @Test fun clearDuringTranslationCannotRestoreDeletedText() {
        val q = TranslationQueue()
        q.add("alt")
        val old = q.next()!!
        q.clear(); q.add("neu")
        assertNull(q.next())
        q.complete(old.id, "vechi")
        val fresh = q.next()!!
        assertNotEquals(old.id, fresh.id)
        q.complete(fresh.id, "nou")
        assertEquals(listOf("nou"), q.entries.map { it.romanian })
    }

    @Test fun failureDoesNotBlockLaterPhrasesAndCanRetry() {
        val q = TranslationQueue()
        q.add("eins"); q.add("zwei")
        q.complete(q.next()!!.id, null)
        q.complete(q.next()!!.id, "doi")
        assertNull(q.next())
        assertTrue(q.entries.first().failed)
        q.retryFailures()
        q.complete(q.next()!!.id, "unu")
        assertEquals(listOf("unu", "doi"), q.entries.map { it.romanian })
    }

    @Test fun restoredHistorySkipsCompletedAndProvisionalText() {
        val q = TranslationQueue(listOf(
            TranscriptEntry(1, "gut", "bine"),
            TranscriptEntry(2, "unfertig", provisional = true),
            TranscriptEntry(3, "neu")
        ))
        assertEquals(3L, q.next()!!.id)
        q.complete(3, "nou")
        assertNull(q.next())
        q.add("mehr")
        assertEquals(4L, q.next()!!.id)
    }

    @Test fun emptyTranslationIsRetryableAndOriginalIsKept() {
        val q = TranslationQueue()
        q.add("  hallo  ")
        q.complete(q.next()!!.id, " ")
        assertEquals("hallo", q.entries.single().german)
        assertTrue(q.entries.single().failed)
        assertNull(q.entries.single().romanian)
    }
}
