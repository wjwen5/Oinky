@file:OptIn(ExperimentalMaterial3Api::class)

package com.moodledger.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.moodledger.app.ui.components.compact
import com.moodledger.app.ui.components.containerViewModel
import com.moodledger.app.ui.components.money
import com.moodledger.app.ui.theme.incomeColor
import java.io.File
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(onOpenDay: (LocalDate) -> Unit) {
    val vm = containerViewModel { CalendarViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshMissed() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (s.mode) {
                            ViewMode.MONTH -> s.anchor.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                            ViewMode.WEEK -> "${s.gridStart.format(DateTimeFormatter.ofPattern("d MMM"))} – " +
                                s.gridEnd.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
                        },
                    )
                },
                navigationIcon = { IconButton(onClick = vm::previous) { Icon(Icons.Filled.ChevronLeft, "Previous") } },
                actions = {
                    IconButton(onClick = vm::today) { Icon(Icons.Filled.Today, "Today") }
                    IconButton(onClick = vm::next) { Icon(Icons.Filled.ChevronRight, "Next") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenDay(LocalDate.now()) },
                icon = { Icon(Icons.Filled.Edit, null) },
                text = { Text("Write today") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    ViewMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = s.mode == m, onClick = { vm.setMode(m) },
                            shape = SegmentedButtonDefaults.itemShape(i, ViewMode.entries.size),
                        ) { Text(if (m == ViewMode.MONTH) "Month" else "Week") }
                    }
                }
            }
            items(s.missed, key = { "${it.rule.id}-${it.due}" }) { item ->
                MissedBanner(item, onRecord = { vm.record(item) }, onDismiss = { vm.dismiss(item) })
            }
            item { SummaryCard(s) }
            when (s.mode) {
                ViewMode.MONTH -> item { MonthGrid(s, onOpenDay) }
                ViewMode.WEEK -> items(s.cells.values.sortedBy { it.date }, key = { it.date.toEpochDay() }) { cell ->
                    WeekRow(cell, s.mainCurrency, onClick = { onOpenDay(cell.date) })
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun MissedBanner(item: MissedItem, onRecord: () -> Unit, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    "${item.rule.category.emoji} ${item.rule.name} · ${money(item.rule.amount, item.rule.currency)}",
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    "Due ${item.due.format(DateTimeFormatter.ofPattern("d MMM"))} · not recorded (${item.daysOverdue}d)",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(onClick = onDismiss) { Text("Skip") }
            TextButton(onClick = onRecord) { Text("Record") }
        }
    }
}

@Composable
private fun SummaryCard(s: CalendarState) {
    val sum = s.summary
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(sum.averageMood?.emoji ?: "📔", fontSize = 40.sp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    "Spent ${money(sum.spent, s.mainCurrency)}${if (sum.hasUnconverted) "*" else ""}",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                )
                if (sum.income.signum() > 0) {
                    Text("Income ${money(sum.income, s.mainCurrency)}", color = incomeColor(), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    buildString {
                        append(if (sum.moodDays > 0) "${sum.averageMood?.label ?: ""} on average · ${sum.moodDays} days logged" else "No moods logged yet")
                        sum.topCategory?.let { append(" · most on ${it.emoji} ${it.label}") }
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (sum.hasUnconverted) {
                    Text("* some entries await an exchange rate", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(s: CalendarState, onOpenDay: (LocalDate) -> Unit) {
    val month = s.anchor.monthValue
    val maxSpent = s.cells.values.filter { it.date.monthValue == month }.maxOfOrNull { it.spent } ?: BigDecimal.ZERO
    Column(Modifier.padding(horizontal = 8.dp)) {
        Row {
            DayOfWeek.entries.forEach {
                Text(
                    it.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        s.cells.values.sortedBy { it.date }.chunked(7).forEach { week ->
            Row {
                week.forEach { cell ->
                    MonthCell(
                        cell, inMonth = cell.date.monthValue == month, maxSpent = maxSpent,
                        modifier = Modifier.weight(1f), onClick = { onOpenDay(cell.date) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthCell(cell: DayCell, inMonth: Boolean, maxSpent: BigDecimal, modifier: Modifier, onClick: () -> Unit) {
    val isToday = cell.date == LocalDate.now()
    // Spending heat: tint the cell more strongly on heavier spending days.
    val heat = if (maxSpent.signum() > 0) (cell.spent.toFloat() / maxSpent.toFloat()).coerceIn(0f, 1f) else 0f
    val bg = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f + 0.18f * heat)
    Column(
        modifier
            .aspectRatio(0.72f)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (inMonth) bg else MaterialTheme.colorScheme.surface)
            .then(if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        val faded = if (inMonth) 1f else 0.35f
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${cell.date.dayOfMonth}", style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = faded),
            )
            cell.tripMark?.let { Text(it, fontSize = 9.sp, modifier = Modifier.padding(start = 1.dp)) }
        }
        Text(cell.mood?.emoji ?: if (cell.notePreview.isNotEmpty() || cell.photoPath != null) "📝" else "", fontSize = 22.sp)
        Text(
            if (cell.spent.signum() > 0) compact(cell.spent) else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = faded),
            maxLines = 1,
        )
    }
}

@Composable
private fun WeekRow(cell: DayCell, currency: String, onClick: () -> Unit) {
    val isToday = cell.date == LocalDate.now()
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        border = if (isToday) CardDefaults.outlinedCardBorder() else null,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(cell.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.labelSmall)
                Text("${cell.date.dayOfMonth}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                cell.tripMark?.let { Text(it, fontSize = 14.sp) }
            }
            Text(cell.mood?.emoji ?: "·", fontSize = 32.sp, modifier = Modifier.padding(horizontal = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    cell.notePreview.ifEmpty { "Nothing written yet" },
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (cell.notePreview.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cell.spent.signum() > 0) Text("−${money(cell.spent, currency)}", style = MaterialTheme.typography.labelMedium)
                    if (cell.income.signum() > 0) Text("+${money(cell.income, currency)}", style = MaterialTheme.typography.labelMedium, color = incomeColor())
                }
            }
            cell.photoPath?.let {
                Box(Modifier.padding(start = 8.dp)) {
                    AsyncImage(
                        model = File(it), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
        }
    }
}
