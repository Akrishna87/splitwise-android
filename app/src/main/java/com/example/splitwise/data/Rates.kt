package com.example.splitwise.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Live exchange rates from the free ECB-backed Frankfurter API. */
object Rates {
    suspend fun fetch(from: String, to: String): Double? = withContext(Dispatchers.IO) {
        if (from == to) return@withContext 1.0
        for (base in listOf("https://api.frankfurter.dev/v1", "https://api.frankfurter.app")) {
            try {
                val c = URL("$base/latest?base=$from&symbols=$to").openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                val body = c.inputStream.bufferedReader().use { it.readText() }
                val r = JSONObject(body).getJSONObject("rates").getDouble(to)
                if (r > 0) return@withContext r
            } catch (e: Exception) {
                // try next host
            }
        }
        null
    }
}
