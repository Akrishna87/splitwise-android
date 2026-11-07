package com.example.splitwise

import com.example.splitwise.data.*
import com.example.splitwise.domain.*
import org.junit.Assert.*
import org.junit.Test

private fun expense(
    id: String, paidBy: String, amount: Long, shares: Map<String, Long>, date: Long = 20000, cat: String = "General", settlement: Boolean = false,
) = ExpenseEntity(
    id = id, groupId = "g", description = id, amount = amount, currency = "USD", baseAmount = amount, paidById = paidBy, category = cat,
    date = date, sharesJson = Json.encodeLongMap(shares), isSettlement = settlement, createdAt = 1, updatedAt = 1,
)

private fun input(type: SplitType, amount: Long, ids: List<String>, values: Map<String, String> = emptyMap(), items: List<ItemInput> = emptyList(),
                  cur: String = "USD", base: String = "USD", rate: Double = 1.0) =
    SplitInput(type, amount, cur, base, rate, ids, values, items)

class MoneyTest {
    @Test fun formatAndParse() {
        assertEquals("$12.05", Money.format(1205, "USD"))
        assertEquals("-$0.50", Money.format(-50, "USD"))
        assertEquals("₹1,234.50", Money.format(123450, "INR"))
        assertEquals("¥1,500", Money.format(1500, "JPY"))
        assertEquals(1250L, Money.parse("12.5", "USD"))
        assertEquals(1250L, Money.parse("12,5", "USD"))
        assertEquals(123456L, Money.parse("1,234.56", "USD"))
        assertEquals(1500L, Money.parse("1500", "JPY"))
        assertNull(Money.parse("1.234", "USD"))
        assertNull(Money.parse("abc", "USD"))
        assertEquals("12.50", Money.plain(1250, "USD"))
    }

    @Test fun convert() {
        assertEquals(8300L, Money.convert(100, "USD", "INR", 83.0) / 1) // 1 USD -> 83.00 INR minor
        assertEquals(10000L, Money.convert(10000, "USD", "USD", 5.0))
        assertEquals(15000L, Money.convert(100, "USD", "JPY", 150.0)) // 1.00 USD -> 150 JPY
    }
}

class SplitsTest {
    @Test fun allocateAlwaysSumsToTotal() {
        val r = Splits.allocate(100, mapOf("a" to 1.0, "b" to 1.0, "c" to 1.0))
        assertEquals(100L, r.values.sum()); assertEquals(34L, r["a"]); assertEquals(33L, r["b"])
        val r2 = Splits.allocate(1001, mapOf("a" to 33.33, "b" to 33.33, "c" to 33.34))
        assertEquals(1001L, r2.values.sum())
    }

    @Test fun equal() {
        val s = Splits.compute(input(SplitType.EQUAL, 1000, listOf("a", "b"))).shares
        assertEquals(mapOf("a" to 500L, "b" to 500L), s)
    }

    @Test fun shares() {
        val s = Splits.compute(input(SplitType.SHARES, 900, listOf("a", "b"), mapOf("a" to "2", "b" to "1"))).shares
        assertEquals(600L, s["a"]); assertEquals(300L, s["b"])
    }

    @Test fun percent() {
        val s = Splits.compute(input(SplitType.PERCENT, 1000, listOf("a", "b"), mapOf("a" to "70", "b" to "30"))).shares
        assertEquals(700L, s["a"]); assertEquals(300L, s["b"])
    }

    @Test(expected = IllegalArgumentException::class) fun percentMustTotal100() {
        Splits.compute(input(SplitType.PERCENT, 1000, listOf("a", "b"), mapOf("a" to "70", "b" to "20")))
    }

    @Test fun exact() {
        val s = Splits.compute(input(SplitType.EXACT, 1000, listOf("a", "b"), mapOf("a" to "3.25", "b" to "6.75"))).shares
        assertEquals(325L, s["a"]); assertEquals(675L, s["b"])
    }

    @Test(expected = IllegalArgumentException::class) fun exactMustMatchTotal() {
        Splits.compute(input(SplitType.EXACT, 1000, listOf("a", "b"), mapOf("a" to "3", "b" to "6")))
    }

    @Test fun adjust() {
        // total 30: a gets +5, b gets -5 -> rest 30 split equally (15 each) -> a 20, b 10
        val s = Splits.compute(input(SplitType.ADJUST, 3000, listOf("a", "b"), mapOf("a" to "5", "b" to "-5"))).shares
        assertEquals(2000L, s["a"]); assertEquals(1000L, s["b"])
    }

