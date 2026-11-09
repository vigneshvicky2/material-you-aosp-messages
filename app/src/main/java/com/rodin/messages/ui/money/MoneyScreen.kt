@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.rodin.messages.ui.money

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rodin.messages.finance.Cat
import com.rodin.messages.finance.Direction
import com.rodin.messages.finance.Txn
import com.rodin.messages.finance.formatRupees
import com.rodin.messages.ui.components.EmptyState
import com.rodin.messages.ui.components.SectionLabel
import com.rodin.messages.ui.components.groupShape

val CreditGreen = Color(0xFF5DBB63)

fun LazyListScope.moneyTab(
    s: MoneyState,
    vm: MoneyViewModel,
    onSeeAll: (Cat?) -> Unit,
    onTxn: (Txn) -> Unit,
) {
    item(key = "m_sample") {
        AnimatedVisibility(s.sample, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Surface(
                shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            ) {
                Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Sample data, not your real transactions", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                    TextButton({ vm.setSample(false) }) { Text("Exit") }
                }
            }
        }
    }
    item(key = "m_month") { MonthSwitcher(s.monthLabel, s.monthOffset < 0, vm::shiftMonth) }
    item(key = "m_import") {
        AnimatedVisibility(s.importing, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            ImportCard(s.importDone, s.importTotal)
        }
    }
    val sum = s.summary
    if (sum == null) return
    item(key = "m_hero") { HeroCard(s) }

    if (sum.txnCount == 0 && !s.importing) {
        item(key = "m_empty") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(Icons.Rounded.AccountBalanceWallet, "No transactions this month",
                    "Bank SMS from IOB, SBI, super.money and others will show up here automatically")
                if (!s.sample) androidx.compose.material3.FilledTonalButton({ vm.setSample(true) }) { Text("Preview with sample data") }
            }
        }
        return
    }

    item(key = "m_daily") {
        Card {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Daily spending", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    val max = sum.dailySpent.maxOrNull() ?: 0
                    if (max > 0) Text("Peak ${formatRupees(max)}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                DailyBars(sum.dailySpent, s.todayIndex)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text("1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("${sum.dailySpent.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (sum.byCategory.isNotEmpty()) {
        item(key = "m_cat_label") { SectionLabel("Where it went") }
        item(key = "m_cats") {
            Card {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    CategoryRows(sum.byCategory.take(6), sum.spentPaise) { onSeeAll(it) }
                    if (sum.byCategory.size > 6) {
                        TextButton({ onSeeAll(null) }, Modifier.align(Alignment.End)) { Text("All categories") }
                    }
                }
            }
        }
    }

    if (s.accounts.isNotEmpty()) {
        item(key = "m_acc_label") { SectionLabel("Accounts & cards") }
        item(key = "m_acc") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(s.accounts, key = { it.account }) { a -> AccountCard(a.bank, a.account, a.balancePaise, a.balanceTime, a.txnCount) }
            }
        }
    }

    if (sum.topMerchants.isNotEmpty()) {
        item(key = "m_top_label") { SectionLabel("Top merchants") }
        item(key = "m_top") {
            Card {
                Column(Modifier.padding(16.dp)) {
                    sum.topMerchants.forEachIndexed { i, (name, amt) ->
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatRupees(amt), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }

    item(key = "m_recent_label") {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Recent", Modifier.weight(1f))
            TextButton({ onSeeAll(null) }, Modifier.padding(top = 12.dp)) { Text("See all ${sum.txnCount}") }
        }
    }
    val recent = s.monthTxns.take(8)
    recent.forEachIndexed { i, t ->
        item(key = "m_t_${t.id}") { TxnRow(t, groupShape(i, recent.size), Modifier.animateItem()) { onTxn(t) } }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) { content() }
}

@Composable
private fun MonthSwitcher(label: String, canNext: Boolean, onShift: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = { onShift(-1) }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month") }
        }
        AnimatedContent(label, Modifier.weight(1f), transitionSpec = {
            (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
        }, label = "month", contentAlignment = Alignment.Center) { l ->
            Text(l, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        val a by animateFloatAsState(if (canNext) 1f else 0.3f, label = "n")
        Surface(onClick = { if (canNext) onShift(1) }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(44.dp).graphicsLayer { alpha = a }) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month") }
        }
    }
}

@Composable
private fun ImportCard(done: Int, total: Int) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Sms, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(12.dp))
                Text("Reading your bank messages…", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
                if (total > 0) Text("$done / $total", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.height(12.dp))
            val p by animateFloatAsState(if (total > 0) done.toFloat() / total else 0f, tween(300), label = "p")
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)))
        }
    }
}

