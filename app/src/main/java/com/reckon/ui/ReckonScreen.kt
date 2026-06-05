package com.reckon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reckon.engine.Reconciler
import com.reckon.model.Member
import com.reckon.model.SplitMode
import com.reckon.ui.theme.Accent
import com.reckon.ui.theme.AccentSoft
import com.reckon.ui.theme.Card
import com.reckon.ui.theme.CardAlt
import com.reckon.ui.theme.Gold
import com.reckon.ui.theme.Ink
import com.reckon.ui.theme.InkFaint
import com.reckon.ui.theme.InkSoft
import com.reckon.ui.theme.LineC
import com.reckon.ui.theme.LineSoft
import com.reckon.ui.theme.Paper
import com.reckon.ui.theme.RedC
import com.reckon.ui.theme.RedSoft
import com.reckon.ui.theme.Teal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

private fun money(v: Double, cur: String): String = try {
    val nf = NumberFormat.getCurrencyInstance()
    nf.currency = Currency.getInstance(cur)
    nf.maximumFractionDigits = if (cur == "JPY") 0 else 2
    nf.minimumFractionDigits = 0
    nf.format(v)
} catch (e: Exception) {
    "$cur " + String.format("%.2f", v)
}

private fun fmtTime(ms: Long): String =
    SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(ms))