    @Test fun items() {
        // pizza 10 shared by a,b ; beer 6 for a ; total 20 (4 of tax shared proportionally)
        val items = listOf(ItemInput("pizza", 1000, setOf("a", "b")), ItemInput("beer", 600, setOf("a")))
        val r = Splits.compute(input(SplitType.ITEMS, 2000, emptyList(), items = items))
        assertEquals(2000L, r.shares.values.sum())
        // item sums a=1100 b=500 -> weights 11:5 of 2000
        assertEquals(1375L, r.shares["a"]); assertEquals(625L, r.shares["b"])
    }

    @Test(expected = IllegalArgumentException::class) fun itemMustBeAssigned() {
        Splits.compute(input(SplitType.ITEMS, 1000, emptyList(), items = listOf(ItemInput("x", 1000, emptySet()))))
    }

    @Test fun multiCurrencyConvertsShares() {
        // 100.00 EUR at 1.10 -> 110.00 USD split equally
        val r = Splits.compute(input(SplitType.EQUAL, 10000, listOf("a", "b", "c"), cur = "EUR", base = "USD", rate = 1.10))
        assertEquals(11000L, r.baseAmount)
        assertEquals(11000L, r.shares.values.sum())
    }
}

class BalancesTest {
    private val list = listOf(
        expense("e1", "a", 9000, mapOf("a" to 3000L, "b" to 3000L, "c" to 3000L)),
        expense("e2", "b", 3000, mapOf("b" to 1500L, "c" to 1500L)),
    )

    @Test fun netSumsToZero() {
        val n = Balances.net(list)
        assertEquals(0L, n.values.sum())
        assertEquals(6000L, n["a"]); assertEquals(-4500L, n["c"])
    }

    @Test fun simplify() {
        val t = Balances.simplify(Balances.net(list))
        assertEquals(setOf(Transfer("c", "a", 4500), Transfer("b", "a", 1500)), t.toSet())
    }

    @Test fun pairwiseNetsEachPair() {
        val t = Balances.pairwise(list)
        assertEquals(setOf(Transfer("b", "a", 3000), Transfer("c", "a", 3000), Transfer("c", "b", 1500)), t.toSet())
    }

    @Test fun settlementCancelsDebt() {
        val all = list + expense("p", "c", 4500, mapOf("a" to 4500L), settlement = true)
        val n = Balances.net(all)
        assertEquals(0L, n["c"]); assertEquals(1500L, n["a"])
    }

    @Test fun deletedExpensesIgnored() {
        assertTrue(Balances.net(listOf(expense("x", "a", 100, mapOf("b" to 100L)).copy(deleted = true))).isEmpty())
    }
}

class StatsTest {
    @Test fun categoriesMonthsAndShare() {
        val day = java.time.LocalDate.of(2026, 11, 7).toEpochDay()
        val oct = java.time.LocalDate.of(2026, 10, 15).toEpochDay()
        val e = listOf(
            expense("a", "x", 1000, mapOf("x" to 500L, "y" to 500L), day, "Food & drink"),
            expense("b", "y", 3000, mapOf("x" to 1500L, "y" to 1500L), oct, "Rent"),
            expense("p", "x", 500, mapOf("y" to 500L), day, "Payment", settlement = true),
        )
        assertEquals(listOf("Rent" to 3000L, "Food & drink" to 1000L), Stats.byCategory(e))
        val months = Stats.byMonth(e, day, 3)
        assertEquals(listOf("Sep" to 0L, "Oct" to 3000L, "Nov" to 1000L), months)
        assertEquals(2000L, Stats.myShare(e, "x"))
        assertEquals(4000L, Stats.total(e))
    }
}

class RecurrenceTest {
    @Test fun nextDates() {
        val jan31 = java.time.LocalDate.of(2026, 1, 31).toEpochDay()
        assertEquals(java.time.LocalDate.of(2026, 2, 28).toEpochDay(), Recurrence.next(jan31, Interval.MONTHLY))
        assertEquals(jan31 + 7, Recurrence.next(jan31, Interval.WEEKLY))
        assertEquals(jan31 + 1, Recurrence.next(jan31, Interval.DAILY))
        assertEquals(java.time.LocalDate.of(2027, 1, 31).toEpochDay(), Recurrence.next(jan31, Interval.YEARLY))
    }
}

