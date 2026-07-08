package com.odometersdk.validation

/**
 * Sanity-checks an OCR'd digit string against what a real odometer reading
 * looks like. Catches the common failure modes: OCR picking up a partial
 * VIN digit sequence, a phone number on a sticker, or a single stray digit.
 */
object ReadingValidator {

    private const val MIN_DIGITS = 4   // fewer than this is almost never a real odometer
    private const val MAX_DIGITS = 7   // most consumer odometers cap at 999999 or 1,000,000

    data class ValidationOutcome(val isPlausible: Boolean, val formatScore: Float, val normalized: String?)

    fun validate(digits: String, previousKnownReading: Int? = null): ValidationOutcome {
        val cleaned = digits.trimStart('0').ifEmpty { "0" }

        if (digits.length < MIN_DIGITS || digits.length > MAX_DIGITS) {
            return ValidationOutcome(false, 0f, null)
        }

        // Reject obviously-fake patterns: all-same-digit runs ("000000", "111111")
        // are a common OCR degenerate output on blank/glared displays.
        if (digits.toSet().size == 1) {
            return ValidationOutcome(false, 0f, null)
        }

        var score = 1.0f

        // A reading that's LOWER than one we've previously recorded for the
        // same vehicle is physically implausible (barring odometer replacement).
        val asInt = cleaned.toIntOrNull()
        if (previousKnownReading != null && asInt != null && asInt < previousKnownReading) {
            score *= 0.3f
        }

        // Mild preference for typical lengths (5-6 digits covers the vast
        // majority of in-service vehicles) without hard-rejecting others.
        if (digits.length !in 5..6) score *= 0.85f

        return ValidationOutcome(isPlausible = score > 0.25f, formatScore = score, normalized = cleaned)
    }
}
