@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)

package com.moodledger.app.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moodledger.app.AppContainer
import com.moodledger.app.ui.components.containerViewModel
import com.moodledger.app.ui.components.money
import com.moodledger.core.Category
import com.moodledger.core.DaySummary
import com.moodledger.core.MoodInsights
import com.moodledger.core.MoodSpending
import com.moodledger.core.TxnType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class InsightsState(
    val month: YearMonth = YearMonth.now(),
    val main: String = "SGD",
    val spent: BigDecimal = BigDecimal.ZERO,
    val income: BigDecimal = BigDecimal.ZERO,
    val byCategory: List<Pair<Category, BigDecimal>> = emptyList(),
    val byCurrency: List<Pair<String, BigDecimal>> = emptyList(),
    val byMood: List<MoodSpending> = emptyList(),
    val headline: String? = null,
)

class InsightsViewModel(c: AppContainer) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())

    val state: StateFlow<InsightsState> = month.flatMapLatest { ym ->
        val from = ym.atDay(1).toEpochDay()
        val to = ym.atEndOfMonth().toEpochDay()
        // Mood/spending correlation looks back 90 days for enough data points.
        val moodFrom = ym.atEndOfMonth().minusDays(89).toEpochDay()
        combine(
            c.ledger.observeTxns(moodFrom, to),
            c.ledger.observeDays(moodFrom, to),
            c.settings.mainCurrency,
        ) { txns, days, main ->
            val monthTxns = txns.filter { it.epochDay in from..to }
            val expenses = monthTxns.filter { it.type == TxnType.EXPENSE }
            val spentByDay = txns.filter { it.type == TxnType.EXPENSE }.groupBy { it.epochDay }
                .mapValues { (_, l) -> l.sumOf { it.baseAmount } }
            val summaries = days.map { DaySummary(it.mood, spentByDay[it.epochDay] ?: BigDecimal.ZERO) }
            val byMood = MoodInsights.spendingByMood(summaries)
            InsightsState(
                month = ym, main = main,
                spent = expenses.sumOf { it.baseAmount },
                income = monthTxns.filter { it.type == TxnType.INCOME }.sumOf { it.baseAmount },
                byCategory = expenses.groupBy { it.category }.map { (k, l) -> k to l.sumOf { it.baseAmount } }
                    .sortedByDescending { it.second },
                byCurrency = expenses.groupBy { it.currency }.map { (k, l) -> k to l.sumOf { it.amount } }
                    .sortedByDescending { it.second },
                byMood = byMood,
                headline = MoodInsights.headline(byMood),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsState())

    fun shift(months: Long) { month.value = month.value.plusMonths(months) }
}

@Composable
fun InsightsScreen() {
    val vm = containerViewModel { InsightsViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))) },
                navigationIcon = { IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.Filled.ChevronLeft, "Previous month") } },
                actions = {
                    IconButton(onClick = { vm.shift(1) }, enabled = s.month < YearMonth.from(LocalDate.now())) {
                        Icon(Icons.Filled.ChevronRight, "Next month")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp)) {
                        Stat("Spent", money(s.spent, s.main), Modifier.weight(1f))
                        Stat("Income", money(s.income, s.main), Modifier.weight(1f))
                        Stat("Net", money(s.income - s.spent, s.main), Modifier.weight(1f))
                    }
                }
            }
            item {
                Section("Mood × money (last 90 days)") {
                    s.headline?.let { Text(it, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)) }
                    if (s.byMood.isEmpty()) Text("Log moods on a few days to see how they relate to spending.")
                    val max = s.byMood.maxOfOrNull { it.averageSpent } ?: BigDecimal.ONE
                    s.byMood.forEach { m ->
                        BarRow(
                            leading = m.mood.emoji, label = "${m.days} days",
                            value = "${money(m.averageSpent, s.main)}/day", fraction = fraction(m.averageSpent, max),
                        )
                    }
                }
            }
            item {
                Section("By category") {
                    if (s.byCategory.isEmpty()) Text("No spending this month yet.")
                    val max = s.byCategory.firstOrNull()?.second ?: BigDecimal.ONE
                    s.byCategory.forEach { (cat, amt) ->
                        BarRow(cat.emoji, cat.label, money(amt, s.main), fraction(amt, max))
                    }
                }
            }
            if (s.byCurrency.size > 1) {
                item {
                    Section("Spent in original currencies") {
                        s.byCurrency.forEach { (code, amt) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(code, modifier = Modifier.weight(1f))
                                Text(money(amt, code))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun fraction(v: BigDecimal, max: BigDecimal) =
    if (max.signum() == 0) 0f else (v.toFloat() / max.toFloat()).coerceIn(0.02f, 1f)

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) = Column(modifier) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
        content()
    }
}

@Composable
private fun BarRow(leading: String, label: String, value: String, fraction: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(leading, fontSize = 20.sp, modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
