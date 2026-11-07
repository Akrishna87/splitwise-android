package com.example.splitwise.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DomainTest {
    @Test fun equalSplitDistributesRemainder() {
        val s = Splits.equal(100, listOf(1, 2, 3))
        assertEquals(100L, s.values.sum())
        assertEquals(34L, s[1]); assertEquals(33L, s[2]); assertEquals(33L, s[3])
    }

    @Test fun percentSplitSumsToTotal() {
        val s = Splits.percent(1001, mapOf(1L to 33.33, 2L to 33.33, 3L to 33.34))
        assertEquals(1001L, s.values.sum())
    }

    @Test(expected = IllegalArgumentException::class)
    fun exactMustMatchTotal() { Splits.exact(100, mapOf(1L to 50L, 2L to 40L)) }

    @Test fun netAndSimplify() {
        // A pays 90 for A,B,C; B pays 30 for B,C
        val net = Balances.net(listOf(
            ExpenseRecord(1, 9000, mapOf(1L to 3000L, 2L to 3000L, 3L to 3000L)),
            ExpenseRecord(2, 3000, mapOf(2L to 1500L, 3L to 1500L)),
        ))
        assertEquals(0L, net.values.sum())
        val t = Balances.simplify(net)
        assertEquals(2, t.size)
        assertEquals(setOf(Transfer(3, 1, 4500), Transfer(2, 1, 1500)), t.toSet())
    }

    @Test fun money() {
        assertEquals("$12.05", formatMoney(1205))
        assertEquals(1250L, parseMoney("12.5"))
        assertEquals(null, parseMoney("abc"))
    }
}
