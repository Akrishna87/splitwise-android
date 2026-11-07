package com.example.splitwise.domain

/** All money is in minor units (cents) to avoid floating point errors. */
object Splits {

    /** Split [total] equally; leftover cents go one each to the first members. */
    fun equal(total: Long, members: List<Long>): Map<Long, Long> {
        require(members.isNotEmpty()) { "Select at least one member" }
        val base = total / members.size
        val extra = (total % members.size).toInt()
        return members.mapIndexed { i, id -> id to base + if (i < extra) 1 else 0 }.toMap()
    }

    /** Split by percentages (should sum to 100). Remainder cents go to the first members. */
    fun percent(total: Long, percents: Map<Long, Double>): Map<Long, Long> {
        require(percents.isNotEmpty()) { "Select at least one member" }
        val sum = percents.values.sum()
        require(kotlin.math.abs(sum - 100.0) < 0.01) { "Percentages must add up to 100" }
        val shares = percents.mapValues { (_, p) -> Math.round(total * p / 100.0) }.toMutableMap()
        var diff = total - shares.values.sum()
        for (id in shares.keys) {
            if (diff == 0L) break
            val step = if (diff > 0) 1L else -1L
            shares[id] = shares.getValue(id) + step
            diff -= step
        }
        return shares
    }

    /** Exact amounts; must add up to [total]. */
    fun exact(total: Long, amounts: Map<Long, Long>): Map<Long, Long> {
        require(amounts.isNotEmpty()) { "Select at least one member" }
        require(amounts.values.sum() == total) { "Amounts must add up to the total" }
        return amounts
    }
}
