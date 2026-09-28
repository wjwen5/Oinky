@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.moodledger.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moodledger.app.data.TxnDraft
import com.moodledger.core.Category
import com.moodledger.core.Frequency
import com.moodledger.core.TxnType
import java.io.File
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

/**
 * Create / edit a money record. [onSave] receives the draft and an optional repeat frequency;
 * choosing a repeat turns the entry into an auto-recorded recurring payment.
 */
@Composable
fun TransactionEditorDialog(
    initial: TxnDraft,
    title: String,
    mainCurrency: String,
    onDismiss: () -> Unit,
    onSave: (TxnDraft, Frequency?) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var type by remember { mutableStateOf(initial.type) }
    var amountText by remember {
        mutableStateOf(if (initial.amount.signum() == 0) "" else initial.amount.stripTrailingZeros().toPlainString())
    }
    var currency by remember { mutableStateOf(initial.currency) }
    var category by remember { mutableStateOf(initial.category) }
    var note by remember { mutableStateOf(initial.note) }
    var merchant by remember { mutableStateOf(initial.merchant.orEmpty()) }
    var repeat by remember { mutableStateOf<Frequency?>(null) }

    val amount = amountText.replace(",", "").toBigDecimalOrNull()
    val valid = amount != null && amount > BigDecimal.ZERO

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    initial.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                initial.receiptPath?.let { path ->
                    AsyncImage(
                        model = File(path), contentDescription = "Receipt",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TxnType.entries.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = type == t,
                            onClick = {
                                type = t
                                if (category.type != t) category = if (t == TxnType.INCOME) Category.OTHER_INCOME else Category.OTHER
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, TxnType.entries.size),
                        ) { Text(if (t == TxnType.EXPENSE) "Expense" else "Income") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("Amount") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    CurrencyPicker(currency, { currency = it }, extra = listOf(mainCurrency))
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, label = { Text("What for?") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = merchant, onValueChange = { merchant = it }, label = { Text("Where (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Category.entries.filter { it.type == type }.forEach { c ->
                        FilterChip(
                            selected = c == category,
                            onClick = { category = c },
                            label = { Text("${c.emoji} ${c.label}") },
                        )
                    }
                }
                if (initial.id == 0L && initial.recurringId == null) {
                    Text("Repeat", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = repeat == null, onClick = { repeat = null }, label = { Text("Once") })
                        listOf(Frequency.WEEKLY, Frequency.MONTHLY, Frequency.YEARLY).forEach { f ->
                            FilterChip(selected = repeat == f, onClick = { repeat = f }, label = { Text(f.label) })
                        }
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Delete entry", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        initial.copy(
                            type = type, amount = amount!!, currency = currency, category = category,
                            note = note, merchant = merchant.ifBlank { null },
                        ),
                        repeat,
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