private fun numText(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

@Composable
fun ReckonScreen(vm: ReckonViewModel = viewModel()) {
    val members = vm.members
    val expenses = vm.expenses
    val base = vm.baseCurrency

    val report = remember(members, expenses, vm.rates, base, vm.roundTo) {
        Reconciler.reconcile(members, expenses, vm.rates, base, vm.roundTo)
    }
    val spendByMember = remember(members, expenses, vm.rates, base) {
        Reconciler.spendByMember(members, expenses, vm.rates, base)
    }
    val nameOf: (String) -> String = { id -> members.find { it.id == id }?.name ?: "—" }

    val context = androidx.compose.ui.platform.LocalContext.current
    val payUpi: (String, String, Double) -> Unit = { payeeName, vpa, amount ->
        try {
            val uri = android.net.Uri.parse("upi://pay").buildUpon()
                .appendQueryParameter("pa", vpa)
                .appendQueryParameter("pn", payeeName)
                .appendQueryParameter("am", String.format(Locale.US, "%.2f", amount))
                .appendQueryParameter("cu", "INR")
                .appendQueryParameter("tn", "Reckon settle-up")
                .build()
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            vm.status = "No UPI app found to open the payment."
        }
    }

    var showSettings by remember { mutableStateOf(false) }
    var upiTarget by remember { mutableStateOf<Member?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 40.dp)
    ) {
        item { Header(base, vm.currencies, dark = vm.darkMode, onPickBase = { vm.setBase(it) }, onSettings = { showSettings = true }, onToggleDark = { vm.toggleDark() }) }

        vm.status?.let { msg ->
            item {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(RedSoft).border(1.dp, RedC.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) { Text(msg, color = RedC, fontSize = 13.sp) }
            }
        }

        if (expenses.isNotEmpty()) {
            item {
                SettlementCard(
                    total = report.total, base = base, included = report.includedCount,
                    missing = vm.missingRates.size, balances = report.balances, members = members,
                    transfers = report.transfers, nameOf = nameOf,
                    fetching = vm.fetchingRates, onRefresh = { vm.fetchRates() },
                    roundTo = vm.roundTo, onRound = { vm.updateRoundTo(it) },
                    onPayUpi = payUpi, onAddUpi = { upiTarget = it }
                )
            }
        }

        if (members.isNotEmpty()) {
            item {
                TripPlanCard(
                    members = members, base = base, spend = spendByMember,
                    onSetBudget = { id, v -> vm.setMemberBudget(id, v) },
                    onSetCatBudget = { id, cat, v -> vm.setMemberCatBudget(id, cat, v) }
                )
            }
        }

        item {
            SectionCard("Group", "◷") {
                FlowChips {
                    members.forEach { m ->
                        MemberChip(m, onTap = { vm.toggleSelf(m.id) }, onRemove = { vm.removeMember(m.id) })
                    }
                    if (members.isEmpty()) Text("Add each person or family below.", color = InkFaint, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                AddMemberRow(onAdd = { name, size, isSelf, upi -> vm.addMember(name, size, isSelf, upi) })
            }
        }

        item {
            AddExpenseCard(
                members = members, base = base, currencies = vm.currencies,
                parsing = vm.parsing, hasKey = vm.apiKey.isNotBlank(),
                onParse = { vm.parseInput(it) },
                onAddManual = { d, a, c, p, parts, mode, cat -> vm.addExpense(d, a, c, p, parts, mode, cat) }
            )
        }

        if (expenses.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⌗", color = Accent, fontSize = 14.sp); Spacer(Modifier.width(8.dp))
                    Text("EXPENSES · ${expenses.size}", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
                }
            }
            items(expenses, key = { it.id }) { e ->
                val inBase = Reconciler.convert(e.amount, e.currency, vm.rates, base)
                val all = e.participants.size == members.size
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardAlt)
                        .border(1.dp, LineC, RoundedCornerShape(12.dp)).padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.desc, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "${nameOf(e.paidBy)} paid · " +
                                (if (all) "everyone" else e.participants.joinToString(", ") { nameOf(it) }) +
                                " · " + (if (e.splitMode == SplitMode.PER_HEAD) "per head" else "per family") +
                                " · " + e.category,
                            color = InkSoft, fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money(e.amount, e.currency), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                        if (e.currency != base) {
                            Text(
                                if (inBase != null) "≈ ${money(inBase, base)}" else "rate needed",
                                color = if (inBase != null) InkFaint else RedC, fontSize = 11.sp, fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text("remove", color = InkFaint, fontSize = 11.sp, modifier = Modifier.clickable { vm.deleteExpense(e.id) })
                    }
                }
            }
        }

        if (expenses.isNotEmpty()) {
            item { ByDayCard(expenses = expenses, rates = vm.rates, base = base) }
        }

        if (vm.currenciesUsed.any { it != base }) {
            item {
                RatesCard(
                    currencies = vm.currenciesUsed.filter { it != base }, base = base, rates = vm.rates,
                    ratesAt = vm.ratesAt, fetching = vm.fetchingRates,
                    onFetch = { vm.fetchRates() }, onSet = { c, v -> vm.setRate(c, v) }
                )
            }
        }

        item {
            Text(
                "Data stays on this device. Live rates come from a public exchange-rate service. " +
                    "Add an Anthropic API key in settings for smarter parsing.",
                color = InkFaint, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    if (showSettings) {
        ApiKeyDialog(current = vm.apiKey, onDismiss = { showSettings = false }, onSave = { vm.updateApiKey(it); showSettings = false })
    }

    upiTarget?.let { target ->
        SetUpiDialog(
            member = target,
            onDismiss = { upiTarget = null },
            onSave = { vm.setMemberUpi(target.id, it); upiTarget = null }
        )
    }
}

/* ---------------- components ---------------- */

@Composable
private fun Header(base: String, currencies: List<String>, dark: Boolean, onPickBase: (String) -> Unit, onSettings: () -> Unit, onToggleDark: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("SHARED EXPENSES · RECONCILED", color = InkFaint, fontSize = 11.sp, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
                Text("Reckon", color = Ink, fontSize = 38.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                Text("Messy groups, partial participation, mixed currencies — settled in the fewest payments.", color = InkSoft, fontSize = 13.sp)
            }
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(CardAlt).border(1.dp, LineC, RoundedCornerShape(10.dp))
                    .clickable { onToggleDark() }.padding(horizontal = 12.dp, vertical = 10.dp)
            ) { Text(if (dark) "☀" else "☾", color = InkSoft, fontSize = 16.sp) }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SETTLE IN", color = InkFaint, fontSize = 11.sp, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(8.dp))
            CurrencyDropdown(base, currencies, onPickBase)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(CardAlt).border(1.dp, LineC, RoundedCornerShape(10.dp))
                    .clickable { onSettings() }.padding(horizontal = 12.dp, vertical = 10.dp)
            ) { Text("⚙ API key", color = InkSoft, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun SettlementCard(
    total: Double, base: String, included: Int, missing: Int,
    balances: Map<String, Double>, members: List<Member>,
    transfers: List<com.reckon.model.Transfer>, nameOf: (String) -> String,
    fetching: Boolean, onRefresh: () -> Unit,
    roundTo: Int, onRound: (Int) -> Unit,
    onPayUpi: (String, String, Double) -> Unit, onAddUpi: (Member) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card)
            .border(1.dp, Accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).padding(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("THE SETTLEMENT", color = InkFaint, fontSize = 11.sp, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)
            Box(
                Modifier.clip(RoundedCornerShape(9.dp)).background(CardAlt).border(1.dp, LineC, RoundedCornerShape(9.dp))
                    .clickable(enabled = !fetching) { onRefresh() }.padding(horizontal = 10.dp, vertical = 6.dp)
            ) { Text(if (fetching) "fetching live rates…" else "↻ refresh live FX", color = Teal, fontSize = 12.sp) }
        }
        Spacer(Modifier.height(8.dp))
        Text(money(total, base), color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
        Row {
            Text("total across $included expense" + if (included == 1) "" else "s", color = InkSoft, fontSize = 13.sp)
            if (missing > 0) Text("  ·  $missing currency need rates", color = RedC, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        members.forEach { m ->
            val v = Math.round((balances[m.id] ?: 0.0) * 100) / 100.0
            val owed = v > 0.009; val owes = v < -0.009
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(m.name + if (m.size > 1) "  ·  ${m.size}" else "", color = Ink, fontSize = 14.sp)
                Text(
                    (if (owed) "is owed " else if (owes) "owes " else "settled ") + if (kotlin.math.abs(v) > 0.009) money(kotlin.math.abs(v), base) else "",
                    color = if (owed) Teal else if (owes) RedC else InkFaint, fontSize = 14.sp, fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ROUND", color = InkFaint, fontSize = 11.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(8.dp))
            Chip("exact", roundTo == 0) { onRound(0) }
            Chip("10", roundTo == 10) { onRound(10) }
            Chip("50", roundTo == 50) { onRound(50) }
            Chip("100", roundTo == 100) { onRound(100) }
        }

        if (transfers.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = LineSoft)
            Spacer(Modifier.height(12.dp))
            Text(
                "SETTLE UP · ${transfers.size} PAYMENT" + (if (transfers.size == 1) "" else "S") + (if (roundTo > 0) " · ROUNDED TO $roundTo" else ""),
                color = InkFaint, fontSize = 11.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(8.dp))
            transfers.forEach { t ->
                val payee = members.find { it.id == t.to }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(CardAlt)
                        .border(1.dp, LineC, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text(nameOf(t.from), color = RedC, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("  →  ", color = InkFaint, fontSize = 14.sp)
                        Text(nameOf(t.to), color = Teal, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(money(t.amount, base), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    if (base == "INR" && payee != null) {
                        Spacer(Modifier.width(10.dp))
                        if (payee.upiId.isNotBlank()) {
                            Box(
                                Modifier.clip(RoundedCornerShape(8.dp)).background(Teal)
                                    .clickable { onPayUpi(payee.name, payee.upiId, t.amount) }.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) { Text("Pay", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        } else {
                            Box(
                                Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, LineC, RoundedCornerShape(8.dp))
                                    .clickable { onAddUpi(payee) }.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) { Text("+ UPI", color = InkSoft, fontSize = 12.sp) }
                        }
                    }
                }
            }
        } else if (included > 0) {
            Spacer(Modifier.height(10.dp))
            Text("✓ Everyone's square.", color = Teal, fontSize = 14.sp)
        }
    }
}

@Composable
private fun AddMemberRow(onAdd: (String, Int, Boolean, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("1") }
    var isSelf by remember { mutableStateOf(false) }
    var upi by remember { mutableStateOf("") }
    Column {
        ReckonField(name, { name = it }, "Name (e.g. the Mehtas, or Atul)", Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        ReckonField(upi, { upi = it.trim() }, "UPI ID (optional, e.g. name@okhdfcbank)", Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ReckonField(size, { size = it.filter { c -> c.isDigit() }.take(2) }, "People", Modifier.width(90.dp), number = true)
            Spacer(Modifier.width(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isSelf = !isSelf }) {
                Checkbox(checked = isSelf, onCheckedChange = { isSelf = it }, colors = CheckboxDefaults.colors(checkedColor = Accent))
                Text("this is me", color = InkSoft, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Ink).clickable {
                    onAdd(name, size.toIntOrNull() ?: 1, isSelf, upi); name = ""; size = "1"; isSelf = false; upi = ""
                }.padding(horizontal = 16.dp, vertical = 11.dp)
            ) { Text("+ Add", color = Paper, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun AddExpenseCard(
    members: List<Member>, base: String, currencies: List<String>,
    parsing: Boolean, hasKey: Boolean,
    onParse: (String) -> Unit,
    onAddManual: (String, Double, String, String, List<String>, SplitMode, String) -> Boolean
) {
    var nl by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }
    var desc by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(base) }
    var paidBy by remember { mutableStateOf("") }
    var parts by remember { mutableStateOf(setOf<String>()) }
    var mode by remember { mutableStateOf(SplitMode.PER_HEAD) }
    var cat by remember { mutableStateOf("Other") }

    SectionCard("Add an expense", "✦") {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AccentSoft.copy(alpha = 0.4f))
                .border(1.dp, Accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(12.dp)
        ) {
            Text(
                if (hasKey) "Describe it in plain English — AI fills in the split" else "Describe it in plain English — the built-in parser fills in the split",
                color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            ReckonField(nl, { nl = it }, "e.g. \"Dinner was ₹6,000, I paid, everyone except the Mehtas\"", Modifier.fillMaxWidth(), singleLine = false)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (showManual) "hide manual entry" else "enter manually instead", color = InkSoft, fontSize = 12.sp, modifier = Modifier.clickable { showManual = !showManual })
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(if (parsing || nl.isBlank()) Accent.copy(alpha = 0.5f) else Accent)
                        .clickable(enabled = !parsing && nl.isNotBlank()) { onParse(nl); nl = "" }.padding(horizontal = 14.dp, vertical = 10.dp)
                ) { Text(if (parsing) "reading…" else "✦ Add with AI", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }

        if (showManual) {
            Spacer(Modifier.height(12.dp))
            ReckonField(desc, { desc = it }, "What was it for?", Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row {
                ReckonField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, "Amount", Modifier.weight(1f), number = true)
                Spacer(Modifier.width(8.dp))
                CurrencyDropdown(currency, currencies, { currency = it })
            }
            Spacer(Modifier.height(10.dp))
            Text("PAID BY", color = InkFaint, fontSize = 11.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(6.dp))
            FlowChips { members.forEach { m -> Chip(m.name, paidBy == m.id) { paidBy = m.id } } }
            Spacer(Modifier.height(10.dp))
            Text("WHO SHARED IT", color = InkFaint, fontSize = 11.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(6.dp))
            FlowChips {
                members.forEach { m ->
                    val on = parts.contains(m.id)
                    Chip(m.name, on) { parts = if (on) parts - m.id else parts + m.id }
                }
                Chip("everyone", false) { parts = members.map { it.id }.toSet() }
            }
            Spacer(Modifier.height(10.dp))
            Text("CATEGORY", color = InkFaint, fontSize = 11.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(6.dp))
            FlowChips { com.reckon.model.Categories.ALL.forEach { ca -> Chip(ca, cat == ca) { cat = ca } } }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row {
                    Chip("split per head", mode == SplitMode.PER_HEAD) { mode = SplitMode.PER_HEAD }
                    Spacer(Modifier.width(6.dp))
                    Chip("per family", mode == SplitMode.EVEN) { mode = SplitMode.EVEN }
                }
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Ink).clickable {
                        val ok = onAddManual(desc, amount.toDoubleOrNull() ?: 0.0, currency, paidBy, parts.toList(), mode, cat)
                        if (ok) { desc = ""; amount = ""; parts = emptySet(); paidBy = ""; cat = "Other" }
                    }.padding(horizontal = 14.dp, vertical = 11.dp)
                ) { Text("+ Add", color = Paper, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun RatesCard(
    currencies: List<String>, base: String, rates: Map<String, Double>,
    ratesAt: Long?, fetching: Boolean, onFetch: () -> Unit, onSet: (String, Double) -> Unit
) {
    SectionCard("Exchange rates", "◎") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (ratesAt != null) "Live rates · ${fmtTime(ratesAt)}" else "Fetch live rates, or type them in.", color = InkFaint, fontSize = 12.sp)
            Box(
                Modifier.clip(RoundedCornerShape(9.dp)).background(CardAlt).border(1.dp, LineC, RoundedCornerShape(9.dp))
                    .clickable(enabled = !fetching) { onFetch() }.padding(horizontal = 10.dp, vertical = 6.dp)
            ) { Text(if (fetching) "fetching…" else "↻ fetch live", color = Teal, fontSize = 12.sp) }
        }
        Spacer(Modifier.height(8.dp))
        currencies.forEach { c ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(CardAlt)
                    .border(1.dp, LineC, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text("1 $c =", color = Ink, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var txt by remember(c, rates[c]) { mutableStateOf(rates[c]?.let { if (it > 0) it.toString() else "" } ?: "") }
                    ReckonField(txt, { v -> txt = v.filter { ch -> ch.isDigit() || ch == '.' }; txt.toDoubleOrNull()?.let { onSet(c, it) } }, "—", Modifier.width(120.dp), number = true)
                    Spacer(Modifier.width(8.dp))
                    Text(base, color = InkFaint, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun ApiKeyDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var key by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(key) }) { Text("Save", color = Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = InkSoft) } },
        title = { Text("Anthropic API key", color = Ink, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text("Optional. With a key, expense sentences are parsed by Claude. Without one, a built-in parser is used. Stored only on this device.", color = InkSoft, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                ReckonField(key, { key = it }, "sk-ant-…", Modifier.fillMaxWidth())
            }
        },
        containerColor = Card
    )
}

/* ---------------- small reusable pieces ---------------- */

@Composable
private fun SectionCard(title: String, glyph: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card)
            .border(1.dp, LineC, RoundedCornerShape(16.dp)).padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(glyph, color = Accent, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(title.uppercase(), color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 6.dp, bottom = 6.dp).clip(RoundedCornerShape(10.dp))
            .background(if (active) AccentSoft else CardAlt)
            .border(1.dp, if (active) Accent else LineC, RoundedCornerShape(10.dp))
            .clickable { onClick() }.padding(horizontal = 10.dp, vertical = 8.dp)
    ) { Text(label, color = if (active) Accent else InkSoft, fontSize = 13.sp) }
}

@Composable
private fun MemberChip(m: Member, onTap: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.padding(end = 6.dp, bottom = 6.dp).clip(RoundedCornerShape(20.dp)).background(CardAlt)
            .border(1.dp, if (m.isSelf) Accent else LineC, RoundedCornerShape(20.dp))
            .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(m.name, color = Ink, fontSize = 13.sp, modifier = Modifier.clickable { onTap() })
        if (m.size > 1) Text(" ×${m.size}", color = InkFaint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        if (m.isSelf) Text(" (me)", color = Accent, fontSize = 11.sp)
        Spacer(Modifier.width(6.dp))
        Text("✕", color = InkFaint, fontSize = 12.sp, modifier = Modifier.clickable { onRemove() })
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(content: @Composable () -> Unit) {
    // simple wrapping row
    androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun CurrencyDropdown(value: String, options: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.wrapContentWidth()) {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).background(CardAlt).border(1.dp, LineC, RoundedCornerShape(10.dp))
                .clickable { open = true }.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, color = Ink, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            Text("  ▾", color = InkFaint, fontSize = 12.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { opt -> DropdownMenuItem(text = { Text(opt, fontFamily = FontFamily.Monospace) }, onClick = { onPick(opt); open = false }) }
        }
    }
}

@Composable
private fun ReckonField(
    value: String, onChange: (String) -> Unit, placeholder: String,
    modifier: Modifier = Modifier, number: Boolean = false, singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier,
        placeholder = { Text(placeholder, color = InkFaint, fontSize = 13.sp) },
        singleLine = singleLine,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, unfocusedBorderColor = LineC,
            focusedContainerColor = CardAlt, unfocusedContainerColor = CardAlt,
            focusedTextColor = Ink, unfocusedTextColor = Ink, cursorColor = Accent
        )
    )
}

/* ---------------- added feature components ---------------- */

private data class DaySummary(val label: String, val total: Double, val count: Int, val sortKey: Long)

@Composable
private fun TripPlanCard(
    members: List<Member>, base: String,
    spend: Map<String, Map<String, Double>>,
    onSetBudget: (String, Double) -> Unit,
    onSetCatBudget: (String, String, Double) -> Unit
) {
    val cats = com.reckon.model.Categories.ALL
    val totalPlanned = members.sumOf { it.budget }
    val totalSpent = spend.values.sumOf { m -> m.values.sum() }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    SectionCard("Trip plan", "▤") {
        if (totalPlanned > 0.0 || totalSpent > 0.0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Planned ${money(totalPlanned, base)}", color = InkSoft, fontSize = 13.sp)
                Text("Spent ${money(totalSpent, base)}", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(6.dp))
            ProgressBar(if (totalPlanned > 0.0) totalSpent / totalPlanned else 0.0, totalPlanned > 0.0 && totalSpent > totalPlanned)
            Spacer(Modifier.height(12.dp))
            cats.forEach { c ->
                val alloc = members.sumOf { it.catBudgets[c] ?: 0.0 }
                val sp = members.sumOf { spend[it.id]?.get(c) ?: 0.0 }
                if (alloc > 0.0 || sp > 0.0) CategoryRow(c, alloc, sp, base)
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = LineSoft)
            Spacer(Modifier.height(8.dp))
        } else {
            Text("Set each family's budget below, then tap “set categories” to plan food, shopping, stay and more.", color = InkFaint, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
        }

        members.forEach { m ->
            val mSpend = spend[m.id] ?: emptyMap()
            val mSpent = mSpend.values.sum()
            val isOpen = expanded.contains(m.id)
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name + if (m.size > 1) "  · ${m.size}" else "", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "${money(mSpent, base)} spent" + if (m.budget > 0.0) " of ${money(m.budget, base)}" else "",
                            color = InkSoft, fontSize = 12.sp, fontFamily = FontFamily.Monospace
                        )
                    }
                    PlanNumField(initial = m.budget, keyId = "b_${m.id}", onSet = { onSetBudget(m.id, it) })
                }
                if (m.budget > 0.0) {
                    Spacer(Modifier.height(6.dp))
                    ProgressBar(mSpent / m.budget, mSpent > m.budget)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (isOpen) "hide categories ▴" else "set categories ▾",
                    color = Accent, fontSize = 12.sp,
                    modifier = Modifier.clickable { expanded = if (isOpen) expanded - m.id else expanded + m.id }
                )
                if (isOpen) {
                    Spacer(Modifier.height(4.dp))
                    cats.forEach { c ->
                        val sp = mSpend[c] ?: 0.0
                        val alloc = m.catBudgets[c] ?: 0.0
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c, color = Ink, fontSize = 13.sp)
                                Text("${money(sp, base)} spent", color = InkFaint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                            PlanNumField(initial = alloc, keyId = "c_${m.id}_$c", onSet = { onSetCatBudget(m.id, c, it) })
                        }
                        if (alloc > 0.0) { ProgressBar(sp / alloc, sp > alloc); Spacer(Modifier.height(2.dp)) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = LineSoft)
            }
        }
    }
}

@Composable
private fun CategoryRow(cat: String, alloc: Double, spent: Double, base: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(cat, color = Ink, fontSize = 13.sp)
            Text(
                money(spent, base) + (if (alloc > 0.0) " / ${money(alloc, base)}" else ""),
                color = InkSoft, fontSize = 12.sp, fontFamily = FontFamily.Monospace
            )
        }
        if (alloc > 0.0) { Spacer(Modifier.height(4.dp)); ProgressBar(spent / alloc, spent > alloc) }
    }
}

@Composable
private fun ProgressBar(fraction: Double, over: Boolean) {
    val f = fraction.coerceIn(0.0, 1.0).toFloat()
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(LineSoft)) {
        Box(Modifier.fillMaxWidth(f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (over) RedC else Teal))
    }
}

@Composable
private fun PlanNumField(initial: Double, keyId: String, onSet: (Double) -> Unit) {
    var txt by remember(keyId) { mutableStateOf(if (initial > 0.0) numText(initial) else "") }
    ReckonField(
        txt,
        { v -> txt = v.filter { ch -> ch.isDigit() || ch == '.' }; onSet(txt.toDoubleOrNull() ?: 0.0) },
        "—", Modifier.width(110.dp), number = true
    )
}

@Composable
private fun ByDayCard(expenses: List<com.reckon.model.Expense>, rates: Map<String, Double>, base: String) {
    val rows = remember(expenses, rates, base) {
        val fmt = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        val map = LinkedHashMap<String, MutableList<com.reckon.model.Expense>>()
        expenses.sortedByDescending { it.at }.forEach { e ->
            val key = fmt.format(Date(e.at))
            map.getOrPut(key) { mutableListOf() }.add(e)
        }
        map.entries.map { entry ->
            val tot = entry.value.sumOf { Reconciler.convert(it.amount, it.currency, rates, base) ?: 0.0 }
            DaySummary(entry.key, tot, entry.value.size, entry.value.maxOf { it.at })
        }.sortedByDescending { it.sortKey }
    }
    SectionCard("By day", "❑") {
        rows.forEach { d ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.label, color = Ink, fontSize = 14.sp)
                    Text("${d.count} expense" + (if (d.count == 1) "" else "s"), color = InkFaint, fontSize = 11.sp)
                }
                Text(money(d.total, base), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun SetUpiDialog(member: Member, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var upi by remember { mutableStateOf(member.upiId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(upi) }) { Text("Save", color = Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = InkSoft) } },
        title = { Text("UPI ID for ${member.name}", color = Ink, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text("Save ${member.name}'s UPI ID so payments to them open your UPI app pre-filled with the amount. Stored only on this device.", color = InkSoft, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                ReckonField(upi, { upi = it.trim() }, "name@okhdfcbank", Modifier.fillMaxWidth())
            }
        },
        containerColor = Card
    )
}
