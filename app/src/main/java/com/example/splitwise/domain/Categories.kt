package com.example.splitwise.domain

object Categories {
    val all = listOf(
        "General" to "🧾", "Food & drink" to "🍽️", "Groceries" to "🛒", "Rent" to "🏠",
        "Utilities" to "💡", "Transport" to "🚕", "Travel" to "✈️", "Entertainment" to "🎬",
        "Shopping" to "🛍️", "Health" to "💊", "Gifts" to "🎁", "Other" to "📦",
    )
    val names = all.map { it.first }
    fun emoji(name: String) = if (name == "Payment") "💸" else all.firstOrNull { it.first == name }?.second ?: "🧾"
}
