package com.reckon.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches live, real-world exchange rates from open.er-api.com — a free, keyless
 * endpoint with broad currency coverage. Returns a map of "base units per 1 unit
 * of the foreign currency" for the requested symbols.
 */
object FxRepository {

    data class Result(val rates: Map<String, Double>, val error: String?)

    suspend fun fetch(base: String, symbols: List<String>): Result = withContext(Dispatchers.IO) {
        val wanted = symbols.filter { it != base }
        if (wanted.isEmpty()) return@withContext Result(emptyMap(), null)
        try {
            val url = URL("https://open.er-api.com/v6/latest/$base")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code !in 200..299) return@withContext Result(emptyMap(), "Rate server returned $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            if (json.optString("result") == "error") {
                return@withContext Result(emptyMap(), json.optString("error-type", "rate error"))
            }
            val ratesObj = json.optJSONObject("rates")
                ?: return@withContext Result(emptyMap(), "Malformed response")

            val out = HashMap<String, Double>()
            for (cur in wanted) {
                // ratesObj holds "foreign per 1 base"; invert to "base per 1 foreign"
                val perBase = ratesObj.optDouble(cur, 0.0)
                if (perBase > 0.0) out[cur] = 1.0 / perBase
            }
            if (out.isEmpty()) Result(emptyMap(), "No matching currencies returned")
            else Result(out, null)
        } catch (e: Exception) {
            Result(emptyMap(), e.message ?: "Network error")
        }
    }
}
