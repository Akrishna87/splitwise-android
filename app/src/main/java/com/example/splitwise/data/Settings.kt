package com.example.splitwise.data

import android.content.Context
import com.example.splitwise.domain.Money
import java.util.Currency
import java.util.Locale

class Settings(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var userName: String
        get() = p.getString("name", "Me") ?: "Me"
        set(v) = p.edit().putString("name", v).apply()

    var defaultCurrency: String
        get() = p.getString("currency", null) ?: localCurrency()
        set(v) = p.edit().putString("currency", v).apply()

    var reminders: Boolean
        get() = p.getBoolean("reminders", false)
        set(v) = p.edit().putBoolean("reminders", v).apply()

    fun cachedRate(from: String, to: String): Double? =
        p.getString("rate_${from}_$to", null)?.toDoubleOrNull()

    fun cacheRate(from: String, to: String, r: Double) =
        p.edit().putString("rate_${from}_$to", r.toString()).apply()

    private fun localCurrency(): String = try {
        Currency.getInstance(Locale.getDefault()).currencyCode.takeIf { it in Money.currencies } ?: "USD"
    } catch (e: Exception) { "USD" }
}
