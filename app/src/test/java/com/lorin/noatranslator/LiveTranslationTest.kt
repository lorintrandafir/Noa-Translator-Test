package com.lorin.noatranslator

import org.junit.Assert.*
import org.junit.Test

class LiveTranslationTest {
    @Test fun coalescesRapidUpdatesWithoutBuildingABacklog() {
        val live = LiveTranslation()
        live.update("Ich")
        val first = live.next(0)!!
        live.update("Ich bin")
        live.update("Ich bin hier")
        assertNull(live.next(500))
        assertTrue(live.complete(first)) // result stays paired with first.source
        assertEquals("Ich", first.source)
        assertEquals(700L, live.delay(500))
        assertNull(live.next(1199))
        assertEquals("Ich bin hier", live.next(1200)!!.source)
    }

    @Test fun clearInvalidatesEvenIdenticalTextFromPreviousUtterance() {
        val live = LiveTranslation()
        live.update("Danke")
        val old = live.next(0)!!
        live.clear()
        live.update("Danke")
        assertNull(live.next(2000))
        assertFalse(live.complete(old))
        val fresh = live.next(2000)!!
        assertTrue(live.complete(fresh))
        assertFalse(live.complete(old))
    }

    @Test fun unchangedOrFailedPreviewDoesNotCreateRetryLoop() {
        val live = LiveTranslation()
        live.update("Hallo")
        val job = live.next(0)!!
        live.complete(job) // caller may have received a failure
        live.update("Hallo")
        assertNull(live.delay(5000))
        live.update("Hallo Welt")
        assertNotNull(live.next(5000))
    }

    @Test fun continuousTextChangesCannotPostponeEveryPreview() {
        val live = LiveTranslation()
        live.update("Anfang")
        live.complete(live.next(0)!!)
        for (time in 100L..1100L step 100) {
            live.update("Wort $time")
            assertNull(live.next(time))
        }
        live.update("Letztes Wort")
        assertNotNull(live.next(1200))
    }

    @Test fun duplicateCompletionCannotReleaseAnotherJob() {
        val live = LiveTranslation()
        live.update("Eins")
        val first = live.next(0)!!
        live.complete(first)
        live.update("Zwei")
        val second = live.next(1200)!!
        assertFalse(live.complete(first))
        live.update("Drei")
        assertNull(live.next(2400))
        assertTrue(live.complete(second))
        assertEquals("Drei", live.next(2400)!!.source)
    }
}
