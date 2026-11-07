@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.splitwise.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitwise.data.*
import com.example.splitwise.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

private data class ItemRow(val name: String, val price: String, val to: Set<String>)

@Composable
fun ExpenseFormScreen(vm: AppViewModel, gid: String, eid: String, onDone: () -> Unit) {
    val snap by vm.snap.collectAsStateWithLifecycle()
    val group = snap.group(gid)
    val members = snap.membersOf(gid)
    val existing = snap.expenses.firstOrNull { it.id == eid }
    if (group == null || members.isEmpty() || (eid.isNotEmpty() && existing == null)) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    ExpenseForm(vm, group, members, existing, onDone)
}

@Composable
private fun ExpenseForm(vm: AppViewModel, group: GroupEntity, members: List<MemberEntity>, existing: ExpenseEntity?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val inputs0 = remember { existing?.let { Json.decodeStringMap(it.inputsJson) } ?: emptyMap() }
    val me = members.firstOrNull { it.isMe } ?: members.first()

    var desc by remember { mutableStateOf(existing?.description ?: "") }
    var currency by remember { mutableStateOf(existing?.currency ?: group.currency) }
    var amountText by remember { mutableStateOf(existing?.let { Money.plain(it.amount, it.currency) } ?: "") }
    var rateText by remember {
        mutableStateOf(existing?.takeIf { it.currency != group.currency }?.let { BigDecimal.valueOf(it.rate).toPlainString() } ?: "")
    }
    var rateNote by remember { mutableStateOf<String?>(null) }
    var date by remember { mutableLongStateOf(existing?.date ?: LocalDate.now().toEpochDay()) }
    var category by remember { mutableStateOf(existing?.category ?: "General") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var payer by remember { mutableStateOf(existing?.paidById ?: me.id) }
    var type by remember { mutableStateOf(existing?.let { runCatching { SplitType.valueOf(it.splitType) }.getOrNull() } ?: SplitType.EQUAL) }
    var included by remember { mutableStateOf<Set<String>>(if (inputs0.isNotEmpty()) inputs0.keys.toSet() else members.map { it.id }.toSet()) }
    var values by remember { mutableStateOf<Map<String, String>>(inputs0) }
    val items0: List<ItemRow> = remember {
        existing?.let { e -> Json.decodeItems(e.itemsJson).map { ItemRow(it.name, Money.plain(it.priceMinor, e.currency), it.assignedTo) } } ?: emptyList()
    }
    var items by remember { mutableStateOf<List<ItemRow>>(items0) }
    var receipt by remember { mutableStateOf(existing?.receiptPath ?: "") }
    var repeat by remember { mutableStateOf(Interval.NONE) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var pendingCamera by remember { mutableStateOf<File?>(null) }

    // Live exchange rate whenever the expense currency differs from the group currency.
    LaunchedEffect(currency) {
        if (currency == group.currency) return@LaunchedEffect
        if (existing != null && currency == existing.currency && rateText.isNotBlank()) return@LaunchedEffect
        rateNote = "Fetching rate…"
        val r = vm.fetchRate(currency, group.currency)
        if (r != null) { rateText = BigDecimal.valueOf(r).toPlainString(); rateNote = "Live rate (editable)" }
        else rateNote = "Couldn't fetch a rate - please enter one"
    }

    fun processReceipt(file: File) {
        receipt = file.absolutePath
        scanning = true
        scope.launch {
            val res = try { ReceiptScanner.scan(ctx, file) } catch (e: Exception) { null }
            scanning = false
            if (res != null && res.items.isNotEmpty()) {
                items = res.items.map { ItemRow(it.first, Money.plain(it.second, currency), emptySet()) }
                val tot = res.total ?: res.items.sumOf { it.second }
                if (res.total != null || amountText.isBlank()) amountText = Money.plain(tot, currency)
                type = SplitType.ITEMS
                info = "Found ${res.items.size} items. Check them, then tap people under each item to assign it."
            } else {
                info = "Couldn't read items from this photo. The receipt is attached; you can add items manually."
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingCamera
        if (ok && f != null) processReceipt(f)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val f = withContext(Dispatchers.IO) { copyToReceipts(ctx, uri) }
            if (f != null) processReceipt(f) else info = "Couldn't open that image"
        }
    }

    fun save() {
        error = null
        try {
            require(desc.isNotBlank()) { "Enter a description" }
            val amount = Money.parse(amountText, currency) ?: throw IllegalArgumentException("Enter a valid amount")
            val rate = if (currency == group.currency) 1.0
            else rateText.trim().replace(',', '.').toDoubleOrNull() ?: throw IllegalArgumentException("Enter a valid exchange rate")
            val ids = members.map { it.id }.filter { it in included }
            val itemInputs = items.map { r ->
                ItemInput(
                    r.name.ifBlank { "Item" },
                    Money.parse(r.price.ifBlank { "0" }, currency) ?: throw IllegalArgumentException("Invalid price for “${r.name}”"),
                    r.to,
                )
            }
            val res = Splits.compute(SplitInput(type, amount, currency, group.currency, rate, if (type == SplitType.ITEMS) emptyList() else ids, values, itemInputs))
            val stored: Map<String, String> = when (type) {
                SplitType.ITEMS -> emptyMap()
                SplitType.EQUAL -> ids.associateWith { "1" }
                else -> ids.associateWith { values[it].orEmpty() }
            }
            val now = System.currentTimeMillis()
            val id = existing?.id ?: UUID.randomUUID().toString()
            vm.saveExpense(
                ExpenseEntity(
                    id = id, groupId = group.id, description = desc.trim(), amount = amount, currency = currency, rate = rate,
                    baseAmount = res.baseAmount, paidById = payer, category = category, note = note.trim(), date = date,
                    splitType = type.name, sharesJson = Json.encodeLongMap(res.shares), inputsJson = Json.encodeStringMap(stored),
                    itemsJson = Json.encodeItems(itemInputs), receiptPath = receipt, recurringId = existing?.recurringId ?: "",
                    createdAt = existing?.createdAt ?: now, updatedAt = now,
                )
            )
            if (existing == null && repeat != Interval.NONE) {
                vm.saveRecurring(
                    RecurringEntity(
                        id = UUID.randomUUID().toString(), groupId = group.id, description = desc.trim(), amount = amount, currency = currency,
                        rate = rate, baseAmount = res.baseAmount, paidById = payer, category = category, note = note.trim(),
                        splitType = type.name, sharesJson = Json.encodeLongMap(res.shares), inputsJson = Json.encodeStringMap(stored),
                        itemsJson = Json.encodeItems(itemInputs), interval = repeat.name, nextDue = Recurrence.next(date, repeat), updatedAt = now,
                    )
                )
            }
            onDone()
        } catch (e: IllegalArgumentException) {
            error = e.message
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (existing == null) "Add expense" else "Edit expense") },
            navigationIcon = { IconButton(onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { TextButton(::save) { Text("Save") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    amountText, { amountText = it }, label = { Text("Amount") }, singleLine = true,
                    prefix = { Text(Money.symbol(currency)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                )
                DropdownField("Currency", Money.currencies, currency, Modifier.width(130.dp)) { currency = it }
            }
            if (currency != group.currency) {
                OutlinedTextField(
                    rateText, { rateText = it }, singleLine = true,
                    label = { Text("1 $currency = ? ${group.currency}") },
                    supportingText = {
                        val a = Money.parse(amountText, currency); val r = rateText.replace(',', '.').toDoubleOrNull()
                        Text(listOfNotNull(rateNote, if (a != null && r != null) "≈ ${Money.format(Money.convert(a, currency, group.currency, r), group.currency)}" else null).joinToString(" • "))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton({ showDate = true }, Modifier.weight(1f)) { Text(dateLabel(date)) }
                DropdownField("Category", Categories.names, category, Modifier.weight(1f), display = { Categories.emoji(it) + " " + it }) { category = it }
            }
            OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())

            Text("Paid by", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                members.forEach { m -> FilterChip(payer == m.id, { payer = m.id }, label = { Text(m.name) }) }
            }

            // ---- receipt
            Text("Receipt", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({
                    val f = newReceiptFile(ctx); pendingCamera = f; camera.launch(receiptUri(ctx, f))
                }) { Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Scan") }
                OutlinedButton({ gallery.launch("image/*") }) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp)); Text("Choose photo") }
                if (receipt.isNotEmpty()) TextButton({ receipt = "" }) { Text("Remove") }
            }
            if (scanning) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Reading receipt…") }
            info?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (receipt.isNotEmpty()) ReceiptImage(receipt, Modifier.fillMaxWidth().height(160.dp))

            // ---- split
            Text("Split", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SplitType.values().forEach { t -> FilterChip(type == t, { type = t }, label = { Text(t.label) }) }
            }
            Text(type.help, style = MaterialTheme.typography.bodySmall)

            if (type == SplitType.ITEMS) {
                items.forEachIndexed { idx, row ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(row.name, { v -> items = items.toMutableList().also { it[idx] = row.copy(name = v) } },
                                    label = { Text("Item") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(row.price, { v -> items = items.toMutableList().also { it[idx] = row.copy(price = v) } },
                                    label = { Text("Price") }, singleLine = true, modifier = Modifier.width(110.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                                IconButton({ items = items.toMutableList().also { it.removeAt(idx) } }) { Icon(Icons.Default.Close, "Remove item") }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                members.forEach { m ->
                                    FilterChip(m.id in row.to, {
                                        val n = if (m.id in row.to) row.to - m.id else row.to + m.id
                                        items = items.toMutableList().also { it[idx] = row.copy(to = n) }
                                    }, label = { Text(m.name) })
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ items = items + ItemRow("", "", emptySet()) }) { Icon(Icons.Default.Add, null); Text("Add item") }
                    OutlinedButton({ items = items.map { it.copy(to = members.map { m -> m.id }.toSet()) } }) { Text("Everyone shares all") }
                }
                val subtotal = items.sumOf { Money.parse(it.price.ifBlank { "0" }, currency) ?: 0L }
                val total = Money.parse(amountText, currency) ?: 0L
                Text(
                    "Items ${Money.format(subtotal, currency)} of total ${Money.format(total, currency)}" +
                        if (total > subtotal) " - the extra ${Money.format(total - subtotal, currency)} (tax/tip) is shared in proportion." else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                members.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(m.id in included, { c -> included = if (c) included + m.id else included - m.id })
                        Text(m.name, Modifier.weight(1f))
                        if (type != SplitType.EQUAL && m.id in included) {
                            OutlinedTextField(
                                values[m.id] ?: "", { v -> values = values + (m.id to v) }, singleLine = true,
                                label = { Text(when (type) { SplitType.PERCENT -> "%"; SplitType.SHARES -> "shares"; SplitType.ADJUST -> "+/-"; else -> Money.symbol(currency) }) },
                                keyboardOptions = KeyboardOptions(keyboardType = if (type == SplitType.ADJUST) KeyboardType.Text else KeyboardType.Decimal),
                                modifier = Modifier.width(120.dp),
                            )
                        }
                    }
                }
                splitPreview(type, amountText, currency, included, values, members)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }

            if (existing == null) {
                DropdownField("Repeat", Interval.values().toList().map { it.name }, repeat.name, Modifier.fillMaxWidth(), display = { Interval.valueOf(it).label }) { repeat = Interval.valueOf(it) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(::save, Modifier.fillMaxWidth()) { Text("Save expense") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton({ state.selectedDateMillis?.let { date = Math.floorDiv(it, 86_400_000L) }; showDate = false }) { Text("OK") } },
            dismissButton = { TextButton({ showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

/** Short "remaining" hint so exact / percent splits are easy to balance. */
private fun splitPreview(type: SplitType, amountText: String, currency: String, included: Set<String>, values: Map<String, String>, members: List<MemberEntity>): String? {
    val ids = members.map { it.id }.filter { it in included }
    return when (type) {
        SplitType.EXACT -> {
            val total = Money.parse(amountText, currency) ?: return null
            val used = ids.sumOf { Money.parse((values[it] ?: "").ifBlank { "0" }, currency) ?: 0L }
            "Remaining: ${Money.format(total - used, currency)}"
        }
        SplitType.PERCENT -> {
            val used = ids.sumOf { (values[it] ?: "").replace(',', '.').toDoubleOrNull() ?: 0.0 }
            "Total: ${used}% (needs 100%)"
        }
        else -> null
    }
}

@Composable
fun ReceiptImage(path: String, modifier: Modifier) {
    val bmp = remember(path) {
        try {
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
            android.graphics.BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
        } catch (e: Exception) { null }
    }
    if (bmp != null) Image(bmp, "Receipt", modifier, contentScale = ContentScale.Fit)
}
