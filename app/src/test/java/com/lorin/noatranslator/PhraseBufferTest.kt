package com.lorin.noatranslator

import org.junit.Assert.*
import org.junit.Test

class PhraseBufferTest {
    @Test fun joinsAdjacentFinalsWithoutDroppingRepeatedWords() {
        val b = PhraseBuffer()
        b.add("Brötchen", 0)
        b.speechActivity(800)
        b.add("mit allerlei Aufschnitt", 1_500)
        assertEquals("Brötchen mit allerlei Aufschnitt", b.text)
        assertEquals(1_400L, b.delay(1_500))
        b.add("Aufschnitt", 2_000)
        assertEquals("Brötchen mit allerlei Aufschnitt Aufschnitt", b.take())
        assertNull(b.delay(2_100))
    }
    @Test fun pauseFlushesButContinuousSpeechHasAnUpperBound() {
        val b = PhraseBuffer()
        b.add("ein", 0)
        assertEquals(0L, b.delay(1_400))
        b.speechActivity(7_900)
        assertEquals(100L, b.delay(7_900))
        assertEquals(0L, b.delay(8_000))
    }
    @Test fun punctuationAndLengthFlushImmediately() {
        val b = PhraseBuffer()
        b.add("Guten Morgen!", 0)
        assertEquals(0L, b.delay(0))
        b.take()
        b.add("a".repeat(300), 0)
        assertEquals(0L, b.delay(0))
    }
    @Test fun reviewedExpressionsDoNotRewriteArbitraryGerman() {
        assertEquals("Mulțumesc frumos.", ReviewedPhrases.translate(" Danke   schön! "))
        assertNull(ReviewedPhrases.translate("Ich sage danke schön und gehe."))
        assertNull(ReviewedPhrases.translate("danke schon"))
        assertNull(ReviewedPhrases.translate("in Lorin"))
    }
}
