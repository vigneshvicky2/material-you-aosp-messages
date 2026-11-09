@file:OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)

package com.rodin.messages.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rodin.messages.data.Category
import com.rodin.messages.data.Contact
import com.rodin.messages.data.Conversation
import com.rodin.messages.data.Message
import com.rodin.messages.data.MsgStatus
import com.rodin.messages.data.ScheduledMessage
import com.rodin.messages.data.SimInfo
import com.rodin.messages.data.SimRepo
import com.rodin.messages.data.SmsRepo
import com.rodin.messages.notify.Notifier
import com.rodin.messages.sms.Mms
import com.rodin.messages.sms.Scheduler
import com.rodin.messages.sms.SmsSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = SmsRepo.get(app)
    val prefs = repo.prefs
    private val simRepo = SimRepo(app)

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _isDefault = MutableStateFlow(repo.isDefaultSmsApp())
    val isDefault: StateFlow<Boolean> = _isDefault.asStateFlow()

    private val _sims = MutableStateFlow<List<SimInfo>>(emptyList())
    val sims: StateFlow<List<SimInfo>> = _sims.asStateFlow()

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _starred = MutableStateFlow<List<Pair<Conversation, Message>>>(emptyList())
    val starred: StateFlow<List<Pair<Conversation, Message>>> = _starred.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Pair<Conversation, Message?>>>(emptyList())
    val searchResults: StateFlow<List<Pair<Conversation, Message?>>> = _searchResults.asStateFlow()
    private val searchQuery = MutableStateFlow("")

    private val _blocked = MutableStateFlow<List<String>>(emptyList())
    val blocked: StateFlow<List<String>> = _blocked.asStateFlow()

    init {
        merge(repo.changes(), prefs.version.map { })
            .debounce(120)
            .onEach { reload() }
            .flowOn(Dispatchers.IO)
            .launchIn(viewModelScope)
        searchQuery.debounce(200).onEach { q ->
            _searchResults.value = withContext(Dispatchers.IO) { repo.search(q) }
        }.launchIn(viewModelScope)
    }

    private suspend fun reload() = withContext(Dispatchers.IO) {
        runCatching { _conversations.value = repo.conversations() }
        runCatching { _starred.value = repo.starredMessages() }
        _loaded.value = true
    }

    fun refreshEnvironment() {
        _isDefault.value = repo.isDefaultSmsApp()
        viewModelScope.launch(Dispatchers.IO) {
            repo.contacts.clearCache()
            _sims.value = simRepo.sims()
            _contacts.value = repo.contacts.all()
            _blocked.value = repo.blockedNumbers()
            reload()
        }
    }

    fun search(q: String) { searchQuery.value = q }

    private fun io(block: () -> Unit) = viewModelScope.launch(Dispatchers.IO) { block() }

    fun setPinned(c: Conversation, on: Boolean) = prefs.setPinned(c.threadId, on)
    fun setArchived(c: Conversation, on: Boolean) = prefs.setArchived(c.threadId, on)
    fun setMuted(c: Conversation, on: Boolean) = prefs.setMuted(c.threadId, on)
    fun markRead(c: Conversation) = io { repo.markRead(c.threadId); Notifier.cancel(getApplication(), c.threadId) }
    fun markUnread(c: Conversation) = io { repo.markUnread(c.threadId) }
    fun delete(c: Conversation) = io { repo.deleteThread(c.threadId) }
    fun markSpam(c: Conversation, spam: Boolean) = prefs.setCategory(c.threadId, if (spam) Category.SPAM else Category.PERSONAL)
    fun block(c: Conversation) = io {
        c.addresses.forEach { repo.block(it) }
        prefs.setCategory(c.threadId, Category.SPAM)
        _blocked.value = repo.blockedNumbers()
    }
    fun unblock(n: String) = io { repo.unblock(n); _blocked.value = repo.blockedNumbers() }
    fun unstar(m: Message) = prefs.setStarred(m.key, false)

    fun threadFor(addresses: List<String>, onResult: (Long) -> Unit) = viewModelScope.launch {
        val id = withContext(Dispatchers.IO) { repo.threadIdFor(addresses) }
        onResult(id)
    }
}

class ThreadViewModel(app: Application, val threadId: Long, initialAddresses: List<String>) : AndroidViewModel(app) {
    val repo = SmsRepo.get(app)
    val prefs = repo.prefs

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _conv = MutableStateFlow<Conversation?>(null)
    val conversation: StateFlow<Conversation?> = _conv.asStateFlow()

    private val _addresses = MutableStateFlow(initialAddresses)
    val addresses: StateFlow<List<String>> = _addresses.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val sims = MutableStateFlow<List<SimInfo>>(emptyList())

    init {
        merge(repo.changes(), prefs.version.map { })
            .debounce(80)
            .onEach { reload() }
            .launchIn(viewModelScope)
        viewModelScope.launch(Dispatchers.IO) { sims.value = SimRepo(app).sims() }
    }

    private suspend fun reload() = withContext(Dispatchers.IO) {
        val conv = repo.conversation(threadId)
        _conv.value = conv
        if (conv != null) _addresses.value = conv.addresses
        val msgs = repo.messages(threadId)
        _messages.value = msgs
        _loaded.value = true
        if (msgs.any { !it.outgoing && !it.read }) {
            repo.markRead(threadId)
            Notifier.cancel(getApplication(), threadId)
        }
    }

    fun contactFor(address: String): Contact = repo.contacts.lookup(address)

    fun send(text: String, images: List<Uri>, subId: Int) {
        if (com.rodin.messages.data.Demo.on) return
        val addrs = _addresses.value
        if (addrs.isEmpty() || (text.isBlank() && images.isEmpty())) return
        prefs.setDraft(threadId, "")
        viewModelScope.launch(Dispatchers.IO) {
            if (images.isNotEmpty()) Mms.send(getApplication(), addrs, text, images, subId)
            else SmsSender.send(getApplication(), addrs, text.trim(), subId)
        }
    }

    fun schedule(text: String, time: Long, subId: Int) {
        if (com.rodin.messages.data.Demo.on) return
        val addrs = _addresses.value
        if (addrs.isEmpty() || text.isBlank()) return
        prefs.setDraft(threadId, "")
        Scheduler.schedule(getApplication(), ScheduledMessage(System.currentTimeMillis(), threadId, addrs, text.trim(), time, subId))
    }

    fun retry(m: Message) = viewModelScope.launch(Dispatchers.IO) {
        if (com.rodin.messages.data.Demo.on) return@launch
        if (m.isMms) return@launch
        SmsSender.retry(getApplication(), m.id)
    }

    fun delete(m: Message) {
        if (m.status == MsgStatus.SCHEDULED) Scheduler.cancel(getApplication(), m.id)
        else viewModelScope.launch(Dispatchers.IO) { repo.deleteMessage(m) }
    }

    fun sendScheduledNow(m: Message) = viewModelScope.launch(Dispatchers.IO) {
        if (com.rodin.messages.data.Demo.on) return@launch
        prefs.scheduled().firstOrNull { it.id == m.id }?.let { Scheduler.sendNow(getApplication(), it) }
    }

    fun toggleStar(m: Message) = prefs.setStarred(m.key, !m.starred)
    fun react(m: Message, emoji: String?) = prefs.setReaction(m.key, if (m.reaction == emoji) null else emoji)

    fun draft(): String = prefs.draft(threadId)
    fun saveDraft(text: String) = prefs.setDraft(threadId, text)
}
