package com.odometersdk

import com.odometersdk.validation.ReadingValidator
import org.junit.Assert.*
import org.junit.Test

class ReadingValidatorTest {

    @Test
    fun `rejects too-short digit strings`() {
        val outcome = ReadingValidator.validate("12")
        assertFalse(outcome.isPlausible)
    }

    @Test
    fun `rejects all-repeated-digit strings`() {
        val outcome = ReadingValidator.validate("000000")
        assertFalse(outcome.isPlausible)
    }

    @Test
    fun `accepts a typical 6-digit reading`() {
        val outcome = ReadingValidator.validate("184203")
        assertTrue(outcome.isPlausible)
        assertEquals("184203", outcome.normalized)
        assertTrue(outcome.formatScore > 0.9f)
    }

    @Test
    fun `penalizes a reading lower than previous known reading`() {
        val outcome = ReadingValidator.validate("100000", previousKnownReading = 150000)
        assertTrue(outcome.formatScore < 0.5f)
    }
}
