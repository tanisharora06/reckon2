package com.reckon.data

import com.reckon.model.Member
import com.reckon.model.ParsedExpense
import com.reckon.model.SplitMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional: full LLM parsing using the user's own Anthropic API key (entered in
 * Settings, stored only on-device). If the key is blank or the call fails, the
 * caller falls back to the built-in [com.reckon.engine.NlParser].
 */
object AiParser {

    private const val MODEL = "claude-3-5-sonnet-latest"

    suspend fun parse(
        text: String,
        members: List<Member>,
        base: String,
        currencies: List<String>,
        apiKey: String
    ): ParsedExpense? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        try {
            val memberDesc = members.joinToString("; ") {
                if (it.size > 1) "${it.name} (family of ${it.size})" else it.name
            }
            val prompt = """
                You convert a natural-language expense for a shared-expense app into JSON.
                Group members (use these exact names): $memberDesc.
                Base currency: $base. Supported currency codes: ${currencies.joinToString(", ")}.
                Input: ""${'"'}$text""${'"'}

                Respond with ONLY a JSON object, no prose, no markdown. Keys:
                desc (short string), amount (number),
                currency (3-letter code; default $base if unstated),
                paidBy (one member name exactly as listed),
                participants (array of member names exactly as listed who shared this cost;
                resolve "everyone" or "all except X" yourself into explicit included names),
                splitMode ("perHead" to split by family size, or "even" to split equally per family),
                category (one of: Food, Shopping, Stay, Transport, Activities, Other).
            """.trimIndent()

            val reqBody = JSONObject()
                .put("model", MODEL)
                .put("max_tokens", 800)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                .toString()

            val url = URL("https://api.anthropic.com/v1/messages")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20000
                readTimeout = 20000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            }
            conn.outputStream.use { it.write(reqBody.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            if (code !in 200..299) return@withContext null

            val content = JSONObject(body).optJSONArray("content") ?: return@withContext null
            val sb = StringBuilder()
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                if (block.optString("type") == "text") sb.append(block.optString("text"))
            }
            val o = extractJson(sb.toString()) ?: return@withContext null

            val amount = o.optDouble("amount", 0.0)
            if (amount <= 0.0) return@withContext null
            val currency = o.optString("currency", base).let { if (currencies.contains(it)) it else base }
            val splitMode = if (o.optString("splitMode") == "even") SplitMode.EVEN else SplitMode.PER_HEAD
            val partsArr = o.optJSONArray("participants") ?: JSONArray()
            val participantNames = ArrayList<String>()
            for (i in 0 until partsArr.length()) participantNames.add(partsArr.optString(i))
            val cats = listOf("Food", "Shopping", "Stay", "Transport", "Activities", "Other")
            val category = o.optString("category", "Other").let { if (cats.contains(it)) it else "Other" }

            ParsedExpense(
                desc = o.optString("desc", text).take(60),
                amount = amount,
                currency = currency,
                paidByName = o.optString("paidBy", "").ifBlank { null },
                participantNames = participantNames,
                splitMode = splitMode,
                everyone = participantNames.isEmpty(),
                category = category
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun extractJson(text: String): JSONObject? {
        val clean = text.replace("```json", "").replace("```", "").trim()
        val s = clean.indexOf('{')
        val e = clean.lastIndexOf('}')
        if (s < 0 || e < 0 || e <= s) return null
        return try { JSONObject(clean.substring(s, e + 1)) } catch (ex: Exception) { null }
    }
}
