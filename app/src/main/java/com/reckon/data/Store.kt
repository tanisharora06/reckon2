package com.reckon.data

import android.content.Context
import com.reckon.model.Expense
import com.reckon.model.Member
import com.reckon.model.SplitMode
import org.json.JSONArray
import org.json.JSONObject

/** Persists all app state on-device as a single JSON blob in SharedPreferences. */
object Store {

    private const val PREFS = "reckon_prefs"
    private const val KEY_STATE = "state"

    data class State(
        val members: List<Member>,
        val expenses: List<Expense>,
        val baseCurrency: String,
        val rates: Map<String, Double>,
        val ratesAt: Long?,
        val apiKey: String,
        val darkMode: Boolean,
        val roundTo: Int
    )

    fun load(context: Context): State? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STATE, null)
            ?: return null
        return try {
            val o = JSONObject(raw)

            val members = ArrayList<Member>()
            val mArr = o.optJSONArray("members") ?: JSONArray()
            for (i in 0 until mArr.length()) {
                val m = mArr.getJSONObject(i)
                val cbObj = m.optJSONObject("catBudgets") ?: JSONObject()
                val cb = HashMap<String, Double>()
                for (k in cbObj.keys()) cb[k] = cbObj.getDouble(k)
                members.add(Member(m.getString("id"), m.getString("name"), m.optInt("size", 1), m.optBoolean("isSelf", false), m.optString("upi", ""), m.optDouble("budget", 0.0), cb))
            }

            val expenses = ArrayList<Expense>()
            val eArr = o.optJSONArray("expenses") ?: JSONArray()
            for (i in 0 until eArr.length()) {
                val e = eArr.getJSONObject(i)
                val pArr = e.optJSONArray("participants") ?: JSONArray()
                val parts = ArrayList<String>()
                for (j in 0 until pArr.length()) parts.add(pArr.getString(j))
                expenses.add(
                    Expense(
                        id = e.getString("id"),
                        desc = e.getString("desc"),
                        amount = e.getDouble("amount"),
                        currency = e.getString("currency"),
                        paidBy = e.getString("paidBy"),
                        participants = parts,
                        splitMode = if (e.optString("splitMode") == "EVEN") SplitMode.EVEN else SplitMode.PER_HEAD,
                        at = e.optLong("at", 0L),
                        category = e.optString("category", "Other")
                    )
                )
            }

            val rates = HashMap<String, Double>()
            val rObj = o.optJSONObject("rates") ?: JSONObject()
            for (k in rObj.keys()) rates[k] = rObj.getDouble(k)

            State(
                members = members,
                expenses = expenses,
                baseCurrency = o.optString("baseCurrency", "INR"),
                rates = rates,
                ratesAt = if (o.has("ratesAt") && !o.isNull("ratesAt")) o.getLong("ratesAt") else null,
                apiKey = o.optString("apiKey", ""),
                darkMode = o.optBoolean("darkMode", false),
                roundTo = o.optInt("roundTo", 0)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun save(context: Context, state: State) {
        val o = JSONObject()
        val mArr = JSONArray()
        for (m in state.members) {
            val cb = JSONObject()
            for ((k, v) in m.catBudgets) cb.put(k, v)
            mArr.put(JSONObject().put("id", m.id).put("name", m.name).put("size", m.size).put("isSelf", m.isSelf).put("upi", m.upiId).put("budget", m.budget).put("catBudgets", cb))
        }
        val eArr = JSONArray()
        for (e in state.expenses) {
            val pArr = JSONArray()
            e.participants.forEach { pArr.put(it) }
            eArr.put(
                JSONObject()
                    .put("id", e.id).put("desc", e.desc).put("amount", e.amount)
                    .put("currency", e.currency).put("paidBy", e.paidBy)
                    .put("participants", pArr).put("splitMode", e.splitMode.name).put("at", e.at).put("category", e.category)
            )
        }
        val rObj = JSONObject()
        for ((k, v) in state.rates) rObj.put(k, v)

        o.put("members", mArr).put("expenses", eArr)
            .put("baseCurrency", state.baseCurrency).put("rates", rObj)
            .put("apiKey", state.apiKey)
            .put("darkMode", state.darkMode)
            .put("roundTo", state.roundTo)
        if (state.ratesAt != null) o.put("ratesAt", state.ratesAt)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_STATE, o.toString()).apply()
    }
}
