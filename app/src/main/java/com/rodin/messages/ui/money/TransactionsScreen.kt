package com.rodin.messages.ui.money

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.rodin.messages.finance.Cat
import com.rodin.messages.finance.Direction
import com.rodin.messages.finance.Txn
import com.rodin.messages.finance.formatRupees
import com.rodin.messages.ui.components.ChipRow
import com.rodin.messages.ui.components.EmptyState
import com.rodin.messages.ui.components.groupShape
import java.util.Calendar

private sealed class TxnFilter(val label: String) {
    data object All : TxnFilter("All")
    data object Spent : TxnFilter("Spent")
    data object Received : TxnFilter("Received")
    data class Category(val cat: Cat) : TxnFilter(cat.label)
}

@Composable
fun TransactionsScreen(vm: MoneyViewModel, initialCat: Cat?, onBack: () -> Unit) {
    val s by vm.state.collectAsState()
    val results by vm.searchResults.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf<TxnFilter>(initialCat?.let { TxnFilter.Category(it) } ?: TxnFilter.All) }
    var open by remember { mutableStateOf<Txn?>(null) }
    LaunchedEffect(q) { vm.search(q) }

    val source = if (q.isBlank()) s.monthTxns else results
    val cats = s.monthTxns.map { it.category }.distinct()
    val filters = listOf(TxnFilter.All, TxnFilter.Spent, TxnFilter.Received) + cats.map { TxnFilter.Category(it) }
    val shown = source.filter {
        when (val f = filter) {
            TxnFilter.All -> true
            TxnFilter.Spent -> it.direction == Direction.DEBIT
            TxnFilter.Received -> it.direction == Direction.CREDIT
            is TxnFilter.Category -> it.category == f.cat
        }
    }
    val byDay = shown.groupBy { dayKey(it.time) }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "top") {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(onClick = onBack, shape = CircleShape, color = Color.Transparent, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    }
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(if (q.isBlank()) s.monthLabel else "All months", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Transactions", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(56.dp)) {
                    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(14.dp))
                        BasicTextField(q, { q = it }, Modifier.weight(1f), singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { inner ->
                                Box { if (q.isEmpty()) Text("Search merchant, UPI ID, note", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() }
                            })
                        if (q.isNotEmpty()) Icon(Icons.Rounded.Close, "Clear",
                            Modifier.size(22.dp).clickable { q = "" }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ChipRow(filters, filter, { it.label }, Modifier.padding(vertical = 8.dp)) { filter = it }
            }
        }
        if (shown.isEmpty()) {
            item(key = "empty") { EmptyState(Icons.Rounded.SearchOff, "Nothing here", "Try another filter or search") }
        }
        byDay.forEach { (day, list) ->
            item(key = "d_$day") {
                val net = list.filter { it.counts }.sumOf { it.signedPaise }
                Row(Modifier.fillMaxWidth().padding(start = 30.dp, end = 30.dp, top = 20.dp, bottom = 8.dp)) {
                    Text(dayLabel(list.first().time), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f))
                    Text((if (net > 0) "+" else "") + formatRupees(net), style = MaterialTheme.typography.labelLarge,
                        color = if (net > 0) CreditGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            list.forEachIndexed { i, t ->
                item(key = "t_${t.id}") { TxnRow(t, groupShape(i, list.size), Modifier.animateItem()) { open = t } }
            }
        }
    }
    open?.let { t -> TxnSheet(t, vm) { open = null } }
}

private fun dayKey(t: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
}

private fun dayLabel(t: Long): String = when {
    android.text.format.DateUtils.isToday(t) -> "Today"
    android.text.format.DateUtils.isToday(t + android.text.format.DateUtils.DAY_IN_MILLIS) -> "Yesterday"
    else -> DateFormat.format("EEE, d MMM", t).toString()
}
