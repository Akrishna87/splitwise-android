@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.splitwise.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitwise.domain.Interval
import com.example.splitwise.domain.Money

@Composable
fun RecurringScreen(vm: AppViewModel, gid: String, onBack: () -> Unit) {
    val snap by vm.snap.collectAsStateWithLifecycle()
    val list = snap.recurring.filter { it.groupId == gid }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Recurring expenses") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { pad ->
        Column(Modifier.padding(pad)) {
            Text(
                "To add one, create an expense and choose Repeat (daily, weekly, monthly or yearly). New copies are added automatically when they fall due.",
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
            )
            if (list.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No recurring expenses.") }
            LazyColumn {
                items(list, key = { it.id }) { r ->
                    val interval = runCatching { Interval.valueOf(r.interval).label }.getOrDefault(r.interval)
                    ListItem(
                        headlineContent = { Text(r.description) },
                        supportingContent = { Text("$interval • ${Money.format(r.amount, r.currency)} • next ${dateLabel(r.nextDue)} • paid by ${snap.memberName(r.paidById)}") },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(r.active, { vm.saveRecurring(r.copy(active = it)) })
                                IconButton({ vm.deleteRecurring(r) }) { Icon(Icons.Default.Delete, "Delete") }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