@Composable
private fun HeroCard(s: MoneyState) {
    val sum = s.summary ?: return
    Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Spent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                AnimatedRupees(sum.spentPaise, MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
                    MaterialTheme.colorScheme.onPrimaryContainer)
                if (s.prevSpentPaise > 0) {
                    val pct = ((sum.spentPaise - s.prevSpentPaise) * 100 / s.prevSpentPaise).toInt()
                    val up = pct > 0
                    Row(
                        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (up) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, null, Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(" ${kotlin.math.abs(pct)}% vs last month", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Received", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                        AnimatedRupees(sum.receivedPaise, MaterialTheme.typography.titleMedium, CreditGreen)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Transactions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                        Text("${sum.txnCount}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            SpendRing(sum.spentPaise, sum.receivedPaise)
        }
    }
}

@Composable
private fun AccountCard(bank: String?, account: String, balance: Long?, balTime: Long?, count: Int) {
    Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.width(210.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center) {
                    Text((bank ?: "₹").take(1), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(bank ?: "Account", style = MaterialTheme.typography.titleSmall)
                    Text("•• $account", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            if (balance != null) {
                AnimatedRupees(balance, MaterialTheme.typography.titleLarge)
                Text("as of ${DateFormat.format("d MMM, h:mm a", balTime ?: 0)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Balance not in SMS", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$count transactions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TxnRow(t: Txn, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val dim = !t.counts
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp).graphicsLayer { alpha = if (dim) 0.55f else 1f },
            verticalAlignment = Alignment.CenterVertically) {
            CatIcon(t.category)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, false))
                    if (t.confidence < 0.7f) Icon(Icons.Rounded.HelpOutline, "Please check", Modifier.padding(start = 4.dp).size(14.dp),
                        tint = MaterialTheme.colorScheme.tertiary)
                }
                val meta = listOfNotNull(
                    t.channel.label,
                    listOfNotNull(t.bank, t.account?.let { "••$it" }).joinToString(" ").ifBlank { null },
                    DateFormat.getTimeFormat(ctx).format(t.time).lowercase(),
                ).joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                (if (t.direction == Direction.CREDIT) "+" else "−") + formatRupees(t.amountPaise),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold,
                    textDecoration = if (dim) TextDecoration.LineThrough else null),
                color = if (t.direction == Direction.CREDIT) CreditGreen else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Detail + edit sheet for one transaction. */
@Composable
fun TxnSheet(t: Txn, vm: MoneyViewModel, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cat by remember(t.id) { mutableStateOf(t.category) }
    var rememberRule by remember { mutableStateOf(true) }
    var excluded by remember(t.id) { mutableStateOf(t.excluded) }
    var note by remember(t.id) { mutableStateOf(t.note ?: "") }
    ModalBottomSheet(onDismissRequest = {
        if (note != (t.note ?: "")) vm.setNote(t, note)
        onDismiss()
    }, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(cat, label = "ci") { c -> CatIcon(c, 56.dp) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                    Text(DateFormat.format("EEE, d MMM yyyy · h:mm a", t.time).toString(), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                (if (t.direction == Direction.CREDIT) "+" else "−") + formatRupees(t.amountPaise, decimals = true),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (t.direction == Direction.CREDIT) CreditGreen else MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(20.dp))
            Text("Category", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Cat.entries.forEach { c ->
                    val sel = c == cat
                    val bg by animateColorAsState(if (sel) c.tint.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainerHigh, label = "b")
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(bg).clickable {
                            cat = c; vm.setCategory(t, c, rememberRule)
                        }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(c.icon, null, Modifier.size(16.dp), tint = c.tint)
                        Spacer(Modifier.width(6.dp))
                        Text(c.label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            val key = t.vpa ?: t.counterparty
            if (key != null) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Always use this category for ${t.title}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(rememberRule, { rememberRule = it })
                }
            }

            Spacer(Modifier.height(12.dp))
            InfoRows(
                listOfNotNull(
                    "Paid via" to t.channel.label,
                    (t.bank ?: t.account)?.let { "Account" to listOfNotNull(t.bank, t.account?.let { "••$it" }).joinToString(" ") },
                    t.vpa?.let { "UPI ID" to it },
                    t.ref?.let { "Reference" to it },
                    t.balancePaise?.let { "Balance after" to formatRupees(it, decimals = true) },
                    "From SMS" to t.sender,
                ),
            )

            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                BasicTextField(
                    note, { note = it }, Modifier.padding(16.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box { if (note.isEmpty()) Text("Add a note", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() }
                    },
                )
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Exclude from totals", style = MaterialTheme.typography.bodyLarge)
                    Text("For self transfers, failed payments or wrong reads", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(excluded, { excluded = it; vm.setExcluded(t, it) })
            }

            Text("Original message", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Text(t.body, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun InfoRows(rows: List<Pair<String, String>>) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            rows.forEach { (k, v) ->
                Row(Modifier.padding(vertical = 8.dp)) {
                    Text(k, Modifier.width(120.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

