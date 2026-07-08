package com.odometersdk

import com.odometersdk.fusion.TemporalFusion
import org.junit.Assert.*
import org.junit.Test

class TemporalFusionTest {

    @Test
    fun `exact majority wins outright`() {
        val fusion = TemporalFusion(windowSize = 5)
        listOf("184203", "184203", "184203", "184208", "184203").forEach { fusion.push(it) }
        val result = fusion.fuse()
        assertEquals("184203", result?.consensus)
    }

    @Test
    fun `per-digit voting handles a single flickering digit`() {
        val fusion = TemporalFusion(windowSize = 5)
        listOf("184203", "184208", "184203", "184203", "184209").forEach { fusion.push(it) }
        val result = fusion.fuse()
        assertEquals("184203", result?.consensus)
        assertTrue(result!!.agreement > 0.6f)
    }

    @Test
    fun `isReady respects minimum sample threshold`() {
        val fusion = TemporalFusion(windowSize = 5)
        assertFalse(fusion.isReady())
        fusion.push("184203")
        fusion.push("184203")
        assertFalse(fusion.isReady())
        fusion.push("184203")
        assertTrue(fusion.isReady())
    }
}
