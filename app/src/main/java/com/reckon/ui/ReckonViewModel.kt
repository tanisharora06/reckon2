package com.reckon.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reckon.data.AiParser
import com.reckon.data.FxRepository
import com.reckon.data.Store
import com.reckon.engine.NlParser
import com.reckon.model.Expense
import com.reckon.model.Member
import com.reckon.model.ParsedExpense
import com.reckon.model.SplitMode
import kotlinx.coroutines.launch

class ReckonViewModel(app: Application) : AndroidViewModel(app) {

    val currencies = listOf("INR", "USD", "EUR", "GBP", "AED", "SGD", "THB", "JPY", "AUD", "NPR", "LKR", "CHF", "CAD")

    var members by mutableStateOf<List<Member>>(emptyList()); private set
    var expenses by mutableStateOf<List<Expense>>(emptyList()); private set
    var baseCurrency by mutableStateOf("INR"); private set
    var rates by mutableStateOf<Map<String, Double>>(emptyMap()); private set
    var ratesAt by mutableStateOf<Long?>(null); private set
    var apiKey by mutableStateOf(""); private set
    var darkMode by mutableStateOf(false); private set
    var roundTo by mutableStateOf(0); private set

    var parsing by mutableStateOf(false); private set
    var fetchingRates by mutableStateOf(false); private set
    var status by mutableStateOf<String?>(null)

    init {
        Store.load(getApplication())?.let { s ->
            members = s.members
            expenses = s.expenses
            baseCurrency = s.baseCurrency
            rates = s.rates
            ratesAt = s.ratesAt
            apiKey = s.apiKey
            darkMode = s.darkMode
            roundTo = s.roundTo
        }
    }

    private fun persist() {
        Store.save(getApplication(), Store.State(members, expenses, baseCurrency, rates, ratesAt, apiKey, darkMode, roundTo))
    }

    val currenciesUsed: List<String>
        get() = (expenses.map { it.currency } + baseCurrency).distinct()

    val missingRates: List<String>
        get() = currenciesUsed.filter { it != baseCurrency && (rates[it] ?: 0.0) <= 0.0 }

    private fun uid() = java.util.UUID.randomUUID().toString().take(8)

    // ---- members ----
    fun addMember(name: String, size: Int, isSelf: Boolean, upi: String = "") {
        val n = name.trim()
        if (n.isEmpty()) return
        val newMember = Member(uid(), n, size.coerceAtLeast(1), isSelf, upi.trim())
        members = if (isSelf) members.map { it.copy(isSelf = false) } + newMember else members + newMember
        persist()
    }

    fun setMemberUpi(id: String, upi: String) {
        members = members.map { if (it.id == id) it.copy(upiId = upi.trim()) else it }
        persist()
    }

    fun toggleSelf(id: String) {
        members = members.map {
            when {
                it.id == id -> it.copy(isSelf = !it.isSelf)
                else -> it.copy(isSelf = false)
            }
        }
        persist()
    }

    fun removeMember(id: String) {
        members = members.filter { it.id != id }
        expenses = expenses.filter { it.paidBy != id }
            .map { it.copy(participants = it.participants.filter { p -> p != id }) }
            .filter { it.participants.isNotEmpty() }
        persist()
    }

    // ---- expenses ----
    fun addExpense(desc: String, amount: Double, currency: String, paidBy: String, participants: List<String>, splitMode: SplitMode, category: String): Boolean {
        if (desc.isBlank() || amount <= 0.0 || paidBy.isBlank() || participants.isEmpty()) {
            status = "Need a description, a positive amount, who paid, and at least one participant."
            return false
        }
        expenses = listOf(Expense(uid(), desc.trim(), amount, currency, paidBy, participants, splitMode, System.currentTimeMillis(), category)) + expenses
        status = null
        persist()
        return true
    }

    fun deleteExpense(id: String) {
        expenses = expenses.filter { it.id != id }
        persist()
    }

    // ---- settings ----
    fun setBase(c: String) { baseCurrency = c; persist() }
    fun updateApiKey(k: String) { apiKey = k.trim(); persist() }
    fun toggleDark() { darkMode = !darkMode; persist() }
    fun updateRoundTo(n: Int) { roundTo = n; persist() }

    fun setMemberBudget(id: String, v: Double) {
        members = members.map { if (it.id == id) it.copy(budget = v.coerceAtLeast(0.0)) else it }
        persist()
    }

    fun setMemberCatBudget(id: String, category: String, v: Double) {
        members = members.map { m ->
            if (m.id == id) {
                val cb = m.catBudgets.toMutableMap()
                if (v > 0.0) cb[category] = v else cb.remove(category)
                m.copy(catBudgets = cb)
            } else m
        }
        persist()
    }
    fun setRate(c: String, v: Double) {
        rates = rates.toMutableMap().apply { put(c, v) }
        persist()
    }

    // ---- AI / heuristic parse ----
    fun parseInput(text: String) {
        if (text.isBlank()) return
        if (members.isEmpty()) { status = "Add the people or families in your group first."; return }
        viewModelScope.launch {
            parsing = true; status = null
            val parsed: ParsedExpense? =
                AiParser.parse(text, members, baseCurrency, currencies, apiKey)
                    ?: NlParser.parse(text, members, baseCurrency)
            if (parsed == null) {
                status = "Couldn't read an amount from that — try the manual form."
            } else {
                applyParsed(parsed)
                if (apiKey.isBlank()) status = "Added with the built-in parser. Check the split is right."
            }
            parsing = false
        }
    }

    private fun applyParsed(p: ParsedExpense) {
        fun findId(name: String?): String? {
            if (name.isNullOrBlank()) return null
            val low = name.lowercase()
            members.firstOrNull { it.name.lowercase() == low }?.let { return it.id }
            return members.firstOrNull { it.name.lowercase().contains(low) || low.contains(it.name.lowercase()) }?.id
        }

        val paidBy = findId(p.paidByName) ?: members.firstOrNull { it.isSelf }?.id ?: members.first().id
        var participants = if (p.everyone) members.map { it.id }
        else p.participantNames.mapNotNull { findId(it) }.distinct()
        if (participants.isEmpty()) participants = members.map { it.id }

        val currency = if (currencies.contains(p.currency)) p.currency else baseCurrency
        val category = if (com.reckon.model.Categories.ALL.contains(p.category)) p.category else "Other"
        expenses = listOf(
            Expense(uid(), p.desc.ifBlank { "Expense" }, p.amount, currency, paidBy, participants, p.splitMode, System.currentTimeMillis(), category)
        ) + expenses
        persist()
    }

    // ---- live FX ----
    fun fetchRates() {
        val need = currenciesUsed.filter { it != baseCurrency }
        if (need.isEmpty()) { status = "Everything is already in $baseCurrency — no conversion needed."; return }
        viewModelScope.launch {
            fetchingRates = true; status = null
            val res = FxRepository.fetch(baseCurrency, need)
            if (res.rates.isNotEmpty()) {
                rates = rates.toMutableMap().apply { putAll(res.rates) }
                ratesAt = System.currentTimeMillis()
                persist()
            } else {
                status = "Live rates unavailable (${res.error ?: "unknown"}). You can enter rates manually."
            }
            fetchingRates = false
        }
    }
}
