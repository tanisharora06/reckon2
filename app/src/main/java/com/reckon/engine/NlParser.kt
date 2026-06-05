package com.reckon.engine

import com.reckon.model.Member
import com.reckon.model.ParsedExpense
import com.reckon.model.SplitMode

/**
 * Offline, no-key parser for plain-English expense lines such as:
 *   "Dinner was ₹6,000, I paid, everyone except the Mehtas"
 *   "Hotel EUR 420, Sharmas paid, split per family"
 * It is intentionally forgiving; anything it can't infer falls back to sensible defaults.
 */
object NlParser {

    private val symbolToCode = mapOf(
        "₹" to "INR", "$" to "USD", "€" to "EUR", "£" to "GBP", "¥" to "JPY"
    )
    private val wordToCode = mapOf(
        "rupee" to "INR", "rupees" to "INR", "inr" to "INR", "rs" to "INR",
        "dollar" to "USD", "dollars" to "USD", "usd" to "USD",
        "euro" to "EUR", "euros" to "EUR", "eur" to "EUR",
        "pound" to "GBP", "pounds" to "GBP", "gbp" to "GBP",
        "dirham" to "AED", "dirhams" to "AED", "aed" to "AED",
        "baht" to "THB", "thb" to "THB",
        "yen" to "JPY", "jpy" to "JPY",
        "franc" to "CHF", "francs" to "CHF", "chf" to "CHF",
        "sgd" to "SGD", "aud" to "AUD", "cad" to "CAD", "npr" to "NPR", "lkr" to "LKR"
    )

    fun parse(text: String, members: List<Member>, base: String): ParsedExpense? {
        val raw = text.trim()
        if (raw.isEmpty()) return null
        val lower = raw.lowercase()

        // ---- amount + currency ----
        val amount = findAmount(raw) ?: return null
        val currency = findCurrency(lower) ?: base

        // ---- payer ----
        val paidByName = findPayer(lower, members)

        // ---- participants ----
        val names = members.map { it.name }
        val mentioned = names.filter { lower.contains(it.lowercase()) }
        var everyone = false
        var participantNames: List<String>

        val exceptIdx = listOf(" except ", " but not ", " besides ", " without ", " apart from ", " minus ")
            .map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull()

        if (lower.contains("everyone") || lower.contains("all of us") || lower.contains("everybody") || exceptIdx != null) {
            everyone = true
            participantNames = names
            if (exceptIdx != null) {
                val tail = lower.substring(exceptIdx)
                val excluded = names.filter { tail.contains(it.lowercase()) }
                if (excluded.isNotEmpty()) {
                    participantNames = names.filter { it !in excluded }
                    everyone = false
                }
            }
        } else if (mentioned.size >= 1 && (lower.contains(" and ") || lower.contains(","))) {
            // an explicit list of people sharing it
            participantNames = mentioned
        } else {
            everyone = true
            participantNames = names
        }

        // ---- split mode ----
        val splitMode = when {
            Regex("per head|per person|per head count|each person|by head").containsMatchIn(lower) -> SplitMode.PER_HEAD
            Regex("per family|per group|evenly|equally|split equally|between families").containsMatchIn(lower) -> SplitMode.EVEN
            else -> SplitMode.PER_HEAD
        }

        val desc = buildDesc(raw)
        val category = findCategory(lower)
        return ParsedExpense(desc, amount, currency, paidByName, participantNames, splitMode, everyone, category)
    }

    private fun findCategory(lower: String): String {
        val map = listOf(
            "Food" to listOf("food", "dinner", "lunch", "breakfast", "brunch", "meal", "meals", "restaurant", "cafe", "coffee", "snack", "snacks", "drinks", "dhaba", "groceries", "grocery"),
            "Stay" to listOf("hotel", "stay", "room", "rooms", "lodge", "resort", "airbnb", "hostel", "accommodation", "homestay", "villa"),
            "Transport" to listOf("taxi", "cab", "uber", "ola", "train", "flight", "flights", "bus", "fuel", "petrol", "diesel", "auto", "transport", "ride", "toll", "parking", "ferry", "rickshaw"),
            "Shopping" to listOf("shopping", "shop", "souvenir", "souvenirs", "clothes", "mall", "market", "gift", "gifts"),
            "Activities" to listOf("ticket", "tickets", "entry", "tour", "activity", "museum", "park", "trek", "trekking", "safari", "show", "movie", "spa", "scuba", "diving", "rafting")
        )
        for ((cat, kws) in map) {
            if (kws.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(lower) }) return cat
        }
        return "Other"
    }

    private fun findAmount(raw: String): Double? {
        // Prefer a number adjacent to a currency symbol/word, else the largest number found.
        val matches = Regex("(?<![A-Za-z])(\\d[\\d,]*(?:\\.\\d+)?)").findAll(raw).toList()
        if (matches.isEmpty()) return null
        // ignore small "family of N" style integers when a bigger money figure exists
        val nums = matches.mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }
        return nums.maxOrNull()
    }

    private fun findCurrency(lower: String): String? {
        for ((sym, code) in symbolToCode) if (lower.contains(sym)) return code
        for ((word, code) in wordToCode) {
            if (Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(lower)) return code
        }
        return null
    }

    private fun findPayer(lower: String, members: List<Member>): String? {
        val verbs = listOf("paid", "covered", "got it", "got the", "put it", "putting", "fronted", "bought", "picked up")
        // "<name> paid"
        for (m in members) {
            val n = m.name.lowercase()
            if (verbs.any { lower.contains("$n $it") || lower.contains("$n's") }) return m.name
        }
        // "I paid" / "me" -> self member
        if (verbs.any { lower.contains("i $it") } || lower.contains("i paid") || lower.contains("i covered")) {
            return members.firstOrNull { it.isSelf }?.name ?: members.firstOrNull()?.name
        }
        // any mentioned name near a verb
        for (m in members) if (lower.contains(m.name.lowercase())) return m.name
        return null
    }

    private fun buildDesc(raw: String): String {
        // take the leading words before the first comma / amount as a label
        val beforeComma = raw.substringBefore(",").trim()
        val cleaned = beforeComma.replace(Regex("(?i)\\bwas\\b|\\bis\\b|\\bcost\\b|[₹$€£¥]"), "").trim()
        val noTrailingNum = cleaned.replace(Regex("\\s*\\d[\\d,]*(?:\\.\\d+)?\\s*$"), "").trim()
        val out = if (noTrailingNum.isNotEmpty()) noTrailingNum else cleaned
        return out.ifEmpty { "Expense" }.take(60)
    }
}
