package com.reckon.engine

import com.reckon.model.Expense
import com.reckon.model.Member
import com.reckon.model.Report
import com.reckon.model.SplitMode
import com.reckon.model.Transfer
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round

/**
 * Computes each member's net balance across multi-currency, partial-participation
 * expenses, then reduces it to the minimum set of payments to settle up.
 *
 * `rates` maps a currency code to "how many base-currency units equal 1 unit of it".
 * Expenses in a currency with no rate (and not the base) are skipped until a rate exists.
 */
object Reconciler {

    fun reconcile(
        members: List<Member>,
        expenses: List<Expense>,
        rates: Map<String, Double>,
        base: String,
        roundTo: Int = 0
    ): Report {
        val bal = HashMap<String, Double>()
        members.forEach { bal[it.id] = 0.0 }

        var total = 0.0
        var included = 0

        for (e in expenses) {
            val rate = if (e.currency == base) 1.0 else (rates[e.currency] ?: continue)
            if (rate <= 0.0) continue

            val baseAmount = e.amount * rate
            val parts = e.participants.filter { bal.containsKey(it) }
            if (parts.isEmpty() || !bal.containsKey(e.paidBy)) continue

            val weights = parts.map { pid ->
                if (e.splitMode == SplitMode.PER_HEAD) (members.find { it.id == pid }?.size ?: 1) else 1
            }
            val wsum = weights.sum().toDouble()
            if (wsum <= 0.0) continue

            total += baseAmount
            included += 1
            bal[e.paidBy] = bal[e.paidBy]!! + baseAmount
            parts.forEachIndexed { i, pid ->
                bal[pid] = bal[pid]!! - baseAmount * (weights[i] / wsum)
            }
        }

        var transfers = settle(bal)
        if (roundTo > 0) {
            transfers = transfers
                .map { it.copy(amount = round(it.amount / roundTo) * roundTo) }
                .filter { it.amount >= 0.01 }
        }
        return Report(balances = bal, total = total, transfers = transfers, includedCount = included)
    }

    /** Greedy minimum cash-flow: match the largest creditor with the largest debtor. */
    private fun settle(balances: Map<String, Double>): List<Transfer> {
        val creditors = ArrayList<Pair<String, Double>>()
        val debtors = ArrayList<Pair<String, Double>>()
        for ((id, v) in balances) {
            val r = round(v * 100) / 100
            when {
                r > 0.009 -> creditors.add(id to r)
                r < -0.009 -> debtors.add(id to -r)
            }
        }
        creditors.sortByDescending { it.second }
        debtors.sortByDescending { it.second }

        val cr = creditors.map { it.first to it.second }.toMutableList()
        val db = debtors.map { it.first to it.second }.toMutableList()
        val out = ArrayList<Transfer>()
        var ci = 0
        var di = 0
        while (ci < cr.size && di < db.size) {
            val pay = min(cr[ci].second, db[di].second)
            out.add(Transfer(from = db[di].first, to = cr[ci].first, amount = round(pay * 100) / 100))
            cr[ci] = cr[ci].first to (cr[ci].second - pay)
            db[di] = db[di].first to (db[di].second - pay)
            if (cr[ci].second < 0.01) ci++
            if (db[di].second < 0.01) di++
        }
        return out.filter { it.amount >= 0.01 }
    }

    fun convert(amount: Double, currency: String, rates: Map<String, Double>, base: String): Double? {
        if (currency == base) return amount
        val rate = rates[currency] ?: return null
        if (rate <= 0.0) return null
        return amount * rate
    }

    /**
     * Each member's consumption (their share of every expense they took part in),
     * grouped by category, in base currency. Independent of who actually paid —
     * this is "what this family spent" for budgeting, not the net settle-up balance.
     */
    fun spendByMember(
        members: List<Member>,
        expenses: List<Expense>,
        rates: Map<String, Double>,
        base: String
    ): Map<String, Map<String, Double>> {
        val out = HashMap<String, HashMap<String, Double>>()
        members.forEach { out[it.id] = HashMap() }
        for (e in expenses) {
            val rate = if (e.currency == base) 1.0 else (rates[e.currency] ?: continue)
            if (rate <= 0.0) continue
            val baseAmount = e.amount * rate
            val parts = e.participants.filter { out.containsKey(it) }
            if (parts.isEmpty()) continue
            val weights = parts.map { pid ->
                if (e.splitMode == SplitMode.PER_HEAD) (members.find { it.id == pid }?.size ?: 1) else 1
            }
            val wsum = weights.sum().toDouble()
            if (wsum <= 0.0) continue
            parts.forEachIndexed { i, pid ->
                val share = baseAmount * (weights[i] / wsum)
                val m = out[pid]!!
                m[e.category] = (m[e.category] ?: 0.0) + share
            }
        }
        return out
    }
}
