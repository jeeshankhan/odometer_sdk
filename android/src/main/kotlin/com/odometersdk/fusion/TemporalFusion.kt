package com.odometersdk.fusion

/**
 * A single OCR read is noisy — a hand tremor, a glare flicker, or motion blur
 * on one frame can turn "184203" into "184208". Rather than trusting one
 * shot, the SDK samples a short burst (default 5 frames over ~600ms) once the
 * detector locks onto a stable region, and this class votes across them.
 *
 * This is the main reason this SDK's accuracy is meaningfully better than a
 * naive single-capture OCR pipeline in the field.
 */
class TemporalFusion(private val windowSize: Int = 5) {

    private val recentReadings = ArrayDeque<String>()

    fun push(reading: String) {
        recentReadings.addLast(reading)
        if (recentReadings.size > windowSize) recentReadings.removeFirst()
    }

    fun reset() = recentReadings.clear()

    /** True once enough frames have been collected to make a fused decision. */
    fun isReady(minSamples: Int = 3): Boolean = recentReadings.size >= minSamples

    data class FusionResult(val consensus: String, val agreement: Float)

    /**
     * Majority vote on exact string match; if no exact majority exists,
     * falls back to per-digit-position voting on same-length candidates
     * (handles the case where one digit flickers but the rest agree).
     */
    fun fuse(): FusionResult? {
        if (recentReadings.isEmpty()) return null

        val counts = recentReadings.groupingBy { it }.eachCount()
        val topExact = counts.maxByOrNull { it.value }!!
        if (topExact.value.toFloat() / recentReadings.size >= 0.6f) {
            return FusionResult(topExact.key, topExact.value.toFloat() / recentReadings.size)
        }

        val sameLength = recentReadings.groupBy { it.length }.maxByOrNull { it.value.size }?.value
            ?: return FusionResult(topExact.key, topExact.value.toFloat() / recentReadings.size)

        val length = sameLength.first().length
        val consensusChars = StringBuilder()
        var agreementSum = 0f
        for (pos in 0 until length) {
            val charsAtPos = sameLength.map { it[pos] }
            val best = charsAtPos.groupingBy { it }.eachCount().maxByOrNull { it.value }!!
            consensusChars.append(best.key)
            agreementSum += best.value.toFloat() / charsAtPos.size
        }

        return FusionResult(consensusChars.toString(), agreementSum / length)
    }
}
