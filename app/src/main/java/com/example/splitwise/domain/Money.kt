package com.example.splitwise.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** All amounts are stored as Long minor units (cents) so there is never floating point drift. */
object Money {
    /** Currencies with live rates available from the ECB reference feed. */
    val currencies = listOf(
        "USD", "EUR", "GBP", "INR", "JPY", "AUD", "CAD", "CHF", "CNY", "SGD", "HKD", "NZD",
        "SEK", "NOK", "DKK", "PLN", "CZK", "HUF", "RON", "BGN", "TRY", "ZAR", "BRL", "MXN",
        "IDR", "MYR", "PHP", "THB", "KRW", "ILS", "ISK",
    )
    private val zeroDecimal = setOf("JPY", "KRW", "ISK")
    private val symbols = mapOf(
        "USD" to "$", "EUR" to "€", "GBP" to "£", "INR" to "₹", "JPY" to "¥", "CNY" to "CN¥",
        "AUD" to "A$", "CAD" to "C$", "NZD" to "NZ$", "HKD" to "HK$", "SGD" to "S$", "KRW" to "₩",
        "ILS" to "₪", "TRY" to "₺", "BRL" to "R$", "MXN" to "MX$", "PHP" to "₱", "THB" to "฿",
    )

    fun digits(code: String) = if (code in zeroDecimal) 0 else 2
    fun symbol(code: String) = symbols[code] ?: "$code "
    private fun pow10(d: Int): Long = if (d == 0) 1L else 100L

    fun format(minor: Long, code: String): String {
        val d = digits(code)
        val abs = Math.abs(minor)
        val whole = abs / pow10(d)
        val frac = abs % pow10(d)
        val wholeStr = String.format(Locale.US, "%,d", whole)
        val body = if (d == 0) wholeStr else wholeStr + "." + frac.toString().padStart(d, '0')
        return (if (minor < 0) "-" else "") + symbol(code) + body
    }

    /** Plain editable text such as "12.50" (no symbol or grouping). */
    fun plain(minor: Long, code: String): String {
        val d = digits(code)
        val abs = Math.abs(minor)
        val body = if (d == 0) abs.toString() else (abs / 100).toString() + "." + (abs % 100).toString().padStart(2, '0')
        return (if (minor < 0) "-" else "") + body
    }

    /** Parses user text into minor units; null when invalid or too many decimals. */
    fun parse(text: String, code: String): Long? {
        var t = text.trim().replace(" ", "")
        if (t.isEmpty()) return null
        t = if (',' in t && '.' !in t) t.replace(',', '.') else t.replace(",", "")
        val v = t.toBigDecimalOrNull() ?: return null
        val d = digits(code)
        if (v.stripTrailingZeros().scale() > d) return null
        return try { v.movePointRight(d).longValueExact() } catch (e: ArithmeticException) { null }
    }

    /** [rate] = how many [to] units one [from] unit is worth. */
    fun convert(minor: Long, from: String, to: String, rate: Double): Long {
        if (from == to) return minor
        return BigDecimal(minor).movePointLeft(digits(from))
            .multiply(BigDecimal.valueOf(rate))
            .movePointRight(digits(to))
            .setScale(0, RoundingMode.HALF_UP).toLong()
    }
}
