package com.example.splitwise.domain

import com.example.splitwise.data.ExpenseEntity
import com.example.splitwise.data.GroupEntity
import com.example.splitwise.data.MemberEntity
import java.time.LocalDate

object Csv {
    private fun esc(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    fun group(group: GroupEntity, members: List<MemberEntity>, expenses: List<ExpenseEntity>): String {
        val cur = group.currency
        val name = { id: String -> members.firstOrNull { it.id == id }?.name ?: "?" }
        val sb = StringBuilder()
        val header = listOf("Date", "Description", "Type", "Category", "Currency", "Amount", "Amount ($cur)", "Paid by", "Note") +
            members.map { "Share: ${it.name}" }
        sb.append(header.joinToString(",") { esc(it) }).append('\n')
        for (e in expenses.filter { !it.deleted }.sortedBy { it.date }) {
            val shares = Json.decodeLongMap(e.sharesJson)
            val row = listOf(
                LocalDate.ofEpochDay(e.date).toString(), e.description, if (e.isSettlement) "Payment" else "Expense",
                e.category, e.currency, Money.plain(e.amount, e.currency), Money.plain(e.baseAmount, cur), name(e.paidById), e.note,
            ) + members.map { Money.plain(shares[it.id] ?: 0L, cur) }
            sb.append(row.joinToString(",") { esc(it) }).append('\n')
        }
        return sb.toString()
    }
}
