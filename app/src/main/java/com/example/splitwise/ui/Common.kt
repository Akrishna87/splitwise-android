@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.splitwise.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import com.example.splitwise.data.AppSnapshot
import com.example.splitwise.data.ExpenseEntity
import com.example.splitwise.domain.Json
import com.example.splitwise.domain.Money
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

fun dateLabel(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("d MMM yyyy"))

/** "you owe $5" / "you are owed $5" / "settled up" with a colour. */
fun balanceLabel(net: Long, currency: String): Pair<String, Color> = when {
    net > 0 -> "you are owed ${Money.format(net, currency)}" to Green
    net < 0 -> "you owe ${Money.format(-net, currency)}" to Red
    else -> "settled up" to Color.Gray
}

/** What this expense means for "me": lent / borrowed / not involved. */
fun myEffect(e: ExpenseEntity, s: AppSnapshot): Pair<String, Color> {
    val me = s.meIn(e.groupId) ?: return "" to Color.Gray
    val cur = s.group(e.groupId)?.currency ?: e.currency
    val shares = Json.decodeLongMap(e.sharesJson)
    val mine = shares[me.id] ?: 0L
    return when {
        e.paidById == me.id && e.isSettlement -> "you paid ${Money.format(e.baseAmount, cur)}" to Green
        e.paidById == me.id -> if (e.baseAmount - mine > 0) "you lent ${Money.format(e.baseAmount - mine, cur)}" to Green else "" to Color.Gray
        mine > 0 && e.isSettlement -> "you received ${Money.format(mine, cur)}" to Green
        mine > 0 -> "you borrowed ${Money.format(mine, cur)}" to Red
        else -> "not involved" to Color.Gray
    }
}

fun Context.shareFile(name: String, mime: String, text: String) {
    val dir = File(cacheDir, "exports").apply { mkdirs() }
    val f = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
    f.writeText(text)
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun receiptsDir(ctx: Context) = File(ctx.filesDir, "receipts").apply { mkdirs() }
fun newReceiptFile(ctx: Context) = File(receiptsDir(ctx), "${UUID.randomUUID()}.jpg")
fun receiptUri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)

/** Copies a picked image into app storage so it survives the source disappearing. */
fun copyToReceipts(ctx: Context, uri: Uri): File? = try {
    val f = newReceiptFile(ctx)
    ctx.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
    if (f.length() > 0) f else null
} catch (e: Exception) { null }

@Composable
fun DropdownField(
    label: String,
    options: List<String>,
    selected: String,
    modifier: Modifier = Modifier,
    display: (String) -> String = { it },
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = display(selected), onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(display(o)) }, onClick = { onSelect(o); open = false }) }
        }
    }
}
