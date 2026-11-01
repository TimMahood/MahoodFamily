package com.mahood.family.core

import com.mahood.family.core.data.Hlc
import com.mahood.family.core.data.Op
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeTest {
    @Test
    fun clockIsMonotonicWhenWallClockStalls() {
        val clock = Hlc("aaa") { 1_000L }
        val a = clock.next()
        val b = clock.next()
        assertTrue(b > a)
    }

    @Test
    fun observingRemoteMovesClockForward() {
        val clock = Hlc("aaa") { 1_000L }
        clock.observe(Hlc.format(5_000L, 3, "bbb"))
        assertTrue(clock.next() > Hlc.format(5_000L, 3, "bbb"))
    }

    @Test
    fun newerWriteWinsWhicheverOrderArrives() {
        val older = Hlc.format(1_000L, 0, "phoneA")
        val newer = Hlc.format(2_000L, 0, "phoneB")
        assertTrue(Op.wins(newer, older))
        assertFalse(Op.wins(older, newer))
        assertTrue(Op.wins(older, null))
    }

    @Test
    fun sameMillisecondTieBreaksOnDevice() {
        val a = Hlc.format(1_000L, 0, "aaa")
        val b = Hlc.format(1_000L, 0, "bbb")
        assertTrue(Op.wins(b, a))
        assertFalse(Op.wins(a, b))
    }

    @Test
    fun valuesRoundTrip() {
        for (v in listOf<Any?>("milk", true, 3, null)) {
            val op = Op("items", "1", "name", v, Hlc.format(1, 0, "x"))
            assertEquals(v, Op.fromJson(op.toJson()).value)
        }
    }
}
