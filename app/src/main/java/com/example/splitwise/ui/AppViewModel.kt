package com.example.splitwise.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitwise.data.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository(AppDb.get(app), Settings(app))
    val snap: StateFlow<AppSnapshot> = repo.snapshot.stateIn(viewModelScope, SharingStarted.Eagerly, AppSnapshot.EMPTY)

    var userName by mutableStateOf(repo.settings.userName); private set
    var defaultCurrency by mutableStateOf(repo.settings.defaultCurrency); private set
    var reminders by mutableStateOf(repo.settings.reminders); private set

    /** One-shot info shown by the home screen (import results, errors). */
    var message by mutableStateOf<String?>(null)
    /** Group whose "who are you?" question is pending after an import. */
    var identityPrompt by mutableStateOf<String?>(null)

    init { viewModelScope.launch { repo.generateDue() } }

    fun saveProfile(name: String, currency: String) {
        repo.settings.userName = name.trim().ifBlank { "Me" }
        repo.settings.defaultCurrency = currency
        userName = repo.settings.userName
        defaultCurrency = currency
        viewModelScope.launch { repo.renameMe(userName) }
    }

    fun updateReminders(on: Boolean) { repo.settings.reminders = on; reminders = on }

    fun createGroup(name: String, currency: String, kind: String, others: List<String>, onCreated: (String) -> Unit = {}) =
        viewModelScope.launch { onCreated(repo.createGroup(name, currency, kind, others)) }

    fun renameGroup(g: GroupEntity, name: String) = viewModelScope.launch { repo.renameGroup(g, name) }
    fun deleteGroup(g: GroupEntity) = viewModelScope.launch { repo.deleteGroup(g) }
    fun addMember(groupId: String, name: String) = viewModelScope.launch { repo.addMember(groupId, name) }
    fun updateMember(m: MemberEntity) = viewModelScope.launch { repo.updateMember(m) }
    fun removeMember(m: MemberEntity) = viewModelScope.launch { repo.removeMember(m) }
    fun setMe(groupId: String, memberId: String) = viewModelScope.launch { repo.setMe(groupId, memberId) }

    fun saveExpense(e: ExpenseEntity) = viewModelScope.launch { repo.saveExpense(e) }
    fun deleteExpense(e: ExpenseEntity) = viewModelScope.launch { repo.deleteExpense(e) }
    fun comments(expenseId: String) = repo.comments(expenseId)
    fun addComment(groupId: String, expenseId: String, text: String) = viewModelScope.launch { repo.addComment(groupId, expenseId, text) }
    fun deleteComment(c: CommentEntity) = viewModelScope.launch { repo.deleteComment(c) }
    fun settle(g: GroupEntity, from: String, to: String, amount: Long) = viewModelScope.launch { repo.settle(g, from, to, amount) }

    fun saveRecurring(r: RecurringEntity) = viewModelScope.launch { repo.saveRecurring(r) }
    fun deleteRecurring(r: RecurringEntity) = viewModelScope.launch { repo.deleteRecurring(r) }

    /** Live rate, falling back to the last rate we saw when offline. */
    suspend fun fetchRate(from: String, to: String): Double? {
        val live = Rates.fetch(from, to)
        if (live != null) { repo.settings.cacheRate(from, to, live); return live }
        return repo.settings.cachedRate(from, to)
    }

    suspend fun exportGroup(groupId: String) = repo.export(setOf(groupId), includeMe = false)
    suspend fun exportBackup() = repo.export(null, includeMe = true)

    suspend fun importText(text: String) {
        try {
            val s = repo.import(text)
            message = "Imported ${s.groups} group(s) / updated data with ${s.expenses} expense(s)."
            identityPrompt = s.newGroupIds.firstOrNull()
        } catch (e: IllegalArgumentException) {
            message = e.message ?: "Couldn't import that file"
        } catch (e: Exception) {
            message = "Couldn't import that file"
        }
    }
}
