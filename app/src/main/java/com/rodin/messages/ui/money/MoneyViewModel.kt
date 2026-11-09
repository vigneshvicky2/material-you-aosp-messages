@file:OptIn(FlowPreview::class)

package com.rodin.messages.ui.money

import android.app.Application
import android.text.format.DateFormat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rodin.messages.data.SmsRepo
import com.rodin.messages.finance.AccountSummary
import com.rodin.messages.finance.Cat
import com.rodin.messages.finance.FinanceRepo
import com.rodin.messages.finance.MonthSummary
import com.rodin.messages.finance.SampleData
import com.rodin.messages.finance.UpiCaptureService
import com.rodin.messages.finance.Txn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class MoneyState(
    val ready: Boolean = false,
    val importing: Boolean = false,
    val importDone: Int = 0,
    val importTotal: Int = 0,
    val monthOffset: Int = 0,
    val monthLabel: String = "",
    val monthStart: Long = 0,
    val summary: MonthSummary? = null,
    val prevSpentPaise: Long = 0,
    val monthTxns: List<Txn> = emptyList(),
    val accounts: List<AccountSummary> = emptyList(),
    val todayIndex: Int = -1,
    val sample: Boolean = false,
    val upiCaptureOn: Boolean = false,
    val upiLearning: Boolean = false,
)

class MoneyViewModel(app: Application) : AndroidViewModel(app) {
    val repo = FinanceRepo.get(app)
    private val sms = SmsRepo.get(app)

    private val _state = MutableStateFlow(MoneyState())
    val state: StateFlow<MoneyState> = _state.asStateFlow()

    private val _search = MutableStateFlow<List<Txn>>(emptyList())
    val searchResults: StateFlow<List<Txn>> = _search.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val first = !repo.hasImported()
            if (first) _state.value = _state.value.copy(importing = true)
            runCatching {
                repo.importNew { d, t -> _state.value = _state.value.copy(importDone = d, importTotal = t) }
            }
            _state.value = _state.value.copy(importing = false)
            reload()
        }
        // New SMS (even ones received while another app was default) → incremental import
        sms.changes().drop(1).debounce(1200).onEach {
            withContext(Dispatchers.IO) { runCatching { repo.importNew() } }
        }.launchIn(viewModelScope)
        repo.version.drop(1).debounce(100).onEach { reload() }.launchIn(viewModelScope)
    }

    private fun monthBounds(offset: Int): Triple<Long, Long, Int> {
        val c = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, offset)
        }
        val start = c.timeInMillis
        val days = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        c.add(Calendar.MONTH, 1)
        return Triple(start, c.timeInMillis, days)
    }

    private suspend fun reload() = withContext(Dispatchers.IO) {
        val off = _state.value.monthOffset
        val (start, end, days) = monthBounds(off)
        val (pStart, pEnd, pDays) = monthBounds(off - 1)
        val today = if (off == 0) Calendar.getInstance().get(Calendar.DAY_OF_MONTH) - 1 else -1
        val sample = _state.value.sample
        val txns = if (sample) sampleCache.getOrPut(start) { SampleData.month(start, days, today) } else repo.between(start, end)
        val summary = repo.summarize(txns, days)
        val prevTxns = if (sample) sampleCache.getOrPut(pStart) { SampleData.month(pStart, pDays, -1) } else repo.between(pStart, pEnd)
        val prev = repo.summarize(prevTxns, pDays)
        _state.value = _state.value.copy(
            ready = true,
            monthOffset = off,
            monthLabel = DateFormat.format("MMMM yyyy", start).toString(),
            monthStart = start,
            summary = summary,
            prevSpentPaise = prev.spentPaise,
            monthTxns = txns,
            accounts = if (sample) SampleData.accounts(txns) else repo.accounts(),
            todayIndex = today,
        )
    }

    private val sampleCache = java.util.concurrent.ConcurrentHashMap<Long, List<Txn>>()

    /** In-memory sample month so the screens can be previewed before real bank SMS exist. */
    fun setSample(on: Boolean) {
        sampleCache.clear()
        _state.value = _state.value.copy(sample = on, monthOffset = 0)
        viewModelScope.launch { reload() }
    }

    /** Edits on sample rows (negative ids) stay in memory and never touch the database. */
    private fun sampleEdit(t: Txn, change: (Txn) -> Txn): Boolean {
        if (t.id >= 0) return false
        sampleCache.replaceAll { _, list -> list.map { if (it.id == t.id) change(it) else it } }
        viewModelScope.launch { reload() }
        return true
    }

    /** Re-check whether the UPI tracker (accessibility service) is switched on, e.g. after returning from Settings. */
    fun refreshCapture() {
        val app = getApplication<Application>()
        _state.value = _state.value.copy(
            upiCaptureOn = UpiCaptureService.isEnabled(app),
            upiLearning = UpiCaptureService.learning(app),
        )
    }

    fun setLearning(on: Boolean) {
        UpiCaptureService.setLearning(getApplication(), on)
        refreshCapture()
    }

    fun shiftMonth(delta: Int) {
        val next = (_state.value.monthOffset + delta).coerceAtMost(0)
        if (next == _state.value.monthOffset) return
        _state.value = _state.value.copy(monthOffset = next)
        viewModelScope.launch { reload() }
    }

    fun search(q: String) = viewModelScope.launch(Dispatchers.IO) {
        _search.value = if (q.isBlank()) emptyList() else repo.search(q)
    }

    fun setCategory(t: Txn, c: Cat, remember: Boolean) {
        if (sampleEdit(t) { it.copy(category = c) }) return
        viewModelScope.launch(Dispatchers.IO) { repo.setCategory(t, c, remember) }
    }

    fun setExcluded(t: Txn, ex: Boolean) {
        if (sampleEdit(t) { it.copy(excluded = ex) }) return
        viewModelScope.launch(Dispatchers.IO) { repo.setExcluded(t, ex) }
    }

    fun setNote(t: Txn, note: String) {
        if (sampleEdit(t) { it.copy(note = note) }) return
        viewModelScope.launch(Dispatchers.IO) { repo.setNote(t, note) }
    }

    fun rebuild() = viewModelScope.launch(Dispatchers.IO) {
        _state.value = _state.value.copy(importing = true, importDone = 0)
        runCatching { repo.rebuild { d, t -> _state.value = _state.value.copy(importDone = d, importTotal = t) } }
        _state.value = _state.value.copy(importing = false)
        reload()
    }
}
