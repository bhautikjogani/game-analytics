package com.ogl.game.analytics

import com.ogl.game.analytics.validation.AnalyticsValidator
import com.ogl.game.analytics.AnalyticsConfig
import org.junit.Assert.*
import org.junit.Test

class AnalyticsValidatorTest {
    private val lenient = AnalyticsValidator(AnalyticsConfig())
    private val strict = AnalyticsValidator(AnalyticsConfig(strictValidation = true))

    @Test fun validEventNamePasses() {
        assertEquals("turn_played", lenient.validateEventName("turn_played").value)
    }

    @Test fun invalidEventNameIsSanitizedWhenLenient() {
        assertEquals("turn_played", lenient.validateEventName("turn played").value)
    }

    @Test fun invalidEventNameIsDroppedWhenStrict() {
        assertNull(strict.validateEventName("turn played").value)
    }

    @Test fun reservedNamesAndPrefixesAreDropped() {
        assertNull(lenient.validateEventName("session_start").value)
        assertNull(lenient.validateEventName("app_background").value)
        assertNull(lenient.validateEventName("firebase_custom").value)
    }

    @Test fun parameterTypesAreCoerced() {
        val r = lenient.validateParameters(mapOf("a" to 1, "b" to 2.5f, "c" to true, "d" to "x", "e" to null)).value!!
        assertEquals(1L, r["a"]); assertEquals(2.5, r["b"]); assertEquals(1L, r["c"]); assertEquals("x", r["d"])
        assertFalse(r.containsKey("e"))
    }

    @Test fun unsupportedAndNonFiniteValuesAreDropped() {
        val r = lenient.validateParameters(mapOf("list" to listOf(1), "nan" to Double.NaN, "ok" to 1))
        assertEquals(setOf("ok"), r.value!!.keys)
        assertEquals(2, r.issues.size)
    }

    @Test fun longStringsAreTruncated() {
        val r = lenient.validateParameters(mapOf("s" to "x".repeat(300))).value!!
        assertEquals(100, (r["s"] as String).length)
    }

    @Test fun excessParametersAreDropped() {
        val many = (1..40).associate { "p$it" to it }
        assertEquals(25, lenient.validateParameters(many).value!!.size)
        assertEquals(3, lenient.validateParameters(many, maxCount = 3).value!!.size)
    }

    @Test fun strictDropsEverythingOnAnyIssue() {
        assertNull(strict.validateParameters(mapOf("ok" to 1, "bad name" to 2)).value)
    }
}
