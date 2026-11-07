@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.splitwise.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitwise.domain.*
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ExpenseDetailScreen(vm: AppViewModel, eid: String, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val snap by vm.snap.collectAsStateWithLifecycle()
    val e = snap.expenses.firstOrNull { it.id == eid }
    val comments by remember(eid) { vm.comments(eid) }.collectAsStateWithLifecycle(emptyList())
    var confirm by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (e?.isSettlement == true) "Payment" else "Expense") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                if (e != null && !e.isSettlement) IconButton({ onEdit(e.groupId) }) { Icon(Icons.Default.Edit, "Edit") }
                if (e != null) IconButton({ confirm = true }) { Icon(Icons.Default.Delete, "Delete") }
            },
        )
    }) { pad ->
        if (e == null) { Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) { Text("This expense no longer exists.") }; return@Scaffold }
        val g = snap.group(e.groupId)
        val cur = g?.currency ?: e.currency
        val shares = Json.decodeLongMap(e.sharesJson)
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${Categories.emoji(e.category)} ${if (e.isSettlement) "Payment" else e.description}", style = MaterialTheme.typography.headlineSmall)
            Text(Money.format(e.amount, e.currency), style = MaterialTheme.typography.displaySmall)
            if (e.currency != cur) Text("≈ ${Money.format(e.baseAmount, cur)} at 1 ${e.currency} = ${e.rate} $cur", style = MaterialTheme.typography.bodyMedium)
            Text("${dateLabel(e.date)} • ${g?.let { snap.title(it) } ?: ""} • ${if (e.isSettlement) "" else e.category}")
            if (e.recurringId.isNotEmpty()) Text("🔁 Recurring expense", style = MaterialTheme.typography.bodySmall)
            if (e.note.isNotBlank()) Text("Note: ${e.note}")
            Text("${snap.memberName(e.paidById)} paid ${Money.format(e.baseAmount, cur)}", style = MaterialTheme.typography.titleMedium)

            HorizontalDivider()
            Text(if (e.isSettlement) "Received by" else "Split (${runCatching { SplitType.valueOf(e.splitType).label }.getOrDefault("")})", style = MaterialTheme.typography.titleSmall)
            shares.forEach { (id, v) -> Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(snap.memberName(id)); Text(Money.format(v, cur)) } }

            val items = Json.decodeItems(e.itemsJson)
            if (items.isNotEmpty()) {
                HorizontalDivider()
                Text("Items", style = MaterialTheme.typography.titleSmall)
                items.forEach { i ->
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) { Text(i.name); Text(i.assignedTo.joinToString { snap.memberName(it) }, style = MaterialTheme.typography.bodySmall) }
                        Text(Money.format(i.priceMinor, e.currency))
                    }
                }
            }
            if (e.receiptPath.isNotEmpty() && File(e.receiptPath).exists()) {
                HorizontalDivider()
                Text("Receipt", style = MaterialTheme.typography.titleSmall)
                ReceiptImage(e.receiptPath, Modifier.fillMaxWidth().heightIn(max = 360.dp))
            }

            HorizontalDivider()
            Text("Comments", style = MaterialTheme.typography.titleSmall)
            if (comments.isEmpty()) Text("No comments yet.", style = MaterialTheme.typography.bodySmall)
            comments.forEach { c ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.author + " • " + Instant.ofEpochMilli(c.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm")), style = MaterialTheme.typography.labelSmall)
                        Text(c.text)
                    }
                    TextButton({ vm.deleteComment(c) }) { Text("Delete") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, placeholder = { Text("Add a comment") }, modifier = Modifier.weight(1f))
                TextButton(enabled = text.isNotBlank(), onClick = { vm.addComment(e.groupId, e.id, text); text = "" }) { Text("Post") }
            }
        }
        if (confirm) AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete this ${if (e.isSettlement) "payment" else "expense"}?") },
            text = { Text("Balances will be recalculated.") },
            confirmButton = { TextButton({ confirm = false; vm.deleteExpense(e); onBack() }) { Text("Delete") } },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
        )
    }
}
