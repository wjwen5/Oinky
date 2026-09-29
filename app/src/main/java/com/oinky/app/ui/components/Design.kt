package com.oinky.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.oinky.app.ui.theme.LocalMoneyColors
import com.oinky.app.ui.theme.Tokens
import com.oinky.core.Currencies
import com.oinky.core.TxnType
import java.math.BigDecimal

// Components from DESIGN.md. Screens should use these rather than raw Material defaults so the
// Piggy Scrapbook look stays consistent.

/** DESIGN.md `card`: white paper on the cream page, 24px corners, no border or shadow. */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val inner: @Composable ColumnScope.() -> Unit = {
        if (padded) Column(Modifier.padding(Tokens.Space.cardPadding), content = content) else content()
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, content = inner)
    } else {
        Card(modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, content = inner)
    }
}

/** DESIGN.md `card-hero`: blush, 32px corners, for the one headline number on a screen. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) { Column(Modifier.padding(Tokens.Space.lg), content = content) }
}

/** Fredoka section title with the standard gutter. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(start = Tokens.Space.gutter, end = Tokens.Space.gutter, top = Tokens.Space.sm),
    )
}

/** DESIGN.md `amount-expense` / `amount-income`: tabular numerals, mint "+" for income. */
@Composable
fun AmountText(
    amount: BigDecimal,
    currency: String,
    type: TxnType = TxnType.EXPENSE,
    style: TextStyle = Tokens.Type.amountMd,
    signed: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val money = LocalMoneyColors.current
    val sign = when {
        !signed -> ""
        type == TxnType.INCOME -> "+"
        else -> "−"
    }
    Text(
        sign + Currencies.format(amount, currency),
        style = style,
        color = if (type == TxnType.INCOME) money.income else money.expense,
        modifier = modifier,
    )
}

/** DESIGN.md `badge-trip`: coin-gold pill, e.g. "🇯🇵 Day 3". */
@Composable
fun TripBadge(text: String, modifier: Modifier = Modifier) {
    Pill(text, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, modifier)
}

@Composable
fun Pill(text: String, background: Color, content: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = modifier.clip(CircleShape).background(background)
            .padding(horizontal = Tokens.Space.sm + Tokens.Space.xs, vertical = Tokens.Space.xs),
    )
}
