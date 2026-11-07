package com.example.splitwise.domain

import com.example.splitwise.data.*
import org.json.JSONArray
import org.json.JSONObject

data class Bundle(
    val groups: List<GroupEntity>,
    val members: List<MemberEntity>,
    val expenses: List<ExpenseEntity>,
    val comments: List<CommentEntity>,
    val recurring: List<RecurringEntity>,
) {
    fun onlyGroups(ids: Set<String>) = Bundle(
        groups.filter { it.id in ids }, members.filter { it.groupId in ids }, expenses.filter { it.groupId in ids },
        comments.filter { it.groupId in ids }, recurring.filter { it.groupId in ids },
    )
}

object Merge {
    /** Rows from [incoming] that are new, or newer than what we already have (last writer wins). */
    fun <T> newer(existing: Map<String, T>, incoming: List<T>, id: (T) -> String, updated: (T) -> Long): List<T> =
        incoming.filter { val e = existing[id(it)]; e == null || updated(it) > updated(e) }
}

/** JSON file format used to share groups between phones and for full backups. */
object Sync {
    private const val APP = "splitwise-android"

    fun write(b: Bundle, includeMe: Boolean): String {
        val o = JSONObject().put("app", APP).put("version", 1)
        o.put("groups", arr(b.groups) { g -> JSONObject().put("id", g.id).put("name", g.name).put("currency", g.currency).put("kind", g.kind).put("updatedAt", g.updatedAt).put("deleted", g.deleted) })
        o.put("members", arr(b.members) { m ->
            JSONObject().put("id", m.id).put("groupId", m.groupId).put("name", m.name).put("upiId", m.upiId)
                .put("paypalUser", m.paypalUser).put("venmoUser", m.venmoUser).put("updatedAt", m.updatedAt).put("deleted", m.deleted)
                .also { if (includeMe) it.put("isMe", m.isMe) }
        })
        o.put("expenses", arr(b.expenses) { e ->
            JSONObject().put("id", e.id).put("groupId", e.groupId).put("description", e.description).put("amount", e.amount)
                .put("currency", e.currency).put("rate", e.rate).put("baseAmount", e.baseAmount).put("paidById", e.paidById)
                .put("category", e.category).put("note", e.note).put("date", e.date).put("splitType", e.splitType)
                .put("sharesJson", e.sharesJson).put("inputsJson", e.inputsJson).put("itemsJson", e.itemsJson)
                .put("isSettlement", e.isSettlement).put("recurringId", e.recurringId).put("createdAt", e.createdAt)
                .put("updatedAt", e.updatedAt).put("deleted", e.deleted)
        })
        o.put("comments", arr(b.comments) { c ->
            JSONObject().put("id", c.id).put("groupId", c.groupId).put("expenseId", c.expenseId).put("author", c.author)
                .put("text", c.text).put("createdAt", c.createdAt).put("updatedAt", c.updatedAt).put("deleted", c.deleted)
        })
        o.put("recurring", arr(b.recurring) { r ->
            JSONObject().put("id", r.id).put("groupId", r.groupId).put("description", r.description).put("amount", r.amount)
                .put("currency", r.currency).put("rate", r.rate).put("baseAmount", r.baseAmount).put("paidById", r.paidById)
                .put("category", r.category).put("note", r.note).put("splitType", r.splitType).put("sharesJson", r.sharesJson)
                .put("inputsJson", r.inputsJson).put("itemsJson", r.itemsJson).put("interval", r.interval)
                .put("nextDue", r.nextDue).put("active", r.active).put("updatedAt", r.updatedAt).put("deleted", r.deleted)
        })
        return o.toString()
    }

    fun parse(text: String): Bundle {
        val o = try { JSONObject(text) } catch (e: Exception) { throw IllegalArgumentException("This isn't a SplitWise file") }
        require(o.optString("app") == APP) { "This isn't a SplitWise file" }
        fun <T> list(key: String, f: (JSONObject) -> T): List<T> {
            val a = o.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).map { f(a.getJSONObject(it)) }
        }
        return try {
            Bundle(
                groups = list("groups") { GroupEntity(it.getString("id"), it.getString("name"), it.getString("currency"), it.optString("kind", "GROUP"), it.getLong("updatedAt"), it.optBoolean("deleted")) },
                members = list("members") {
                    MemberEntity(it.getString("id"), it.getString("groupId"), it.getString("name"), it.optBoolean("isMe", false),
                        it.optString("upiId"), it.optString("paypalUser"), it.optString("venmoUser"), it.getLong("updatedAt"), it.optBoolean("deleted"))
                },
                expenses = list("expenses") {
                    ExpenseEntity(
                        id = it.getString("id"), groupId = it.getString("groupId"), description = it.getString("description"),
                        amount = it.getLong("amount"), currency = it.getString("currency"), rate = it.optDouble("rate", 1.0),
                        baseAmount = it.getLong("baseAmount"), paidById = it.getString("paidById"), category = it.optString("category", "General"),
                        note = it.optString("note"), date = it.getLong("date"), splitType = it.optString("splitType", "EQUAL"),
                        sharesJson = it.getString("sharesJson"), inputsJson = it.optString("inputsJson", "{}"), itemsJson = it.optString("itemsJson", "[]"),
                        isSettlement = it.optBoolean("isSettlement"), recurringId = it.optString("recurringId"),
                        createdAt = it.getLong("createdAt"), updatedAt = it.getLong("updatedAt"), deleted = it.optBoolean("deleted"),
                    )
                },
                comments = list("comments") {
                    CommentEntity(it.getString("id"), it.getString("groupId"), it.getString("expenseId"), it.optString("author"),
                        it.getString("text"), it.getLong("createdAt"), it.getLong("updatedAt"), it.optBoolean("deleted"))
                },
                recurring = list("recurring") {
                    RecurringEntity(
                        id = it.getString("id"), groupId = it.getString("groupId"), description = it.getString("description"),
                        amount = it.getLong("amount"), currency = it.getString("currency"), rate = it.optDouble("rate", 1.0),
                        baseAmount = it.getLong("baseAmount"), paidById = it.getString("paidById"), category = it.optString("category", "General"),
                        note = it.optString("note"), splitType = it.optString("splitType", "EQUAL"), sharesJson = it.getString("sharesJson"),
                        inputsJson = it.optString("inputsJson", "{}"), itemsJson = it.optString("itemsJson", "[]"), interval = it.getString("interval"),
                        nextDue = it.getLong("nextDue"), active = it.optBoolean("active", true), updatedAt = it.getLong("updatedAt"), deleted = it.optBoolean("deleted"),
                    )
                },
            )
        } catch (e: org.json.JSONException) {
            throw IllegalArgumentException("This SplitWise file is damaged or from a newer version")
        }
    }

    private fun <T> arr(items: List<T>, f: (T) -> JSONObject): JSONArray {
        val a = JSONArray(); items.forEach { a.put(f(it)) }; return a
    }
}
