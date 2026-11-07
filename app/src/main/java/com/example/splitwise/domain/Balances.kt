package com.example.splitwise.domain

import com.example.splitwise.data.ExpenseEntity

data class Transfer(val from: String, val to: String, val amount: Long)

object Balances {

    /** Positive = is owed money, negative = owes money. Settlements are ordinary expenses. */
    fun net(expenses: List<ExpenseEntity>): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        for (e in expenses) {
            if (e.deleted) continue
            out[e.paidById] = (out[e.paidById] ?: 0L) + e.baseAmount
            for ((id, owed) in Json.decodeLongMap(e.sharesJson)) out[id] = (out[id] ?: 0L) - owed
        }
        return out
    }

    /** Minimum-ish number of payments that settles everyone (greedy on net balances). */
    fun simplify(net: Map<String, Long>): List<Transfer> {
        val byAmount = compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first }
        val debtors = net.filter { it.value < 0 }.map { it.key to -it.value }.sortedWith(byAmount).toMutableList()
        val creditors = net.filter { it.value > 0 }.map { it.key to it.value }.sortedWith(byAmount).toMutableList()
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

    /** Who owes whom directly (no re-routing), netting each pair of people. */
    fun pairwise(expenses: List<ExpenseEntity>): List<Transfer> {
        val owed = HashMap<Pair<String, String>, Long>() // (debtor, creditor) -> amount
        for (e in expenses) {
            if (e.deleted) continue
            for ((m, s) in Json.decodeLongMap(e.sharesJson)) {
                if (m != e.paidById) owed.merge(m to e.paidById, s) { a, b -> a + b }
            }
        }
        val out = mutableListOf<Transfer>()
        val seen = HashSet<Pair<String, String>>()
        for ((k, v) in owed) {
            if (k in seen) continue
            val rev = k.second to k.first
            seen += k; seen += rev
            val d = v - (owed[rev] ?: 0L)
            if (d > 0) out += Transfer(k.first, k.second, d) else if (d < 0) out += Transfer(rev.first, rev.second, -d)
        }
        return out.sortedWith(compareBy({ it.from }, { it.to }))
    }
}