class ReceiptParserTest {
    @Test fun parsesItemsAndTotal() {
        val r = ReceiptParser.parse(
            listOf("CAFE LUNA", "Burger  12.50", "Fries", "4.00", "2 Coke 6,00", "Subtotal 22.50", "Tax 1.80", "TOTAL 24.30", "Thank you")
        )
        assertEquals(listOf("Burger" to 1250L, "Fries" to 400L, "2 Coke" to 600L), r.items)
        assertEquals(2430L, r.total)
    }
}

class SyncTest {
    private val g = GroupEntity("g1", "Trip", "USD", "GROUP", 10)
    private val m = MemberEntity("m1", "g1", "Asha", isMe = true, upiId = "a@upi", updatedAt = 10)
    private val e = expense("e1", "m1", 1000, mapOf("m1" to 1000L)).copy(groupId = "g1", itemsJson = Json.encodeItems(listOf(ItemInput("x", 5, setOf("m1")))))
    private val c = CommentEntity("c1", "g1", "e1", "Asha", "hi", 1, 1)
    private val r = RecurringEntity("r1", "g1", "Rent", 100, "USD", baseAmount = 100, paidById = "m1", sharesJson = "{}", interval = "MONTHLY", nextDue = 5, updatedAt = 1)
    private val bundle = Bundle(listOf(g), listOf(m), listOf(e), listOf(c), listOf(r))

    @Test fun roundTrip() {
        val back = Sync.parse(Sync.write(bundle, includeMe = true))
        assertEquals(bundle, back)
    }

    @Test fun sharingOmitsWhoIAm() {
        val back = Sync.parse(Sync.write(bundle, includeMe = false))
        assertFalse(back.members.single().isMe)
        assertEquals("a@upi", back.members.single().upiId)
    }

    @Test fun onlyGroupsFilters() {
        val other = bundle.copy(groups = bundle.groups + g.copy(id = "g2"), expenses = bundle.expenses + e.copy(id = "e2", groupId = "g2"))
        assertEquals(1, other.onlyGroups(setOf("g1")).expenses.size)
    }

    @Test fun mergeLastWriterWins() {
        val existing = mapOf("e1" to e.copy(updatedAt = 50))
        val older = e.copy(updatedAt = 40); val newer = e.copy(updatedAt = 60); val brandNew = e.copy(id = "e9", updatedAt = 1)
        val out = Merge.newer(existing, listOf(older, newer, brandNew), { it.id }, { it.updatedAt })
        assertEquals(listOf(newer, brandNew), out)
    }

    @Test fun deletionsPropagate() {
        val existing = mapOf("e1" to e.copy(updatedAt = 50))
        val tomb = e.copy(updatedAt = 60, deleted = true)
        assertTrue(Merge.newer(existing, listOf(tomb), { it.id }, { it.updatedAt }).single().deleted)
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsForeignFiles() { Sync.parse("{\"hello\": 1}") }
    @Test(expected = IllegalArgumentException::class) fun rejectsGarbage() { Sync.parse("not json") }
}

class CsvAndLinksTest {
    @Test fun csvHasMembersAndEscapes() {
        val g = GroupEntity("g", "Trip", "USD", updatedAt = 1)
        val ms = listOf(MemberEntity("a", "g", "Asha", updatedAt = 1), MemberEntity("b", "g", "Bo", updatedAt = 1))
        val csv = Csv.group(g, ms, listOf(expense("Dinner, \"nice\"", "a", 1000, mapOf("a" to 500L, "b" to 500L))))
        val lines = csv.trim().lines()
        assertTrue(lines[0].endsWith("Share: Asha,Share: Bo"))
        assertTrue(lines[1].contains("\"Dinner, \"\"nice\"\"\""))
        assertTrue(lines[1].endsWith("5.00,5.00"))
    }

    @Test fun payLinks() {
        val m = MemberEntity("a", "g", "Asha K", upiId = "asha@upi", paypalUser = "asha", venmoUser = "@asha", updatedAt = 1)
        val inr = PaymentLinks.forMember(m, 12550, "INR", "Settle up")
        assertTrue(inr.any { it.url.startsWith("upi://pay?pa=asha%40upi") && it.url.contains("am=125.50") && it.url.contains("cu=INR") })
        assertTrue(inr.any { it.url == "https://paypal.me/asha/125.50INR" })
        assertFalse(inr.any { it.label.contains("Venmo") })
        val usd = PaymentLinks.forMember(m, 500, "USD", "x")
        assertTrue(usd.any { it.url.startsWith("https://venmo.com/asha?") })
        assertFalse(usd.any { it.label.contains("UPI") })
    }
}
