package com.example.splitwise.domain

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object Json {
    fun encodeLongMap(m: Map<String, Long>): String {
        val o = JSONObject(); m.forEach { (k, v) -> o.put(k, v) }; return o.toString()
    }
    fun encodeStringMap(m: Map<String, String>): String {
        val o = JSONObject(); m.forEach { (k, v) -> o.put(k, v) }; return o.toString()
    }
    fun decodeLongMap(s: String): Map<String, Long> = decode(s) { o, k -> o.getLong(k) }
    fun decodeStringMap(s: String): Map<String, String> = decode(s) { o, k -> o.getString(k) }

    private fun <T> decode(s: String, f: (JSONObject, String) -> T): Map<String, T> {
        if (s.isBlank()) return emptyMap()
        return try {
            val o = JSONObject(s)
            val out = LinkedHashMap<String, T>()
            val keys = o.keys()
            while (keys.hasNext()) { val k = keys.next(); out[k] = f(o, k) }
            out
        } catch (e: JSONException) { emptyMap() }
    }

    fun encodeItems(items: List<ItemInput>): String {
        val a = JSONArray()
        items.forEach { i ->
            val t = JSONArray(); i.assignedTo.forEach { t.put(it) }
            a.put(JSONObject().put("n", i.name).put("p", i.priceMinor).put("t", t))
        }
        return a.toString()
    }

    fun decodeItems(s: String): List<ItemInput> {
        if (s.isBlank()) return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map { idx ->
                val o = a.getJSONObject(idx)
                val t = o.optJSONArray("t") ?: JSONArray()
                ItemInput(o.optString("n"), o.optLong("p"), (0 until t.length()).map { t.getString(it) }.toCollection(LinkedHashSet()))
            }
        } catch (e: JSONException) { emptyList() }
    }
}
