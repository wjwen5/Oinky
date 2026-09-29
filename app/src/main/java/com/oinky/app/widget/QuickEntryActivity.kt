@file:OptIn(FlowPreview::class)

package com.oinky.app.widget

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.oinky.app.MainActivity
import com.oinky.app.OinkyApp
import com.oinky.app.data.QuickPreview
import com.oinky.app.ui.theme.OinkyTheme
import com.oinky.app.work.Notifications
import com.oinky.core.Currencies
import com.oinky.core.TxnType
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Floating quick-entry card opened from the home-screen widget. It floats over the launcher,
 * saves with the same parser as the in-app bar ("rm135 on dinner"), then closes.
 */
class QuickEntryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val c = (application as OinkyApp).container
        val today = LocalDate.now()

        setContent {
            OinkyTheme {
                var text by remember { mutableStateOf("") }
                var preview by remember { mutableStateOf<QuickPreview?>(null) }
                var error by remember { mutableStateOf<String?>(null) }
                var saving by remember { mutableStateOf(false) }
                val focus = remember { FocusRequester() }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    focus.requestFocus()
                    snapshotFlow { text }.debounce(120).collectLatest { t ->
                        preview = if (t.isBlank()) null else c.ledger.quickPreview(t, today)
                    }
                }

                fun submit() {
                    if (saving || text.isBlank()) return
                    saving = true
                    scope.launch {
                        val result = c.ledger.addQuick(text, today)
                        if (result == null) {
                            error = "Couldn't find an amount — try “rm135 on dinner”"
                            saving = false
                        } else {
                            val p = result.first
                            Toast.makeText(
                                this@QuickEntryActivity,
                                "Added ${p.category.emoji} ${p.note.ifBlank { p.category.label }} · ${Currencies.format(p.amount, p.currency)}",
                                Toast.LENGTH_SHORT,
                            ).show()
                            finish()
                        }
                    }
                }

                // Tapping the dimmed area outside the card dismisses it.
                Box(
                    Modifier.fillMaxSize().clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                    ) { finish() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Card(
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        modifier = Modifier.fillMaxWidth().imePadding()
                            // Swallow taps so they don't reach the dismiss area behind the card.
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    ) {
                        Column(
                            Modifier.navigationBarsPadding().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "🐷 Quick entry · ${today.format(DateTimeFormatter.ofPattern("EEE d MMM"))}",
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            )
                            val err = error
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it; error = null },
                                placeholder = { Text("rm135 on dinner") },
                                singleLine = true,
                                isError = err != null,
                                supportingText = if (err != null) ({ Text(err) }) else null,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { submit() }),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            )
                            preview?.let { p ->
                                val e = p.parsed
                                AssistChip(
                                    onClick = { submit() },
                                    label = {
                                        Text(
                                            buildString {
                                                append("${e.category.emoji} ${e.category.label} · ")
                                                if (e.type == TxnType.INCOME) append("+")
                                                append(Currencies.format(e.amount, e.currency))
                                                if (e.currency != p.mainCurrency && p.inMain != null) {
                                                    append(" ≈ ${Currencies.format(p.inMain, p.mainCurrency)}")
                                                }
                                                if (e.date != today) append(" · ${e.date.format(DateTimeFormatter.ofPattern("d MMM"))}")
                                            },
                                        )
                                    },
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { openToday(); finish() }) { Text("Open Oinky") }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { finish() }) { Text("Cancel") }
                                Button(onClick = { submit() }, enabled = text.isNotBlank() && !saving) { Text("Add") }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openToday() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(Notifications.EXTRA_DESTINATION, "today")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }
}
