@file:OptIn(ExperimentalMaterial3Api::class)

package com.oinky.app.ui.calendar

import androidx.compose.foundation.background
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.oinky.app.ui.components.AmountText
import com.oinky.app.ui.components.HeroCard
import com.oinky.app.ui.components.MoodIcon
import com.oinky.app.ui.components.PaperCard
import com.oinky.app.ui.components.StickerImage
import com.oinky.app.ui.components.compact
import com.oinky.app.ui.components.containerViewModel
import com.oinky.app.ui.components.money
import com.oinky.app.ui.components.moodFace
import com.oinky.app.ui.theme.Tokens
import com.oinky.core.TxnType
import java.io.File
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val gutter = Tokens.Space.gutter

@Composable
fun CalendarScreen(onOpenDay: (LocalDate) -> Unit) {
    val vm = containerViewModel { CalendarViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshMissed() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column {
                        Text(
                            when (s.mode) {
                                ViewMode.MONTH -> s.anchor.format(DateTimeFormatter.ofPattern("MMMM"))
                                ViewMode.WEEK -> "Week of ${s.gridStart.format(DateTimeFormatter.ofPattern("d MMM"))}"
                            },
                            style = MaterialTheme.typography.headlineLarge,
                        )
                        Text(
                            "${s.anchor.year} · 🐷 Oinky",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = vm::previous) { Icon(Icons.Filled.ChevronLeft, "Previous") }
                    IconButton(onClick = vm::today) { Icon(Icons.Filled.Today, "Today") }
                    IconButton(onClick = vm::next) { Icon(Icons.Filled.ChevronRight, "Next") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenDay(LocalDate.now()) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Edit, null) },
                text = { Text("Write today") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.md),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = gutter)) {
                    ViewMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = s.mode == m, onClick = { vm.setMode(m) },
                            shape = SegmentedButtonDefaults.itemShape(i, ViewMode.entries.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            ),
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
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

/** DESIGN.md `banner-reminder`. */
@Composable
private fun MissedBanner(item: MissedItem, onRecord: () -> Unit, onDismiss: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = gutter),
    ) {
        Column(Modifier.padding(start = Tokens.Space.md, top = Tokens.Space.md, end = Tokens.Space.sm, bottom = Tokens.Space.xs)) {
            Text(
                "${item.rule.category.emoji} Did you pay ${item.rule.name}?",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${money(item.rule.amount, item.rule.currency)} was due ${item.due.format(DateTimeFormatter.ofPattern("d MMM"))} " +
                    "— ${item.daysOverdue} days ago, and there's no entry yet.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("Skip this one") }
                TextButton(onClick = onRecord) { Text("Record it") }
            }
        }
    }
}

/** DESIGN.md `card-hero`: average mood face and the period's spend. */
@Composable
private fun SummaryCard(s: CalendarState) {
    val sum = s.summary
    HeroCard(Modifier.fillMaxWidth().padding(horizontal = gutter)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoodIcon(sum.averageMood?.score, 56.dp, fallback = "📔")
            Column(Modifier.weight(1f).padding(start = Tokens.Space.md)) {
                Text(
                    if (s.mode == ViewMode.MONTH) "Spent this month" else "Spent this week",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    money(sum.spent, s.mainCurrency) + if (sum.hasUnconverted) "*" else "",
                    style = Tokens.Type.amountLg,
                )
                if (sum.income.signum() > 0) {
                    AmountText(sum.income, s.mainCurrency, TxnType.INCOME)
                }
            }
        }
        Text(
            buildString {
                val face = moodFace(sum.averageMood?.score)
                append(if (sum.moodDays > 0) "Mostly ${face?.label?.lowercase() ?: "okay"} · ${sum.moodDays} days logged" else "No moods logged yet")
                sum.topCategory?.let { append(" · most on ${it.emoji} ${it.label}") }
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Tokens.Space.sm),
        )
        if (sum.hasUnconverted) {
            Text("* some entries are waiting for an exchange rate", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MonthGrid(s: CalendarState, onOpenDay: (LocalDate) -> Unit) {
    val month = s.anchor.monthValue
    val maxSpent = s.cells.values.filter { it.date.monthValue == month }.maxOfOrNull { it.spent } ?: BigDecimal.ZERO
    Column(Modifier.padding(horizontal = gutter - Tokens.Space.xs)) {
        Row {
            DayOfWeek.entries.forEach {
                Text(
                    it.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
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

/** DESIGN.md `calendar-day` / `calendar-day-today`, heat-tinted with pink as spending grows. */
@Composable
private fun MonthCell(cell: DayCell, inMonth: Boolean, maxSpent: BigDecimal, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val isToday = cell.date == LocalDate.now()
    val heat = if (maxSpent.signum() > 0) (cell.spent.toFloat() / maxSpent.toFloat()).coerceIn(0f, 1f) else 0f
    val bg = when {
        isToday -> scheme.primaryContainer
        !inMonth -> Color.Transparent
        else -> lerp(scheme.surfaceContainerLow, scheme.primaryContainer, heat * 0.7f)
    }
    val fg = if (isToday) scheme.onPrimaryContainer else scheme.onSurface
    val alpha = if (inMonth) 1f else 0.35f
    Box(
        modifier
            .aspectRatio(0.74f)
            .padding(Tokens.Space.xs / 2)
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(bg)
            .clickable(onClick = onClick),
    ) {
        Text(
            "${cell.date.dayOfMonth}", style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = alpha),
            modifier = Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 4.dp),
        )
        cell.tripMark?.let {
            Text(it, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp, top = 3.dp))
        }
        Box(Modifier.align(Alignment.Center).padding(top = 6.dp)) {
            when {
                cell.mood != null -> MoodIcon(cell.mood.score, 28.dp)
                cell.stickerPath != null -> StickerImage(cell.stickerPath, 30.dp)
                cell.notePreview.isNotEmpty() || cell.photoPath != null -> Text("📝", fontSize = 16.sp)
            }
        }
        if (cell.spent.signum() > 0) {
            Text(
                compact(cell.spent), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.8f * alpha),
                maxLines = 1, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun WeekRow(cell: DayCell, currency: String, onClick: () -> Unit) {
    val isToday = cell.date == LocalDate.now()
    PaperCard(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = gutter), padded = false) {
        Row(Modifier.padding(Tokens.Space.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    cell.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("${cell.date.dayOfMonth}", style = MaterialTheme.typography.headlineMedium)
                cell.tripMark?.let { Text(it, fontSize = 13.sp) }
            }
            Box(Modifier.padding(horizontal = Tokens.Space.sm)) {
                when {
                    cell.mood != null -> MoodIcon(cell.mood.score, 40.dp)
                    cell.stickerPath != null -> StickerImage(cell.stickerPath, 40.dp)
                    else -> MoodIcon(null, 40.dp, fallback = "·")
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    cell.notePreview.ifEmpty { "Nothing written yet" },
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (cell.notePreview.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.sm)) {
                    if (cell.spent.signum() > 0) AmountText(cell.spent, currency)
                    if (cell.income.signum() > 0) AmountText(cell.income, currency, TxnType.INCOME)
                }
            }
            cell.photoPath?.let {
                AsyncImage(
                    model = File(it), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.padding(start = Tokens.Space.sm).size(56.dp).clip(RoundedCornerShape(Tokens.Radius.md)),
                )
            }
        }
    }
}
