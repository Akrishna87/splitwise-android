package com.example.splitwise.domain

import com.example.splitwise.data.MemberEntity
import java.net.URLEncoder

data class PayLink(val label: String, val url: String)

object PaymentLinks {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun forMember(m: MemberEntity, amountMinor: Long, currency: String, note: String): List<PayLink> {
        val amt = Money.plain(amountMinor, currency)
        val out = mutableListOf<PayLink>()
        if (m.upiId.isNotBlank() && currency == "INR")
            out += PayLink("Pay with UPI", "upi://pay?pa=${enc(m.upiId.trim())}&pn=${enc(m.name)}&am=$amt&cu=INR&tn=${enc(note)}")
        if (m.paypalUser.isNotBlank())
            out += PayLink("Pay with PayPal", "https://paypal.me/${enc(m.paypalUser.trim())}/$amt$currency")
        if (m.venmoUser.isNotBlank() && currency == "USD")
            out += PayLink("Pay with Venmo", "https://venmo.com/${enc(m.venmoUser.trim().removePrefix("@"))}?txn=pay&amount=$amt&note=${enc(note)}")
        return out
    }
}
