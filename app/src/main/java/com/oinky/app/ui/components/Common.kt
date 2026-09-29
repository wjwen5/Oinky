package com.oinky.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.oinky.app.AppContainer
import com.oinky.app.OinkyApp
import com.oinky.core.Currencies
import java.math.BigDecimal

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as OinkyApp).container

/** viewModel() with a factory that receives the app container. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

fun money(amount: BigDecimal, code: String) = Currencies.format(amount, code)

/** Compact amount for calendar cells: 1234 -> 1.2k. */
fun compact(amount: BigDecimal): String {
    val v = amount.toDouble()
    return when {
        v >= 100_000 -> "%.0fk".format(v / 1000)
        v >= 1000 -> "%.1fk".format(v / 1000)
        v >= 100 -> "%.0f".format(v)
        else -> "%.0f".format(v).let { if (v > 0 && it == "0") "<1" else it }
    }
}

@Composable
fun CurrencyPicker(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, extra: List<String> = emptyList()) {
    var open by remember { mutableStateOf(false) }
    val options = (listOf(selected) + extra + Currencies.common).distinct()
    Box(modifier) {
        OutlinedButton(onClick = { open = true }) {
            Text(selected)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose currency")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { code ->
                DropdownMenuItem(
                    text = { Text("$code  ${Currencies.symbols[code].orEmpty()}") },
                    onClick = { onSelect(code); open = false },
                )
            }
        }
    }
}
