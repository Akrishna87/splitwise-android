@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.splitwise.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitwise.data.*
import com.example.splitwise.domain.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun GroupScreen(vm: AppViewModel, gid: String, onBack: () -> Unit, onAdd: () -> Unit, onOpenExpense: (String) -> Unit, onRecurring: () -> Unit) {
    val snap by vm.snap.collectAsStateWithLifecycle()
    val g = snap.group(gid)
    if (g == null) {
        Scaffold(topBar = { TopAppBar(title = { Text("") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) {
            Box(Modifier.padding(it).fillMaxSize(), Alignment.Center) { Text("Group not found") }
        }
        return
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val members = snap.membersOf(gid)
    val expenses = snap.expensesOf(gid)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var showMembers by remember { mutableStateOf(false) }
    var showWho by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val isFriend = g.kind == "FRIEND"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(snap.title(g)) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(menu, { menu = false }) {
                        if (!isFriend) DropdownMenuItem({ Text("Members & payment details") }, { menu = false; showMembers = true })
                        DropdownMenuItem({ Text("Recurring expenses") }, { menu = false; onRecurring() })
                        DropdownMenuItem({ Text("Who am I?") }, { menu = false; showWho = true })
                        DropdownMenuItem({ Text("Share group (file)") }, {
                            menu = false
                            scope.launch { ctx.shareFile("${snap.title(g)}.splitwise.json", "application/json", vm.exportGroup(gid)) }
                        })
                        DropdownMenuItem({ Text("Export CSV") }, {
                            menu = false
                            ctx.shareFile("${snap.title(g)}.csv", "text/csv", Csv.group(g, members, expenses))
                        })
                        if (!isFriend) DropdownMenuItem({ Text("Rename") }, { menu = false; showRename = true })
                        DropdownMenuItem({ Text("Delete") }, { menu = false; showDelete = true })
                    }
                },
            )
        },
        floatingActionButton = {
            if (members.isNotEmpty() && tab == 0) ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add expense") })
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(tab) {
                listOf("Expenses", "Balances", "Stats").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
            }
            when (tab) {
                0 -> ExpensesTab(snap, g, expenses, onOpenExpense)
                1 -> BalancesTab(vm, snap, g)
                else -> StatsTab(snap, g)
            }
        }
    }

    if (showMembers) MembersDialog(vm, snap, gid) { showMembers = false }
    if (showWho) {
        var pick by remember { mutableStateOf(snap.meIn(gid)?.id ?: members.firstOrNull()?.id ?: "") }
        AlertDialog(
            onDismissRequest = { showWho = false },
            title = { Text("Who are you in this group?") },
            text = {
                Column {
                    members.forEach { m ->
                        Row(Modifier.fillMaxWidth().clickable { pick = m.id }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(pick == m.id, { pick = m.id }); Text(m.name)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ if (pick.isNotEmpty()) vm.setMe(gid, pick); showWho = false }) { Text("Save") } },
            dismissButton = { TextButton({ showWho = false }) { Text("Cancel") } },
        )
    }
    if (showRename) {
        var name by remember { mutableStateOf(g.name) }
        AlertDialog(
            onDismissRequest = { showRename = false }, title = { Text("Rename group") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.renameGroup(g, name); showRename = false }) { Text("Save") } },
            dismissButton = { TextButton({ showRename = false }) { Text("Cancel") } },
        )
    }
    if (showDelete) AlertDialog(
        onDismissRequest = { showDelete = false },
        title = { Text("Delete “${snap.title(g)}”?") },
        text = { Text("This removes the group and all its expenses from this phone. If you shared it, others keep their copy.") },
        confirmButton = { TextButton({ showDelete = false; vm.deleteGroup(g); onBack() }) { Text("Delete") } },
        dismissButton = { TextButton({ showDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun ExpensesTab(snap: AppSnapshot, g: GroupEntity, expenses: List<ExpenseEntity>, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf("All") }
    val filtered = expenses.filter { e ->
        (cat == "All" || e.category == cat) &&
            (query.isBlank() || e.description.contains(query, true) || e.note.contains(query, true) || snap.memberName(e.paidById).contains(query, true))
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query, { query = it }, placeholder = { Text("Search expenses") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.weight(1f),
            )
            DropdownField("Category", listOf("All") + Categories.names + "Payment", cat, Modifier.width(150.dp)) { cat = it }
        }
        if (filtered.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(if (expenses.isEmpty()) "No expenses yet. Tap “Add expense”." else "No matching expenses.")
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            items(filtered, key = { it.id }) { e ->
                val (eff, color) = myEffect(e, snap)
                val title = if (e.isSettlement) "${snap.memberName(e.paidById)} paid ${snap.memberName(Json.decodeLongMap(e.sharesJson).keys.firstOrNull() ?: "")}" else e.description
                ListItem(
                    leadingContent = { Text(Categories.emoji(e.category)) },
                    headlineContent = { Text(title) },
                    supportingContent = {
                        Text(dateLabel(e.date) + if (e.isSettlement) "" else " • ${snap.memberName(e.paidById)} paid ${Money.format(e.amount, e.currency)}")
                    },
                    trailingContent = { Text(eff, color = color, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.clickable { onOpen(e.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun BalancesTab(vm: AppViewModel, snap: AppSnapshot, g: GroupEntity) {
    val members = snap.membersOf(g.id)
    val expenses = snap.expensesOf(g.id)
    val net = snap.net(g.id)
    var simplify by rememberSaveable { mutableStateOf(true) }
    var settle by remember { mutableStateOf<Transfer?>(null) }
    val transfers = if (simplify) Balances.simplify(net) else Balances.pairwise(expenses)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Balances", style = MaterialTheme.typography.titleMedium) }
        items(members, key = { "m${it.id}" }) { m ->
            val (t, c) = balanceLabel(net[m.id] ?: 0L, g.currency)
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(if (m.isMe) "${m.name} (you)" else m.name)
                Text(t, color = c)
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Who pays whom", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("Simplify debts", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(8.dp))
                Switch(simplify, { simplify = it })
            }
        }
        if (transfers.isEmpty()) item { Text("Everyone is settled up 🎉") }
        items(transfers, key = { "t${it.from}-${it.to}" }) { t ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${snap.memberName(t.from)} owes ${snap.memberName(t.to)} ${Money.format(t.amount, g.currency)}", Modifier.weight(1f))
                    Button({ settle = t }) { Text("Settle up") }
                }
            }
        }
    }
    settle?.let { t -> SettleDialog(vm, snap, g, t) { settle = null } }
}

@Composable
private fun SettleDialog(vm: AppViewModel, snap: AppSnapshot, g: GroupEntity, t: Transfer, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var amount by remember { mutableStateOf(Money.plain(t.amount, g.currency)) }
    var error by remember { mutableStateOf<String?>(null) }
    val receiver = snap.members.firstOrNull { it.id == t.to }
    val parsed = Money.parse(amount, g.currency)
    val links = if (receiver != null && parsed != null && parsed > 0)
        PaymentLinks.forMember(receiver, parsed, g.currency, "Settle up - ${snap.title(g)}") else emptyList()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${snap.memberName(t.from)} pays ${snap.memberName(t.to)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amount, { amount = it; error = null }, label = { Text("Amount (${g.currency})") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                links.forEach { l ->
                    OutlinedButton(onClick = {
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(l.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        catch (e: Exception) { error = "No app found for ${l.label}" }
                    }) { Text(l.label) }
                }
                if (links.isEmpty()) Text("Add UPI / PayPal / Venmo details for ${snap.memberName(t.to)} under Members to get pay links here.", style = MaterialTheme.typography.bodySmall)
                Text("After paying (in an app or in cash), tap “Record payment”.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = Money.parse(amount, g.currency)
                if (a == null || a <= 0) error = "Enter a valid amount" else { vm.settle(g, t.from, t.to, a); onDismiss() }
            }) { Text("Record payment") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StatsTab(snap: AppSnapshot, g: GroupEntity) {
    val expenses = snap.expensesOf(g.id)
    val me = snap.meIn(g.id)
    val cur = g.currency
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Total spending", Money.format(Stats.total(expenses), cur), Modifier.weight(1f))
            if (me != null) StatCard("Your share", Money.format(Stats.myShare(expenses, me.id), cur), Modifier.weight(1f))
        }
        val cats = Stats.byCategory(expenses)
        if (cats.isEmpty()) Text("Add some expenses to see charts.")
        else {
            Text("By category", style = MaterialTheme.typography.titleMedium)
            DonutChart(cats, cur)
            Text("Last 6 months", style = MaterialTheme.typography.titleMedium)
            BarChart(Stats.byMonth(expenses, LocalDate.now().toEpochDay()), cur)
            Text("Who paid", style = MaterialTheme.typography.titleMedium)
            Stats.paidBy(expenses).forEach { (id, v) ->
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(snap.memberName(id)); Text(Money.format(v, cur)) }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun MembersDialog(vm: AppViewModel, snap: AppSnapshot, gid: String, onDismiss: () -> Unit) {
    val members = snap.membersOf(gid)
    var newName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<MemberEntity?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Members") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                members.forEach { m ->
                    val used = snap.expensesOf(gid).any { it.paidById == m.id || Json.decodeLongMap(it.sharesJson).containsKey(m.id) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (m.isMe) "${m.name} (you)" else m.name, Modifier.weight(1f).clickable { editing = m })
                        IconButton({ editing = m }) { Icon(Icons.Default.Edit, "Edit") }
                        IconButton({ vm.removeMember(m) }, enabled = !used && !m.isMe) { Icon(Icons.Default.Delete, "Remove") }
                    }
                }
                Text("Tap a member to edit their name and payment details. Members with expenses can't be removed.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newName, { newName = it }, label = { Text("Add member") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(enabled = newName.isNotBlank(), onClick = { vm.addMember(gid, newName); newName = "" }) { Text("Add") }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
    )
    editing?.let { m ->
        var name by remember(m.id) { mutableStateOf(m.name) }
        var upi by remember(m.id) { mutableStateOf(m.upiId) }
        var paypal by remember(m.id) { mutableStateOf(m.paypalUser) }
        var venmo by remember(m.id) { mutableStateOf(m.venmoUser) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit member") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(upi, { upi = it }, label = { Text("UPI ID (INR groups)") }, singleLine = true)
                    OutlinedTextField(paypal, { paypal = it }, label = { Text("PayPal.me username") }, singleLine = true)
                    OutlinedTextField(venmo, { venmo = it }, label = { Text("Venmo username (USD groups)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vm.updateMember(m.copy(name = name.trim(), upiId = upi.trim(), paypalUser = paypal.trim(), venmoUser = venmo.trim())); editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton({ editing = null }) { Text("Cancel") } },
        )
    }
}
