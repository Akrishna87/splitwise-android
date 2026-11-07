@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.splitwise.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitwise.data.AppSnapshot
import com.example.splitwise.domain.Categories
import com.example.splitwise.domain.Json
import com.example.splitwise.domain.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(vm: AppViewModel, onOpenGroup: (String) -> Unit, onOpenExpense: (String) -> Unit, onSearch: () -> Unit) {
    val snap by vm.snap.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                try { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } } catch (e: Exception) { null }
            }
            if (text != null) vm.importText(text) else vm.message = "Couldn't read that file"
        }
    }
    val onImport = { importLauncher.launch(arrayOf("*/*")) }

    val tabs: List<Pair<String, ImageVector>> = listOf(
        "Groups" to Icons.Default.Group, "Friends" to Icons.Default.Person,
        "Activity" to Icons.Default.History, "Account" to Icons.Default.AccountCircle,
    )
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(tabs[tab].first) }, actions = {
                if (tab == 0) TextButton(onClick = { onImport() }) { Text("Import") }
                IconButton(onSearch) { Icon(Icons.Default.Search, "Search") }
            })
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, label) }, label = { Text(label) })
                }
            }
        },
        floatingActionButton = {
            if (tab <= 1) ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(if (tab == 0) "New group" else "Add friend") },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> GroupList(snap, friends = false, onOpen = onOpenGroup)
                1 -> GroupList(snap, friends = true, onOpen = onOpenGroup)
                2 -> ActivityList(snap, onOpenExpense)
                else -> AccountTab(vm, onImport = { onImport() })
            }
        }
    }

    if (creating) CreateDialog(vm, friend = tab == 1, onDismiss = { creating = false }, onCreated = { creating = false; onOpenGroup(it) })

    vm.message?.let { msg ->
        AlertDialog(onDismissRequest = { vm.message = null }, text = { Text(msg) },
            confirmButton = { TextButton({ vm.message = null }) { Text("OK") } })
    }
    vm.identityPrompt?.let { gid ->
        val g = snap.group(gid)
        val members = snap.membersOf(gid)
        if (g != null && members.isNotEmpty()) {
            var pick by remember(gid) { mutableStateOf(members.first().id) }
            AlertDialog(
                onDismissRequest = { vm.identityPrompt = null },
                title = { Text("Who are you in “${g.name}”?") },
                text = {
                    Column {
                        members.forEach { m ->
                            Row(Modifier.fillMaxWidth().clickable { pick = m.id }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(pick == m.id, { pick = m.id }); Text(m.name)
                            }
                        }
                    }
                },
                confirmButton = { TextButton({ vm.setMe(gid, pick); vm.identityPrompt = null }) { Text("That's me") } },
                dismissButton = { TextButton({ vm.identityPrompt = null }) { Text("Later") } },
            )
        } else vm.identityPrompt = null
    }
}

@Composable
private fun GroupList(snap: AppSnapshot, friends: Boolean, onOpen: (String) -> Unit) {
    val list = snap.groups.filter { (it.kind == "FRIEND") == friends }
    val totals = snap.groups.groupBy { it.currency }.mapValues { (_, gs) -> gs.sumOf { snap.myNet(it.id) } }.filterValues { it != 0L }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Overall", style = MaterialTheme.typography.labelLarge)
                    if (totals.isEmpty()) Text("You are all settled up", style = MaterialTheme.typography.titleMedium)
                    totals.forEach { (cur, n) ->
                        val (t, c) = balanceLabel(n, cur)
                        Text(t, color = c, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        if (list.isEmpty()) item {
            Text(
                if (friends) "No friends yet. Add one to track what you owe each other outside of any group."
                else "No groups yet. Create one for a trip, flat or dinner - or tap Import to open a group a friend shared with you.",
                Modifier.padding(24.dp),
            )
        }
        items(list, key = { it.id }) { g ->
            val (t, c) = balanceLabel(snap.myNet(g.id), g.currency)
            ListItem(
                leadingContent = { Icon(if (friends) Icons.Default.Person else Icons.Default.Group, null) },
                headlineContent = { Text(snap.title(g)) },
                supportingContent = { Text(t, color = c) },
                trailingContent = { Text(g.currency) },
                modifier = Modifier.clickable { onOpen(g.id) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ActivityList(snap: AppSnapshot, onOpen: (String) -> Unit) {
    val recent = snap.expenses.filter { e -> snap.groups.any { it.id == e.groupId } }.sortedByDescending { it.updatedAt }.take(100)
    if (recent.isEmpty()) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Nothing here yet.") }; return }
    LazyColumn(Modifier.fillMaxSize()) {
        items(recent, key = { it.id }) { e ->
            val g = snap.group(e.groupId)!!
            val payer = snap.memberName(e.paidById)
            val title = if (e.isSettlement) "$payer paid ${snap.memberName(Json.decodeLongMap(e.sharesJson).keys.firstOrNull() ?: "")}" else e.description
            val (eff, color) = myEffect(e, snap)
            ListItem(
                leadingContent = { Text(Categories.emoji(e.category)) },
                headlineContent = { Text(title) },
                supportingContent = { Text("${snap.title(g)} • ${dateLabel(e.date)}" + if (eff.isNotEmpty()) " • $eff" else "", color = color) },
                trailingContent = { Text(Money.format(e.amount, e.currency)) },
                modifier = Modifier.clickable { onOpen(e.id) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun AccountTab(vm: AppViewModel, onImport: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(vm.userName) }
    var currency by remember { mutableStateOf(vm.defaultCurrency) }
    var saved by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.updateReminders(it) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(name, { name = it; saved = false }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        DropdownField("Default currency", Money.currencies, currency, Modifier.fillMaxWidth()) { currency = it; saved = false }
        Button({ vm.saveProfile(name, currency); saved = true }) { Text(if (saved) "Saved ✓" else "Save profile") }
        Text("New groups use this name for you and this currency by default.", style = MaterialTheme.typography.bodySmall)

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Daily reminders", style = MaterialTheme.typography.titleMedium)
                Text("Get a notification when you have unsettled balances.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(vm.reminders, { on ->
                if (on && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.updateReminders(on)
            })
        }

        HorizontalDivider()
        Text("Backup & sharing", style = MaterialTheme.typography.titleMedium)
        Text(
            "Everything is stored on this phone. To share a group, open it and choose Share group; your friend taps Import. " +
                "Edits from either side merge automatically (the newest change wins), so you can keep sending updates back and forth.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ scope.launch { ctx.shareFile("splitwise-backup.json", "application/json", vm.exportBackup()) } }) { Text("Export backup") }
            OutlinedButton(onImport) { Text("Import file") }
        }
    }
}

@Composable
private fun CreateDialog(vm: AppViewModel, friend: Boolean, onDismiss: () -> Unit, onCreated: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var members by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(vm.defaultCurrency) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (friend) "Add friend" else "New group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(if (friend) "Friend's name" else "Group name") }, singleLine = true)
                if (!friend) OutlinedTextField(members, { members = it }, label = { Text("Other members (comma separated)") })
                DropdownField("Currency", Money.currencies, currency) { currency = it }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                if (friend) vm.createGroup(name, currency, "FRIEND", listOf(name), onCreated)
                else vm.createGroup(name, currency, "GROUP", members.split(","), onCreated)
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
