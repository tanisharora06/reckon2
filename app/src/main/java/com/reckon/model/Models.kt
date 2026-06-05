package com.reckon.model

enum class SplitMode { PER_HEAD, EVEN }

object Categories {
    val ALL = listOf("Food", "Shopping", "Stay", "Transport", "Activities", "Other")
}

data class Member(
    val id: String,
    val name: String,
    val size: Int = 1,
    val isSelf: Boolean = false,
    val upiId: String = "",
    val budget: Double = 0.0,
    val catBudgets: Map<String, Double> = emptyMap()
)

data class Expense(
    val id: String,
    val desc: String,
    val amount: Double,
    val currency: String,
    val paidBy: String,
    val participants: List<String>,
    val splitMode: SplitMode,
    val at: Long,
    val category: String = "Other"
)

data class Transfer(
    val from: String,
    val to: String,
    val amount: Double
)

data class Report(
    val balances: Map<String, Double>,
    val total: Double,
    val transfers: List<Transfer>,
    val includedCount: Int
)

/** Result of parsing a natural-language expense (names not yet resolved to ids). */
data class ParsedExpense(
    val desc: String,
    val amount: Double,
    val currency: String,
    val paidByName: String?,
    val participantNames: List<String>,
    val splitMode: SplitMode,
    val everyone: Boolean,
    val category: String = "Other"
)
