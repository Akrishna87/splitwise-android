package com.example.splitwise.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitwise.data.*
import com.example.splitwise.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GroupState(
    val group: GroupEntity?,
    val members: List<MemberEntity>,
    val expenses: List<ExpenseEntity>,
    val net: Map<Long, Long>,
    val transfers: List<Transfer>,
) { fun name(id: Long) = members.firstOrNull { it.id == id }?.name ?: "?" }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()

    val groups: StateFlow<List<GroupEntity>> =
        dao.groups().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun groupState(id: Long): Flow<GroupState> = combine(
        dao.group(id), dao.members(id), dao.expenses(id), dao.shares(id)
    ) { g, m, e, s ->
        val byExpense = s.groupBy { it.expenseId }
        val records = e.map { ex -> ExpenseRecord(ex.paidById, ex.amount, byExpense[ex.id].orEmpty().associate { it.memberId to it.owed }) }
        val net = Balances.net(records)
        GroupState(g, m, e, net, Balances.simplify(net))
    }

    fun addGroup(name: String, memberNames: List<String>) = viewModelScope.launch {
        val gid = dao.insert(GroupEntity(name = name.trim()))
        memberNames.map { it.trim() }.filter { it.isNotEmpty() }
            .forEach { dao.insert(MemberEntity(groupId = gid, name = it)) }
    }

    fun addMember(groupId: Long, name: String) = viewModelScope.launch {
        if (name.isNotBlank()) dao.insert(MemberEntity(groupId = groupId, name = name.trim()))
    }

    fun deleteGroup(id: Long) = viewModelScope.launch { dao.deleteGroup(id) }
    fun deleteExpense(id: Long) = viewModelScope.launch { dao.deleteExpense(id) }

    fun addExpense(groupId: Long, description: String, amount: Long, paidBy: Long, owed: Map<Long, Long>) =
        viewModelScope.launch {
            dao.addExpense(ExpenseEntity(groupId = groupId, description = description.trim(), amount = amount, paidById = paidBy), owed)
        }

    /** Recording a payment is an expense where the payer "pays" the full amount on behalf of the receiver. */
    fun settle(groupId: Long, t: Transfer) = viewModelScope.launch {
        dao.addExpense(
            ExpenseEntity(groupId = groupId, description = "Settle up", amount = t.amount, paidById = t.fromId, isSettlement = true),
            mapOf(t.toId to t.amount),
        )
    }
}
