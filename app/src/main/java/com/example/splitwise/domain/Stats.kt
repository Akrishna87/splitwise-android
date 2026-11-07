package com.example.splitwise.domain

import com.example.splitwise.data.ExpenseEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

object Stats {
    private fun real(e: List<ExpenseEntity>) = e.filter { !it.isSettlement && !it.deleted }

    fun byCategory(expenses: List<ExpenseEntity>): List<Pair<String, Long>> =
        real(expenses).groupBy { it.category }.map { (k, v) -> k to v.sumOf { it.baseAmount } }.sortedByDescending { it.second }

    /** Last [months] months (oldest first) including empty months. */
    fun byMonth(expenses: List<ExpenseEntity>, todayEpochDay: Long, months: Int = 6): List<Pair<String, Long>> {
        val cur = YearMonth.from(LocalDate.ofEpochDay(todayEpochDay))
        val sums = real(expenses).groupBy { YearMonth.from(LocalDate.ofEpochDay(it.date)) }
            .mapValues { (_, v) -> v.sumOf { it.baseAmount } }
        return (months - 1 downTo 0).map { back ->
            val ym = cur.minusMonths(back.toLong())
            ym.month.getDisplayName(TextStyle.SHORT, Locale.US) to (sums[ym] ?: 0L)
        }
    }

    fun total(expenses: List<ExpenseEntity>) = real(expenses).sumOf { it.baseAmount }

    fun myShare(expenses: List<ExpenseEntity>, meId: String) =
        real(expenses).sumOf { Json.decodeLongMap(it.sharesJson)[meId] ?: 0L }

    fun paidBy(expenses: List<ExpenseEntity>): List<Pair<String, Long>> =
        real(expenses).groupBy { it.paidById }.map { (k, v) -> k to v.sumOf { it.baseAmount } }.sortedByDescending { it.second }
}
