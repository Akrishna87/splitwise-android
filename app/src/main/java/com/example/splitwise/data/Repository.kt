package com.example.splitwise.data

import androidx.room.withTransaction
import com.example.splitwise.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.util.UUID

/** Everything the UI needs in one immutable value; derived data is computed on the fly. */
data class AppSnapshot(
    val groups: List<GroupEntity>,
    val members: List<MemberEntity>,
    val expenses: List<ExpenseEntity>,
    val recurring: List<RecurringEntity>,
) {
    fun membersOf(gid: String) = members.filter { it.groupId == gid }
    fun expensesOf(gid: String) = expenses.filter { it.groupId == gid }
    fun meIn(gid: String) = members.firstOrNull { it.groupId == gid && it.isMe }
    fun net(gid: String) = Balances.net(expensesOf(gid))
    fun myNet(gid: String): Long = meIn(gid)?.let { net(gid)[it.id] } ?: 0L
    fun memberName(id: String) = members.firstOrNull { it.id == id }?.name ?: "?"
    fun group(id: String) = groups.firstOrNull { it.id == id }
    fun title(g: GroupEntity) =
        if (g.kind == "FRIEND") membersOf(g.id).firstOrNull { !it.isMe }?.name ?: g.name else g.name

    companion object { val EMPTY = AppSnapshot(emptyList(), emptyList(), emptyList(), emptyList()) }
}

data class ImportSummary(val groups: Int, val expenses: Int, val newGroupIds: List<String>)

class Repository(private val db: AppDb, val settings: Settings) {
    private val dao = db.dao()
    private fun now() = System.currentTimeMillis()
    private fun uid() = UUID.randomUUID().toString()

    val snapshot: Flow<AppSnapshot> =
        combine(dao.groups(), dao.members(), dao.expenses(), dao.recurring()) { g, m, e, r -> AppSnapshot(g, m, e, r) }

    fun comments(expenseId: String) = dao.comments(expenseId)

    // ---- groups & members
    suspend fun createGroup(name: String, currency: String, kind: String, others: List<String>): String {
        val gid = uid(); val t = now()
        dao.upsertGroup(GroupEntity(gid, name.trim(), currency, kind, t))
        dao.upsertMember(MemberEntity(uid(), gid, settings.userName.ifBlank { "Me" }, isMe = true, updatedAt = t))
        others.map { it.trim() }.filter { it.isNotEmpty() }.forEach { dao.upsertMember(MemberEntity(uid(), gid, it, updatedAt = t)) }
        return gid
    }

    suspend fun renameGroup(g: GroupEntity, name: String) = dao.upsertGroup(g.copy(name = name.trim(), updatedAt = now()))
    suspend fun deleteGroup(g: GroupEntity) = dao.upsertGroup(g.copy(deleted = true, updatedAt = now()))
    suspend fun addMember(groupId: String, name: String) {
        if (name.isNotBlank()) dao.upsertMember(MemberEntity(uid(), groupId, name.trim(), updatedAt = now()))
    }
    suspend fun updateMember(m: MemberEntity) = dao.upsertMember(m.copy(updatedAt = now()))
    suspend fun removeMember(m: MemberEntity) = dao.upsertMember(m.copy(deleted = true, updatedAt = now()))
    suspend fun setMe(groupId: String, memberId: String) = dao.setMe(groupId, memberId)

    suspend fun renameMe(name: String) {
        val t = now()
        dao.rawMembers().filter { it.isMe && !it.deleted && it.name != name }.forEach { dao.upsertMember(it.copy(name = name, updatedAt = t)) }
    }

    // ---- expenses
    suspend fun saveExpense(e: ExpenseEntity) = dao.upsertExpense(e.copy(updatedAt = now()))
    suspend fun deleteExpense(e: ExpenseEntity) = dao.upsertExpense(e.copy(deleted = true, updatedAt = now()))

    suspend fun addComment(groupId: String, expenseId: String, text: String) {
        if (text.isBlank()) return
        val t = now()
        dao.upsertComment(CommentEntity(uid(), groupId, expenseId, settings.userName.ifBlank { "Me" }, text.trim(), t, t))
    }
    suspend fun deleteComment(c: CommentEntity) = dao.upsertComment(c.copy(deleted = true, updatedAt = now()))

