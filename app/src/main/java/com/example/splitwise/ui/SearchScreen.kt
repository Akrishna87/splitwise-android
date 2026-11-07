@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.splitwise.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitwise.domain.Categories
import com.example.splitwise.domain.Money

@Composable
fun SearchScreen(vm: AppViewModel, onBack: () -> Unit, onOpenExpense: (String) -> Unit) {
    val snap by vm.snap.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf("All") }
    val results = snap.expenses.filter { e ->
        val g = snap.group(e.groupId) ?: return@filter false
        (cat == "All" || e.category == cat) && (query.isBlank() ||
            e.description.contains(query, true) || e.note.contains(query, true) || e.category.contains(query, true) ||
            snap.memberName(e.paidById).contains(query, true) || snap.title(g).contains(query, true))
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Search") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(query, { query = it }, placeholder = { Text("Description, note, person or group") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf("All") + Categories.names + "Payment").forEach { c -> FilterChip(cat == c, { cat = c }, label = { Text(c) }) }
            }
            Text("${results.size} result(s)", Modifier.padding(16.dp), style = MaterialTheme.typography.labelMedium)
            LazyColumn {
                items(results, key = { it.id }) { e ->
                    val g = snap.group(e.groupId)!!
                    ListItem(
                        leadingContent = { Text(Categories.emoji(e.category)) },
                        headlineContent = { Text(if (e.isSettlement) "Payment" else e.description) },
                        supportingContent = { Text("${snap.title(g)} • ${dateLabel(e.date)} • ${snap.memberName(e.paidById)} paid") },
                        trailingContent = { Text(Money.format(e.amount, e.currency)) },
                        modifier = Modifier.clickable { onOpenExpense(e.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
