package com.example.splitwise.domain

enum class SplitType(val label: String, val help: String) {
    EQUAL("Equally", "Everyone selected pays the same."),
    EXACT("Exact", "Enter exactly what each person owes."),
    PERCENT("Percent", "Enter each person's percentage (must total 100)."),
    SHARES("Shares", "Split by shares, e.g. 2 nights vs 1 night."),
    ADJUST("Adjust", "Add or subtract an amount per person; the rest is split equally."),
    ITEMS("Items", "Assign each line item to people. Tax and tip are shared in proportion."),
}

data class ItemInput(val name: String, val priceMinor: Long, val assignedTo: Set<String>)

data class SplitInput(
    val type: SplitType,
    val amountMinor: Long,
    val currency: String,
    val baseCurrency: String,
    val rate: Double,
    val participants: List<String>,
    val values: Map<String, String>,
    val items: List<ItemInput>,
)

data class SplitResult(val baseAmount: Long, val shares: Map<String, Long>)

object Splits {

    /** Largest-remainder allocation: result always sums exactly to [total]. */
    fun allocate(total: Long, weights: Map<String, Double>): Map<String, Long> {
        require(weights.isNotEmpty()) { "Select at least one person" }
        require(total >= 0) { "Amount must be positive" }
        require(weights.values.all { it >= 0 }) { "Values can't be negative" }
        val sum = weights.values.sum()
        require(sum > 0) { "Values must add up to more than zero" }
        val raw = weights.mapValues { total * it.value / sum }
        val out = LinkedHashMap<String, Long>()
        raw.forEach { (k, v) -> out[k] = Math.floor(v).toLong() }
        var rem = total - out.values.sum()
        for (e in raw.entries.sortedByDescending { it.value - Math.floor(it.value) }) {
            if (rem <= 0) break
            out[e.key] = out.getValue(e.key) + 1
            rem--
        }
        if (rem < 0) { // floating point overshoot, take it back from the largest share
            val k = out.maxByOrNull { it.value }!!.key
            out[k] = out.getValue(k) + rem
        }
        return out
    }

    private fun num(text: String?, what: String, default: String? = null): Double {
        val t = (text ?: "").trim().replace(',', '.').ifBlank { default ?: "" }
        return t.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid $what")
    }

    fun compute(i: SplitInput): SplitResult {
        require(i.amountMinor > 0) { "Enter an amount greater than zero" }
        require(i.rate > 0) { "Enter a valid exchange rate" }
        val cur = i.currency
        val ones = i.participants.associateWith { 1.0 }
        val orig: Map<String, Long> = when (i.type) {
            SplitType.EQUAL -> allocate(i.amountMinor, ones)
            SplitType.SHARES -> allocate(i.amountMinor, i.participants.associateWith { num(i.values[it], "number of shares", "1") })
            SplitType.PERCENT -> {
                val p = i.participants.associateWith { num(i.values[it], "percentage", "0") }
                require(Math.abs(p.values.sum() - 100.0) < 0.01) { "Percentages must add up to 100 (now ${p.values.sum()})" }
                allocate(i.amountMinor, p)
            }
            SplitType.EXACT -> {
                require(i.participants.isNotEmpty()) { "Select at least one person" }
                val a = i.participants.associateWith {
                    Money.parse((i.values[it] ?: "").ifBlank { "0" }, cur) ?: throw IllegalArgumentException("Invalid amount")
                }
                require(a.values.sum() == i.amountMinor) {
                    "Amounts must add up to ${Money.format(i.amountMinor, cur)} (now ${Money.format(a.values.sum(), cur)})"
                }
                a
            }
            SplitType.ADJUST -> {
                require(i.participants.isNotEmpty()) { "Select at least one person" }
                val adj = i.participants.associateWith {
                    Money.parse((i.values[it] ?: "").ifBlank { "0" }, cur) ?: throw IllegalArgumentException("Invalid adjustment")
                }
                val rest = i.amountMinor - adj.values.sum()
                require(rest >= 0) { "Adjustments are larger than the total" }
                val eq = allocate(rest, ones)
                val r = i.participants.associateWith { adj.getValue(it) + eq.getValue(it) }
                require(r.values.all { it >= 0 }) { "An adjustment makes someone's share negative" }
                r
            }
            SplitType.ITEMS -> {
                require(i.items.isNotEmpty()) { "Add at least one item" }
                val sums = LinkedHashMap<String, Long>()
                for (item in i.items) {
                    require(item.assignedTo.isNotEmpty()) { "Assign '${item.name}' to at least one person" }
                    require(item.priceMinor >= 0) { "Item prices can't be negative" }
                    allocate(item.priceMinor, item.assignedTo.associateWith { 1.0 })
                        .forEach { (k, v) -> sums[k] = (sums[k] ?: 0L) + v }
                }
                require(i.items.sumOf { it.priceMinor } <= i.amountMinor) { "Items add up to more than the total" }
                require(sums.values.sum() > 0) { "Items must have a price" }
                allocate(i.amountMinor, sums.mapValues { it.value.toDouble() })
            }
        }
        val base = Money.convert(i.amountMinor, cur, i.baseCurrency, i.rate)
        require(base > 0) { "Converted amount is zero" }
        val shares = if (cur == i.baseCurrency) orig else allocate(base, orig.mapValues { it.value.toDouble() })
        return SplitResult(base, shares.filterValues { it != 0L })
    }
}