    suspend fun settle(g: GroupEntity, from: String, to: String, amount: Long) {
        val t = now()
        saveExpense(
            ExpenseEntity(
                id = uid(), groupId = g.id, description = "Payment", amount = amount, currency = g.currency, baseAmount = amount,
                paidById = from, category = "Payment", date = LocalDate.now().toEpochDay(), splitType = "EXACT",
                sharesJson = Json.encodeLongMap(mapOf(to to amount)), isSettlement = true, createdAt = t, updatedAt = t,
            )
        )
    }

    // ---- recurring
    suspend fun saveRecurring(r: RecurringEntity) = dao.upsertRecurring(r.copy(updatedAt = now()))
    suspend fun deleteRecurring(r: RecurringEntity) = dao.upsertRecurring(r.copy(deleted = true, updatedAt = now()))

    /** Creates every expense that is due. Ids are deterministic so running twice can never duplicate. */
    suspend fun generateDue(today: Long = LocalDate.now().toEpochDay()): List<ExpenseEntity> {
        val alive = dao.rawGroups().filter { !it.deleted }.map { it.id }.toSet()
        val created = mutableListOf<ExpenseEntity>()
        for (r in dao.rawRecurring()) {
            if (r.deleted || !r.active || r.groupId !in alive) continue
            val interval = runCatching { Interval.valueOf(r.interval) }.getOrNull() ?: continue
            if (interval == Interval.NONE) continue
            var next = r.nextDue
            var guard = 0
            while (next <= today && guard++ < 400) {
                val t = now()
                created += ExpenseEntity(
                    id = "rec-${r.id}-$next", groupId = r.groupId, description = r.description, amount = r.amount, currency = r.currency,
                    rate = r.rate, baseAmount = r.baseAmount, paidById = r.paidById, category = r.category, note = r.note, date = next,
                    splitType = r.splitType, sharesJson = r.sharesJson, inputsJson = r.inputsJson, itemsJson = r.itemsJson,
                    recurringId = r.id, createdAt = t, updatedAt = t,
                )
                next = Recurrence.next(next, interval)
            }
            if (next != r.nextDue) dao.upsertRecurring(r.copy(nextDue = next, updatedAt = now()))
        }
        if (created.isNotEmpty()) dao.upsertExpenses(created)
        return created
    }

    // ---- sharing / backup
    suspend fun export(groupIds: Set<String>?, includeMe: Boolean): String {
        var b = Bundle(dao.rawGroups(), dao.rawMembers(), dao.rawExpenses(), dao.rawComments(), dao.rawRecurring())
        if (groupIds != null) b = b.onlyGroups(groupIds)
        return Sync.write(b, includeMe)
    }

    suspend fun import(text: String): ImportSummary {
        val b = Sync.parse(text)
        return db.withTransaction {
            val groups = dao.rawGroups().associateBy { it.id }
            val members = dao.rawMembers().associateBy { it.id }
            val expenses = dao.rawExpenses().associateBy { it.id }
            val comments = dao.rawComments().associateBy { it.id }
            val recurring = dao.rawRecurring().associateBy { it.id }

            val g = Merge.newer(groups, b.groups, { it.id }, { it.updatedAt })
            val m = Merge.newer(members, b.members, { it.id }, { it.updatedAt })
                .map { x -> x.copy(isMe = members[x.id]?.isMe ?: x.isMe) }
            val e = Merge.newer(expenses, b.expenses, { it.id }, { it.updatedAt })
                .map { x -> x.copy(receiptPath = expenses[x.id]?.receiptPath ?: "") }
            val c = Merge.newer(comments, b.comments, { it.id }, { it.updatedAt })
            val r = Merge.newer(recurring, b.recurring, { it.id }, { it.updatedAt })
            dao.upsertGroups(g); dao.upsertMembers(m); dao.upsertExpenses(e); dao.upsertComments(c); dao.upsertRecurrings(r)

            val all = dao.rawMembers()
            val needIdentity = g.filter { it.id !in groups && !it.deleted }
                .filter { grp -> all.none { it.groupId == grp.id && it.isMe && !it.deleted } }
                .map { it.id }
            ImportSummary(g.size, e.count { !it.deleted }, needIdentity)
        }
    }
}
