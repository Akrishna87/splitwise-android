package com.example.splitwise.domain

import java.math.BigDecimal

data class ParsedReceipt(val items: List<Pair<String, Long>>, val total: Long?)

/** Turns OCR'd receipt lines into (item, price) pairs plus the receipt total. */
object ReceiptParser {
    private val priceAtEnd = Regex("""^(.*?)[\s:]*[$€£₹]?\s*(\d{1,6}[.,]\d{2})\s*[A-Za-z]?$""")
    private val priceOnly = Regex("""^[$€£₹]?\s*(\d{1,6}[.,]\d{2})\s*[A-Za-z]?$""")
    private val totalWord = Regex("""(?i)^(?!.*sub).*\b(grand\s*total|total|amount\s*due|balance\s*due)\b""")
    private val skip = Regex("""(?i)\b(sub\s*total|total|tax|gst|vat|cgst|sgst|tip|gratuity|change|cash|visa|mastercard|card|balance|rounding|discount|savings|thank)\b""")

    private fun minor(s: String): Long = BigDecimal(s.replace(',', '.')).movePointRight(2).toLong()

    fun parse(lines: List<String>): ParsedReceipt {
        var total: Long? = null
        val items = mutableListOf<Pair<String, Long>>()

        fun classify(name: String, price: Long) {
            val n = name.trim().trimEnd('-', ':', '.', ' ')
            when {
                totalWord.containsMatchIn(n) -> total = maxOf(total ?: 0L, price)
                skip.containsMatchIn(n) -> Unit
                n.length >= 2 && price > 0 -> items += n to price
            }
        }

        var pending: String? = null
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val only = priceOnly.matchEntire(line)
            if (only != null) {
                pending?.let { classify(it, minor(only.groupValues[1])) }
                pending = null
                continue
            }
            val end = priceAtEnd.matchEntire(line)
            if (end != null && end.groupValues[1].isNotBlank()) {
                classify(end.groupValues[1], minor(end.groupValues[2]))
                pending = null
                continue
            }
            pending = line
        }
        return ParsedReceipt(items, total)
    }
}
