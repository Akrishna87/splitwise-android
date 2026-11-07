package com.example.splitwise.domain

data class Transfer(val fromId: Long, val toId: Long, val amount: Long)

/** One recorded expense (or settlement): who paid and how much each member owes. */
data class ExpenseRecord(val paidById: Long, val amount: Long, val shares: Map<Long, Long>)

object Balances {

    /** Positive = is owed money, negative = owes money. */
    fun net(expenses: List<ExpenseRecord>): Map<Long, Long> {
        val out = mutableMapOf<Long, Long>()
        for (e in expenses) {
            out[e.paidById] = (out[e.paidById] ?: 0L) + e.amount
            for ((id, owed) in e.shares) out[id] = (out[id] ?: 0L) - owed
        }
        return out
    }

    /** Greedy debt simplification: minimises the number of payments needed to settle up. */
    fun simplify(net: Map<Long, Long>): List<Transfer> {
        val debtors = net.filter { it.value < 0 }.map { it.key to -it.value }
            .sortedByDescending { it.second }.toMutableList()
        val creditors = net.filter { it.value > 0 }.map { it.key to it.value }
            .sortedByDescending { it.second }.toMutableList()
        val result = mutableListOf<Transfer>()
        var i = 0
        var j = 0
        while (i < debtors.size && j < creditors.size) {
            val pay = minOf(debtors[i].second, creditors[j].second)
            result += Transfer(debtors[i].first, creditors[j].first, pay)
            debtors[i] = debtors[i].first to debtors[i].second - pay
            creditors[j] = creditors[j].first to creditors[j].second - pay
            if (debtors[i].second == 0L) i++
            if (creditors[j].second == 0L) j++
        }
        return result
    }
}

fun formatMoney(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val a = kotlin.math.abs(cents)
    return "$sign$${a / 100}.${(a % 100).toString().padStart(2, '0')}"
}

/** Parses "12.5" or "12.50" into cents; returns null when invalid. */
fun parseMoney(text: String): Long? {
    val v = text.trim().toBigDecimalOrNull() ?: return null
    if (v.scale() > 2 && v.stripTrailingZeros().scale() > 2) return null
    return v.movePointRight(2).toLong()
}
