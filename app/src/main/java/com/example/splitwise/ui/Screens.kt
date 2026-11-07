@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.splitwise.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitwise.domain.*

// ---------------- Groups list ----------------
@Composable
fun GroupsScreen(vm: AppViewModel, onOpen: (Long) -> Unit) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Groups") }) },
        floatingActionButton = { FloatingActionButton({ creating = true }) { Icon(Icons.Default.Add, "New group") } },
    ) { pad ->
        if (groups.isEmpty()) Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) { Text("No groups yet. Tap + to create one.") }
        LazyColumn(Modifier.padding(pad)) {
            items(groups, key = { it.id }) { g ->
                ListItem(
                    headlineContent = { Text(g.name) },
                    trailingContent = { IconButton({ vm.deleteGroup(g.id) }) { Icon(Icons.Default.Delete, "Delete") } },
                    modifier = Modifier.clickable { onOpen(g.id) },
                )
                HorizontalDivider()
            }
        }
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        var members by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Group name") }, singleLine = true)
                    OutlinedTextField(members, { members = it }, label = { Text("Members (comma separated)") })
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vm.addGroup(name, members.split(","))
                    creating = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton({ creating = false }) { Text("Cancel") } },
        )
    }
}

// ---------------- Group detail ----------------
@Composable
fun GroupScreen(vm: AppViewModel, groupId: Long, onBack: () -> Unit, onAdd: () -> Unit) {
    val state by remember(groupId) { vm.groupState(groupId) }.collectAsStateWithLifecycle(null)
    val s = state ?: return
    var tab by remember { mutableIntStateOf(0) }
    var addingMember by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.group?.name ?: "") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton({ addingMember = true }) { Text("Add member") } },
            )
        },
        floatingActionButton = {
            if (s.members.isNotEmpty()) ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Expense") })
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Expenses") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Balances") })
            }
            if (tab == 0) ExpensesTab(vm, s) else BalancesTab(vm, groupId, s)
        }
    }
    if (addingMember) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingMember = false },
            title = { Text("Add member") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = { TextButton({ vm.addMember(groupId, name); addingMember = false }) { Text("Add") } },
            dismissButton = { TextButton({ addingMember = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ExpensesTab(vm: AppViewModel, s: GroupState) {
    if (s.expenses.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No expenses yet.") }
    LazyColumn {
        items(s.expenses, key = { it.id }) { e ->
            ListItem(
                headlineContent = { Text(e.description) },
                supportingContent = {
                    Text(if (e.isSettlement) "${s.name(e.paidById)} paid back" else "${s.name(e.paidById)} paid ${formatMoney(e.amount)}")
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatMoney(e.amount))
                        IconButton({ vm.deleteExpense(e.id) }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun BalancesTab(vm: AppViewModel, groupId: Long, s: GroupState) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Balances", style = MaterialTheme.typography.titleMedium) }
        items(s.members, key = { "m${it.id}" }) { m ->
            val v = s.net[m.id] ?: 0L
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(m.name)
                Text(
                    when { v > 0 -> "gets back ${formatMoney(v)}"; v < 0 -> "owes ${formatMoney(-v)}"; else -> "settled up" },
                    color = when { v > 0 -> Color(0xFF2E7D32); v < 0 -> Color(0xFFC62828); else -> Color.Gray },
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)); Text("Suggested payments", style = MaterialTheme.typography.titleMedium) }
        if (s.transfers.isEmpty()) item { Text("Everyone is settled up 🎉") }
        items(s.transfers, key = { "t${it.fromId}-${it.toId}" }) { t ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.name(t.fromId)} pays ${s.name(t.toId)} ${formatMoney(t.amount)}", Modifier.weight(1f))
                    Button({ vm.settle(groupId, t) }) { Text("Settle") }
                }
            }
        }
    }
}

// ---------------- Add expense ----------------
private enum class SplitType(val label: String) { EQUAL("Equally"), EXACT("Exact"), PERCENT("Percent") }

@Composable
fun AddExpenseScreen(vm: AppViewModel, groupId: Long, onDone: () -> Unit) {
    val state by remember(groupId) { vm.groupState(groupId) }.collectAsStateWithLifecycle(null)
    val s = state ?: return
    var desc by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var payer by remember { mutableStateOf(s.members.first().id) }
    var type by remember { mutableStateOf(SplitType.EQUAL) }
    var included by remember { mutableStateOf(s.members.map { it.id }.toSet()) }
    var values by remember { mutableStateOf(mapOf<Long, String>()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        val amount = parseMoney(amountText)
        if (desc.isBlank() || amount == null || amount <= 0) { error = "Enter a description and a valid amount"; return }
        val ids = s.members.map { it.id }.filter { it in included }
        try {
            val owed = when (type) {
                SplitType.EQUAL -> Splits.equal(amount, ids)
                SplitType.EXACT -> Splits.exact(amount, ids.associateWith { parseMoney(values[it] ?: "0") ?: throw IllegalArgumentException("Invalid amount") })
                SplitType.PERCENT -> Splits.percent(amount, ids.associateWith { values[it]?.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid percentage") })
            }
            vm.addExpense(groupId, desc, amount, payer, owed)
            onDone()
        } catch (e: IllegalArgumentException) { error = e.message }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Add expense") },
            navigationIcon = { IconButton(onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { TextButton(::save) { Text("Save") } })
    }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(amountText, { amountText = it }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item {
                Text("Paid by")
                FlowRowChips(s.members.map { it.id to it.name }, selected = setOf(payer)) { payer = it }
            }
            item {
                Text("Split")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SplitType.values().forEach { t -> FilterChip(type == t, { type = t }, label = { Text(t.label) }) }
                }
            }
            items(s.members, key = { it.id }) { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(m.id in included, { c -> included = if (c) included + m.id else included - m.id })
                    Text(m.name, Modifier.weight(1f))
                    if (type != SplitType.EQUAL && m.id in included) {
                        OutlinedTextField(
                            values[m.id] ?: "", { values = values + (m.id to it) },
                            label = { Text(if (type == SplitType.PERCENT) "%" else "$") },
                            singleLine = true, modifier = Modifier.width(110.dp),
                        )
                    }
                }
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
    }
}

@Composable
private fun FlowRowChips(items: List<Pair<Long, String>>, selected: Set<Long>, onClick: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (id, name) -> FilterChip(id in selected, { onClick(id) }, label = { Text(name) }) }
    }
}
